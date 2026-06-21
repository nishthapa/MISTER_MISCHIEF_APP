package com.nishthapa.mistermischief.domain

// A centralized state container for the entire robot
data class RobotTelemetry(
    val cognition: CognitiveState = CognitiveState(),
    val physics: PhysicsState = PhysicsState(),
    val sensors: SensorState = SensorState(),
    val health: SystemHealth = SystemHealth(),

    // --- SOME MORE TELEMETRY STREAMS ---
    val network: NetworkLink = NetworkLink(),
    val events: EventState = EventState(),
    val controlDebug: ControlDebug = ControlDebug(),
    val perception: PerceptionMetrics = PerceptionMetrics()
)

data class CognitiveState(
    val systemMode: String = "UNKNOWN",
    val robotMood: String = "UNKNOWN"
)

data class PhysicsState(
    val yaw: Float = 0f, val pitch: Float = 0f, val roll: Float = 0f,
    val gForce: Float = 0f, val hasCompass: Boolean = false, val compassHeading: Float = 0f, // <-- ADDED hasCompass
    val leftMotorPWM: Short = 0, val rightMotorPWM: Short = 0
)

data class SensorState(
    val distanceCM: Float = -1f,
    val batteryVoltageMV: UShort = 0u,
    val currentDrawMA: Short = 0
)

data class SystemHealth(
    val loopTimeUs: UInt = 0u,
    val freeHeap: UInt = 0u,
    val hardwareBitmask: UShort = 0u
)

data class NetworkLink(
    val wifiRSSI: Byte = -127,
    val bleRSSI: Byte = -127
)

data class EventState(
    val hazardDetected: Boolean = false,
    val teaseConfirmed: Boolean = false,
    val targetVanished: Boolean = false,
    val dizzyTriggered: Boolean = false,
    val dizzyFinished: Boolean = false,
    val readyForCompassLock: Boolean = false,
    val safelyLanded: Boolean = false,
    val frustrationPeaked: Boolean = false,

    val dizzyBarYaw: Float = 0f,
    val dizzyBarPitch: Float = 0f,
    val dizzyBarRoll: Float = 0f,
    val smoothedTotalEnergy: Float = 0f,
    val frustrationLevel: Float = 0f,

    val isHandTeasing: Boolean = false,
    val isHandVanishing: Boolean = false,
    val isHandling: Boolean = false,
    val hasExperiencedLift: Boolean = false,
    val isLowering: Boolean = false,
    val hasLanded: Boolean = false,
    val isDizzy: Boolean = false
)

data class ControlDebug(
    val targetHeading: Float = 0f,
    val headingError: Float = 0f,
    val pidEnabled: Boolean = false
)

data class PerceptionMetrics(
    val distanceDelta: Float = 0f,
    val totalRawEnergy: Float = 0f,
    val rawYawEnergy: Float = 0f,
    val rawPitchEnergy: Float = 0f,
    val rawRollEnergy: Float = 0f,
    val currentGForce: Float = 0f
)