package ru.speedmeter.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.speedmeter.app.data.TestRecord
import ru.speedmeter.app.ui.History
import ru.speedmeter.app.ui.theme.Accents
import ru.speedmeter.app.ui.theme.GaugeStyle
import ru.speedmeter.app.ui.theme.ThemeMode
import java.util.Calendar

/**
 * Оформление и история — то, что пользователь видит и может поменять.
 * Ошибка здесь не роняет приложение, но делает его «непрофессиональным»
 * в глазах: не тот цвет, сломанная группировка, кривой процент.
 */
class AppearanceTest {

    @Test
    fun `акцент выбирается по имени и не падает на мусоре`() {
        assertEquals(Accents.AMBER, Accents.of("amber"))
        assertEquals(Accents.DEFAULT, Accents.of("нет такого"))
        assertEquals(Accents.DEFAULT, Accents.of(null))
    }

    @Test
    fun `у каждого акцента свои оттенки для тёмной и светлой темы`() {
        Accents.ALL.forEach { accent ->
            assertNotEquals(
                "${accent.title}: тёмный и светлый совпадают",
                accent.dark.first.value,
                accent.light.first.value,
            )
        }
    }

    @Test
    fun `у акцентов разные идентификаторы и названия`() {
        val ids = Accents.ALL.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        val titles = Accents.ALL.map { it.title }
        assertEquals(titles.size, titles.distinct().size)
    }

    @Test
    fun `тема по умолчанию тёмная`() {
        assertEquals(ThemeMode.DARK, ThemeMode.DEFAULT)
        assertEquals(ThemeMode.DARK, ThemeMode.of(null))
        assertEquals(ThemeMode.LIGHT, ThemeMode.of("LIGHT"))
    }

    @Test
    fun `датчик по умолчанию дуга`() {
        assertEquals(GaugeStyle.ARC, GaugeStyle.DEFAULT)
        assertEquals(GaugeStyle.ARC, GaugeStyle.of(null))
        assertEquals(GaugeStyle.RING, GaugeStyle.of("RING"))
    }
}

class HistoryTest {

    private fun record(at: Long, down: Double = 20.0, up: Double = 10.0, ping: Double = 30.0) =
        TestRecord(at, down, up, ping, 5.0, "Cloudflare")

    @Test
    fun `изменение считается в процентах`() {
        assertEquals(10, History.deltaPercent(110.0, 100.0)!!)
        assertEquals(-10, History.deltaPercent(90.0, 100.0)!!)
    }

    @Test
    fun `почти равное значение показывается как ноль`() {
        assertEquals(0, History.deltaPercent(100.4, 100.0)!!)
    }

    @Test
    fun `без предыдущего замера сравнивать не с чем`() {
        assertNull(History.deltaPercent(50.0, null))
        assertNull(History.deltaPercent(50.0, 0.0))
    }

    @Test
    fun `группировка по дням идёт от свежих к старым`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 4, 12, 0, 0)
        }.timeInMillis
        val yesterday = now - 86_400_000L

        val groups = History.byDay(
            records = listOf(
                record(now - 3_600_000),
                record(now - 7_200_000),
                record(yesterday),
            ),
            nowMillis = now,
        )

        assertEquals(2, groups.size)
        assertEquals("Сегодня", groups[0].title)
        assertEquals(2, groups[0].records.size)
        assertEquals("Вчера", groups[1].title)
    }

    @Test
    fun `внутри дня замеры свежие сверху`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 4, 20, 0, 0)
        }.timeInMillis

        val groups = History.byDay(
            records = listOf(record(now - 7_200_000), record(now - 600_000)),
            nowMillis = now,
        )

        val today = groups.first()
        assertTrue(
            "свежий замер должен быть первым",
            today.records.first().atMillis > today.records.last().atMillis,
        )
    }

    @Test
    fun `старые дни подписываются датой`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 20, 12, 0, 0)
        }.timeInMillis
        val old = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 2, 12, 0, 0)
        }.timeInMillis

        val title = History.dayTitle(History.startOfDay(old), now)
        assertEquals("2 октября", title)
    }
}