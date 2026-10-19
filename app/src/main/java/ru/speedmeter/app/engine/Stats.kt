package ru.speedmeter.app.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Один замер и всё, что из него считается.
 *
 * Скорость — это всегда «сколько байт за сколько миллисекунд». Никаких
 * мегабайт в секунду внутри: их любят показывать в рекламе, а человеку
 * нужно то, что написано в договоре с провайдером, — мегабиты.
 */
data class Sample(
    val bytes: Long,
    val millis: Long,
) {
    init {
        require(bytes > 0) { "замер без байтов" }
        require(millis > 0) { "замер без времени" }
    }

    /** Мбит/с. */
    val mbps: Double get() = bytes * 8.0 / (millis / 1000.0) / 1_000_000.0

    val megabits: Double get() = bytes * 8.0 / 1_000_000.0
}

/**
 * Свод по серии замеров: медиана и коридор, в котором канал держался.
 *
 * Медиана — основное число. P10 и P90 показывают разброс: «54 Мбит/с
 * (P10 41 — P90 61)» говорит больше, чем одно число, потому что видно,
 * насколько ровно канал держит скорость.
 */
data class MetricStats(
    val median: Double,
    val p10: Double,
    val p90: Double,
    val count: Int,
    val peak: Double,
) {
    val isEmpty: Boolean get() = count == 0

    companion object {
        val EMPTY = MetricStats(0.0, 0.0, 0.0, 0, 0.0)

        fun of(values: List<Double>): MetricStats {
            if (values.isEmpty()) return EMPTY
            return MetricStats(
                median = Stats.median(values),
                p10 = Stats.percentile(values, 0.10),
                p90 = Stats.percentile(values, 0.90),
                count = values.size,
                peak = values.max(),
            )
        }
    }
}

/**
 * Свод по замерам: скорость, отбрасывание выбросов, джиттер.
 *
 * Медиана вместо среднего — обязательна: один замер может зацепить
 * переподключение TCP или чужую раздачу файлов, и среднее уедет вниз
 * вместе с ним. Медиана такую вспышку просто не видит.
 */
object Stats {

    fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    /**
     * Основная скорость — медиана всех валидных замеров.
     *
     * Раньше здесь выбрасывался самый медленный замер, и это давало
     * систематическое завышение: из любой серии удалялось худшее значение,
     * то есть приложение показывало скорость лучшую, чем канал даёт
     * в среднем. Теперь не выбрасывается ничего: медиана сама устойчива
     * к единичному выбросу, и её не нужно подправлять в свою пользу.
     *
     * Разгон соединения исключается не здесь, а в движке: серия начинается
     * после прогрева, поэтому первый замер уже не платит за TCP и TLS.
     */
    fun summary(values: List<Double>): Double = median(values)

    /** Разброс между соседними замерами: чем меньше, тем ровнее канал. */
    fun jitter(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val deltas = values.zipWithNext { a, b -> abs(a - b) }
        return deltas.sum() / deltas.size
    }

    /**
     * Убирает единственную самую долгую запись — заминку.
     *
     * Секундное ожидание в сети — обычное дело, и без этого разовое
     * ожидание превращает джиттер в 600 мс: число перестаёт говорить о
     * канале и начинает говорить о том, что один раз кто-то качнул файл.
     *
     * Именно максимум, а не минимум: для задержки «дольше» значит «хуже».
     * Замеров меньше трём ничего не убираем — там и так нечего отбрасывать.
     */
    fun dropLongest(values: List<Double>): List<Double> {
        if (values.size < 3) return values
        val longest = values.indexOf(values.max())
        return values.filterIndexed { index, _ -> index != longest }
    }

    /** Размах значений: насколько канал гуляет во времени. */
    fun spread(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        return values.max() - values.min()
    }

    /**
     * Отбрасывание выбросов по границе: всё, что выше отрезка, не берём.
     * Нужно для пинга — один запрос может уйти в забытую сеть и вернуться
     * через две секунды.
     */
    fun percentile(values: List<Double>, p: Double): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val index = ((sorted.size - 1) * p.coerceIn(0.0, 1.0)).roundToInt()
        return sorted[index]
    }

    /** Отбрасываем медленные замеры: оставляем верхние [keep] процентов. */
    fun trimSlow(values: List<Double>, keep: Double = 0.5): List<Double> {
        if (values.size < 3) return values
        val threshold = percentile(values, keep)
        val kept = values.filter { it >= threshold }
        return kept.ifEmpty { values }
    }

    /**
     * Границы шкалы для датчика: округляем вверх с запасом в 5%.
     *
     * Запас нужен, чтобы стрелка не упиралась в конец дуги на пике: иначе
     * «максимум» на экране неотличим от «чуть меньше максимума».
     */
    fun gaugeCeiling(observed: Double): Double {
        if (observed <= 0) return 1.0
        val steps = listOf(
            1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0, 1000.0,
        )
        return steps.firstOrNull { it >= observed * 1.05 } ?: 2000.0
    }

    /** Человеческая запись: 12,4 вместо 12.400000000000001. */
    fun tidy(value: Double): Double {
        val rounded = (value * 10.0).roundToInt() / 10.0
        return max(0.0, rounded)
    }

    /** МБ/с для тех, кто привык к диску: это Мбит/с / 8. */
    fun megabytesPerSecond(mbps: Double): Double = min(mbps / 8.0, 9999.0)
}
