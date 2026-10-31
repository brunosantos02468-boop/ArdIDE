package com.example.arduinoide

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume

object IntelHex {
    /** Converte texto Intel HEX em imagem binária (começa no endereço 0). */
    fun parse(text: String): ByteArray {
        val mem = ByteArray(65536) { 0xFF.toByte() }
        var max = 0
        var base = 0
        for (raw in text.lineSequence()) {
            val l = raw.trim()
            if (!l.startsWith(":")) continue
            val b = l.substring(1).chunked(2).map { it.toInt(16) }
            val len = b[0]
            val addr = (b[1] shl 8) or b[2]
            require(b.size >= len + 5 && (b.sum() and 0xFF) == 0) { "Checksum do arquivo HEX inválido" }
            when (b[3]) {
                0 -> for (i in 0 until len) {
                    val a = base + addr + i
                    require(a < mem.size) { "Arquivo HEX maior que a memória" }
                    mem[a] = b[4 + i].toByte()
                    max = maxOf(max, a + 1)
                }
                4 -> base = ((b[4] shl 8) or b[5]) shl 16
            }
        }
        require(max > 0) { "Arquivo HEX vazio" }
        return mem.copyOf(max)
    }
}

/** Upload para Arduino Uno (Optiboot, STK500v1, 115200) via USB OTG + CH340. */
class UsbUploader(private val ctx: Context) {
    private val action = "com.example.arduinoide.USB_PERMISSION"

    suspend fun upload(image: ByteArray, log: (String) -> Unit) = withContext(Dispatchers.IO) {
        require(image.size <= 32256) { "Programa maior que a memória do Uno (32256 bytes)" }
        val manager = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        val driver = UsbSerialProber.getDefaultProber().findAllDrivers(manager).firstOrNull()
            ?: throw IOException("Nenhuma placa USB encontrada. Conecte o Arduino com o cabo OTG.")
        if (!manager.hasPermission(driver.device) && !requestPermission(manager, driver.device))
            throw IOException("Permissão USB negada")
        val conn = manager.openDevice(driver.device) ?: throw IOException("Não foi possível abrir o USB")
        val port = driver.ports[0]
        try {
            port.open(conn)
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            log("Reiniciando a placa...")
            port.setDTR(false); port.setRTS(false); Thread.sleep(250)
            port.setDTR(true); port.setRTS(true); Thread.sleep(50)
            log("Sincronizando com o bootloader...")
            sync(port)
            send(port, 0x50, 0x20); expectOk(port)
            val page = 128
            var off = 0
            var lastPct = -1
            while (off < image.size) {
                val len = minOf(page, image.size - off)
                setAddress(port, off / 2)
                val cmd = ByteArray(len + 5)
                cmd[0] = 0x64; cmd[1] = (len shr 8).toByte(); cmd[2] = len.toByte(); cmd[3] = 0x46
                System.arraycopy(image, off, cmd, 4, len)
                cmd[len + 4] = 0x20
                port.write(cmd, 2000); expectOk(port)
                off += len
                val pct = off * 100 / image.size / 25 * 25
                if (pct != lastPct) { lastPct = pct; log("Gravando... ${off * 100 / image.size}%") }
            }
            log("Verificando...")
            off = 0
            while (off < image.size) {
                val len = minOf(page, image.size - off)
                setAddress(port, off / 2)
                send(port, 0x74, len shr 8, len and 0xFF, 0x46, 0x20)
                val r = recv(port, len + 2)
                if (r[0] != 0x14.toByte() || r[len + 1] != 0x10.toByte()) throw IOException("Falha na verificação")
                for (i in 0 until len) if (r[i + 1] != image[off + i]) throw IOException("Verificação falhou no byte ${off + i}")
                off += len
            }
            send(port, 0x51, 0x20); expectOk(port)
            log("Carregado com sucesso.")
        } finally {
            runCatching { port.close() }
        }
    }

    private fun setAddress(p: UsbSerialPort, wordAddr: Int) {
        send(p, 0x55, wordAddr and 0xFF, (wordAddr shr 8) and 0xFF, 0x20); expectOk(p)
    }

    private fun sync(p: UsbSerialPort) {
        repeat(10) {
            runCatching { p.purgeHwBuffers(true, true) }
            send(p, 0x30, 0x20)
            val r = runCatching { recv(p, 2, 300) }.getOrNull()
            if (r != null && r[0] == 0x14.toByte() && r[1] == 0x10.toByte()) return
        }
        throw IOException("A placa não respondeu. Confira o cabo, a placa selecionada e o bootloader.")
    }

    private fun expectOk(p: UsbSerialPort) {
        val r = recv(p, 2)
        if (r[0] != 0x14.toByte() || r[1] != 0x10.toByte()) throw IOException("Resposta inesperada da placa")
    }

    private fun send(p: UsbSerialPort, vararg b: Int) = p.write(ByteArray(b.size) { b[it].toByte() }, 2000)

    private fun recv(p: UsbSerialPort, n: Int, timeout: Int = 1500): ByteArray {
        val out = ByteArray(n)
        val buf = ByteArray(256)
        var got = 0
        val end = SystemClock.uptimeMillis() + timeout
        while (got < n && SystemClock.uptimeMillis() < end) {
            val r = p.read(buf, 100)
            val take = minOf(r, n - got)
            if (take > 0) { System.arraycopy(buf, 0, out, got, take); got += take }
        }
        if (got < n) throw IOException("Sem resposta da placa")
        return out
    }

    private suspend fun requestPermission(manager: UsbManager, device: UsbDevice): Boolean =
        suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    runCatching { ctx.unregisterReceiver(this) }
                    if (cont.isActive) cont.resume(i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                }
            }
            ContextCompat.registerReceiver(ctx, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
            val pi = PendingIntent.getBroadcast(ctx, 0, Intent(action).setPackage(ctx.packageName), PendingIntent.FLAG_MUTABLE)
            manager.requestPermission(device, pi)
            cont.invokeOnCancellation { runCatching { ctx.unregisterReceiver(receiver) } }
        }
}
