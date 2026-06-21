package com.nishthapa.mistermischief.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Module Offline", style = MaterialTheme.typography.headlineMedium, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            Text("The $title interface is under construction.", color = Color.Gray)
        }
    }
}