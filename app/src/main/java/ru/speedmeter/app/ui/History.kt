package ru.speedmeter.app.ui

import ru.speedmeter.app.data.TestRecord
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Разбор истории для показа: заголовки дней и изменение к прошлому замеру.
 *
 * Отдельный файл без Compose: это арифметика и календарь, их удобно
 * проверять тестами, а держать их в экране нечем.
 */
object History {

    /**
     * Изменение загрузки к предыдущему замеру в процентах.
     *
     * Знак важен: «+12%» и «−4%» говорят разные вещи, поэтому ноль
     * отдаётся как 0, а не как «плюс ноль». Если прошлого замера нет или
     * он нулевой, сравнивать не с чем — возвращаем null, и строка просто
     * не рисуется.
     */
    fun deltaPercent(current: Double, previous: Double?): Int? {
        if (previous == null || previous <= 0.0) return null
        val delta = (current - previous) / previous * 100.0
        if (abs(delta) < 0.5) return 0
        return delta.roundToInt()
    }

    /** Группировка истории по календарным дням, свежие сверху. */
    fun byDay(records: List<TestRecord>, nowMillis: Long = System.currentTimeMillis()): List<DayGroup> {
        val groups = LinkedHashMap<Long, MutableList<TestRecord>>()
        records.forEach { record ->
            val day = startOfDay(record.atMillis)
            groups.getOrPut(day) { mutableListOf() }.add(record)
        }
        return groups.entries.map { (day, items) ->
            DayGroup(
                dayStartMillis = day,
                title = dayTitle(day, nowMillis),
                records = items.sortedByDescending { it.atMillis },
            )
        }
    }

    fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** «Сегодня», «Вчера» или дата вроде «4 октября». */
    fun dayTitle(dayStartMillis: Long, nowMillis: Long): String {
        val days = ((startOfDay(nowMillis) - dayStartMillis) / 86_400_000L).toInt()
        return when (days) {
            0 -> "Сегодня"
            1 -> "Вчера"
            in 2..6 -> weekdayName(dayStartMillis)
            else -> dateName(dayStartMillis)
        }
    }

    private fun weekdayName(millis: Long): String {
        val names = arrayOf("воскресенье", "понедельник", "вторник", "среда", "четверг", "пятница", "суббота")
        val index = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_WEEK) - 1
        return names[index].replaceFirstChar { it.uppercase() }
    }

    private fun dateName(millis: Long): String {
        val months = arrayOf(
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря",
        )
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
        return "${calendar.get(Calendar.DAY_OF_MONTH)} ${months[calendar.get(Calendar.MONTH)]}"
    }

    data class DayGroup(
        val dayStartMillis: Long,
        val title: String,
        val records: List<TestRecord>,
    )
}