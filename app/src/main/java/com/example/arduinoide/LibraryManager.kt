package com.example.arduinoide

import android.content.Context
import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

data class LibEntry(
    val name: String, val version: String, val author: String, val sentence: String,
    val url: String, val checksum: String, val deps: List<String>
)

data class InstalledLib(val name: String, val version: String)

/** Gerenciador de bibliotecas: busca no índice oficial, instala por HTTPS ou a partir de um .zip local. */
class LibraryManager(private val ctx: Context) {
    private val root = File(ctx.filesDir, "sketchbook/libraries").apply { mkdirs() }
    private val indexFile = File(ctx.filesDir, "library_index.json")
    private var index: Map<String, LibEntry>? = null
    private val maxUnzipped = 100L * 1024 * 1024

    fun dirs(): List<File> = root.listFiles()?.filter { it.isDirectory }.orEmpty()

    suspend fun seedBundled() = withContext(Dispatchers.IO) {
        if (ctx.assets.list("bundled_libraries")?.isNotEmpty() == true) copyAssets("bundled_libraries", root)
    }

    private fun copyAssets(path: String, dest: File) {
        val names = ctx.assets.list(path) ?: return
        if (names.isEmpty()) {
            if (!dest.exists()) {
                dest.parentFile?.mkdirs()
                ctx.assets.open(path).use { i -> dest.outputStream().use { i.copyTo(it) } }
            }
        } else {
            dest.mkdirs()
            names.forEach { copyAssets("$path/$it", File(dest, it)) }
        }
    }

    suspend fun refreshIndex() = withContext(Dispatchers.IO) {
        download("https://downloads.arduino.cc/libraries/library_index.json", indexFile)
        index = null
    }

    suspend fun search(q: String): List<LibEntry> = withContext(Dispatchers.IO) {
        if (q.isBlank()) emptyList()
        else loadIndex().values.filter { it.name.contains(q, true) || it.sentence.contains(q, true) }.take(50)
    }

    suspend fun install(e: LibEntry, depth: Int = 0) {
        withContext(Dispatchers.IO) {
            require(depth < 5) { "Dependências demais" }
            val zip = File(ctx.cacheDir, "lib.zip")
            try {
                download(e.url, zip)
                verifyChecksum(zip, e.checksum)
                extract(zip, e.name)
            } finally { zip.delete() }
            val idx = loadIndex()
            for (d in e.deps) if (!isInstalled(d)) idx[d]?.let { install(it, depth + 1) }
        }
    }

    suspend fun installZip(uri: Uri) = withContext(Dispatchers.IO) {
        val zip = File(ctx.cacheDir, "lib.zip")
        try {
            ctx.contentResolver.openInputStream(uri)?.use { i -> zip.outputStream().use { i.copyTo(it) } }
                ?: throw IOException("Não foi possível ler o arquivo")
            extract(zip, null)
        } finally { zip.delete() }
    }

    suspend fun installed(): List<InstalledLib> = withContext(Dispatchers.IO) { installedSync() }

    private fun installedSync(): List<InstalledLib> = dirs().map {
        val p = props(it)
        InstalledLib(p["name"] ?: it.name, p["version"] ?: "")
    }.sortedBy { it.name.lowercase() }

    private fun isInstalled(name: String) = installedSync().any { it.name.equals(name, true) }

    private fun props(d: File): Map<String, String> =
        File(d, "library.properties").takeIf { it.exists() }?.readLines()?.mapNotNull {
            val i = it.indexOf('=')
            if (i > 0) it.substring(0, i).trim() to it.substring(i + 1).trim() else null
        }?.toMap().orEmpty()

    @Synchronized
    private fun loadIndex(): Map<String, LibEntry> {
        index?.let { return it }
        if (!indexFile.exists()) throw IOException("Índice não baixado. Conecte à internet e toque em Atualizar índice.")
        val map = LinkedHashMap<String, LibEntry>()
        JsonReader(indexFile.bufferedReader()).use { r ->
            r.beginObject()
            while (r.hasNext()) {
                if (r.nextName() == "libraries") {
                    r.beginArray()
                    while (r.hasNext()) readEntry(r)?.let { map[it.name] = it } // última versão listada vence
                    r.endArray()
                } else r.skipValue()
            }
            r.endObject()
        }
        index = map
        return map
    }

    private fun JsonReader.str(): String = if (peek() == JsonToken.NULL) { nextNull(); "" } else nextString()

    private fun readEntry(r: JsonReader): LibEntry? {
        var name = ""; var version = ""; var author = ""; var sentence = ""; var url = ""; var sum = ""
        val deps = mutableListOf<String>()
        r.beginObject()
        while (r.hasNext()) when (r.nextName()) {
            "name" -> name = r.str()
            "version" -> version = r.str()
            "author" -> author = r.str()
            "sentence" -> sentence = r.str()
            "url" -> url = r.str()
            "checksum" -> sum = r.str()
            "dependencies" -> {
                r.beginArray()
                while (r.hasNext()) {
                    r.beginObject()
                    while (r.hasNext()) if (r.nextName() == "name") deps += r.str() else r.skipValue()
                    r.endObject()
                }
                r.endArray()
            }
            else -> r.skipValue()
        }
        r.endObject()
        return if (name.isNotEmpty() && url.startsWith("https://")) LibEntry(name, version, author, sentence, url, sum, deps) else null
    }

    private fun download(url: String, dest: File) {
        require(url.startsWith("https://")) { "Somente HTTPS é permitido" }
        val tmp = File(dest.path + ".part")
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 30000
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            c.inputStream.use { i -> tmp.outputStream().use { i.copyTo(it) } }
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) throw IOException("Falha ao salvar o download")
        } finally { c.disconnect(); tmp.delete() }
    }

    private fun verifyChecksum(f: File, checksum: String) {
        require(checksum.startsWith("SHA-256:")) { "Biblioteca sem checksum SHA-256; instalação recusada" }
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i -> val b = ByteArray(8192); while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n) } }
        val hex = md.digest().joinToString("") { "%02x".format(it) }
        if (!hex.equals(checksum.removePrefix("SHA-256:"), true)) throw SecurityException("Checksum não confere; download descartado")
    }

    private fun safeName(n: String) = n.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    /** Extrai com proteção contra zip-slip e zip-bomb; valida que parece uma biblioteca Arduino. */
    private fun extract(zip: File, libName: String?) {
        val tmp = File(ctx.cacheDir, "extract").apply { deleteRecursively(); mkdirs() }
        val tmpPath = tmp.canonicalPath
        var total = 0L
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                val out = File(tmp, e.name)
                if (!out.canonicalPath.startsWith(tmpPath + File.separator)) throw SecurityException("Entrada de ZIP inválida: ${e.name}")
                if (e.isDirectory) { out.mkdirs(); continue }
                out.parentFile?.mkdirs()
                out.outputStream().use { o ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = zin.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > maxUnzipped) throw IOException("ZIP grande demais")
                        o.write(buf, 0, n)
                    }
                }
            }
        }
        val top = tmp.listFiles()?.singleOrNull()?.takeIf { it.isDirectory } ?: tmp
        val propsFile = File(top, "library.properties")
        val looksLikeLib = propsFile.exists() || File(top, "src").isDirectory || top.listFiles()?.any { it.extension == "h" } == true
        require(looksLikeLib) { "O ZIP não parece uma biblioteca Arduino" }
        val name = libName ?: props(top)["name"] ?: top.name
        val target = File(root, safeName(name))
        target.deleteRecursively()
        if (!top.renameTo(target)) top.copyRecursively(target, true)
        tmp.deleteRecursively()
    }
}
