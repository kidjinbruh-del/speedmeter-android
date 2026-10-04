package ru.speedmeter.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Акценты оформления.
 *
 * Каждый акцент описан тремя цветами — загрузка, отдача, задержка — плюс
 * отдельные варианты для тёмной и светлой темы: на чёрном нужен один
 * оттенок, на белом тот же оттенок становится нечитаемым, поэтому
 * светлые варианты темнее.
 *
 * Контейнеры и подписи не хранятся здесь: они считаются из этих трёх
 * цветов смешиванием с поверхностью. Иначе при добавлении акцента пришлось
 * бы подбирать восемь оттенков вручную, и через один акцент что-нибудь
 * обязательно разъезжалось бы с остальными.
 */
data class Accent(
    val id: String,
    val title: String,
    val dark: Triple<Color, Color, Color>,
    val light: Triple<Color, Color, Color>,
) {
    val primary: Color get() = dark.first
    val secondary: Color get() = dark.second
    val tertiary: Color get() = dark.third
}

/** Как выбирается тема: пользователь или система. */
enum class ThemeMode(val title: String) {
    DARK("Тёмная"),
    LIGHT("Светлая"),
    SYSTEM("Как в системе");

    companion object {
        val DEFAULT = DARK

        fun of(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** Вид датчика. Оба сделаны по одной геометрии, различаются подачей. */
enum class GaugeStyle(val title: String) {
    ARC("Дуга со стрелкой"),
    RING("Кольцо");

    companion object {
        val DEFAULT = ARC

        fun of(name: String?): GaugeStyle =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

object Accents {

    val CYAN = Accent(
        id = "cyan",
        title = "Циан",
        dark = Triple(Color(0xFF2EE6D6), Color(0xFFF7B955), Color(0xFF7FE7A1)),
        light = Triple(Color(0xFF00897B), Color(0xFF9A6700), Color(0xFF2E7D32)),
    )

    val AMBER = Accent(
        id = "amber",
        title = "Янтарь",
        dark = Triple(Color(0xFFFFB020), Color(0xFF4FC3F7), Color(0xFFB39DDB)),
        light = Triple(Color(0xFFB26A00), Color(0xFF0277BD), Color(0xFF5E35B1)),
    )

    val LIME = Accent(
        id = "lime",
        title = "Лайм",
        dark = Triple(Color(0xFFB6F03C), Color(0xFF38BDF8), Color(0xFFFB7185)),
        light = Triple(Color(0xFF4D7C0F), Color(0xFF0369A1), Color(0xFFBE123C)),
    )

    val VIOLET = Accent(
        id = "violet",
        title = "Фиолет",
        dark = Triple(Color(0xFFB79CFF), Color(0xFF34D399), Color(0xFFFBBF24)),
        light = Triple(Color(0xFF6D28D9), Color(0xFF047857), Color(0xFFB45309)),
    )

    val ICE = Accent(
        id = "ice",
        title = "Лёд",
        dark = Triple(Color(0xFF7CB8FF), Color(0xFFF78FD0), Color(0xFF6EE7F0)),
        light = Triple(Color(0xFF1D4ED8), Color(0xFFBE185D), Color(0xFF0E7490)),
    )

    val ALL = listOf(CYAN, AMBER, LIME, VIOLET, ICE)

    val DEFAULT = CYAN

    fun of(id: String?): Accent = ALL.firstOrNull { it.id == id } ?: DEFAULT
}

/** Нейтральная часть палитры: одинаковая для всех акцентов. */
object Neutrals {
    val darkBackground = Color(0xFF0A0E13)
    val darkSurface = Color(0xFF10161D)
    val darkSurfaceHigh = Color(0xFF18212B)
    val darkSurfaceHigher = Color(0xFF1E2A36)
    val darkOutline = Color(0xFF2A3745)
    val darkOnSurface = Color(0xFFE8EFF6)
    val darkOnSurfaceVariant = Color(0xFF93A5B5)

    val lightBackground = Color(0xFFF5F7FA)
    val lightSurface = Color(0xFFFFFFFF)
    val lightSurfaceHigh = Color(0xFFEDF1F5)
    val lightOutline = Color(0xFFC4CFDA)
    val lightOnSurface = Color(0xFF0B1218)
    val lightOnSurfaceVariant = Color(0xFF4C5D6B)

    /** Смешивание цвета с поверхностью: чем больше доля цвета, тем он громче. */
    fun mix(color: Color, surface: Color, ratio: Float): Color = lerp(surface, color, ratio)
}