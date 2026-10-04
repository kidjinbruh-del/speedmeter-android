package ru.speedmeter.app.engine

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

/**
 * Замер скорости по HTTP. Без сторонних библиотек: HttpURLConnection и
 * корутины — этого хватает, а лишние мегабайты в APK и версии для
 * поддержки не нужны.
 *
 * Как меряем правильно:
 *  * размер файла растёт лесенкой (см. [Ramp]) — маленький файл измеряет
 *    не скорость линии, а скорость установления соединения;
 *  * первые четверть потока не учитываем: это разгон TCP и заголовки;
 *  * скорость считаем по фактически прочитанным байтам и реальному
 *    времени, а не по заявленному Content-Length — иначе обрыв на
 *    середине покажет фантастические мегабиты.
 */
class SpeedTest(private val endpoint: Endpoint) {

    /** Что происходит прямо сейчас — на экране это видно как подпись. */
    sealed interface Progress {
        data class Phase(val title: String) : Progress
        data class Running(
            val mbps: Double,
            val bytes: Long,
            val targetBytes: Long,
            val done: Boolean = false,
        ) : Progress
    }

    data class DownloadOutcome(val mbps: Double, val samples: List<Sample>)
    data class UploadOutcome(val mbps: Double, val samples: List<Sample>)

    /** Итог замера целиком — то, что показывают и кладут в историю. */
    data class Result(
        val downloadMbps: Double,
        val uploadMbps: Double,
        val pingMs: Double,
        val jitterMs: Double,
        val endpointName: String,
        val seconds: Long,
    )

    /**
     * Задержка и джиттер.
     *
     * Задержка — время до первого байта ответа (TTFB). Тело у такого
     * запроса пустое, поэтому замер засчитывается по факту успешного
     * ответа: раньше пустой ответ считался «нет данных» и на экране
     * появлялись нули.
     *
     * Перед серией делается прогрев: первый запрос платит за DNS, TCP и
     * TLS, и на мобильном интернете это до секунды. Без прогрева
     * «задержка» показывала время установления соединения, а не отклик
     * сервера. Первый настоящий замер тоже отбрасывается — соединение к
     * этому моменту ещё не прогрелось полностью.
     */
    suspend fun measureLatency(rounds: Int = 8): Pair<Double, Double> =
        withContext(Dispatchers.IO) {
            probe()
            val samples = ArrayList<Double>(rounds)
            repeat(rounds) { index ->
                val took = probe()
                if (took != null) samples += took
                // Пауза между запросами, иначе они сливаются в один поток.
                if (index != rounds - 1) delay(120)
            }
            val warm = samples.drop(1).ifEmpty { samples }
            // Задержку берём медианой по всем замерам — медиана сама по себе
            // устойчива. А вот джиттер считаем без самой медленной записи:
            // одна заминка в сети иначе даёт «джиттер 600 мс», и число
            // перестаёт что-либо говорить о канале.
            Stats.percentile(warm, 0.5) to Stats.jitter(Stats.dropLongest(warm))
        }

    /**
     * Один запрос с замером: [null], если сеть недоступна или ответ не 2xx.
     *
     * Соединение намеренно не закрываем — есть шанс, что следующий запрос
     * переиспользует его из пула keep-alive и не платит за TLS заново.
     * На замере эффект оказался небольшим: 78 мс против 89 мс, потому что
     * Android держит соединение не для всех запросов подряд. Хуже от
     * этого не становится, а код остаётся тем же, что и при явном
     * закрытии.
     */
    private suspend fun probe(): Double? {
        coroutineContext.ensureActive()
        val started = SystemClock.elapsedRealtime()
        return try {
            val connection = open(endpoint.latencyUrl(), "GET")
            connection.connect()
            val code = connection.responseCode
            connection.inputStream.use { stream -> stream.read() }
            val took = (SystemClock.elapsedRealtime() - started).toDouble()
            if (code in 200..299 && took > 0) took else null
        } catch (e: IOException) {
            null
        }
    }

    /** Загрузка: лесенка размеров, пока хватает бюджета времени. */
    suspend fun measureDownload(
        budgetMs: Long,
        onProgress: (Progress) -> Unit = {},
    ): DownloadOutcome = withContext(Dispatchers.IO) {
        val ramp = Ramp()
        val samples = ArrayList<Sample>()
        val startedAt = SystemClock.elapsedRealtime()
        var target = ramp.size

        while (true) {
            val left = budgetMs - (SystemClock.elapsedRealtime() - startedAt)
            if (left <= 0 || ramp.size >= ramp.maxBytes) break

            onProgress(Progress.Phase("Загрузка · файл ${Fmt.one(megabytes(target))} МБ"))
            val sample = downloadOnce(target) { mbps, bytes ->
                onProgress(Progress.Running(mbps, bytes, target))
            }
            if (sample != null) {
                samples += sample
                onProgress(
                    Progress.Running(sample.mbps, sample.bytes, target, done = true),
                )
            }
            target = ramp.next(sample)
        }

        DownloadOutcome(Stats.summary(samples.map { it.mbps }), samples)
    }

