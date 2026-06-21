package com.nishthapa.mistermischief.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nishthapa.mistermischief.core.RobotConnection
import com.nishthapa.mistermischief.domain.TeleopCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

    fun updateMacAddress(newMac: String) {
        _macAddress.value = newMac
    }

    // --- NEW: BLE THROTTLE STATE ---
    private var lastSendTime = 0L

    // Pass-through connection methods
    fun connectToRobot() {
        connection.connect(_macAddress.value)
    }

    fun disconnect() {
        // Safety: Explicitly release the token BEFORE dropping the Bluetooth link!
        toggleDriving(false)
        connection.disconnect()
        // Safety: zero out the joysticks on disconnect
        updateJoystick(0f, 0f)
    }

    // NEW: Token Request Handler
    fun toggleDriving(enabled: Boolean) {
        _isDrivingEnabled.value = enabled
        connection.sendControlToken(enabled)

        // If we revoke the token, instantly zero out the joystick values on the UI
        if (!enabled) {
            updateJoystick(0f, 0f)
        }
    }

    // The UI calls this rapidly as your thumb moves
    fun updateJoystick(x: Float, y: Float) {
        _currentCommand.update { it.copy(joyX = x, joyY = y) }

        // --- NEW: SMART THROTTLING LOGIC ---
        val currentTime = System.currentTimeMillis()
        val isStopCommand = (x == 0f && y == 0f)

        // Only send the BLE packet if 50ms has passed (20Hz limit)
        // OR if it is a critical STOP command (bypasses the timer)
        if (isStopCommand || currentTime - lastSendTime > 50) {
            connection.sendCommand(_currentCommand.value)
            lastSendTime = currentTime
        }
    }

    fun togglePidDrive(enabled: Boolean) {
        _currentCommand.update { it.copy(usePIDDrive = enabled) }
        connection.sendCommand(_currentCommand.value)
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