package ru.speedmeter.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Оформление «приборная панель»: тёмный графит, холодный циан вместо
 * зелени и янтарь для отдачи.
 *
 * Цвета метрик разведены намеренно: человек сравнивает «вниз» и «вверх»
 * и должен различать их не подписью, а цветом. Циан — загрузка, янтарь —
 * отдача, зелёный — задержка.
 *
 * Светлая тема — не вывернутая тёмная: на белом фоне тонкие линии
 * датчика и бледный циан теряют контраст, поэтому она написана отдельно.
 */
private object Panel {
    val dark = darkColorScheme(
        primary = Color(0xFF35E0D0),
        onPrimary = Color(0xFF00201C),
        primaryContainer = Color(0xFF0B3B38),
        onPrimaryContainer = Color(0xFFA8FFF4),
        secondary = Color(0xFFF5B942),
        onSecondary = Color(0xFF2A1900),
        secondaryContainer = Color(0xFF3E2E0B),
        onSecondaryContainer = Color(0xFFFFE0A3),
        tertiary = Color(0xFF9BE86F),
        onTertiary = Color(0xFF123000),
        tertiaryContainer = Color(0xFF23421A),
        onTertiaryContainer = Color(0xFFBDF7A0),
        error = Color(0xFFFF8A80),
        onError = Color(0xFF3A0806),
        background = Color(0xFF070A0F),
        onBackground = Color(0xFFE6EDF3),
        surface = Color(0xFF0E131A),
        onSurface = Color(0xFFE6EDF3),
        surfaceVariant = Color(0xFF1A222D),
        onSurfaceVariant = Color(0xFF93A4B4),
        surfaceContainerLowest = Color(0xFF05070B),
        surfaceContainerLow = Color(0xFF0B0F15),
        surfaceContainer = Color(0xFF10161E),
        surfaceContainerHigh = Color(0xFF172029),
        surfaceContainerHighest = Color(0xFF1E2833),
        outline = Color(0xFF3C4B5C),
        outlineVariant = Color(0xFF1D2632),
    )

    val light = lightColorScheme(
        primary = Color(0xFF00897B),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF9FF2E6),
        onPrimaryContainer = Color(0xFF00201C),
        secondary = Color(0xFF9A6700),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFE0A3),
        onSecondaryContainer = Color(0xFF2A1900),
        tertiary = Color(0xFF3F8A1E),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFBDF7A0),
        onTertiaryContainer = Color(0xFF123000),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        background = Color(0xFFF4F7F9),
        onBackground = Color(0xFF0B1219),
        surface = Color.White,
        onSurface = Color(0xFF0B1219),
        surfaceVariant = Color(0xFFE4EBF1),
        onSurfaceVariant = Color(0xFF44545F),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF7FAFC),
        surfaceContainer = Color(0xFFF1F5F8),
        surfaceContainerHigh = Color(0xFFEAF0F5),
        surfaceContainerHighest = Color(0xFFE2E9F0),
        outline = Color(0xFF74838F),
        outlineVariant = Color(0xFFC8D5DE),
    )
}

private val PanelTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 52.sp,
        lineHeight = 56.sp,
        fontWeight = FontWeight.Medium,
    ),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun SpeedmeterTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (dark) Panel.dark else Panel.light,
        typography = PanelTypography,
        content = content,
    )
}

/** Размеры, общие для экранов: подписи, отступы, радиусы. */
object Sizes {
    val gaugeHeight = 260.dp
    val cardRadius = 20.dp
    val gutter = 16.dp
}
