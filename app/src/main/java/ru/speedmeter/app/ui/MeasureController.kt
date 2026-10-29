package ru.speedmeter.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.speedmeter.app.data.Store
import ru.speedmeter.app.data.TestRecord
import ru.speedmeter.app.engine.Endpoint
import ru.speedmeter.app.engine.MetricStats
import ru.speedmeter.app.engine.NetworkInfo
import ru.speedmeter.app.engine.NetworkProbe
import ru.speedmeter.app.engine.SpeedTest
import ru.speedmeter.app.engine.Stats
import ru.speedmeter.app.ui.theme.Accents
import ru.speedmeter.app.ui.theme.GaugeStyle
import ru.speedmeter.app.ui.theme.ThemeMode

/** Состояние замера для экрана: живые цифры, фаза и итог. */
data class MeasureState(
    val running: Boolean = false,
    val liveMbps: Double = 0.0,
    val ceilingMbps: Double = 1.0,
    val progress: Float = 0f,
    val caption: String = "Готов к замеру",
    val result: SpeedTest.Result? = null,
    val history: List<TestRecord> = emptyList(),
    val durationSeconds: Int = Store.DEFAULT_DURATION,
    val measureUpload: Boolean = true,
    val endpoint: String = Endpoint.CLOUDFLARE.name,
    val accentId: String = Accents.DEFAULT.id,
    val themeMode: ThemeMode = ThemeMode.DEFAULT,
    val gaugeStyle: GaugeStyle = GaugeStyle.DEFAULT,
    val settingsOpen: Boolean = false,
    val networkInfo: NetworkInfo? = null,
    val customServerDialogOpen: Boolean = false,
    val customServers: List<Endpoint> = emptyList(),
    val onClearHistory: () -> Unit = {},
    val onDeleteRecord: (Long) -> Unit = {},
    val onCustomServerDialogOpen: (Boolean) -> Unit = {},
    val onAddCustomServer: (String, String) -> Unit = { _, _ -> },
    val onRemoveCustomServers: () -> Unit = {},
    val onDuration: (Int) -> Unit = {},
    val onMeasureUpload: (Boolean) -> Unit = {},
    val onEndpoint: (String) -> Unit = {},
    val onAccent: (String) -> Unit = {},
    val onThemeMode: (ThemeMode) -> Unit = {},
    val onGaugeStyle: (GaugeStyle) -> Unit = {},
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
fun rememberMeasureController(store: Store, context: android.content.Context): MeasureController {
    val scope = rememberCoroutineScope()
    val saved by store.state.collectAsState()

    var running by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf(0.0) }
    var caption by remember { mutableStateOf("Готов к замеру") }
    var progress by remember { mutableFloatStateOf(0f) }
    var ceiling by remember { mutableStateOf(1.0) }
    var result by remember { mutableStateOf<SpeedTest.Result?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var customServerDialogOpen by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var networkInfo by remember { mutableStateOf<NetworkInfo?>(null) }

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
        val netBefore = NetworkProbe.current(context)
        networkInfo = netBefore

        job = scope.launch {
            val started = System.currentTimeMillis()
            try {
                val latency = engine.measureLatency()
                if (latency == null) {
                    // Ни один запрос не прошёл: пинг 0 в истории выглядел бы
                    // как идеальная сеть, поэтому замер просто не выполняется.
                    caption = "Сеть недоступна — замер не выполнен"
                    return@launch
                }
                val (ping, jitter) = latency

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

                // Шаблонному серверу ({bytes}) некуда отправлять отдачу —
                // фаза пропускается, чтобы не генерировать трафик в никуда.
                val canUpload = withUpload && endpoint.uploadUrl().isNotEmpty()
                var upload = 0.0
                var uploadStats = MetricStats.EMPTY
                if (canUpload) {
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
                    uploadStats = outcome.stats
                }

                // Если сеть сменилась посреди замера (Wi-Fi → LTE), медиана
                // становится смесью двух разных каналов — такому результату
                // нельзя верить и нельзя сравнивать его с тарифом.
                val netAfter = NetworkProbe.current(context)
                val networkChanged = netAfter.summary != netBefore.summary

                val final = SpeedTest.Result(
                    downloadMbps = download.mbps,
                    uploadMbps = upload,
                    pingMs = ping,
                    jitterMs = jitter,
                    endpointName = endpoint.name,
                    seconds = (System.currentTimeMillis() - started) / 1000,
                    networkSummary = networkInfo?.summary ?: "",
                    downloadStats = download.stats,
                    uploadStats = uploadStats,
                    streams = SpeedTest.STREAMS,
                    networkChanged = networkChanged,
                )
                result = final
                live = download.mbps
                ceiling = Stats.gaugeCeiling(download.mbps)
                caption = when {
                    download.mbps <= 0.0 -> "Замер не удался: сервер не отдал данные"
                    networkChanged -> "Сеть сменилась во время замера — цифры несравнимы"
                    else -> "Готово за ${final.seconds} с"
                }

                // Неудачный замер в историю не пишем: ноль в списке выглядит
                // как реальная скорость 0 Мбит/с, а не как сбой.
                if (download.mbps > 0.0) {
                    store.addRecord(
                        TestRecord(
                            atMillis = started,
                            downloadMbps = final.downloadMbps,
                            uploadMbps = final.uploadMbps,
                            pingMs = final.pingMs,
                            jitterMs = final.jitterMs,
                            endpoint = final.endpointName,
                            networkKind = networkInfo?.kind?.name ?: "",
                            networkOperator = networkInfo?.operator ?: "",
                            networkSsid = networkInfo?.ssid ?: "",
                            downloadP10 = download.stats.p10,
                            downloadP90 = download.stats.p90,
                            samples = download.stats.count,
                            streams = SpeedTest.STREAMS,
                        ),
                    )
                }
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
            accentId = saved.accentId,
            themeMode = saved.themeMode,
            gaugeStyle = saved.gaugeStyle,
            settingsOpen = settingsOpen,
            customServerDialogOpen = customServerDialogOpen,
            networkInfo = networkInfo,
            customServers = saved.customServers,
            onClearHistory = store::clearHistory,
            onDeleteRecord = store::deleteRecord,
            onCustomServerDialogOpen = { customServerDialogOpen = it },
            onAddCustomServer = store::addCustomServer,
            onRemoveCustomServers = store::removeCustomServers,
            onDuration = store::setDuration,
            onMeasureUpload = store::setMeasureUpload,
            onEndpoint = store::setEndpoint,
            onAccent = store::setAccent,
            onThemeMode = store::setThemeMode,
            onGaugeStyle = store::setGaugeStyle,
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
