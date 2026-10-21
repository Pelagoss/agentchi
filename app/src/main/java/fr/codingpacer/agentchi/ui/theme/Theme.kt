package fr.codingpacer.agentchi.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

val BrandOrange = Color(0xFFD97757)
val BrandOrangeDark = Color(0xFFB4593B)
val BrandCream = Color(0xFFF0EEE6)
val BrandInk = Color(0xFF1F1E1D)
val BrandSurface = Color(0xFF2B2826)

private val colors = Colors(
    primary = BrandOrange,
    primaryVariant = BrandOrangeDark,
    onPrimary = BrandInk,
    secondary = BrandCream,
    secondaryVariant = Color(0xFFC9C4B5),
    onSecondary = BrandInk,
    surface = BrandSurface,
    onSurface = BrandCream,
    onSurfaceVariant = Color(0xFFB8B2A7),
    background = Color.Black,
    onBackground = BrandCream,
)

@Composable
fun CompanionTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = colors, content = content)
}
