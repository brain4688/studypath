package com.studypath.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Blue = Color(0xFF1E6BFF)
private val LightScheme = lightColorScheme(
    primary = Blue,
    secondary = Color(0xFF006970),
    tertiary = Color(0xFF7C4E8E),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFAAC7FF),
    secondary = Color(0xFF4DD9E2),
    tertiary = Color(0xFFD0BCFF),
)

@Composable
fun StudyPathTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
