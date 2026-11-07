package com.bqsqwad.share.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object ShareTokens {
    val Space = 16.dp
    val Radius = 20.dp
    val Touch = 48.dp
    val Royal = Color(0xFF0A4DFF)
    val Sky = Color(0xFF19C6FF)
    val Navy = Color(0xFF07111F)
    val Mint = Color(0xFF2EE6C7)
}

private val Light = lightColorScheme(
    primary = ShareTokens.Royal,
    onPrimary = Color.White,
    secondary = ShareTokens.Sky,
    background = Color.White,
    surface = Color.White,
    surfaceContainer = Color(0xFFE7F0FF),
    onBackground = Color(0xFF0B1B4A),
    onSurface = Color(0xFF0B1B4A),
)

private val Dark = darkColorScheme(
    primary = ShareTokens.Mint,
    onPrimary = Color(0xFF04241C),
    secondary = Color(0xFF7AA2FF),
    background = ShareTokens.Navy,
    surface = Color(0xFF101A2C),
    surfaceContainer = Color(0xFF18253C),
    onBackground = Color(0xFFE8EEF9),
    onSurface = Color(0xFFE8EEF9),
)

private fun accented(base: androidx.compose.material3.ColorScheme, accent: String) = when (accent) {
    "ocean" -> base.copy(primary = Color(0xFF0077B6), secondary = Color(0xFF48CAE4))
    "ember" -> base.copy(primary = Color(0xFFC2410C), secondary = Color(0xFFFDBA74))
    "violet" -> base.copy(primary = Color(0xFF6D28D9), secondary = Color(0xFFC4B5FD))
    else -> base
}

private val Type = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
)

@Composable
fun ShareTheme(dark: Boolean, accent: String, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = accented(if (dark) Dark else Light, accent),
        typography = Type,
        shapes = Shapes(
            medium = RoundedCornerShape(ShareTokens.Radius),
            large = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
