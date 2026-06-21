package com.nishthapa.mistermischief.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nishthapa.mistermischief.core.ConnectionStatus
import com.nishthapa.mistermischief.presentation.RemoteControlViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RobotDashboard(viewModel: RemoteControlViewModel) {
    val connState by viewModel.connectionState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf("Manual Control") }

    if (connState != ConnectionStatus.CONNECTED) {
        ConnectionScreen(viewModel, connState)
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
                    Text(
                        text = "Mister Mischief",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    val screens = listOf("Manual Control", "Robot Configuration", "Sensor Diagnostics", "Autonomous Behaviours")

                    screens.forEach { screenName ->
                        NavigationDrawerItem(
                            label = { Text(screenName) },
                            selected = currentScreen == screenName,
                            onClick = {
                                currentScreen = screenName
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(currentScreen, fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        actions = {
                            Button(
                                onClick = { viewModel.disconnect() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Disconnect", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Disconnect")
                            }
                        }
                    )
                }
            ) { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                    when (currentScreen) {
                        "Manual Control" -> ManualControlScreen(viewModel)
                        else -> PlaceholderScreen(currentScreen)
                    }
                }
            }
        }
    }
}