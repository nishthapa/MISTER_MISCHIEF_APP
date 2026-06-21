package com.nishthapa.mistermischief.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun VirtualJoystick(
    modifier: Modifier = Modifier,
    onMove: (Float, Float) -> Unit
) {
    // Hold the thumbpad position relative to the center
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }
    var radius by remember { mutableFloatStateOf(0f) }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { },
                    onDragEnd = {
                        // Snap back to center when released
                        thumbOffset = Offset.Zero
                        onMove(0f, 0f)
                    },
                    onDragCancel = {
                        thumbOffset = Offset.Zero
                        onMove(0f, 0f)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()

                        // Calculate new raw position
                        val rawX = thumbOffset.x + dragAmount.x
                        val rawY = thumbOffset.y + dragAmount.y

                        // Calculate distance from center
                        val distance = sqrt(rawX * rawX + rawY * rawY)

                        // Clamp the thumbpad inside the base radius
                        thumbOffset = if (distance <= radius) {
                            Offset(rawX, rawY)
                        } else {
                            val angle = atan2(rawY, rawX)
                            Offset(cos(angle) * radius, sin(angle) * radius)
                        }

                        // Map to -1.0 to 1.0 (Note: Android Y is inverted, so we negate it)
                        val normalizedX = (thumbOffset.x / radius).coerceIn(-1f, 1f)
                        val normalizedY = -(thumbOffset.y / radius).coerceIn(-1f, 1f)

                        onMove(normalizedX, normalizedY)
                    }
                )
            }
        ) {
            // Establish the drawing radius based on the assigned container size
            val center = Offset(size.width / 2, size.height / 2)
            radius = size.width / 2f

            // Draw the base boundary
            drawCircle(
                color = Color.DarkGray,
                radius = radius,
                center = center,
                style = Stroke(width = 4f)
            )

            // Draw the movable thumbpad
            drawCircle(
                color = Color.LightGray,
                radius = radius / 3f,
                center = center + thumbOffset
            )
        }
    }
}