package ru.speedmeter.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import ru.speedmeter.app.engine.Fmt
import ru.speedmeter.app.ui.theme.GaugeStyle
import ru.speedmeter.app.ui.theme.TabularFigures
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Датчик скорости.
 *
 * Дуга вписана в квадрат по меньшей стороне блока, поэтому на любом экране
 * остаётся круглой и ничего не перекрывает. Шкала нелинейная по максимуму:
 * дуга показывает долю от текущей верхней границы, а граница растёт вместе
 * с замером, поэтому на 10 и на 700 Мбит/с стрелка одинаково
 * информативна. Линейная шкала в гигабитном канале показывала бы вечное
 * «2%».
 *
 * Границы подписаны: без них дуга — просто красивая картинка, а с ними
 * видно, что именно показывает прибор.
 */
@Composable
fun Gauge(
    valueMbps: Double,
    ceilingMbps: Double,
    caption: String,
    style: GaugeStyle = GaugeStyle.DEFAULT,
    unit: String = "Мбит/с",
    /** Значение держится от прошлого замера: показываем приглушённо. */
    dimmed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val track = scheme.surfaceVariant
    val accent = scheme.primary
    val valueColor = if (dimmed) {
        scheme.onSurface.copy(alpha = 0.55f)
    } else {
        scheme.onSurface
    }
    val dialAccent = if (dimmed) accent.copy(alpha = 0.6f) else accent
    val ratio = if (ceilingMbps <= 0.0) {
        0f
    } else {
        (valueMbps / ceilingMbps).coerceIn(0.0, 1.0).toFloat()
    }
    val animated by animateFloatAsState(
        targetValue = ratio,
        animationSpec = tween(durationMillis = 220),
        label = "gauge",
    )
    val hasValue = valueMbps > 0.0

    BoxWithConstraints(
        modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopStart,
    ) {
        // Геометрия считается один раз и используется и рисованием, и
        // раскладкой подписи: иначе число и дуга расходятся по вертикали.
        val ring = style == GaugeStyle.RING
        val diameter = min(maxWidth - 34.dp, 250.dp)
        val hubY = if (ring) diameter / 2f else diameter / 2f + 12.dp
        val blockHeight = if (ring) diameter + 24.dp else hubY + 132.dp

        Box(
            Modifier
                .fillMaxWidth()
                .height(blockHeight),
            contentAlignment = Alignment.TopStart,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = if (ring) 12.dp.toPx() else 18.dp.toPx()
                val radius = diameter.toPx() / 2f
                val cx = size.width / 2f
                val cy = hubY.toPx()
                val topLeft = Offset(cx - radius, cy - radius)
                val arcSize = Size(radius * 2f, radius * 2f)
                val startAngle = if (ring) -90f else 180f
                val sweepTotal = if (ring) 360f else 180f

                // Свечение под заполненной частью: без него дуга выглядит
                // нарисованной, а не включённой.
                if (animated > 0f && !ring) {
                    drawArc(
                        color = dialAccent.copy(alpha = 0.18f),
                        startAngle = startAngle,
                        sweepAngle = sweepTotal * animated,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke + 14.dp.toPx(), cap = StrokeCap.Round),
                    )
                }

                drawArc(
                    color = track,
                    startAngle = startAngle,
                    sweepAngle = sweepTotal,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )

                if (animated > 0f) {
                    // Градиент по дуге: ближе к стрелке цвет светлее — глаз
                    // считывает направление роста без чтения подписи.
                    val fillBrush = Brush.linearGradient(
                        colors = listOf(dialAccent.copy(alpha = 0.5f), dialAccent),
                        start = Offset(cx - radius, cy),
                        end = Offset(cx + radius, cy - radius * 2f),
                    )
                    drawArc(
                        brush = fillBrush,
                        startAngle = startAngle,
                        sweepAngle = sweepTotal * animated,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }

                if (!ring) {
                    // Деления стоят внутри дуги: снаружи места для них нет,
                    // а внутри они читаются как градуировка прибора.
                    drawTicks(
                        cx = cx,
                        cy = cy,
                        radius = radius,
                        stroke = stroke,
                        startAngle = startAngle,
                        sweepTotal = sweepTotal,
                        majorColor = scheme.onSurfaceVariant,
                        minorColor = scheme.outlineVariant,
                    )
                }

                // Стрелка появляется вместе с первым измерением. До этого
                // она лежала на нуле и читалась как случайная чёрточка.
                if (hasValue && !ring) {
                    val needleOuter = radius - stroke - 4.dp.toPx()
                    val angle = Math.toRadians((startAngle + sweepTotal * animated).toDouble())
                    val tip = Offset(
                        cx + (needleOuter * cos(angle)).toFloat(),
                        cy + (needleOuter * sin(angle)).toFloat(),
                    )
                    drawLine(
                        color = dialAccent,
                        start = Offset(cx, cy),
                        end = tip,
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawCircle(
                        color = dialAccent,
                        radius = 6.dp.toPx(),
                        center = Offset(cx, cy),
                    )
                }
            }

            // Подписи границ: по бокам, на уровне ступицы — там, где
            // заканчивается дуга. У кольца отступ нужен, иначе подпись
            // ложится прямо на обод.
            ScaleLabel(
                text = "0",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = hubY - 8.dp)
                    .padding(start = 6.dp),
            )
            ScaleLabel(
                text = Fmt.whole(ceilingMbps),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = hubY - 8.dp)
                    .padding(end = 6.dp),
            )

            if (ring) {
                // В кольце число живёт в центре: без рамки нужного размера
                // оно наезжало на обод, а рамка центрирует его по ступице
                // при любой высоте текста.
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(y = hubY - diameter / 2f)
                        .fillMaxWidth()
                        .height(diameter),
                    contentAlignment = Alignment.Center,
                ) {
                    ValueBlock(
                        hasValue = hasValue,
                        valueText = formatValue(valueMbps),
                        valueColor = valueColor,
                        unit = unit,
                        unitColor = if (dimmed) scheme.onSurfaceVariant else accent,
                        caption = caption,
                    )
                }
            } else {
                Column(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(y = hubY + 14.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ValueBlock(
                        hasValue = hasValue,
                        valueText = formatValue(valueMbps),
                        valueColor = valueColor,
                        unit = unit,
                        unitColor = if (dimmed) scheme.onSurfaceVariant else accent,
                        caption = caption,
                    )
                }
            }
        }
    }
}

