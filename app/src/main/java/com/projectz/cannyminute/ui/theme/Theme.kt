package com.projectz.cannyminute.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1E6DB6),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF77B8F2),
    onSecondary = Color(0xFF0E2F4D),
    background = Color(0xFFF4F8FF),
    onBackground = Color(0xFF153651),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF173A58),
    surfaceVariant = Color(0xFFE5EEF9),
    onSurfaceVariant = Color(0xFF4A6684),
    outline = Color(0xFFB7CBDF),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8BC4FF),
    onPrimary = Color(0xFF003258),
    secondary = Color(0xFF92C9FF),
    onSecondary = Color(0xFF0C2E4A),
    background = Color(0xFF0D1E2F),
    onBackground = Color(0xFFE6F1FF),
    surface = Color(0xFF12293F),
    onSurface = Color(0xFFE0EEFF),
    surfaceVariant = Color(0xFF22415F),
    onSurfaceVariant = Color(0xFFBCD3EA),
    outline = Color(0xFF5B7897),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun CannyMinuteTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content
    )
}

