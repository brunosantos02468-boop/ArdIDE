package com.example.arduinoide

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val SAMPLE = """// Pisca o LED do pino 13
const int led = 13;

void setup() {
  pinMode(led, OUTPUT);
  Serial.begin(9600);
}

void loop() {
  digitalWrite(led, HIGH);
  Serial.println("LED ligado");
  delay(1000);
  digitalWrite(led, LOW);
  delay(1000);
}"""

class IdeViewModel(app: Application) : AndroidViewModel(app) {
    var code by mutableStateOf(SAMPLE)
    var log by mutableStateOf("Pronto.")
    var busy by mutableStateOf(false)
    var showLibraries by mutableStateOf(false)

    var query by mutableStateOf("")
    var results by mutableStateOf<List<LibEntry>>(emptyList())
    var installed by mutableStateOf<List<InstalledLib>>(emptyList())
    var libMessage by mutableStateOf("")

    private val libs = LibraryManager(app)
    private val uploader = UsbUploader(app)
    private val compiler: SketchCompiler = AvrGccCompiler()
    private var lastHex: String? = null

    init {
        viewModelScope.launch { libs.seedBundled(); installed = libs.installed() }
    }

    private fun append(s: String) { log = if (log.isEmpty()) s else log + "\n" + s }

    fun verify() {
        if (busy) return
        viewModelScope.launch {
            busy = true; log = "Compilando sketch..."
            compiler.compile(code, libs.dirs())
                .onSuccess { lastHex = it; append("Compilado com sucesso.") }
                .onFailure { append("Erro: ${it.message}") }
            busy = false
        }
    }

    fun upload() {
        val hex = lastHex
        if (hex == null) append("Compile o sketch antes de carregar, ou use \"Abrir .hex\".") else sendHex(hex)
    }

    fun uploadHexFile(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            }
            if (text == null) append("Não foi possível ler o arquivo.") else sendHex(text)
        }
    }

    private fun sendHex(hex: String) {
        if (busy) return
        viewModelScope.launch {
            busy = true; log = ""
            runCatching { uploader.upload(IntelHex.parse(hex)) { append(it) } }
                .onFailure { append("Erro: ${it.message}") }
            busy = false
        }
    }

    fun refreshIndex() = viewModelScope.launch {
        libMessage = "Baixando índice..."
        libMessage = runCatching { libs.refreshIndex(); "Índice atualizado." }.getOrElse { "Falha: ${it.message}" }
    }

    fun search() = viewModelScope.launch {
        libMessage = ""
        results = runCatching { libs.search(query) }.getOrElse { libMessage = it.message ?: "Erro"; emptyList() }
    }

    fun install(e: LibEntry) = viewModelScope.launch {
        libMessage = "Instalando ${e.name}..."
        libMessage = runCatching { libs.install(e); installed = libs.installed(); "Instalada: ${e.name}" }
            .getOrElse { "Falha: ${it.message}" }
    }

    fun installZip(uri: Uri) = viewModelScope.launch {
        libMessage = "Importando ZIP..."
        libMessage = runCatching { libs.installZip(uri); installed = libs.installed(); "Biblioteca importada." }
            .getOrElse { "Falha: ${it.message}" }
    }
}
