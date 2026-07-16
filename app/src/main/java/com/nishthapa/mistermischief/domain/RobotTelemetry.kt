package com.nishthapa.mistermischief.domain

// A centralized state container for the entire robot
data class RobotTelemetry(
    val cognition: CognitiveState = CognitiveState(),
    val physics: PhysicsState = PhysicsState(),
    val actuators: ActuatorState = ActuatorState(),
    val sensors: SensorState = SensorState(),
    val health: SystemHealth = SystemHealth(),

    // --- SOME MORE TELEMETRY STREAMS ---
    val network: NetworkLink = NetworkLink(),
    val events: EventState = EventState(),
    val controlDebug: ControlDebug = ControlDebug(),
    val perception: PerceptionMetrics = PerceptionMetrics(),
    val logs: SystemLogs = SystemLogs()
)

data class CognitiveState(
    val systemMode: String = "UNKNOWN",
    val robotMood: String = "UNKNOWN"
)

data class PhysicsState(
    val yaw: Float = 0f, val pitch: Float = 0f, val roll: Float = 0f,
    val gForce: Float = 0f,
    val accelX: Float = 0f, val accelY: Float = 0f, val accelZ: Float = 0f,
    val gyroX: Float = 0f, val gyroY: Float = 0f, val gyroZ: Float = 0f,
    val hasCompass: Boolean = false, val compassHeading: Float = 0f // <-- ADDED hasCompass
)

data class ActuatorState(
    val leftMotorPWM: Short = 0,
    val rightMotorPWM: Short = 0,
    val isDriving: Boolean = false
)

data class SensorState(
    val distanceCM: Float = -1f,
    val hasBaro: Boolean = false,
    val pressurePa: Float = 0.0f,
    val altitudeCM: Float = 0.0f,
    val pressureDeltaPa: Float = 0.0f,
    val temperatureC: Float = 0.0f,
    val batteryVoltageMV: UShort = 0u,
    val currentDrawMA: Short = 0
)

data class SystemHealth(
    val loopTimeUs: UInt = 0u,
    val freeHeap: UInt = 0u,
    val hardwareBitmask: UShort = 0u,
    val cpu0Load: UByte = 0u, // Robot CPU0 Load
    val cpu1Load: UByte = 0u  // Robot CPU1 Load
)

// NEW: Container for the text logs // Serial.println() mirrors
data class SystemLogs(
    val latestMessage: String = "Serial.println() will be mirrored here"
)

data class NetworkLink(
    val wifiRSSI: Byte = -127,
    val bleRSSI: Byte = -127
)

data class EventState(
    // ==========================================================
    // 1. THE AI PERCEPTION LATCHES (The Physical Truth)
    // ==========================================================
    val isHandling: Boolean = false,
    val isFreeFalling: Boolean = false,

    // --- The Complete Orientation Set ---
    val isUpright: Boolean = true,          // Tracks on the floor
    val isUpsideDown: Boolean = false,      // Completely flipped over (Resting on top)
    val isTippedLeft: Boolean = false,      // Resting on left side
    val isTippedRight: Boolean = false,     // Resting on right side
    val isNoseUp: Boolean = false,          // Pitch > 70 deg (Stuck pointing at ceiling)
    val isNoseDown: Boolean = false,        // Pitch < -70 deg (Faceplant / Pointing at floor)

    val isAbsolutelyStill: Boolean = false,
    val isImpactDetected: Boolean = false,
    val isStuck: Boolean = false,
    val hazardDetected: Boolean = false,
    val isBeingTeased: Boolean = false,
    val isBeingPushed: Boolean = false,
    val isDizzy: Boolean = false,

    // ==========================================================
    // 2. MOOD & BEHAVIOUR TRIGGERS
    // ==========================================================
    val frustrationPeaked: Boolean = false,

    // ==========================================================
    // 3. LEGACY HEURISTIC METRICS
    // (Keep these until the HeuristicDecider is fully deleted)
    // ==========================================================
    val dizzyBarYaw: Float = 0.0f,
    val dizzyBarPitch: Float = 0.0f,
    val dizzyBarRoll: Float = 0.0f,
    val smoothedTotalEnergy: Float = 0.0f,
    val frustrationLevel: Float = 0.0f,

    val teaseConfirmed: Boolean = false,
    val targetVanished: Boolean = false,
    val dizzyTriggered: Boolean = false,
    val dizzyFinished: Boolean = false,
    val readyForCompassLock: Boolean = false,
    val isHandTeasing: Boolean = false,
    val isHandVanishing: Boolean = false,
    val hasExperiencedLift: Boolean = false,
    val isLowering: Boolean = false,
    val hasLanded: Boolean = false
)

data class ControlDebug(
    val targetHeading: Float = 0f,
    val headingError: Float = 0f,
    val pidEnabled: Boolean = false
)

data class PerceptionMetrics(
    val distanceDelta: Float = 0f,
    val rawYawEnergy: Float = 0f,
    val rawPitchEnergy: Float = 0f,
    val rawRollEnergy: Float = 0f,
    val totalRawEnergy: Float = 0f,
    val currentGForce: Float = 0f
)