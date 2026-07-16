package com.nishthapa.mistermischief

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.nishthapa.mistermischief.data.BleManager
import com.nishthapa.mistermischief.presentation.RemoteControlViewModel
import com.nishthapa.mistermischief.presentation.RemoteControlViewModelFactory
import com.nishthapa.mistermischief.presentation.screens.RobotDashboard
import com.nishthapa.mistermischief.ui.theme.MisterMischiefAppTheme

class MainActivity : ComponentActivity() {

    // Register the permission callback that Android uses to report if the user clicked "Allow" or "Deny"
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // If needed, we can handle the exact outcome here.
        // For now, simply prompting the user is enough to unblock the BLE stack.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Immediately request BLE permissions before the UI fully loads
        requestBluetoothPermissions()

        // 2. Initialize our Managers and ViewModels
        val bleManager = BleManager(this)
        val factory = RemoteControlViewModelFactory(bleManager)
        val viewModel = ViewModelProvider(this, factory)[RemoteControlViewModel::class.java]

        setContent {
            MisterMischiefAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RobotDashboard(viewModel)
                }
            }
        }
    }

    private fun requestBluetoothPermissions() {
        // Determine exactly which permissions this specific phone needs based on its Android Version
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            // Android 11 and below needed Location permission to scan for BLE devices
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        // Filter down to only the permissions the user hasn't granted yet
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        // If there are missing permissions, launch the system popup!
        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
}