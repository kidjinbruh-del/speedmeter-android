package ru.speedmeter.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.speedmeter.app.data.TestRecord
import ru.speedmeter.app.engine.Fmt
import ru.speedmeter.app.engine.SpeedTest
import ru.speedmeter.app.engine.Stats
import ru.speedmeter.app.ui.theme.Sizes
import ru.speedmeter.app.ui.theme.TabularFigures
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Заголовок секции: капс с разрядкой, как на приборах.
 *
 * Смысл не в украшении: подпись обычным шрифтом среди чисел теряется, а
 * так видно, где кончается одна секция и начинается другая.
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** График истории: линия тренда с мягкой заливкой под ней. */
@Composable
fun HistorySparkline(
    records: List<TestRecord>,
    modifier: Modifier = Modifier,
) {
    if (records.size < 2) return
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        val values = records.map { it.downloadMbps }
        val max = values.max().coerceAtLeast(1.0)
        val min = values.min()
        val range = (max - min).coerceAtLeast(1.0)
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        val inset = 6.dp.toPx()
        val usable = size.height - inset * 2

        // Две направляющие вместо залитого прямоугольника: фон читался
        // как «блок», а не как сетка графика.
        listOf(0f, 0.5f, 1f).forEach { fraction ->
            val y = inset + usable * fraction
            drawLine(
                color = grid.copy(alpha = 0.5f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        fun point(index: Int): Offset {
            val normalized = ((values[index] - min) / range).toFloat()
            return Offset(index * stepX, inset + usable * (1f - normalized))
        }

        val line = Path()
        val area = Path()
        line.moveTo(point(0).x, point(0).y)
        area.moveTo(point(0).x, size.height)
        area.lineTo(point(0).x, point(0).y)
        for (i in 1 until values.size) {
            val p = point(i)
            line.lineTo(p.x, p.y)
            area.lineTo(p.x, p.y)
        }
        area.lineTo(point(values.lastIndex).x, size.height)
        area.close()

        drawPath(
            path = area,
            brush = Brush.verticalGradient(
                colors = listOf(primary.copy(alpha = 0.26f), primary.copy(alpha = 0f)),
                startY = 0f,
                endY = size.height,
            ),
        )
        drawPath(
            path = line,
            color = primary,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        // Точка только у последнего замера: это «где мы сейчас».
        drawCircle(color = primary, radius = 4.dp.toPx(), center = point(values.lastIndex))
    }
}

/** Подпись сети в строке истории. */
private fun networkLabel(record: TestRecord): String {
    val kind = when (record.networkKind) {
        "WIFI" -> "Wi-Fi"
        "CELLULAR_5G" -> "5G"
        "CELLULAR_4G" -> "4G"
        "CELLULAR_3G" -> "3G"
        "CELLULAR_2G" -> "2G"
        "ETHERNET" -> "Ethernet"
        "NONE" -> "Нет сети"
        else -> record.networkKind
    }
    val op = record.networkOperator.takeIf { it.isNotBlank() } ?: return kind
    val ssid = record.networkSsid.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
    return "$kind · $op$ssid"
}

/** Карточка-контейнер: единый радиус и тонкая рамка вместо тени. */
@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(Sizes.cardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp)) { content() }
    }
}

/**
 * Главная кнопка замера.
 *
 * Во время замера она превращается в «Прервать»: действие должно быть
 * видно сразу, иначе на втором экране люди не находят отмену.
 */
