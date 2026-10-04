package ru.speedmeter.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.speedmeter.app.data.TestRecord
import ru.speedmeter.app.engine.Fmt
import ru.speedmeter.app.engine.SpeedTest
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

    Button(
        onClick = onClick,
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
    PanelCard {
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
        Spacer(Modifier.height(12.dp))
        Text(
            text = "${result.endpointName} · за ${result.seconds} с",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
) {
    val groups = remember(history) { History.byDay(history) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("История", modifier = Modifier.weight(1f))
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

        groups.forEach { group ->
            Text(
                text = group.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
            )
            group.records.forEachIndexed { index, record ->
                HistoryRow(record = record, previous = group.records.getOrNull(index + 1))
                if (index != group.records.lastIndex) Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HistoryRow(record: TestRecord, previous: TestRecord?) {
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