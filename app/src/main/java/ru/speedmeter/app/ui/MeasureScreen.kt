package ru.speedmeter.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.speedmeter.app.engine.Endpoint
import ru.speedmeter.app.engine.SpeedTest
import ru.speedmeter.app.ui.theme.Sizes
import ru.speedmeter.app.ui.theme.TabularFigures

/**
 * Главный экран: датчик, кнопка, результат, история.
 *
 * Один экран без вкладок: замер — это одна кнопка и одно число, всё
 * остальное живёт под ними и в шторке. Лишние переходы мешают ждать
 * результата.
 *
 * Порядок блоков — по важности в момент использования: пока идёт замер,
 * важны датчик и кнопка; результат и история важны после.
 */
@Composable
fun MeasureScreen(state: MeasureState, onStart: () -> Unit, onStop: () -> Unit) {
    // Surface, а не голый Column с фоном: Material3 задаёт цвет содержимого
    // только в нём. Без него текст без явного цвета рисуется чёрным — на
    // тёмной теме заголовок был почти не виден.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
    Column(
        Modifier
            .fillMaxSize()
            // Отступы системных полос: иначе заголовок уезжает под
            // статус-бар, а подпись под панель навигации.
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Sizes.gutter),
    ) {
        AppHeader(
            endpointName = Endpoint.find(state.endpoint).name,
            durationSeconds = state.durationSeconds,
            running = state.running,
            onSettings = state.onOpenSettings,
        )

        // Между замерами прибор держит последнее значение: на настоящих
        // панелях так же, и пустота под дугой не выглядит ошибкой.
        val holding = !state.running && state.liveMbps <= 0.0 && state.result != null
        val shownMbps = if (state.liveMbps > 0.0) state.liveMbps else state.result?.downloadMbps ?: 0.0
        val shownCaption = if (state.liveMbps > 0.0) {
            state.caption
        } else {
            state.result?.let { "Прошлый замер · ${formatMs(it.pingMs)}" } ?: state.caption
        }

        Gauge(
            valueMbps = shownMbps,
            ceilingMbps = state.ceilingMbps,
            caption = shownCaption,
            style = state.gaugeStyle,
            dimmed = holding,
        )

        AnimatedVisibility(
            visible = state.running,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            PhaseProgress(progress = state.progress)
        }

        Spacer(Modifier.height(18.dp))

        PrimaryAction(running = state.running, onClick = { if (state.running) onStop() else onStart() })

        Spacer(Modifier.height(Sizes.sectionGap))

        state.result?.let { result ->
            ResultBlock(result = result, previous = state.history.getOrNull(1))
            Spacer(Modifier.height(Sizes.sectionGap))
        }

        if (state.history.isNotEmpty()) {
            HistoryBlock(history = state.history, onClear = state.onClearHistory)
            Spacer(Modifier.height(18.dp))
        } else if (state.result == null) {
            HistoryEmptyHint()
            Spacer(Modifier.height(18.dp))
        }

        AboutLine()
        Spacer(Modifier.height(28.dp))
    }
    }
}

/**
 * Шапка: название, сведения о замере и кнопка настроек.
 *
 * Точка состояния слева от названия — единственное место, где видно, что
 * приложение занято, не читая подпись под датчиком.
 */
@Composable
private fun AppHeader(
    endpointName: String,
    durationSeconds: Int,
    running: Boolean,
    onSettings: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(active = running)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Спидометр",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "$endpointName · $durationSeconds с",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFeatureSettings = TabularFigures,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onSettings) {
            Icon(
                Icons.Filled.Tune,
                contentDescription = "Настройки",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusDot(active: Boolean) {
    val color = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val alpha by animateFloatAsState(if (active) 1f else 0.7f, label = "dot")
    Box(
        Modifier
            .size(8.dp)
            .background(color.copy(alpha = alpha), MaterialTheme.shapes.small),
    )
}

/** Полоса хода замера: без процентов, но с долей в подписи датчика. */
@Composable
private fun PhaseProgress(progress: Float) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

/** Нижняя строка: чем меряем и куда ничего не отправляем. */
@Composable
private fun AboutLine() {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Info,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Замер через открытый эндпоинт Cloudflare · данные никуда не отправляются",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

/** Шторка настроек: замер, оформление, сервер. */
@Composable
fun SettingsSheet(state: MeasureState) {
    SettingsSheetContent(
        state = state,
        onDismiss = state.onCloseSettings,
    )
}