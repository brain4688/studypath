package com.studypath.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 视觉体系来自「纸质学习手册」设计：
 * 米纸底 + 墨色文字 + 玉绿主色 + 朱红点缀 + 琥珀警示；
 * 衬线标题、等宽小标签。
 */
private val Paper = Color(0xFFF4F1E9)
private val Paper2 = Color(0xFFEAE6DA)
private val Ink = Color(0xFF171613)
private val Ink2 = Color(0xFF4E4A42)
private val Ink3 = Color(0xFF8B857A)
private val Rule = Color(0xFFDCD6C8)
private val Jade = Color(0xFF1E5B4C)
private val JadeSoft = Color(0xFFE2EDE8)
private val JadeLine = Color(0xFFB9D2C8)
private val Verm = Color(0xFFB8431B)
private val VermSoft = Color(0xFFF8E6DD)
private val Amber = Color(0xFF8A5A12)
private val AmberSoft = Color(0xFFF7EBD7)

private val LightScheme = lightColorScheme(
    primary = Jade,
    onPrimary = Color.White,
    primaryContainer = JadeSoft,
    onPrimaryContainer = Jade,
    secondary = Amber,
    onSecondary = Color.White,
    secondaryContainer = AmberSoft,
    onSecondaryContainer = Amber,
    tertiary = Verm,
    onTertiary = Color.White,
    tertiaryContainer = VermSoft,
    onTertiaryContainer = Verm,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Paper2,
    onSurfaceVariant = Ink2,
    outline = Rule,
    outlineVariant = Rule,
    error = Color(0xFF9A3412),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8FC0AE),
    onPrimary = Color(0xFF0E2C24),
    primaryContainer = Color(0xFF2A4C41),
    onPrimaryContainer = Color(0xFFCBE7DB),
    secondary = Color(0xFFD9B071),
    onSecondary = Color(0xFF2C1F08),
    secondaryContainer = Color(0xFF41371F),
    onSecondaryContainer = Color(0xFFF2DDB6),
    tertiary = Color(0xFFE39B77),
    onTertiary = Color(0xFF33110A),
    tertiaryContainer = Color(0xFF52281B),
    onTertiaryContainer = Color(0xFFF8E6DD),
    background = Color(0xFF191815),
    onBackground = Color(0xFFE8E4D8),
    surface = Color(0xFF21201C),
    onSurface = Color(0xFFE8E4D8),
    surfaceVariant = Color(0xFF2B2925),
    onSurfaceVariant = Color(0xFFA6A091),
    outline = Color(0xFF4A463C),
    outlineVariant = Color(0xFF4A463C),
    error = Color(0xFFF2B8A2),
)

/** 衬线标题 + 等宽标签，贴近手册排版 */
private val AppTypography: Typography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif),
        titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
        ),
    )
}

@Composable
fun StudyPathTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkScheme else LightScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
