package com.cristianwer.plugin.perfetto

import com.cristianwer.plugin.adb.AdbManager
import java.io.File

object PerfettoTraceManager {

    @Volatile
    var isRecording: Boolean = false
        private set

    private var activeProcess: Process? = null
    private var recordingStartTimeMs: Long = 0
    private var activeDeviceSerial: String = ""

    const val REMOTE_TRACE_PATH = "/data/local/traces/trace.perfetto-trace"

    fun startTrace(
        serial: String,
        categories: List<String> = listOf("sched", "freq", "gfx", "view", "am", "wm", "app"),
        bufferSizeKb: Int = 32768,
        onStatusChanged: ((String) -> Unit)? = null
    ): Boolean {
        if (isRecording) {
            onStatusChanged?.invoke("Ya hay una captura en curso.")
            return false
        }

        if (serial.isBlank()) {
            onStatusChanged?.invoke("No se ha seleccionado ningún dispositivo.")
            return false
        }

        try {
            // Ensure remote directory exists
            AdbManager.executeShell(serial, "mkdir -p /data/local/traces")

            val categoriesArg = categories.joinToString(" ")
            // Command format: perfetto --out /data/local/traces/trace.perfetto-trace --buffer 32768k sched freq gfx view ...
            val process = AdbManager.launchProcess(
                "-s", serial,
                "shell",
                "perfetto",
                "--out", REMOTE_TRACE_PATH,
                "--buffer", "${bufferSizeKb}k",
                categoriesArg
            )

            activeProcess = process
            activeDeviceSerial = serial
            recordingStartTimeMs = System.currentTimeMillis()
            isRecording = true

            onStatusChanged?.invoke("Grabación iniciada en el dispositivo ($serial)...")
            return true
        } catch (e: Exception) {
            isRecording = false
            activeProcess = null
            onStatusChanged?.invoke("Error al iniciar traza Perfetto: ${e.message}")
            return false
        }
    }

    fun stopTrace(
        targetLocalDir: String,
        onStatusChanged: ((String) -> Unit)? = null
    ): File? {
        if (!isRecording) {
            onStatusChanged?.invoke("No hay ninguna captura activa.")
            return null
        }

        val serial = activeDeviceSerial
        onStatusChanged?.invoke("Finalizando captura y deteniendo Perfetto...")

        try {
            // Stop Perfetto gracefully on device via SIGINT
            AdbManager.executeShell(serial, "pkill -INT perfetto")

            // Destroy background process on host if still running
            activeProcess?.destroy()
            activeProcess = null

            // Give device 2 seconds to flush buffer and write file
            Thread.sleep(2000)

            val timestamp = System.currentTimeMillis()
            val fileName = "perfetto_trace_$timestamp.perfetto-trace"
            val localFile = File(targetLocalDir, fileName)

            onStatusChanged?.invoke("Descargando traza ($fileName) desde el dispositivo...")

            val success = AdbManager.pullFile(serial, REMOTE_TRACE_PATH, localFile.absolutePath)

            isRecording = false

            return if (success && localFile.exists() && localFile.length() > 0) {
                onStatusChanged?.invoke("Traza guardada exitosamente: ${localFile.absolutePath}")
                localFile
            } else {
                onStatusChanged?.invoke("Warning: No se pudo descargar la traza o el archivo está vacío.")
                if (localFile.exists()) localFile else null
            }
        } catch (e: Exception) {
            isRecording = false
            onStatusChanged?.invoke("Error al detener la traza: ${e.message}")
            return null
        }
    }

    fun getRecordingDurationSeconds(): Long {
        if (!isRecording) return 0
        return (System.currentTimeMillis() - recordingStartTimeMs) / 1000
    }
}
