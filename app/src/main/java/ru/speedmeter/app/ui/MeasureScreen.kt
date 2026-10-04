// Шторка настроек и её состояние в Material3 пока помечены экспериментальными.
// Отключаем один раз на файл, а не на каждый вызов.
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ru.speedmeter.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.speedmeter.app.data.Store
import ru.speedmeter.app.data.TestRecord
import ru.speedmeter.app.engine.Endpoint
import ru.speedmeter.app.engine.SpeedTest
import ru.speedmeter.app.ui.theme.Sizes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Главный экран: датчик, кнопка, результат и история.
 *
 * Один экран без вкладок: замер — это одна кнопка и одно число, а всё
 * остальное (история, настройки) живёт под ней и в шторке. Лишние
 * переходы только мешают ждать результата.
 */
@Composable
fun MeasureScreen(state: MeasureState, onStart: () -> Unit, onStop: () -> Unit) {
    val busy = state.running
    val result = state.result

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Отступы системных полос: иначе заголовок уезжает под статус-бар
            // и подпись под панель навигации.
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Sizes.gutter),
    ) {
        TopBar(
            endpointName = Endpoint.find(state.endpoint).name,
            durationSeconds = state.durationSeconds,
            onSettings = state.onOpenSettings,
        )

        Spacer(Modifier.height(4.dp))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Gauge(
                valueMbps = state.liveMbps,
                ceilingMbps = state.ceilingMbps,
                caption = state.caption,
            )
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(34.dp)
                        .align(Alignment.TopEnd),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AnimatedVisibility(visible = busy) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { if (busy) onStop() else onStart() },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            // Отступ между иконкой и подписью задаём рядом, а не пробелами в
            // строке: с пробелами текст был «  Измерить», и ни поиск по
            // подписи, ни скринридер его не узнавали.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (busy) Icons.Filled.DeleteSweep else Icons.Filled.Refresh,
                    contentDescription = null,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (busy) "Прервать" else "Измерить",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (result != null) {
            ResultBlock(result)
            Spacer(Modifier.height(16.dp))
        }

        if (state.history.isNotEmpty()) {
            HistoryBlock(
                history = state.history,
                onClear = state.onClearHistory,
            )
        }

        Spacer(Modifier.height(20.dp))
        FootNote()
        Spacer(Modifier.height(24.dp))
    }

    if (state.settingsOpen) {
        SettingsSheet(state)
    }
}

@Composable
private fun TopBar(
    endpointName: String,
    durationSeconds: Int,
    onSettings: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Спидометр", style = MaterialTheme.typography.headlineSmall)
            Text(
                "$endpointName · $durationSeconds с",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Настройки")
        }
    }
}

/** Три метрики рядом: под ними главное число, а не заголовок. */
@Composable
private fun ResultBlock(result: SpeedTest.Result) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Sizes.cardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            MetricRow(MetricKind.DOWNLOAD, result.downloadMbps, "Загрузка")
            Spacer(Modifier.height(12.dp))
            MetricRow(MetricKind.UPLOAD, result.uploadMbps, "Отдача")
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                SmallMetric(
                    label = "Задержка",
                    value = formatMs(result.pingMs),
                    color = metricColor(MetricKind.PING),
                    modifier = Modifier.weight(1f),
                )
                SmallMetric(
                    label = "Джиттер",
                    value = formatMs(result.jitterMs),
                    color = metricColor(MetricKind.PING),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Сервер: ${result.endpointName} · за ${result.seconds} с",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Пометка о достоверности: честные цифры плохого канала
            // бесполезны для сравнения с тарифом, и об этом лучше сказать
            // прямо, чем оставить вопрос без ответа.
            qualityNote(result.pingMs, result.jitterMs)?.let { note ->
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Filled.WarningAmber,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricRow(kind: MetricKind, mbps: Double, label: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .background(metricColor(kind), CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            formatValue(mbps),
            style = MaterialTheme.typography.headlineSmall,
            color = metricColor(kind),
            fontWeight = FontWeight.Bold,
        )
        Text(
            "  Мбит/с",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SmallMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
    }
}

@Composable
private fun HistoryBlock(history: List<TestRecord>, onClear: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "История",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButtonWithIcon("Очистить", onClear)
        }
        Spacer(Modifier.height(6.dp))
        history.take(8).forEach { record ->
            HistoryRow(record)
            Spacer(Modifier.height(6.dp))
        }
        if (history.size > 8) {
            Text(
                "…и ещё ${history.size - 8}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryRow(record: TestRecord) {
    val formatter = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    formatter.format(Date(record.atMillis)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "↓ ${formatValue(record.downloadMbps)} · ↑ ${formatValue(record.uploadMbps)} Мбит/с",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                formatMs(record.pingMs),
                style = MaterialTheme.typography.titleSmall,
                color = metricColor(MetricKind.PING),
            )
        }
    }
}

@Composable
private fun TextButtonWithIcon(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.DeleteSweep,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "  $label",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FootNote() {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Filled.Info,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Замер идёт по открытому эндпоинту Cloudflare: приложение ничего " +
                "не отправляет и никуда не пишет, кроме своей истории на " +
                "устройстве. Первые четверть каждого замера не учитываются — " +
                "это разгон соединения.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsSheet(state: MeasureState) {
    ModalBottomSheet(
        onDismissRequest = state.onCloseSettings,
        // Шторка открывается на всю высоту: на телефоне с кнопками
        // навигации половина экрана прятала строку с сервером под ними.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // Без этого нижний край уходил под панель навигации:
                // шторка на телефоне с кнопками, а не с жестами.
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text("Настройки", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))

            Text("Длительность замера", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Store.DURATIONS.forEach { seconds ->
                    Chip(
                        text = "$seconds с",
                        selected = state.durationSeconds == seconds,
                        onClick = { state.onDuration(seconds) },
                        fill = false,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Дольше — точнее и честнее: короткий замер на быстром канале " +
                    "чаще завышает результат.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))
            Text("Отдача", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Проверять скорость отправки",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Отдача съедает трафик и время",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.measureUpload, onCheckedChange = state.onMeasureUpload)
            }

            Spacer(Modifier.height(20.dp))
            Text("Сервер замера", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Endpoint.BY_NAME.forEach { endpoint ->
                Chip(
                    text = endpoint.name,
                    selected = state.endpoint == endpoint.name,
                    onClick = { state.onEndpoint(endpoint.name) },
                    note = endpoint.note,
                )
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(16.dp))
            Text(
                textAlign = TextAlign.Center,
                text = "Приложение хранит только историю замеров. Без аккаунтов, " +
                    "без аналитики, без отправки данных.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

/**
 * Переключатель-наклейка. [fill] выключается для ряда коротких вариантов:
 * с fill = true каждый чип занимал всю ширину, и в строке помещался только
 * первый — остальные уезжали за экран и оставляли после себя пустоту.
 */
@Composable
private fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    note: String? = null,
    fill: Boolean = true,
) {
    Card(
        Modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (note != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
