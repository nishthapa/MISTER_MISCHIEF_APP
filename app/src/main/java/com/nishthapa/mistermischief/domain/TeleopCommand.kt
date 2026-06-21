package com.nishthapa.mistermischief.domain

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class TeleopCommand(
    val joyX: Float,         // -1.0f (Left) to 1.0f (Right)
    val joyY: Float,         // -1.0f (Reverse) to 1.0f (Forward)
    val usePIDDrive: Boolean // True = Heading Hold, False = Raw Arcade Mixing
) {
    /**
     * Serializes our high-level inputs into a compact 9-byte raw payload
     * matching the cross-core memory structures of the ESP32-S3 firmware.
     */
    fun toByteArray(): ByteArray {
        return ByteBuffer.allocate(9).apply {
            order(ByteOrder.LITTLE_ENDIAN) // Matches ESP32 architecture
            putFloat(joyX)
            putFloat(joyY)
            put((if (usePIDDrive) 1 else 0).toByte())
        }.array()
    }
}