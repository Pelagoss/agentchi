package com.claudecompanion.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

val ClaudeOrange = Color(0xFFD97757)
val ClaudeOrangeDark = Color(0xFFB4593B)
val ClaudeCream = Color(0xFFF0EEE6)
val ClaudeInk = Color(0xFF1F1E1D)
val ClaudeSurface = Color(0xFF2B2826)

private val colors = Colors(
    primary = ClaudeOrange,
    primaryVariant = ClaudeOrangeDark,
    onPrimary = ClaudeInk,
    secondary = ClaudeCream,
    secondaryVariant = Color(0xFFC9C4B5),
    onSecondary = ClaudeInk,
    surface = ClaudeSurface,
    onSurface = ClaudeCream,
    onSurfaceVariant = Color(0xFFB8B2A7),
    background = Color.Black,
    onBackground = ClaudeCream,
)

@Composable
fun CompanionTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = colors, content = content)
}
