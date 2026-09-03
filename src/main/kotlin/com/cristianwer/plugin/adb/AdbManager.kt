package com.cristianwer.plugin.adb

import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object AdbManager {

    private val resolvedAdbPath: String by lazy {
        findAdbExecutable()
    }

    private fun findAdbExecutable(): String {
        // 1. Env variables
        val envSdk = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        if (!envSdk.isNullOrBlank()) {
            val adb = File(envSdk, "platform-tools/adb")
            val adbExe = File(envSdk, "platform-tools/adb.exe")
            if (adb.exists()) return adb.absolutePath
            if (adbExe.exists()) return adbExe.absolutePath
        }

        // 2. Standard macOS SDK location
        val userHome = System.getProperty("user.home") ?: ""
        val macAdb = File(userHome, "Library/Android/sdk/platform-tools/adb")
        if (macAdb.exists()) return macAdb.absolutePath

        // 3. Standard Linux SDK location
        val linuxAdb = File(userHome, "Android/Sdk/platform-tools/adb")
        if (linuxAdb.exists()) return linuxAdb.absolutePath

        // 4. Standard Windows SDK location
        val winAdb = File(userHome, "AppData/Local/Android/Sdk/platform-tools/adb.exe")
        if (winAdb.exists()) return winAdb.absolutePath

        // 5. Fallback to system PATH adb
        return "adb"
    }

    fun getAdbPath(): String = resolvedAdbPath

    fun getConnectedDevices(): List<AdbDevice> {
        val output = executeAdbCommand("devices", "-l")
        val devices = mutableListOf<AdbDevice>()

        output.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotBlank() && !trimmed.startsWith("List of devices attached")) {
                val parts = trimmed.split("\\s+".toRegex())
                if (parts.size >= 2) {
                    val serial = parts[0]
                    val state = parts[1]
                    if (state == "device") {
                        var model = ""
                        for (part in parts.drop(2)) {
                            if (part.startsWith("model:")) {
                                model = part.removePrefix("model:")
                            } else if (part.startsWith("device:")) {
                                if (model.isEmpty()) model = part.removePrefix("device:")
                            }
                        }
                        val isEmulator = serial.startsWith("emulator-") || model.contains("sdk_gphone", ignoreCase = true)
                        devices.add(AdbDevice(serial, model, state, isEmulator))
                    }
                }
            }
        }
        return devices
    }

    fun getDumpsysGfxInfo(serial: String, packageName: String): String {
        return executeAdbCommand("-s", serial, "shell", "dumpsys", "gfxinfo", packageName)
    }

    fun resetGfxInfo(serial: String, packageName: String): String {
        return executeAdbCommand("-s", serial, "shell", "dumpsys", "gfxinfo", packageName, "reset")
    }

    fun pullFile(serial: String, remotePath: String, localPath: String): Boolean {
        val result = executeAdbCommand("-s", serial, "pull", remotePath, localPath)
        return !result.contains("error", ignoreCase = true) && !result.contains("0 files pulled")
    }

    fun executeShell(serial: String, command: String): String {
        return executeAdbCommand("-s", serial, "shell", command)
    }

    fun launchProcess(vararg args: String): Process {
        val commandList = mutableListOf(resolvedAdbPath)
        commandList.addAll(args)
        return ProcessBuilder(commandList).redirectErrorStream(true).start()
    }

    private fun executeAdbCommand(vararg args: String): String {
        return try {
            val commandList = mutableListOf(resolvedAdbPath)
            commandList.addAll(args)
            val process = ProcessBuilder(commandList).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor(10, TimeUnit.SECONDS)
            output
        } catch (e: Exception) {
            "Error al ejecutar comando ADB: ${e.message}"
        }
    }
}
