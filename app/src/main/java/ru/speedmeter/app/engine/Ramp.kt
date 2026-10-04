package ru.speedmeter.app.engine

/**
 * Как выбрать размер следующего замера.
 *
 * Замер идёт лесенкой: начинаем с килобайта, чтобы не тратить трафик на
 * медленный канал, и поднимаем размер, пока хватает времени. Слишком
 * маленький файл измеряет скорость установления соединения, а не линии;
 * слишком большой — трафик и время пользователя.
 *
 * Правило: удваиваем, пока предыдущий замер уложился в [fastMs] и есть
 * запас бюджета; иначе остаёмся на этом размере.
 */
class Ramp(
    val firstBytes: Long = 1_000_000,
    private val fastMs: Long = 2_500,
    val minBytes: Long = 250_000,
    val maxBytes: Long = 100_000_000,
) {
    var size: Long = firstBytes
        private set

    var step: Int = 0
        private set

    /** Уложился замер в «быстрый» бюджет — можно пробовать крупнее. */
    fun next(lastSample: Sample?): Long {
        if (lastSample == null) return size
        val quick = lastSample.millis <= fastMs
        val nextSize = if (quick) size * 2 else size
        size = nextSize.coerceIn(minBytes, maxBytes)
        step++
        return size
    }

    /** Сколько ещё попыток разумно сделать. */
    fun done(budgetLeftMs: Long): Boolean = budgetLeftMs <= 0 || size >= maxBytes
}
