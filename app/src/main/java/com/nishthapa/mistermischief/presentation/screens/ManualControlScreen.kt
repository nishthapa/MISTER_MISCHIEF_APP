package com.nishthapa.mistermischief.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nishthapa.mistermischief.presentation.RemoteControlViewModel
import com.nishthapa.mistermischief.presentation.components.VirtualJoystick

@Composable
fun ManualControlScreen(viewModel: RemoteControlViewModel) {
    val isDrivingEnabled by viewModel.isDrivingEnabled.collectAsState()
    val currentCommand by viewModel.currentCommand.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (!isDrivingEnabled) {
            Button(
                onClick = { viewModel.toggleDriving(true) },
                modifier = Modifier.align(Alignment.Center).size(width = 250.dp, height = 80.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Enable Manual Control", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            // 1. Move Joystick to the Middle Right
            VirtualJoystick(
                modifier = Modifier
                    .align(Alignment.CenterEnd) // <--- Pushes it to the right
                    .padding(end = 64.dp)       // <--- Keeps it comfortably away from the screen edge
                    .fillMaxHeight(0.65f)
                    .aspectRatio(1f)
            ) { x, y ->
                viewModel.updateJoystick(x, y)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
            ) {
                FilledTonalButton(
                    onClick = { viewModel.togglePidDrive(!currentCommand.usePIDDrive) },
                    //modifier = Modifier.width(180.dp)
                ) {
                    Text(if (currentCommand.usePIDDrive) "PID Assist: ON" else "PID Assist: OFF")
                }

                Button(
                    onClick = { viewModel.toggleDriving(false) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    //modifier = Modifier.width(180.dp)
                ) {
                    Text("Disable Control")
                }
            }

            Text(
                text = "X: ${"%.2f".format(currentCommand.joyX)} | Y: ${"%.2f".format(currentCommand.joyY)}",
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                color = Color.Gray,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}