/** Число, единица и подпись фазы — одинаково для обоих видов датчика. */
@Composable
private fun ValueBlock(
    hasValue: Boolean,
    valueText: String,
    valueColor: androidx.compose.ui.graphics.Color,
    unit: String,
    unitColor: androidx.compose.ui.graphics.Color,
    caption: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Пока замера нет, большого числа не показываем вовсе: тире «—» на
        // 58 пунктах перечёркивало ступицу стрелки и читалось как артефакт
        // отрисовки. Место под число всё равно резервируется, иначе
        // содержимое прыгало бы при первом измерении.
        if (hasValue) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFeatureSettings = TabularFigures,
                ),
                color = valueColor,
            )
        } else {
            Spacer(Modifier.height(62.dp))
        }
        Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall,
            color = unitColor,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.78f),
        )
    }
}

/** Деления шкалы: длинные на круглых значениях, короткие между ними. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTicks(
    cx: Float,
    cy: Float,
    radius: Float,
    stroke: Float,
    startAngle: Float,
    sweepTotal: Float,
    majorColor: androidx.compose.ui.graphics.Color,
    minorColor: androidx.compose.ui.graphics.Color,
) {
    val outer = radius - stroke / 2f - 7.dp.toPx()
    val count = 21
    for (i in 0 until count) {
        val fraction = i / (count - 1f)
        val angle = Math.toRadians((startAngle + sweepTotal * fraction).toDouble())
        val major = i % 5 == 0
        val length = if (major) 9.dp.toPx() else 4.dp.toPx()
        val end = Offset(
            cx + (outer * cos(angle)).toFloat(),
            cy + (outer * sin(angle)).toFloat(),
        )
        val start = Offset(
            end.x - (length * cos(angle)).toFloat(),
            end.y - (length * sin(angle)).toFloat(),
        )
        drawLine(
            color = if (major) majorColor else minorColor,
            start = start,
            end = end,
            strokeWidth = if (major) 2.5.dp.toPx() else 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** Подпись границы шкалы: мелкая, моноширинная, не спорит с числом. */
@Composable
private fun ScaleLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontFeatureSettings = TabularFigures,
            letterSpacing = 0.5.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        modifier = modifier.padding(horizontal = 2.dp),
    )
}

/**
 * Число для датчика. Разделитель и число знаков — через [Fmt]: в русском
 * интерфейсе дробная часть через запятую.
 */
fun formatValue(mbps: Double): String =
    if (mbps <= 0.0) "—" else Fmt.speed(mbps)

/** Граница шкалы: округляем вверх с запасом, чтобы стрелка не упиралась. */
fun gaugeCeiling(observed: Double): Double = ru.speedmeter.app.engine.Stats.gaugeCeiling(observed)

/**
 * Миллисекунды для показа: под десять — с дробной частью, выше — целые.
 * «3,7 мс» и «204 мс» читаются по-разному, а «684,8 мс» — просто шум.
 * Единица измерения дописывается здесь: [Fmt] о числах знает, а о
 * единицах решает экран.
 */
fun formatMs(ms: Double): String {
    val value = Fmt.millis(ms)
    return if (value == "—") value else "$value мс"
}

/** Цвет метрики: загрузка — акцент, отдача — вторичный, задержка — третичный. */
@Composable
fun metricColor(kind: MetricKind) = when (kind) {
    MetricKind.DOWNLOAD -> MaterialTheme.colorScheme.primary
    MetricKind.UPLOAD -> MaterialTheme.colorScheme.secondary
    MetricKind.PING -> MaterialTheme.colorScheme.tertiary
}

enum class MetricKind { DOWNLOAD, UPLOAD, PING }