@Composable
fun PrimaryAction(running: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.975f else 1f, label = "press")
    val context = LocalContext.current
    val haptics = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }

    Button(
        onClick = {
            // Вибрация не должна ронять нажатие: на части прошивок вызов
            // без разрешения бросает SecurityException, и кнопка «Измерить»
            // вместо замера закрывала приложение.
            runCatching {
                val vibrator = haptics?.takeIf { it.hasVibrator() }
                if (vibrator != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(
                            VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE),
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(20)
                    }
                }
            }
            onClick()
        },
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .scale(scale),
        shape = RoundedCornerShape(18.dp),
        colors = if (running) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) {
        Icon(
            imageVector = if (running) Icons.Filled.Stop else Icons.Filled.Refresh,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (running) "Прервать" else "Измерить",
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * Строка метрики: подпись слева, значение справа, единица рядом.
 *
 * Цифры — табличные, иначе при обновлении раз в полсекунды строка дёргалась
 * бы по ширине и читалась бы как живая, а не как прибор.
 */
@Composable
fun MetricRow(
    kind: MetricKind,
    label: String,
    value: String,
    unit: String,
    deltaPercent: Int? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(metricColor(kind)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        DeltaBadge(deltaPercent)
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFeatureSettings = TabularFigures,
                fontWeight = FontWeight.SemiBold,
            ),
            color = metricColor(kind),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = unit,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Изменение к прошлому замеру.
 *
 * Знак «+» читается как «стало лучше» только вместе с числом, поэтому зелёным
 * и красным красится именно изменение, а не само значение.
 */
@Composable
fun DeltaBadge(deltaPercent: Int?) {
    if (deltaPercent == null) return
    val scheme = MaterialTheme.colorScheme
    val (sign, color) = when {
        deltaPercent > 0 -> "+" to scheme.primary
        deltaPercent < 0 -> "−" to scheme.error
        else -> return
    }
    Text(
        text = "$sign${kotlin.math.abs(deltaPercent)}%",
        style = MaterialTheme.typography.labelMedium,
        color = color,
        // Отступ с обеих сторон: без слева бейдж прилипает к единице
        // измерения и читается как «Мбит/с+97%».
        modifier = Modifier.padding(start = 10.dp, end = 8.dp),
    )
}

/** Пометка о достоверности: цветная полоса слева, текст рядом. */
@Composable
fun QualityNote(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Filled.WarningAmber,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** Блок результата: главные метрики, сетка задержки и пометка. */
@Composable
fun ResultBlock(result: SpeedTest.Result, previous: TestRecord?) {
    // Неудачный замер — это отдельное состояние, а не «0 Мбит/с с −100%».
    // Показывать пустые строки метрик рядом с процентами нельзя: человек
    // читает «−100%» как падение скорости, хотя скорость просто не измерилась.
    if (result.downloadMbps <= 0.0) {
        PanelCard {
            SectionLabel("Результат")
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Замер не удался",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Сервер не отдал данные. Проверьте подключение и повторите: " +
                    "в историю такой замер не записывается.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "${result.endpointName} · ${result.networkSummary.ifBlank { "сеть недоступна" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
        return
    }

    PanelCard {
        val context = LocalContext.current
        SectionLabel("Результат")
        Spacer(Modifier.height(14.dp))
        MetricRow(
            kind = MetricKind.DOWNLOAD,
            label = "Загрузка",
            value = formatValue(result.downloadMbps),
            unit = "Мбит/с",
            deltaPercent = History.deltaPercent(result.downloadMbps, previous?.downloadMbps),
        )
        Spacer(Modifier.height(12.dp))
        MetricRow(
            kind = MetricKind.UPLOAD,
            label = "Отдача",
            value = formatValue(result.uploadMbps),
            unit = "Мбит/с",
            deltaPercent = if (result.uploadMbps > 0) {
                History.deltaPercent(result.uploadMbps, previous?.uploadMbps)
            } else {
                null
            },
        )
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            MiniMetric(
                label = "Задержка",
                value = formatMs(result.pingMs),
                color = metricColor(MetricKind.PING),
                modifier = Modifier.weight(1f),
            )
            MiniMetric(
                label = "Джиттер",
                value = formatMs(result.jitterMs),
                color = metricColor(MetricKind.PING),
                modifier = Modifier.weight(1f),
            )
        }
        qualityNote(result.pingMs, result.jitterMs)?.let { note ->
            Spacer(Modifier.height(14.dp))
            QualityNote(note)
        }
        if (result.networkChanged) {
            Spacer(Modifier.height(10.dp))
            QualityNote(
                "Сеть сменилась во время замера (например, Wi-Fi → мобильная): " +
                    "цифры — смесь двух каналов, сравнивать их с тарифом нельзя",
            )
        }

        if (!result.downloadStats.isEmpty) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = methodLine(result),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFeatureSettings = TabularFigures,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ShareRow(result = result, onShare = { shareResult(context, result) })

        Spacer(Modifier.height(12.dp))
        Text(
            text = "${result.endpointName} · за ${result.seconds} с",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (result.networkSummary.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = result.networkSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
    }
}

/**
 * Как считалось число: сколько окон, какой разброс и сколько было потоков.
 *
 * Без этой строки медиана выглядит как «магия»: человек не может отличить
 * устойчивый замер от одного случайного выстрела. С ней видно и число
 * измерений, и коридор, в котором канал держался.
 */
private fun methodLine(result: SpeedTest.Result): String {
    val stats = result.downloadStats
    val core = "медиана из ${stats.count} окон · ${Fmt.one(stats.p10)}–${Fmt.one(stats.p90)} Мбит/с"
    return if (result.streams > 0) "$core · ${result.streams} потока" else core
}

/**
 * Текст для мессенджера: не одна цифра, а весь расклад — чтобы получатель
 * видел и коридор, и метод замера.
 */
private fun shareResult(context: Context, result: SpeedTest.Result) {
    val stats = result.downloadStats
    val text = buildString {
        appendLine("Спидометр · ${result.endpointName}")
        appendLine("Загрузка: ${Fmt.speed(result.downloadMbps)} Мбит/с")
        if (!stats.isEmpty) {
            appendLine("Коридор: ${Fmt.one(stats.p10)}–${Fmt.one(stats.p90)} Мбит/с (медиана из ${stats.count} окон)")
        }
        if (result.uploadMbps > 0) {
            appendLine("Отдача: ${Fmt.speed(result.uploadMbps)} Мбит/с")
        }
        appendLine("Задержка: ${formatMs(result.pingMs)} · джиттер ${formatMs(result.jitterMs)}")
        if (result.networkSummary.isNotBlank()) appendLine(result.networkSummary)
        append(SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date()))
    }
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Поделиться замером"))
}

@Composable
private fun ShareRow(result: SpeedTest.Result, onShare: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onShare)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Share,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Поделиться",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MiniMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFeatureSettings = TabularFigures,
                fontWeight = FontWeight.SemiBold,
            ),
            color = color,
        )
    }
}

/**
 * История по дням.
 *
 * Группировка нужна не для красоты: пять замеров подряд сегодня и пять
 * на прошлой неделе — это разные вещи, и без заголовков «вторник» список
 * из пятидесяти строк читать невозможно.
 */
@Composable
fun HistoryBlock(
    history: List<TestRecord>,
    onClear: () -> Unit,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onDeleteRecord: (Long) -> Unit,
) {
    val groups = remember(history) { History.byDay(history) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("История", modifier = Modifier.weight(1f))
            IconButton(
                onClick = onExportJson,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = "Экспорт JSON",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onExportCsv,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Filled.Description,
                    contentDescription = "Экспорт CSV",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Очистить",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        if (history.size >= 3) {
            HistorySummary(history)
            Spacer(Modifier.height(10.dp))
        }
        if (history.size >= 2) {
            HistorySparkline(records = history)
            Spacer(Modifier.height(10.dp))
        }

        groups.forEach { group ->
            Text(
                text = group.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
            )
            group.records.forEachIndexed { index, record ->
                HistoryRow(
                    record = record,
                    previous = group.records.getOrNull(index + 1),
                    onDelete = { onDeleteRecord(record.atMillis) },
                )
                if (index != group.records.lastIndex) Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Сводка по истории: медиана, лучший и число замеров.
 *
 * Медиана, а не среднее: один провал в метро не должен портить картину
 * «как обычно работает мой интернет».
 */
@Composable
private fun HistorySummary(history: List<TestRecord>) {
    val values = history.map { it.downloadMbps }.filter { it > 0.0 }
    if (values.isEmpty()) return
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SummaryCell("Медиана", "${formatValue(Stats.summary(values))} Мбит/с", Modifier.weight(1f))
        SummaryCell("Лучший", "${formatValue(values.max())} Мбит/с", Modifier.weight(1f))
        SummaryCell("Замеров", values.size.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFeatureSettings = TabularFigures,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
private fun HistoryRow(
    record: TestRecord,
    previous: TestRecord?,
    onDelete: () -> Unit = {},
) {
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    PanelCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatter.format(Date(record.atMillis)),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFeatureSettings = TabularFigures,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatValue(record.downloadMbps),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFeatureSettings = TabularFigures,
                        ),
                        color = metricColor(MetricKind.DOWNLOAD),
                    )
                    Text(
                        text = " Мбит/с",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DeltaBadge(History.deltaPercent(record.downloadMbps, previous?.downloadMbps))
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "отдача ${formatValue(record.uploadMbps)} · ${formatMs(record.pingMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (record.networkKind.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = networkLabel(record),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Удалить запись",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

/** Подсказка, когда истории ещё нет: что именно хранится и где. */
@Composable
fun HistoryEmptyHint() {
    PanelCard(Modifier.fillMaxWidth()) {
        Text(
            text = "Здесь появятся прошлые замеры",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Последние 50 замеров хранятся только на этом телефоне: " +
                "ни аккаунта, ни отправки данных.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Строка-чип для настроек: заголовок, примечание, состояние выбора. */
@Composable
fun SelectRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
    leading: @Composable (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(Sizes.chipRadius)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .then(
                if (selected) {
                    Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        shape,
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        if (selected) {
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Ряд переключателей для компактных настроек: тема, датчик. */
@Composable
fun SegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Образец цвета для выбора акцента.
 *
 * Сам по себе ничего не нажимает: нажатие ловит ячейка вместе с подписью —
 * иначе тап по названию цвета молча ничего не делал.
 */
@Composable
fun AccentSwatch(color: Color, selected: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.22f))
            .then(
                if (selected) {
                    Modifier.border(2.dp, color, CircleShape)
                } else {
                    Modifier.border(1.dp, scheme.outline, CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (selected) 22.dp else 18.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

/** Кнопка-иконка очистки истории рядом с заголовком. */
@Composable
fun ClearIconButton(onClick: () -> Unit) {
    Icon(
        Icons.Filled.DeleteSweep,
        contentDescription = "Очистить историю",
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(8.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}