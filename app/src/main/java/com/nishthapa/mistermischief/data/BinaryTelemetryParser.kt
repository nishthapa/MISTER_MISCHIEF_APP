package com.nishthapa.mistermischief.data

import com.nishthapa.mistermischief.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BinaryTelemetryParser(private val telemetryState: MutableStateFlow<RobotTelemetry>) {

    private val buffer = ByteArrayOutputStream()

    // Hold a local snapshot of the telemetry to prevent UI recomposition spam
    private var localSnapshot = RobotTelemetry()

    private val MODE_MAP = mapOf(
        0 to "BOOTING", 1 to "MANUAL_OVERRIDE", 2 to "NORMAL_DRIVING",
        3 to "OBSTACLE_AVOIDANCE", 4 to "MAINTAIN_DISTANCE", 5 to "COMPASS_LOCK",
        6 to "DIZZY", 7 to "DEEP_SLEEP", 8 to "AUTOTUNE", 9 to "DIAGNOSTICS", 10 to "BRAIN_DEAD"
    )
    private val MOOD_MAP = mapOf(
        0 to "HAPPY", 1 to "SAD", 2 to "ANGRY", 3 to "SCARED",
        4 to "CURIOUS", 5 to "SLEEPY", 6 to "GROGGY"
    )

    fun processIncomingBytes(newBytes: ByteArray) {
        buffer.write(newBytes)
        var data = buffer.toByteArray()
        var stateChanged = false // Track if we actually parsed anything useful

        while (data.size >= 6) { // Minimum packet size
            var headerIdx = -1
            for (i in 0..data.size - 3) {
                if (data[i] == '$'.code.toByte() && data[i + 1] == 'M'.code.toByte() && data[i + 2] == '>'.code.toByte()) {
                    headerIdx = i
                    break
                }
            }

            if (headerIdx == -1) {
                buffer.reset()
                if (data.size > 2) buffer.write(data.takeLast(2).toByteArray())
                break
            }

            if (headerIdx > 0) data = data.drop(headerIdx).toByteArray()

            // Safety check: ensure we have at least 4 bytes to read payloadLen safely
            if (data.size < 4) break
            val payloadLen = data[3].toInt() and 0xFF
            val expectedTotalLength = 6 + payloadLen

            if (data.size >= expectedTotalLength) {
                val msgId = data[4].toInt() and 0xFF
                val payload = data.sliceArray(5 until 5 + payloadLen)
                val checksum = data[5 + payloadLen]

                var calcChecksum = (payloadLen xor msgId)
                for (b in payload) calcChecksum = calcChecksum xor (b.toInt() and 0xFF)

                if (calcChecksum.toByte() == checksum) {
                    // Update our local memory footprint
                    parsePayload(msgId, ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN), payloadLen)
                    stateChanged = true
                }

                data = data.drop(expectedTotalLength).toByteArray()
                buffer.reset()
                buffer.write(data)
            } else {
                buffer.reset()
                buffer.write(data)
                break
            }
        }

        // --- THE FIX: BATCHED UI UPDATE ---
        // Only trigger Jetpack Compose to redraw the screen ONCE per byte chunk!
        if (stateChanged) {
            telemetryState.value = localSnapshot
        }
    }

    private fun parsePayload(msgId: Int, bb: ByteBuffer, payloadLen: Int) {
        try {
            when (msgId) {
                131 -> { // Cognitive State (<BB)
                    val modeId = bb.get().toInt() and 0xFF
                    val moodId = bb.get().toInt() and 0xFF
                    localSnapshot = localSnapshot.copy(cognition = CognitiveState(
                        systemMode = MODE_MAP[modeId] ?: "UNKNOWN($modeId)",
                        robotMood = MOOD_MAP[moodId] ?: "UNKNOWN($moodId)"
                    ))
                }
                101 -> { // Physics State (<ffff?fhh)
                    localSnapshot = localSnapshot.copy(physics = PhysicsState(
                        yaw = bb.float, pitch = bb.float, roll = bb.float,
                        gForce = bb.float,
                        accelX = bb.float, accelY = bb.float, accelZ = bb.float,
                        gyroX = bb.float, gyroY = bb.float, gyroZ = bb.float,
                        hasCompass = bb.get().toInt() != 0, compassHeading = bb.float // <-- ADDED THIS LINE
                        //leftMotorPWM = bb.short, rightMotorPWM = bb.short
                    ))
                }
                104 -> { // Actuators state (II)
                    localSnapshot = localSnapshot.copy(actuators = ActuatorState(
                        leftMotorPWM = bb.short, rightMotorPWM = bb.short,
                        isDriving = bb.get().toInt() != 0
                    ))
                }
                110 -> { // Sensor State (<fHh)
                    localSnapshot = localSnapshot.copy(sensors = SensorState(
                        distanceCM = bb.float, hasBaro = bb.get().toInt() != 0, pressurePa = bb.float,
                        altitudeCM = bb.float, pressureDeltaPa = bb.float, temperatureC = bb.float,
                        batteryVoltageMV = bb.short.toUShort(), currentDrawMA = bb.short
                    ))
                }
                130 -> { // System Health (<IIHBB)
                    localSnapshot = localSnapshot.copy(health = SystemHealth(
                        loopTimeUs = bb.int.toUInt(), freeHeap = bb.int.toUInt(),
                        hardwareBitmask = bb.short.toUShort(),
                        cpu0Load = bb.get().toUByte(), cpu1Load = bb.get().toUByte()
                    ))
                }
                140 -> { // System Logs (String)
                    val textBytes = ByteArray(payloadLen)
                    bb.get(textBytes)
                    // Convert raw bytes to text and strip any trailing null terminators
                    val text = String(textBytes, Charsets.UTF_8).trimEnd('\u0000')
                    localSnapshot = localSnapshot.copy(logs = SystemLogs(latestMessage = text))
                }
                132 -> { // Network Link (<bb)
                    localSnapshot = localSnapshot.copy(network = NetworkLink(
                        wifiRSSI = bb.get(), bleRSSI = bb.get()
                    ))
                }
                121 -> { // Control Debug (<ff?)
                    localSnapshot = localSnapshot.copy(controlDebug = ControlDebug(
                        targetHeading = bb.float, headingError = bb.float, pidEnabled = bb.get().toInt() != 0
                    ))
                }
                136 -> { // Perception (<6f)
                    localSnapshot = localSnapshot.copy(perception = PerceptionMetrics(
                        distanceDelta = bb.float,  rawYawEnergy = bb.float, rawPitchEnergy = bb.float,
                        rawRollEnergy = bb.float, totalRawEnergy = bb.float,currentGForce = bb.float
                    ))
                }
                135 -> {// Events (<8?5f7?)
                    // Extract the first 8 booleans
                    // ==========================================================
                    // 1. THE AI PERCEPTION LATCHES (The Physical Truth)
                    // ==========================================================
                    val isHandling: Boolean = bb.get().toInt() != 0
                    val isFreeFalling: Boolean = bb.get().toInt() != 0

                    // --- The Complete Orientation Set ---
                    val isUpright: Boolean = bb.get().toInt() != 0          // Tracks on the floor
                    val isUpsideDown: Boolean = bb.get().toInt() != 0     // Completely flipped over (Resting on top)
                    val isTippedLeft: Boolean = bb.get().toInt() != 0     // Resting on left side
                    val isTippedRight: Boolean = bb.get().toInt() != 0     // Resting on right side
                    val isNoseUp: Boolean = bb.get().toInt() != 0         // Pitch > 70 deg (Stuck pointing at ceiling)
                    val isNoseDown: Boolean = bb.get().toInt() != 0       // Pitch < -70 deg (Faceplant / Pointing at floor)

                    val isAbsolutelyStill: Boolean = bb.get().toInt() != 0
                    val isImpactDetected: Boolean = bb.get().toInt() != 0
                    val isStuck: Boolean = bb.get().toInt() != 0
                    val hazardDetected: Boolean = bb.get().toInt() != 0
                    val isBeingTeased: Boolean = bb.get().toInt() != 0
                    val isBeingPushed: Boolean = bb.get().toInt() != 0
                    val isDizzy: Boolean = bb.get().toInt() != 0

                    // ==========================================================
                    // 2. MOOD & BEHAVIOUR TRIGGERS
                    // ==========================================================
                    val frustrationPeaked: Boolean = bb.get().toInt() != 0

                    // ==========================================================
                    // 3. LEGACY HEURISTIC METRICS
                    // (Keep these until the HeuristicDecider is fully deleted)
                    // ==========================================================
                    val dizzyBarYaw: Float = bb.float
                    val dizzyBarPitch: Float = bb.float
                    val dizzyBarRoll: Float = bb.float
                    val smoothedTotalEnergy: Float = bb.float
                    val frustrationLevel: Float = bb.float

                    val teaseConfirmed: Boolean = bb.get().toInt() != 0
                    val targetVanished: Boolean = bb.get().toInt() != 0
                    val dizzyTriggered: Boolean = bb.get().toInt() != 0
                    val dizzyFinished: Boolean = bb.get().toInt() != 0
                    val readyForCompassLock: Boolean = bb.get().toInt() != 0
                    val isHandTeasing: Boolean = bb.get().toInt() != 0
                    val isHandVanishing: Boolean = bb.get().toInt() != 0
                    val hasExperiencedLift: Boolean = bb.get().toInt() != 0
                    val isLowering: Boolean = bb.get().toInt() != 0
                    val hasLanded: Boolean = bb.get().toInt() != 0

                    localSnapshot = localSnapshot.copy(events = EventState(
                        isHandling = isHandling,
                        isFreeFalling = isFreeFalling,

                        isUpright = isUpright,
                        isUpsideDown = isUpsideDown,
                        isTippedLeft = isTippedLeft,
                        isTippedRight = isTippedRight,
                        isNoseUp = isNoseUp,
                        isNoseDown = isNoseDown,

                        isAbsolutelyStill = isAbsolutelyStill,
                        isImpactDetected = isImpactDetected,
                        isStuck = isStuck,
                        hazardDetected = hazardDetected,
                        isBeingTeased = isBeingTeased,
                        isBeingPushed = isBeingPushed,
                        isDizzy = isDizzy,

                        frustrationPeaked = frustrationPeaked,

                        dizzyBarYaw = dizzyBarYaw,
                        dizzyBarPitch = dizzyBarPitch,
                        dizzyBarRoll = dizzyBarRoll,
                        smoothedTotalEnergy = smoothedTotalEnergy,
                        frustrationLevel = frustrationLevel,

                        teaseConfirmed = teaseConfirmed,
                        targetVanished = targetVanished,
                        dizzyTriggered = dizzyTriggered,
                        dizzyFinished = dizzyFinished,
                        readyForCompassLock = readyForCompassLock,
                        isHandTeasing = isHandTeasing,
                        isHandVanishing = isHandVanishing,
                        hasExperiencedLift = hasExperiencedLift,
                        isLowering = isLowering,
                        hasLanded = hasLanded
                    ))
                }
            }
        } catch (e: Exception) { /* Malformed payload */ }
    }
}