    /** Отдача: та же лесенка, но поменьше — загрузка съедает трафик быстрее. */
    suspend fun measureUpload(
        budgetMs: Long,
        onProgress: (Progress) -> Unit = {},
    ): UploadOutcome = withContext(Dispatchers.IO) {
        val ramp = Ramp(
            firstBytes = 500_000,
            minBytes = 500_000,
            maxBytes = 25_000_000,
            fastMs = 2_000,
        )
        val samples = ArrayList<Sample>()
        val startedAt = SystemClock.elapsedRealtime()
        var target = ramp.size
        // Случайные байты, а не нули: нули сервер принимает «в полтора раза
        // быстрее», чем они ушли по проводу, и замер соврёт в свою пользу.
        val chunk = Random.nextBytes(64 * 1024)

        while (true) {
            val left = budgetMs - (SystemClock.elapsedRealtime() - startedAt)
            if (left <= 0 || ramp.size >= ramp.maxBytes) break

            onProgress(Progress.Phase("Отдача · отправляем ${Fmt.one(megabytes(target))} МБ"))
            val sample = uploadOnce(target, chunk) { mbps, bytes ->
                onProgress(Progress.Running(mbps, bytes, target))
            }
            if (sample != null) {
                samples += sample
                onProgress(
                    Progress.Running(sample.mbps, sample.bytes, target, done = true),
                )
            }
            target = ramp.next(sample)
        }

        UploadOutcome(Stats.summary(samples.map { it.mbps }), samples)
    }

    /**
     * Один загрузочный замер [target] байт.
     *
     * Возвращает Sample только если скорость считалась дольше 200 мс:
     * на коротком отрезке SystemClock.elapsedRealtime() даёт 0, и деление
     * даёт бесконечность.
     */
    private suspend fun downloadOnce(
        target: Long,
        onTick: (Double, Long) -> Unit,
    ): Sample? {
        val connection = open(endpoint.downloadUrl(target), "GET")
        try {
            connection.connect()
            val stream = connection.inputStream
            val buffer = ByteArray(BUFFER)
            val warmup = (target / 4).coerceAtLeast(1)
            var raw = 0L
            var counted = 0L
            var speedStarted = 0L

            while (raw < target) {
                coroutineContext.ensureActive()
                val read = stream.read(buffer)
                if (read < 0) break
                raw += read
                if (speedStarted == 0L && raw >= warmup) {
                    speedStarted = SystemClock.elapsedRealtime()
                }
                if (speedStarted > 0L) {
                    counted += read
                    val seconds = (SystemClock.elapsedRealtime() - speedStarted) / 1000.0
                    if (seconds >= 0.2) {
                        onTick(counted * 8.0 / seconds / 1_000_000.0, counted)
                    }
                }
            }
            stream.close()

            val millis = SystemClock.elapsedRealtime() - speedStarted
            return if (speedStarted > 0L && millis >= 200) {
                Sample(counted.coerceAtLeast(1), millis)
            } else {
                null
            }
        } catch (e: IOException) {
            return null
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun uploadOnce(
        target: Long,
        chunk: ByteArray,
        onTick: (Double, Long) -> Unit,
    ): Sample? {
        val connection = open(endpoint.uploadUrl(), "POST")
        try {
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(target)
            val warmup = target / 4
            val started = SystemClock.elapsedRealtime()
            var speedStarted = 0L
            var sent = 0L
            val out = connection.outputStream

            while (sent < target) {
                coroutineContext.ensureActive()
                val size = minOf(chunk.size.toLong(), target - sent).toInt()
                out.write(chunk, 0, size)
                out.flush()
                sent += size
                if (speedStarted == 0L && sent >= warmup) {
                    speedStarted = SystemClock.elapsedRealtime()
                }
                if (speedStarted > 0L) {
                    val seconds = (SystemClock.elapsedRealtime() - speedStarted) / 1000.0
                    if (seconds >= 0.2) {
                        onTick((sent - warmup) * 8.0 / seconds / 1_000_000.0, sent - warmup)
                    }
                }
            }
            out.close()
            // Сервер обязан ответить: иначе мы мерили не сеть, а буфер.
            connection.inputStream.use { it.read(ByteArray(1024)) }

            val millis = SystemClock.elapsedRealtime() - speedStarted
            return if (speedStarted > 0L && millis >= 200) {
                Sample((sent - warmup).coerceAtLeast(1), millis)
            } else {
                null
            }
        } catch (e: IOException) {
            return null
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, method: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        // identity и no-cache — обязательны: иначе сервер пришлёт заголовок
        // gzip и мы посчитаем сжатый объём вместо реального трафика.
        connection.setRequestProperty("Accept-Encoding", "identity")
        connection.setRequestProperty("Cache-Control", "no-cache")
        connection.setRequestProperty("User-Agent", USER_AGENT)
        return connection
    }

    private fun megabytes(bytes: Long): Double = bytes / 1_000_000.0

    private companion object {
        const val BUFFER = 128 * 1024
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 12_000
        const val USER_AGENT = "SpeedmeterAndroid/1.0"
    }
}
