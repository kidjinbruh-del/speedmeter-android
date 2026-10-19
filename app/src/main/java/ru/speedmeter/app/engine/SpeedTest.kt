package ru.speedmeter.app.engine

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.random.Random

/**
 * Замер скорости по HTTP. Без сторонних библиотек: HttpURLConnection и
 * корутины — этого хватает, а лишние мегабайты в APK и версии для
 * поддержки не нужны.
 *
 * Как меряем правильно:
 *  * **несколько потоков.** Один TCP-поток на канале с большой задержкой
 *    не успевает заполнить трубу и показывает скорость ниже реальной.
 *    Четыре параллельных соединения дают цифру, которую можно сравнивать
 *    с тарифом провайдера;
 *  * **окно измерения, а не «файл целиком».** После прогрева каждые 500 мс
 *    снимается прирост байтов и времени. Из этих окон берётся медиана:
 *    один провал в сети не переписывает результат;
 *  * **заявленное не считается полученным.** Скорость считается только по
 *    фактически прочитанным байтам. Если соединение оборвалось на середине,
 *    в зачёт идёт прочитанное, а не Content-Length;
 *  * **прогрев выбрасывается.** Первые 900 мс — это разгон TCP и TLS,
 *    в результат они не попадают;
 *  * **задержка — после установления соединения.** Тело ответа читается
 *    до конца и закрывается, иначе соединение не возвращается в пул и
 *    каждый замер снова платит за рукопожатие, показывая «пинг» в сотни
 *    миллисекунд на самом деле быстрого канала.
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

    data class DownloadOutcome(
        val mbps: Double,
        val samples: List<Sample>,
        val stats: MetricStats,
    )

    data class UploadOutcome(
        val mbps: Double,
        val samples: List<Sample>,
        val stats: MetricStats,
    )

    /** Итог замера целиком — то, что показывают и кладут в историю. */
    data class Result(
        val downloadMbps: Double,
        val uploadMbps: Double,
        val pingMs: Double,
        val jitterMs: Double,
        val endpointName: String,
        val seconds: Long,
        val networkSummary: String = "",
        val downloadStats: MetricStats = MetricStats.EMPTY,
        val uploadStats: MetricStats = MetricStats.EMPTY,
        val streams: Int = STREAMS,
        /** Сеть сменилась во время замера — результат нельзя сравнивать. */
        val networkChanged: Boolean = false,
    )

    /**
     * Задержка и джиттер.
     *
     * Замер — время до заголовков ответа. Первый запрос платит за DNS, TCP
     * и TLS, поэтому он идёт в прогрев и в серию не попадает. Джиттер —
     * медиана абсолютных разниц между соседними замерами: одна заминка
     * сдвигает её на десятые, а не на сотни миллисекунд.
     */
    suspend fun measureLatency(rounds: Int = 10): Pair<Double, Double> =
        withContext(Dispatchers.IO) {
            probe() // прогрев: DNS, TCP, TLS
            val samples = ArrayList<Double>(rounds)
            repeat(rounds) { index ->
                val took = probe()
                if (took != null) samples += took
                if (index != rounds - 1) delay(120)
            }
            if (samples.isEmpty()) return@withContext 0.0 to 0.0
            val median = Stats.median(samples)
            val deltas = samples.zipWithNext { a, b -> abs(a - b) }
            median to Stats.median(deltas)
        }

    /**
     * Один запрос с замером: [null], если сеть недоступна или ответ не 2xx.
     *
     * Тело читается до конца и закрывается. Раньше читался один байт, поток
     * не закрывался — соединение не возвращалось в пул, и следующий замер
     * снова платил за рукопожатие. На HTTPS это давало «пинг» в разы
     * больше настоящего.
     */
    private suspend fun probe(): Double? {
        coroutineContext.ensureActive()
        val started = SystemClock.elapsedRealtime()
        return try {
            val connection = open(endpoint.latencyUrl(), "GET")
            connection.connect()
            val code = connection.responseCode
            connection.inputStream.use { stream -> stream.readBytes() }
            val took = (SystemClock.elapsedRealtime() - started).toDouble()
            if (code in 200..299 && took > 0) took else null
        } catch (e: IOException) {
            null
        }
    }

    /** Загрузка: несколько потоков, окно измерения после прогрева. */
    suspend fun measureDownload(
        budgetMs: Long,
        onProgress: (Progress) -> Unit = {},
    ): DownloadOutcome = withContext(Dispatchers.IO) {
        val deadline = SystemClock.elapsedRealtime() + budgetMs
        val counter = AtomicLong(0)
        val samples = ArrayList<Sample>()

        onProgress(Progress.Phase("Загрузка · $STREAMS потока"))
        coroutineScope {
            val jobs = (1..STREAMS).map {
                launch { downloadStream(counter) }
            }
            collect(counter, deadline, MAX_DOWNLOAD_BYTES, onProgress, samples)
            jobs.forEach(Job::cancel)
        }

        DownloadOutcome(Stats.summary(samples.map { it.mbps }), samples, MetricStats.of(samples.map { it.mbps }))
    }

    /** Отдача: те же правила, но потолок трафика ниже. */
    suspend fun measureUpload(
        budgetMs: Long,
        onProgress: (Progress) -> Unit = {},
    ): UploadOutcome = withContext(Dispatchers.IO) {
        val deadline = SystemClock.elapsedRealtime() + budgetMs
        val counter = AtomicLong(0)
        val samples = ArrayList<Sample>()
        // Случайные байты, а не нули: нули сервер принимает «в полтора раза
        // быстрее», чем они ушли по проводу, и замер соврёт в свою пользу.
        val chunk = Random.nextBytes(64 * 1024)

        onProgress(Progress.Phase("Отдача · $STREAMS потока"))
        coroutineScope {
            val jobs = (1..STREAMS).map {
                launch { uploadStream(counter, chunk) }
            }
            collect(counter, deadline, MAX_UPLOAD_BYTES, onProgress, samples)
            jobs.forEach(Job::cancel)
        }

        UploadOutcome(Stats.summary(samples.map { it.mbps }), samples, MetricStats.of(samples.map { it.mbps }))
    }

    /**
     * Снимает окна по 500 мс и складывает их в [sink].
     *
     * Первые [WARMUP_MS] не учитываются: это разгон. Дальше каждое окно —
     * это реально полученные байты за реально прошедшее время.
     */
    private suspend fun collect(
        counter: AtomicLong,
        deadline: Long,
        cap: Long,
        onProgress: (Progress) -> Unit,
        sink: MutableList<Sample>,
    ) {
        delay(WARMUP_MS)
        var lastBytes = counter.get()
        var lastAt = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() < deadline && counter.get() < cap) {
            delay(SAMPLE_MS)
            val now = SystemClock.elapsedRealtime()
            val bytes = counter.get()
            val deltaBytes = bytes - lastBytes
            val deltaMs = now - lastAt
            lastBytes = bytes
            lastAt = now
            if (deltaBytes <= 0 || deltaMs <= 0) continue
            val sample = Sample(deltaBytes, deltaMs)
            sink += sample
            onProgress(Progress.Running(sample.mbps, bytes, cap))
        }
    }

    private suspend fun downloadStream(counter: AtomicLong) {
        var connection: HttpURLConnection? = null
        try {
            connection = open(endpoint.downloadUrl(PER_STREAM_DOWNLOAD), "GET")
            connection.connect()
            connection.inputStream.use { input ->
                val buffer = ByteArray(BUFFER)
                while (counter.get() < MAX_DOWNLOAD_BYTES) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    counter.addAndGet(read.toLong())
                }
            }
        } catch (e: Exception) {
            // обрыв или отмена — поток закончился, остальные продолжают
        } finally {
            connection?.disconnect()
        }
    }

    private suspend fun uploadStream(counter: AtomicLong, chunk: ByteArray) {
        var connection: HttpURLConnection? = null
        try {
            connection = open(endpoint.uploadUrl(), "POST")
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(PER_STREAM_UPLOAD)
            val out = connection.outputStream
            var sent = 0L
            while (sent < PER_STREAM_UPLOAD && counter.get() < MAX_UPLOAD_BYTES) {
                coroutineContext.ensureActive()
                val size = minOf(chunk.size.toLong(), PER_STREAM_UPLOAD - sent).toInt()
                out.write(chunk, 0, size)
                out.flush()
                sent += size
                counter.addAndGet(size.toLong())
            }
            try {
                out.close()
            } catch (_: Exception) {
                // поток обрезан отменой — сервер вернёт ошибку, она не нужна
            }
        } catch (e: Exception) {
            // обрыв или отмена
        } finally {
            connection?.disconnect()
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
        connection.setRequestProperty("Connection", "keep-alive")
        connection.setRequestProperty("User-Agent", USER_AGENT)
        return connection
    }

    companion object {
        /** Потоков на замер: одно соединение не заполняет канал с большой задержкой. */
        const val STREAMS = 4

        private const val BUFFER = 128 * 1024
        private const val SAMPLE_MS = 500L
        private const val WARMUP_MS = 900L
        private const val MAX_DOWNLOAD_BYTES = 60_000_000L
        private const val MAX_UPLOAD_BYTES = 30_000_000L
        private const val PER_STREAM_DOWNLOAD = 20_000_000L
        private const val PER_STREAM_UPLOAD = 10_000_000L
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 12_000
        private const val USER_AGENT = "SpeedmeterAndroid/1.2"
    }
}
