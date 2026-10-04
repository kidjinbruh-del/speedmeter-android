package ru.speedmeter.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Датчик скорости: дуга от 135° до 405° и бегущая стрелка.
 *
 * Дуга вписана в квадрат по меньшей стороне блока, поэтому на любом
 * экране остаётся круглой и ничего не перекрывает. Шкала нелинейная по
 * максимуму: дуга показывает долю от текущей верхней границы, а граница
 * растёт вместе с замером, поэтому на 10 и на 700 Мбит/с стрелка
 * одинаково информативна. Линейная шкала в гигабитном канале показывала
 * бы вечное «2%».
 */
@Composable
fun Gauge(
    valueMbps: Double,
    ceilingMbps: Double,
    caption: String,
    unit: String = "Мбит/с",
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val track = scheme.surfaceVariant
    val accent = scheme.primary

    val ratio = if (ceilingMbps <= 0.0) 0f else (valueMbps / ceilingMbps).coerceIn(0.0, 1.0).toFloat()
    val animated by animateFloatAsState(targetValue = ratio, label = "gauge")

    Box(
        modifier
            .fillMaxWidth()
            .height(240.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 20.dp.toPx()
            // Запас снизу под подпись: дуга смещена вверх, чтобы стрелка в
            // нижней четверти не упиралась в край блока.
            val bottomMargin = 34.dp.toPx()
            val diameter = min(size.width, size.height - bottomMargin) - stroke - 8.dp.toPx()
            val cx = size.width / 2f
            val cy = (size.height - bottomMargin) / 2f
            val topLeft = Offset(cx - diameter / 2f, cy - diameter / 2f)
            val arcSize = Size(diameter, diameter)
            val startAngle = 135f
            val sweepTotal = 270f

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
                drawArc(
                    color = accent,
                    startAngle = startAngle,
                    sweepAngle = sweepTotal * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }

            // Деления: короткие по кругу, длинные на круглых значениях.
            val ticks = 27
            val outer = diameter / 2f + stroke / 2f + 7.dp.toPx()
            for (i in 0 until ticks) {
                val fraction = i / (ticks - 1f)
                val angleRad = Math.toRadians((startAngle + sweepTotal * fraction).toDouble())
                val length = if (i % 3 == 0) 11.dp.toPx() else 5.dp.toPx()
                val outerPoint = Offset(
                    cx + (outer * cos(angleRad)).toFloat(),
                    cy + (outer * sin(angleRad)).toFloat(),
                )
                val innerPoint = Offset(
                    outerPoint.x - (length * cos(angleRad)).toFloat(),
                    outerPoint.y - (length * sin(angleRad)).toFloat(),
                )
                drawLine(
                    color = if (i % 3 == 0) scheme.onSurfaceVariant else track,
                    start = innerPoint,
                    end = outerPoint,
                    strokeWidth = if (i % 3 == 0) 3.dp.toPx() else 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            val needleAngle = Math.toRadians((startAngle + sweepTotal * animated).toDouble())
            // Стрелка рисуется только во внешнем поясе: раньше она шла от
            // центра и перечёркивала само число.
            val needleOuter = diameter / 2f - stroke
            val needleInner = needleOuter - 52.dp.toPx()
            val pivot = Offset(cx, cy)
            val tip = Offset(
                cx + (needleOuter * cos(needleAngle)).toFloat(),
                cy + (needleOuter * sin(needleAngle)).toFloat(),
            )
            val tail = Offset(
                cx + (needleInner * cos(needleAngle)).toFloat(),
                cy + (needleInner * sin(needleAngle)).toFloat(),
            )
            drawLine(
                color = scheme.onSurface,
                start = tail,
                end = tip,
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = scheme.onSurface,
                radius = 6.dp.toPx(),
                center = Offset(
                    cx + (needleInner * cos(needleAngle)).toFloat(),
                    cy + (needleInner * sin(needleAngle)).toFloat(),
                ),
            )
        }

        Column(
            Modifier
                .align(Alignment.Center)
                .offset(y = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Пока замера нет, большого числа не показываем вовсе. Тире
            // «—» на 52 пунктах перечёркивало ступицу стрелки и читалось
            // как артефакт отрисовки, а не как «измерять ещё нечем».
            if (valueMbps > 0.0) {
                Text(
                    text = formatValue(valueMbps),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Spacer(Modifier.height(56.dp))
            }
            Text(
                text = unit,
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.8f),
            )
        }
    }
}

/**
 * Число для датчика. Разделитель и число знаков — через [Fmt]: в русском
 * интерфейсе дробная часть через запятую.
 */
fun formatValue(mbps: Double): String =
    if (mbps <= 0.0) "—" else ru.speedmeter.app.engine.Fmt.speed(mbps)

/** Граница шкалы: округляем вверх с запасом, чтобы стрелка не упиралась. */
fun gaugeCeiling(observed: Double): Double = ru.speedmeter.app.engine.Stats.gaugeCeiling(observed)

/**
 * Миллисекунды для показа: под десять — с дробной частью, выше — целые.
 * «3,7 мс» и «204 мс» читаются по-разному, а «684,8 мс» — просто шум.
 * Единица измерения дописывается здесь: [Fmt] о числах знает, а о
 * единицах решает экран.
 */
fun formatMs(ms: Double): String {
    val value = ru.speedmeter.app.engine.Fmt.millis(ms)
    return if (value == "—") value else "$value мс"
}

/** Цвет метрики: загрузка — циан, отдача — янтарь, задержка — зелёный. */
@Composable
fun metricColor(kind: MetricKind) = when (kind) {
    MetricKind.DOWNLOAD -> MaterialTheme.colorScheme.primary
    MetricKind.UPLOAD -> MaterialTheme.colorScheme.secondary
    MetricKind.PING -> MaterialTheme.colorScheme.tertiary
}

enum class MetricKind { DOWNLOAD, UPLOAD, PING }
