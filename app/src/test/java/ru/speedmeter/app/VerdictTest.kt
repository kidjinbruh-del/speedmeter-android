package ru.speedmeter.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.speedmeter.app.engine.Fmt
import ru.speedmeter.app.ui.formatMs
import ru.speedmeter.app.ui.qualityNote

/**
 * Пометка о достоверности — это обещание пользователю, что цифры можно
 * сравнивать с тарифом. Если обещание выполняется не всегда, тест должен
 * падать раньше, чем это заметят на замере в плохую минуту.
 */
class VerdictTest {

    @Test
    fun `на хорошем канале пометки нет`() {
        assertNull(qualityNote(pingMs = 18.0, jitterMs = 4.0))
        assertNull(qualityNote(pingMs = 90.0, jitterMs = 30.0))
    }

    @Test
    fun `очень большая задержка помечает замер как приблизительный`() {
        val note = qualityNote(pingMs = 1764.0, jitterMs = 100.0)
        assertTrue("должно быть про секунду: $note", note!!.contains("больше секунды"))
    }

    @Test
    fun `большой джиттер просит повторить замер`() {
        val note = qualityNote(pingMs = 100.0, jitterMs = 400.0)
        assertTrue("должно быть про нестабильность: $note", note!!.contains("нестабилен"))
        assertTrue("должно называть джиттер: $note", note.contains("400 мс"))
    }

    @Test
    fun `умеренная задержка объясняется загрузкой канала или расстоянием`() {
        val note = qualityNote(pingMs = 350.0, jitterMs = 20.0)
        assertTrue(note!!.contains("большая"))
        assertTrue(note.contains("350 мс"))
    }

    @Test
    fun `без ответа сервера замер не годится`() {
        val note = qualityNote(pingMs = 0.0, jitterMs = 0.0)
        assertTrue("должно быть про неудачу: $note", note!!.contains("не удалось"))
    }

    @Test
    fun `миллисекунды форматируются по величине и через запятую`() {
        assertEquals("—", formatMs(0.0))
        assertEquals("3,7 мс", formatMs(3.7))
        assertEquals("204 мс", formatMs(204.4))
    }
}

class FmtTest {

    @Test
    fun `дробная часть через запятую`() {
        assertEquals("8,0", Fmt.one(8.0))
        assertEquals("0,5", Fmt.one(0.45))
    }

    @Test
    fun `скорость без дробной части выше десяти`() {
        assertEquals("9,8", Fmt.speed(9.8))
        assertEquals("87", Fmt.speed(87.4))
    }

    @Test
    fun `нулевые миллисекунды без следа`() {
        assertEquals("—", Fmt.millis(0.0))
        assertEquals("—", Fmt.millis(-1.0))
    }
}