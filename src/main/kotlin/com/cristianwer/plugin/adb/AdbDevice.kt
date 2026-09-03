package com.cristianwer.plugin.adb

data class AdbDevice(
    val serial: String,
    val model: String,
    val state: String,
    val isEmulator: Boolean
) {
    override fun toString(): String {
        val type = if (isEmulator) "[Emulator]" else "[Device]"
        val name = if (model.isNotBlank()) model else serial
        return "$name ($serial) $type"
    }
}
