package com.nishthapa.mistermischief.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nishthapa.mistermischief.core.ConnectionStatus
import com.nishthapa.mistermischief.presentation.RemoteControlViewModel

@Composable
fun ConnectionScreen(viewModel: RemoteControlViewModel, connState: ConnectionStatus) {
    val macAddress by viewModel.macAddress.collectAsState()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.width(400.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Connect to Robot", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = macAddress,
                    onValueChange = { viewModel.updateMacAddress(it) },
                    label = { Text("BLE MAC Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { viewModel.connectToRobot() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = connState != ConnectionStatus.CONNECTING
                ) {
                    Text(if (connState == ConnectionStatus.CONNECTING) "Connecting..." else "Connect")
                }

                if (connState == ConnectionStatus.ERROR) {
                    Text("Connection Failed. Check power and MAC.", color = Color.Red, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}