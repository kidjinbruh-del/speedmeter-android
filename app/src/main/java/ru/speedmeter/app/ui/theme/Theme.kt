package ru.speedmeter.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Оформление «приборная панель».
 *
 * Тёмная тема основная и написана первой: на чёрном циан и янтарь читаются
 * как настоящие шкалы, а не как цветные плашки. Светлая — не вывернутая
 * тёмная, а отдельная: тонкие линии датчика и бледный акцент на белом
 * фоне теряют контраст, поэтому там акцент темнее, а трек светлее.
 *
 * Цвета метрик разведены намеренно: человек сравнивает «вниз» и «вверх» и
 * должен различать их не подписью, а цветом. Акцент — загрузка,
 * вторичный — отдача, третичный — задержка и джиттер.
 */
private object Panel {

    /** Тёмный текст на светлом акценте: уводим цвет к чёрному. */
    private fun on(accent: Color): Color = Neutrals.mix(Color.Black, accent, 0.82f)

    /** Светлый текст на тёмном акценте: уводим к белому. */
    private fun tint(accent: Color): Color = Neutrals.mix(accent, Color.White, 0.45f)

    fun darkScheme(accent: Accent): ColorScheme {
        val p = accent.dark.first
        val s = accent.dark.second
        val t = accent.dark.third
        val surface = Neutrals.darkSurface
        return darkColorScheme(
            primary = p,
            onPrimary = on(p),
            primaryContainer = Neutrals.mix(surface, p, 0.20f),
            onPrimaryContainer = tint(p),
            secondary = s,
            onSecondary = on(s),
            secondaryContainer = Neutrals.mix(surface, s, 0.18f),
            onSecondaryContainer = tint(s),
            tertiary = t,
            onTertiary = on(t),
            tertiaryContainer = Neutrals.mix(surface, t, 0.18f),
            onTertiaryContainer = tint(t),
            error = Color(0xFFFF8A80),
            onError = Color(0xFF3A0806),
            background = Neutrals.darkBackground,
            onBackground = Neutrals.darkOnSurface,
            surface = surface,
            onSurface = Neutrals.darkOnSurface,
            surfaceVariant = Neutrals.darkSurfaceHigh,
            onSurfaceVariant = Neutrals.darkOnSurfaceVariant,
            surfaceContainerLowest = Color(0xFF070A0E),
            surfaceContainerLow = Color(0xFF0C1117),
            surfaceContainer = surface,
            surfaceContainerHigh = Neutrals.darkSurfaceHigh,
            surfaceContainerHighest = Neutrals.darkSurfaceHigher,
            surfaceBright = Neutrals.darkSurfaceHigher,
            surfaceDim = Color(0xFF0C1117),
            outline = Neutrals.darkOutline,
            outlineVariant = Color(0xFF1D2733),
            inverseSurface = Neutrals.lightSurface,
            inverseOnSurface = Neutrals.lightOnSurface,
        )
    }

    fun lightScheme(accent: Accent): ColorScheme {
        val p = accent.light.first
        val s = accent.light.second
        val t = accent.light.third
        val surface = Neutrals.lightSurface
        return lightColorScheme(
            primary = p,
            onPrimary = Color.White,
            primaryContainer = Neutrals.mix(p, surface, 0.16f),
            onPrimaryContainer = Neutrals.mix(p, Color.Black, 0.45f),
            secondary = s,
            onSecondary = Color.White,
            secondaryContainer = Neutrals.mix(s, surface, 0.16f),
            onSecondaryContainer = Neutrals.mix(s, Color.Black, 0.45f),
            tertiary = t,
            onTertiary = Color.White,
            tertiaryContainer = Neutrals.mix(t, surface, 0.16f),
            onTertiaryContainer = Neutrals.mix(t, Color.Black, 0.45f),
            error = Color(0xFFB3261E),
            onError = Color.White,
            background = Neutrals.lightBackground,
            onBackground = Neutrals.lightOnSurface,
            surface = surface,
            onSurface = Neutrals.lightOnSurface,
            surfaceVariant = Neutrals.lightSurfaceHigh,
            onSurfaceVariant = Neutrals.lightOnSurfaceVariant,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFFAFBFD),
            surfaceContainer = Color(0xFFF2F5F8),
            surfaceContainerHigh = Neutrals.lightSurfaceHigh,
            surfaceContainerHighest = Color(0xFFE3EAF0),
            surfaceBright = Color.White,
            surfaceDim = Color(0xFFE3EAF0),
            outline = Neutrals.lightOutline,
            outlineVariant = Color(0xFFDCE3EA),
            inverseSurface = Neutrals.darkSurface,
            inverseOnSurface = Neutrals.darkOnSurface,
        )
    }
}

/**
 * Шрифты.
 *
 * Цифры — моноширинные: во время замера значение меняется десятки раз в
 * секунду, и обычные цифры «прыгают» по ширине, а табличные стоят на месте.
 * Подписи — с разрядкой и капсом: приборные надписи так и выглядят, и
 * заметно спокойнее читаются в плотной сетке.
 */
private val PanelTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 58.sp,
        lineHeight = 62.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-1).sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    ),
    headlineSmall = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = TextStyle(
        fontSize = 19.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    labelLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
    /** Заголовки секций: капс с разрядкой, как на приборах. */
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp,
    ),
)

/** Цифры одинаковой ширины — иначе значение прыгает во время замера. */
val TabularFigures = "tnum"

@Composable
fun SpeedmeterTheme(
    accentId: String = Accents.DEFAULT.id,
    themeMode: ThemeMode = ThemeMode.DEFAULT,
    content: @Composable () -> Unit,
) {
    val accent = Accents.of(accentId)
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val scheme = if (dark) Panel.darkScheme(accent) else Panel.lightScheme(accent)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // Панели рисуются под содержимым: подложка должна совпадать с
            // фоном приложения, иначе видна полоска другого оттенка.
            @Suppress("DEPRECATION")
            window.statusBarColor = scheme.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = scheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = PanelTypography,
        content = content,
    )
}

/** Общие размеры: отступы и радиусы, одинаковые на всех экранах. */
object Sizes {
    val gutter = 20.dp
    val cardRadius = 22.dp
    val chipRadius = 14.dp
    val sectionGap = 26.dp
    val hairline = 1.dp
}