// Шторка настроек и её состояние в Material3 помечены экспериментальными.
// Отключаем один раз на файл, а не на каждый вызов.
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ru.speedmeter.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ru.speedmeter.app.data.Store
import ru.speedmeter.app.engine.Endpoint
import ru.speedmeter.app.ui.theme.Accents
import ru.speedmeter.app.ui.theme.GaugeStyle
import ru.speedmeter.app.ui.theme.Sizes
import ru.speedmeter.app.ui.theme.ThemeMode

/**
 * Настройки: сам замер и оформление.
 *
 * Оформление вынесено в отдельную секцию и показывается прямо в шторке —
 * цвет и вид датчика должны быть видны сразу, иначе выбор делается вслепую.
 */
@Composable
fun SettingsSheetContent(state: MeasureState, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Шторка открывается на всю высоту: на телефоне с кнопками
        // навигации половина экрана прятала строку с сервером под ними.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // Без этого нижний край уходил под панель навигации:
                // шторка на телефоне с кнопками, а не с жестами.
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 8.dp),
        ) {
            Text("Настройки", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(22.dp))

            SectionLabel("Длительность замера")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Store.DURATIONS.forEach { seconds ->
                    CompactChip(
                        text = "$seconds с",
                        selected = state.durationSeconds == seconds,
                        onClick = { state.onDuration(seconds) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Дольше — честнее: на коротком замере быстрый канал " +
                    "чаще завышает результат.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel("Отдача")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Проверять скорость отправки",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "Отдача съедает трафик и время",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.measureUpload, onCheckedChange = state.onMeasureUpload)
            }

            Spacer(Modifier.height(26.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(20.dp))

            SectionLabel("Оформление")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Тема",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))
            SegmentedRow(
                options = ThemeMode.entries.map { it.title },
                selectedIndex = ThemeMode.entries.indexOf(state.themeMode),
                onSelect = { state.onThemeMode(ThemeMode.entries[it]) },
            )
            Spacer(Modifier.height(18.dp))

            Text(
                text = "Акцент",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Цвет загрузки; отдача и задержка подбираются к нему",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Accents.ALL.forEach { accent ->
                    val selected = state.accentId == accent.id
                    // Нажатие на всю ячейку, а не только на кружок: подпись
                    // цвета тоже должна реагировать, иначе тап по ней молча
                    // ничего не делает.
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { state.onAccent(accent.id) }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                    ) {
                        AccentSwatch(color = accent.primary, selected = selected)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = accent.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = "Датчик",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))
            SegmentedRow(
                options = GaugeStyle.entries.map { it.title },
                selectedIndex = GaugeStyle.entries.indexOf(state.gaugeStyle),
                onSelect = { state.onGaugeStyle(GaugeStyle.entries[it]) },
            )

            Spacer(Modifier.height(26.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(20.dp))

            SectionLabel("Сервер замера")
            Spacer(Modifier.height(10.dp))
            Endpoint.BY_NAME.forEach { endpoint ->
                SelectRow(
                    title = endpoint.name,
                    subtitle = endpoint.note,
                    selected = state.endpoint == endpoint.name,
                    onClick = { state.onEndpoint(endpoint.name) },
                    leading = {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    },
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(22.dp))
            Text(
                text = "Приложение хранит только историю замеров: без аккаунтов, " +
                    "без аналитики, без отправки данных.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            // Запас снизу: содержимое шторки длиннее экрана, и последняя
            // строка иначе прижимается к самому краю под панелью.
            Spacer(Modifier.height(56.dp))
        }
    }
}

/**
 * Короткий чип: длительности, у которых нет смысла занимать всю ширину.
 *
 * Раньше наклейка тянулась на всю ширину, и в ряд помещался только первый
 * вариант — остальные уезжали за экран и оставляли после себя пустоту.
 */
@Composable
fun CompactChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .height(44.dp)
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
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}