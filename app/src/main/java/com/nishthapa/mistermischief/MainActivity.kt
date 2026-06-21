package com.nishthapa.mistermischief

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.nishthapa.mistermischief.data.BleManager
import com.nishthapa.mistermischief.presentation.RemoteControlViewModel
import com.nishthapa.mistermischief.presentation.RemoteControlViewModelFactory
import com.nishthapa.mistermischief.presentation.screens.RobotDashboard
import com.nishthapa.mistermischief.ui.theme.MisterMischiefAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
}