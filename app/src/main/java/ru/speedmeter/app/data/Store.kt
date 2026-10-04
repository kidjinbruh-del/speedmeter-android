package ru.speedmeter.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import ru.speedmeter.app.engine.Endpoint
import ru.speedmeter.app.ui.theme.Accent
import ru.speedmeter.app.ui.theme.Accents
import ru.speedmeter.app.ui.theme.GaugeStyle
import ru.speedmeter.app.ui.theme.ThemeMode

/** Один завершённый замер в истории. */
data class TestRecord(
    val atMillis: Long,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val pingMs: Double,
    val jitterMs: Double,
    val endpoint: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("at", atMillis)
        put("down", downloadMbps)
        put("up", uploadMbps)
        put("ping", pingMs)
        put("jitter", jitterMs)
        put("endpoint", endpoint)
    }

    companion object {
        fun fromJson(raw: String): TestRecord? = runCatching {
            val json = JSONObject(raw)
            TestRecord(
                atMillis = json.optLong("at"),
                downloadMbps = json.optDouble("down"),
                uploadMbps = json.optDouble("up"),
                pingMs = json.optDouble("ping"),
                jitterMs = json.optDouble("jitter"),
                endpoint = json.optString("endpoint"),
            )
        }.getOrNull()
    }
}

/**
 * Настройки и история — в одном хранилище.
 *
 * SharedPreferences, а не база: записей десятки, запросов к ним нет,
 * а Room ради этого тянет с собой миграции и аннотации.
 */
class Store private constructor(private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow(read())
    val state: StateFlow<State> = _state.asStateFlow()

    data class State(
        val durationSeconds: Int = DEFAULT_DURATION,
        val measureUpload: Boolean = true,
        val endpoint: String = Endpoint.CLOUDFLARE.name,
        val accentId: String = Accents.DEFAULT.id,
        val themeModeName: String = ThemeMode.DEFAULT.name,
        val gaugeStyleName: String = GaugeStyle.DEFAULT.name,
        val history: List<TestRecord> = emptyList(),
    ) {
        val durationMs: Long get() = durationSeconds * 1000L

        /** Оформление хранится как строки: значения — перечисления. */
        val accent: Accent get() = Accents.of(accentId)
        val themeMode: ThemeMode get() = ThemeMode.of(themeModeName)
        val gaugeStyle: GaugeStyle get() = GaugeStyle.of(gaugeStyleName)
    }

    private fun read(): State {
        val raw = prefs.getString(KEY_HISTORY, null)
        val records = if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching {
                val array = JSONArray(raw)
                (0 until array.length()).mapNotNull { i ->
                    TestRecord.fromJson(array.optString(i))
                }
            }.getOrDefault(emptyList())
        }
        return State(
            durationSeconds = prefs.getInt(KEY_DURATION, DEFAULT_DURATION),
            measureUpload = prefs.getBoolean(KEY_UPLOAD, true),
            endpoint = prefs.getString(KEY_ENDPOINT, Endpoint.CLOUDFLARE.name)
                ?: Endpoint.CLOUDFLARE.name,
            accentId = prefs.getString(KEY_ACCENT, Accents.DEFAULT.id) ?: Accents.DEFAULT.id,
            themeModeName = prefs.getString(KEY_THEME, ThemeMode.DEFAULT.name)
                ?: ThemeMode.DEFAULT.name,
            gaugeStyleName = prefs.getString(KEY_GAUGE, GaugeStyle.DEFAULT.name)
                ?: GaugeStyle.DEFAULT.name,
            history = records,
        )
    }

    private fun write(state: State) {
        val array = JSONArray()
        state.history.forEach { array.put(it.toJson()) }
        prefs.edit()
            .putInt(KEY_DURATION, state.durationSeconds)
            .putBoolean(KEY_UPLOAD, state.measureUpload)
            .putString(KEY_ENDPOINT, state.endpoint)
            .putString(KEY_ACCENT, state.accentId)
            .putString(KEY_THEME, state.themeModeName)
            .putString(KEY_GAUGE, state.gaugeStyleName)
            .putString(KEY_HISTORY, array.toString())
            .apply()
        _state.value = state
    }

    fun setDuration(seconds: Int) {
        if (seconds !in DURATIONS) return
        write(_state.value.copy(durationSeconds = seconds))
    }

    fun setMeasureUpload(enabled: Boolean) = write(_state.value.copy(measureUpload = enabled))

    fun setEndpoint(name: String) {
        if (Endpoint.BY_NAME.none { it.name == name }) return
        write(_state.value.copy(endpoint = name))
    }

    fun setAccent(id: String) {
        if (Accents.ALL.none { it.id == id }) return
        write(_state.value.copy(accentId = id))
    }

    fun setThemeMode(mode: ThemeMode) = write(_state.value.copy(themeModeName = mode.name))

    fun setGaugeStyle(style: GaugeStyle) = write(_state.value.copy(gaugeStyleName = style.name))

    fun addRecord(record: TestRecord) {
        write(_state.value.copy(history = (listOf(record) + _state.value.history).take(MAX_HISTORY)))
    }

    fun clearHistory() = write(_state.value.copy(history = emptyList()))

    companion object {
        /** Варианты длительности: меньше десяти секунд результат недостоверен. */
        val DURATIONS = intArrayOf(10, 20, 30, 60)
        const val DEFAULT_DURATION = 20
        const val MAX_HISTORY = 50

        private const val KEY_HISTORY = "history"
        private const val KEY_DURATION = "duration"
        private const val KEY_UPLOAD = "upload"
        private const val KEY_ENDPOINT = "endpoint"
        private const val KEY_ACCENT = "accent"
        private const val KEY_THEME = "theme"
        private const val KEY_GAUGE = "gauge"

        fun of(context: Context): Store = Store(
            context.getSharedPreferences("speedmeter", Context.MODE_PRIVATE),
        )
    }
}

object SpeedStore {
    private var instance: Store? = null

    fun get(context: Context): Store =
        instance ?: synchronized(this) {
            instance ?: Store.of(context.applicationContext).also { instance = it }
        }
}
