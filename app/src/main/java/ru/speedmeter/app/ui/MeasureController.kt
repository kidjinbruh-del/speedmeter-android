package ru.speedmeter.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.speedmeter.app.data.Store
import ru.speedmeter.app.data.TestRecord
import ru.speedmeter.app.engine.Endpoint
import ru.speedmeter.app.engine.SpeedTest
import ru.speedmeter.app.engine.Stats

/** Состояние замера для экрана: живые цифры, фаза и итог. */
data class MeasureState(
    val running: Boolean = false,
    val liveMbps: Double = 0.0,
    val ceilingMbps: Double = 1.0,
    val progress: Float = 0f,
    // Подпись не повторяет текст кнопки: иначе поиск по надписи находит
    // подпись датчика вместо самой кнопки и тап уходит мимо.
    val caption: String = "Готов к замеру",
    val result: SpeedTest.Result? = null,
    val history: List<TestRecord> = emptyList(),
    val durationSeconds: Int = Store.DEFAULT_DURATION,
    val measureUpload: Boolean = true,
    val endpoint: String = Endpoint.CLOUDFLARE.name,
    val settingsOpen: Boolean = false,
    val onClearHistory: () -> Unit = {},
    val onDuration: (Int) -> Unit = {},
    val onMeasureUpload: (Boolean) -> Unit = {},
    val onEndpoint: (String) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onCloseSettings: () -> Unit = {},
)

/**
 * Запуск замера и связь с хранилищем.
 *
 * Отдельная функция, а не ViewModel: состояние живёт ровно столько,
 * сколько открыт экран, а настроек и истории достаточно SharedPreferences.
 * ViewModel здесь добавила бы класс файлов без единого выигрыша.
 */
@Composable
fun rememberMeasureController(store: Store): MeasureController {
    val scope = rememberCoroutineScope()
    val saved by store.state.collectAsState()

    var running by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf(0.0) }
    var caption by remember { mutableStateOf("Нажмите «Измерить»") }
    var progress by remember { mutableFloatStateOf(0f) }
    var ceiling by remember { mutableStateOf(1.0) }
    var result by remember { mutableStateOf<SpeedTest.Result?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun start() {
        if (running) return
        running = true
        live = 0.0
        caption = "Задержка…"
        progress = 0f
        result = null

        // Настройки читаем один раз на запуск: смена длительности посреди
        // замера привела бы к тому, что половина файлов скачана по старым
        // правилам, а вторая по новым.
        val endpoint = Endpoint.find(saved.endpoint)
        val budgetMs = saved.durationMs
        val withUpload = saved.measureUpload
        val engine = SpeedTest(endpoint)

        job = scope.launch {
            val started = System.currentTimeMillis()
            try {
                val (ping, jitter) = engine.measureLatency()

                caption = "Загрузка"
                val download = engine.measureDownload(if (withUpload) budgetMs / 2 else budgetMs) { p ->
                    when (p) {
                        is SpeedTest.Progress.Phase -> caption = p.title
                        is SpeedTest.Progress.Running -> {
                            live = p.mbps
                            ceiling = Stats.gaugeCeiling(p.mbps)
                            progress = (p.bytes.toFloat() / p.targetBytes).coerceIn(0f, 1f)
                            caption = if (p.done) {
                                "Загрузка: ${formatValue(p.mbps)} Мбит/с"
                            } else {
                                "Загрузка"
                            }
                        }
                    }
                }

                var upload = 0.0
                if (withUpload) {
                    caption = "Отдача"
                    progress = 0f
                    val outcome = engine.measureUpload(budgetMs / 2) { p ->
                        when (p) {
                            is SpeedTest.Progress.Phase -> caption = p.title
                            is SpeedTest.Progress.Running -> {
                                live = p.mbps
                                ceiling = Stats.gaugeCeiling(p.mbps)
                                progress = (p.bytes.toFloat() / p.targetBytes).coerceIn(0f, 1f)
                                caption = if (p.done) {
                                    "Отдача: ${formatValue(p.mbps)} Мбит/с"
                                } else {
                                    "Отдача"
                                }
                            }
                        }
                    }
                    upload = outcome.mbps
                }

                val final = SpeedTest.Result(
                    downloadMbps = download.mbps,
                    uploadMbps = upload,
                    pingMs = ping,
                    jitterMs = jitter,
                    endpointName = endpoint.name,
                    seconds = (System.currentTimeMillis() - started) / 1000,
                )
                result = final
                live = download.mbps
                ceiling = Stats.gaugeCeiling(download.mbps)
                caption = "Готово за ${final.seconds} с"
                store.addRecord(
                    TestRecord(
                        atMillis = started,
                        downloadMbps = final.downloadMbps,
                        uploadMbps = final.uploadMbps,
                        pingMs = final.pingMs,
                        jitterMs = final.jitterMs,
                        endpoint = final.endpointName,
                    ),
                )
            } catch (e: CancellationException) {
                caption = "Прервано"
                throw e
            } catch (e: Exception) {
                // Причина в подписи: «нет сети» и «сервер недоступен» для
                // пользователя — разные вещи.
                caption = "Не получилось: ${e.message ?: e::class.java.simpleName}"
            } finally {
                running = false
                progress = 0f
            }
        }
    }

    fun stop() {
        job?.cancel()
        running = false
        caption = "Прервано"
    }

    return MeasureController(
        state = MeasureState(
            running = running,
            liveMbps = live,
            ceilingMbps = ceiling,
            progress = progress,
            caption = caption,
            result = result,
            history = saved.history,
            durationSeconds = saved.durationSeconds,
            measureUpload = saved.measureUpload,
            endpoint = saved.endpoint,
            settingsOpen = settingsOpen,
            onClearHistory = store::clearHistory,
            onDuration = store::setDuration,
            onMeasureUpload = store::setMeasureUpload,
            onEndpoint = store::setEndpoint,
            onOpenSettings = { settingsOpen = true },
            onCloseSettings = { settingsOpen = false },
        ),
        start = ::start,
        stop = ::stop,
    )
}
class MeasureController(
    val state: MeasureState,
    val start: () -> Unit,
    val stop: () -> Unit,
)
