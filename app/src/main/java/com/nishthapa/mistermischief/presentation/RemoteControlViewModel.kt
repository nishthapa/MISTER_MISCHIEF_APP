package com.nishthapa.mistermischief.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope // <--- The missing import!
import com.nishthapa.mistermischief.core.RobotConnection
import com.nishthapa.mistermischief.domain.TeleopCommand
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RemoteControlViewModel(private val connection: RobotConnection) : ViewModel() {

    // Expose the connection stream to the UI
    val connectionState = connection.connectionState

    // --- NEW: Editable MAC Address State ---
    private val _macAddress = MutableStateFlow("44:1B:F6:FF:94:19")
    val macAddress = _macAddress.asStateFlow()

    // Hold the live command state
    private val _currentCommand = MutableStateFlow(TeleopCommand(0f, 0f, false))
    val currentCommand = _currentCommand.asStateFlow()

    // --- NEW: DRIVING TOKEN STATE ---
    private val _isDrivingEnabled = MutableStateFlow(false)
    val isDrivingEnabled = _isDrivingEnabled.asStateFlow()
    // Expose live telemetry to the UI
    val telemetryState = connection.telemetryState

    // --- NEW: THE CONTINUOUS HEARTBEAT LOOP ---
    private var heartbeatJob: Job? = null

    fun updateMacAddress(newMac: String) {
        _macAddress.value = newMac
    }

    // Pass-through connection methods
    fun connectToRobot() {
        connection.connect(_macAddress.value)
    }

    fun disconnect() {
        // Safety: Explicitly release the token BEFORE dropping the Bluetooth link!
        toggleDriving(false)
        connection.disconnect()
    }

    // NEW: Token Request & Heartbeat Handler
    fun toggleDriving(enabled: Boolean) {
        _isDrivingEnabled.value = enabled
        connection.sendControlToken(enabled)

        if (enabled) {
            // START THE HEARTBEAT: Feed the robot's deadman switch 10 times a second!
            heartbeatJob?.cancel()
            heartbeatJob = viewModelScope.launch {
                while (true) {
                    connection.sendCommand(_currentCommand.value)
                    delay(100) // 100ms delay = 10Hz continuous transmission
                }
            }
        } else {
            // KILL THE HEARTBEAT & ZERO MOTORS
            heartbeatJob?.cancel()
            updateJoystick(0f, 0f)
            // Blast one final zeroed command to guarantee the robot stops
            connection.sendCommand(_currentCommand.value)
        }
    }

    // The UI calls this rapidly as your thumb moves
    fun updateJoystick(x: Float, y: Float) {
        // Simply update the local state!
        // The continuous heartbeat loop running in the background will automatically
        // pick up these new values and beam them to the robot every 100ms.
        _currentCommand.update { it.copy(joyX = x, joyY = y) }
    }

    fun togglePidDrive(enabled: Boolean) {
        _currentCommand.update { it.copy(usePIDDrive = enabled) }
    }
}

// Android requires a Factory to pass the BleManager interface into the ViewModel
class RemoteControlViewModelFactory(private val connection: RobotConnection) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RemoteControlViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RemoteControlViewModel(connection) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}