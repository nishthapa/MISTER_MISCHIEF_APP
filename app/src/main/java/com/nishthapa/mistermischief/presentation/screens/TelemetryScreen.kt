package com.nishthapa.mistermischief.presentation.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nishthapa.mistermischief.domain.RobotTelemetry
import com.nishthapa.mistermischief.presentation.RemoteControlViewModel

@Composable
fun TelemetryScreen(viewModel: RemoteControlViewModel) {
    val telemetry by viewModel.telemetryState.collectAsState()

    // Split the categories into two equal columns
    val leftCategories = listOf("Cognition", "Physics", "Sensors", "System Health", "System Logs")
    val rightCategories = listOf("Control Debug", "Actuators", "Events", "Network Link", "Perception")

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- COLUMN 1 (Left Side) ---
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(leftCategories.size) { index ->
                ExpandableTelemetryCard(
                    category = leftCategories[index],
                    telemetry = telemetry
                )
            }
        }

        // --- COLUMN 2 (Right Side) ---
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rightCategories.size) { index ->
                ExpandableTelemetryCard(
                    category = rightCategories[index],
                    telemetry = telemetry
                )
            }
        }
    }
}

@Composable
fun ExpandableTelemetryCard(category: String, telemetry: RobotTelemetry) {
    // Remember whether this specific card is open or closed (default closed)
    var expanded by remember { mutableStateOf(false) }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize( // <--- THIS is what creates the smooth "inflating" animation
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded } // Click anywhere on the card to open/close
                .padding(16.dp)
        ) {
            // The Header Row (Always visible)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = Color.Gray
                )
            }

            // The Content Area (Only visible when inflated)
            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                TelemetryDataDisplay(category, telemetry)
            }
        }
    }
}

@Composable
fun TelemetryDataDisplay(category: String, telemetry: RobotTelemetry) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (category) {
            "Cognition" -> {
                DataRow("System Mode", telemetry.cognition.systemMode)
                DataRow("Robot Mood", telemetry.cognition.robotMood)
            }
            "Actuators" -> {
                DataRow("Left Motor PWM", telemetry.actuators.leftMotorPWM.toString())
                DataRow("Right Motor PWM", telemetry.actuators.rightMotorPWM.toString())
            }
            "Control Debug" -> {
                DataRow("Target Heading", "${"%.2f".format(telemetry.controlDebug.targetHeading)}°")
                DataRow("Heading Error", "${"%.2f".format(telemetry.controlDebug.headingError)}°")
                DataRow("PID Enabled", if (telemetry.controlDebug.pidEnabled) "ON" else "OFF")
            }
            "Events" -> {
                DataRow("Hazard Detected", telemetry.events.hazardDetected.toString().uppercase())
                DataRow("Tease Confirmed", telemetry.events.teaseConfirmed.toString().uppercase())
                DataRow("Target Vanished", telemetry.events.targetVanished.toString().uppercase())
                DataRow("Dizzy Triggered", telemetry.events.dizzyTriggered.toString().uppercase())
                DataRow("Dizzy Finished", telemetry.events.dizzyFinished.toString().uppercase())
                DataRow("Ready For Compass Lock", telemetry.events.readyForCompassLock.toString().uppercase())
                DataRow("Safely Landed", telemetry.events.safelyLanded.toString().uppercase())
                DataRow("Frustration Peaked", telemetry.events.frustrationPeaked.toString().uppercase())

                Spacer(modifier = Modifier.height(8.dp)) // Visual break between bools and floats

                DataRow("Dizzy Bar (Yaw)", "%.2f".format(telemetry.events.dizzyBarYaw))
                DataRow("Dizzy Bar (Pitch)", "%.2f".format(telemetry.events.dizzyBarPitch))
                DataRow("Dizzy Bar (Roll)", "%.2f".format(telemetry.events.dizzyBarRoll))
                DataRow("Smoothed Energy", "%.2f".format(telemetry.events.smoothedTotalEnergy))
                DataRow("Frustration Level", "%.2f".format(telemetry.events.frustrationLevel))

                Spacer(modifier = Modifier.height(8.dp)) // Visual break between floats and bools

                DataRow("Is Hand Teasing", telemetry.events.isHandTeasing.toString().uppercase())
                DataRow("Is Hand Vanishing", telemetry.events.isHandVanishing.toString().uppercase())
                DataRow("Is Handling", telemetry.events.isHandling.toString().uppercase())
                DataRow("Has Experienced Lift", telemetry.events.hasExperiencedLift.toString().uppercase())
                DataRow("Is Lowering", telemetry.events.isLowering.toString().uppercase())
                DataRow("Has Landed", telemetry.events.hasLanded.toString().uppercase())
                DataRow("Is Dizzy", telemetry.events.isDizzy.toString().uppercase())
            }
            "Network Link" -> {
                DataRow("Wi-Fi RSSI", "${telemetry.network.wifiRSSI} dBm")
                DataRow("BLE RSSI", "${telemetry.network.bleRSSI} dBm")
            }
            "Perception" -> {
                DataRow("Distance Delta", "%.3f".format(telemetry.perception.distanceDelta))
                DataRow("Total Raw Energy", "%.3f".format(telemetry.perception.totalRawEnergy))
                DataRow("Raw Yaw Energy", "%.3f".format(telemetry.perception.rawYawEnergy))
                DataRow("Raw Pitch Energy", "%.3f".format(telemetry.perception.rawPitchEnergy))
                DataRow("Raw Roll Energy", "%.3f".format(telemetry.perception.rawRollEnergy))
                DataRow("Current G-Force", "%.3f G".format(telemetry.perception.currentGForce))
            }
            "Physics" -> {
                DataRow("Yaw / Pitch / Roll", "${"%.1f".format(telemetry.physics.yaw)}° / ${"%.1f".format(telemetry.physics.pitch)}° / ${"%.1f".format(telemetry.physics.roll)}°")
                DataRow("G-Force", "${"%.2f".format(telemetry.physics.gForce)} G")
                DataRow("Has Compass", telemetry.physics.hasCompass.toString().uppercase())
                DataRow("Compass Heading", "${"%.1f".format(telemetry.physics.compassHeading)}°")
                //DataRow("Motor PWM (L/R)", "${telemetry.physics.leftMotorPWM} / ${telemetry.physics.rightMotorPWM}")
            }
            "Sensors" -> {
                DataRow("Sonar Distance", if (telemetry.sensors.distanceCM < 0) "OOR" else "${"%.1f".format(telemetry.sensors.distanceCM)} cm")
                DataRow("Battery Voltage", "${telemetry.sensors.batteryVoltageMV} mV")
                DataRow("Current Draw", "${telemetry.sensors.currentDrawMA} mA")
            }
            "System Health" -> {
                DataRow("Loop Time", "${telemetry.health.loopTimeUs} µs")
                DataRow("Free RAM", "${telemetry.health.freeHeap / 1024u} KB")
                DataRow("CPU 0 (Sensors)", "${telemetry.health.cpu0Load}%")
                DataRow("CPU 1 (Physics)", "${telemetry.health.cpu1Load}%")
                DataRow("HW Bitmask", "0x${telemetry.health.hardwareBitmask.toString(16).uppercase()}")
            }
            "System Logs" -> {
                // We use a Text box instead of a DataRow so long logs can wrap to the next line naturally!
                Text(
                    text = "> ${telemetry.logs.latestMessage}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun DataRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = FontWeight.SemiBold, color = Color.Gray)
        Text(value, fontWeight = FontWeight.Bold)
    }
}