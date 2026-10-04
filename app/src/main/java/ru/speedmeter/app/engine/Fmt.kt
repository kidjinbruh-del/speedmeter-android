package ru.speedmeter.app.engine

import java.util.Locale

/**
 * Формат чисел в одном месте.
 *
 * Приложение русское, поэтому дробная часть через запятую: «8,0 МБ»
 * читается как число, а «8.0 МБ» выглядит как машинный вывод. Локаль
 * задана явно, а не берётся из настроек системы: на телефоне с
 * американским языком интерфейса русское приложение всё равно должно
 * показывать запятую.
 */
object Fmt {

    val RU: Locale = Locale("ru", "RU")

    /** Одно знак после запятой: 8,0 */
    fun one(value: Double): String = String.format(RU, "%.1f", value)

    /** Без дробной части: 204 */
    fun whole(value: Double): String = String.format(RU, "%.0f", value)

    /** Сколько знаков уместно: под десять — с дробью, выше — без. */
    fun speed(value: Double): String =
        if (value < 10.0) one(value) else whole(value)

    fun millis(value: Double): String =
        if (value <= 0.0) "—" else if (value < 10.0) one(value) else whole(value)
}