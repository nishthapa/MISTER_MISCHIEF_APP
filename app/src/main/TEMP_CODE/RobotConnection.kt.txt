package com.nishthapa.mistermischief.core

import com.nishthapa.mistermischief.domain.TeleopCommand
import kotlinx.coroutines.flow.StateFlow

interface RobotConnection {
    val connectionState: StateFlow<ConnectionStatus>
    fun connect(deviceAddress: String)
    fun disconnect()
    fun sendCommand(command: TeleopCommand)

    // NEW: The Token Authority mechanism to trigger manual driving
    fun sendControlToken(claim: Boolean)
}