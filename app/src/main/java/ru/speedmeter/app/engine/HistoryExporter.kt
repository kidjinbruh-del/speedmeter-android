package ru.speedmeter.app.engine

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.speedmeter.app.data.TestRecord
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HistoryExporter {

    suspend fun exportJson(context: Context, records: List<TestRecord>): File =
        withContext(Dispatchers.IO) {
            val array = JSONArray()
            records.forEach { array.put(it.toJson()) }
            val file = File(context.cacheDir, "speedmeter-history.json")
            file.writeText(array.toString(2))
            file
        }

    suspend fun exportCsv(context: Context, records: List<TestRecord>): File =
        withContext(Dispatchers.IO) {
            val sb = StringBuilder()
            sb.appendLine("date,download_mbps,upload_mbps,ping_ms,jitter_ms,endpoint,network_kind,network_operator,network_ssid")
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            records.forEach { r ->
                sb.appendLine(
                    "${fmt.format(Date(r.atMillis))},${Fmt.one(r.downloadMbps)},${Fmt.one(r.uploadMbps)},${Fmt.millis(r.pingMs)},${Fmt.millis(r.jitterMs)},\"${r.endpoint}\",${r.networkKind},\"${r.networkOperator}\",\"${r.networkSsid}\""
                )
            }
            val file = File(context.cacheDir, "speedmeter-history.csv")
            file.writeText(sb.toString())
            file
        }

    fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться историей"))
    }

    fun shareResult(context: Context, result: SpeedTest.Result, networkSummary: String) {
        val text = buildString {
            appendLine("Замер скорости — ${result.endpointName}")
            appendLine("Загрузка: ${Fmt.speed(result.downloadMbps)} Мбит/с")
            appendLine("Отдача: ${Fmt.speed(result.uploadMbps)} Мбит/с")
            appendLine("Задержка: ${Fmt.millis(result.pingMs)} мс")
            appendLine("Джиттер: ${Fmt.millis(result.jitterMs)} мс")
            if (networkSummary.isNotBlank()) appendLine("Сеть: $networkSummary")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться результатом"))
    }
}
