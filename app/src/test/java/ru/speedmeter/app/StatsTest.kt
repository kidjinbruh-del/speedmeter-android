package ru.speedmeter.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.speedmeter.app.engine.Endpoint
import ru.speedmeter.app.engine.Ramp
import ru.speedmeter.app.engine.Sample
import ru.speedmeter.app.engine.Stats

/**
 * Расчёты проверяем отдельно от сети: если формула скорости или медиана
 * сломаны, приложение показывает правдоподобные неправильные цифры, и
 * это замечают только на реальном замере.
 */
class StatsTest {

    /** Замер с заданной скоростью: байты = Мбит/8 * время. */
    private fun sample(mbps: Double, seconds: Double = 1.0): Sample =
        Sample(
            bytes = (mbps * 1_000_000 / 8 * seconds).toLong(),
            millis = (seconds * 1000).toLong(),
        )

    @Test
    fun `скорость считается в мегабитах в секунду`() {
        // 1 МБ за секунду = 8 Мбит/с
        assertEquals(8.0, sample(8.0).mbps, 0.001)
    }

    @Test
    fun `мегабайты в секунду это мегабиты делённые на восемь`() {
        assertEquals(1.0, Stats.megabytesPerSecond(8.0), 0.001)
        assertEquals(0.0, Stats.megabytesPerSecond(0.0), 0.001)
    }

    @Test
    fun `медиана не поддаётся одному выбросу`() {
        val values = listOf(50.0, 51.0, 49.0, 52.0, 900.0)
        assertEquals(51.0, Stats.median(values), 0.001)
    }

    @Test
    fun `итог не уводит вспышка в сто мегабит`() {
        val values = listOf(50.0, 51.0, 49.0, 52.0, 900.0)
        val summary = Stats.summary(values)
        assertTrue(
            "итог $summary должен остаться в кластере 49–52",
            summary >= 49.0 && summary <= 52.0,
        )
    }

    @Test
    fun `медленный первый замер не тянет итог вниз`() {
        // Первый замер почти всегда медленнее: соединение ещё разгоняется.
        val values = listOf(12.0, 80.0, 82.0, 79.0)
        assertEquals(80.0, Stats.summary(values), 0.001)
    }

    @Test
    fun `медиана двух замеров берёт середину`() {
        assertEquals(10.0, Stats.median(listOf(8.0, 12.0)), 0.001)
    }

    @Test
    fun `один замер идёт в итог как есть`() {
        assertEquals(42.0, Stats.summary(listOf(42.0)), 0.001)
    }

    @Test
    fun `джиттер это средняя разница между соседними замерами`() {
        // 10 -> 14 -> 10: разницы 4 и 4, средняя 4
        assertEquals(4.0, Stats.jitter(listOf(10.0, 14.0, 10.0)), 0.001)
        assertEquals(0.0, Stats.jitter(listOf(10.0)), 0.001)
    }

    @Test
    fun `размах показывает гуляние канала`() {
        assertEquals(20.0, Stats.spread(listOf(30.0, 50.0, 40.0)), 0.001)
        assertEquals(0.0, Stats.spread(listOf(30.0)), 0.001)
    }

    @Test
    fun `заминка в сети не переписывает джиттер`() {
        // Раз заминка — 1200 мс. Без отбрасывания среднее по разницам
        // дало бы сотни миллисекунд и ничего не значило бы.
        val samples = listOf(60.0, 59.0, 1200.0, 61.0, 62.0, 60.0)
        assertEquals(1.5, Stats.jitter(Stats.dropLongest(samples)), 0.001)
    }

    @Test
    fun `убирается ровно одна самая долгая запись`() {
        val samples = listOf(60.0, 900.0, 61.0, 62.0)
        assertEquals(listOf(60.0, 61.0, 62.0), Stats.dropLongest(samples))
        // Долгих два: убирается один, второй остаётся — он тоже настоящее
        // измерение, а не случайность.
        assertEquals(listOf(60.0, 900.0), Stats.dropLongest(listOf(60.0, 900.0, 901.0)))
    }

    @Test
    fun `на двух замерах убирать нечего`() {
        assertEquals(listOf(10.0, 900.0), Stats.dropLongest(listOf(10.0, 900.0)))
    }

    @Test
    fun `одинаковые замеры не ломают отбрасывание`() {
        assertEquals(listOf(50.0, 50.0), Stats.dropLongest(listOf(50.0, 50.0, 50.0)))
    }

    @Test
    fun `процентиль берёт значение из отсортированного списка`() {
        val values = listOf(5.0, 1.0, 9.0, 3.0, 7.0)
        assertEquals(1.0, Stats.percentile(values, 0.0), 0.001)
        assertEquals(9.0, Stats.percentile(values, 1.0), 0.001)
        assertEquals(5.0, Stats.percentile(values, 0.5), 0.001)
    }

    @Test
    fun `медленные замеры отбрасываются а быстрые остаются`() {
        val values = listOf(1.0, 90.0, 92.0, 91.0, 89.0)
        val kept = Stats.trimSlow(values, keep = 0.5)
        assertTrue("медленные должны уйти", kept.none { it < 50.0 })
        assertTrue("быстрые должны остаться", kept.any { it >= 90.0 })
    }

    @Test
    fun `два замера отбрасывать нечего`() {
        assertEquals(listOf(1.0, 2.0), Stats.trimSlow(listOf(1.0, 2.0)))
    }

    @Test
    fun `шкала датчика округляется вверх с запасом`() {
        assertEquals(10.0, Stats.gaugeCeiling(9.0), 0.001)
        assertEquals(100.0, Stats.gaugeCeiling(80.0), 0.001)
        assertEquals(1000.0, Stats.gaugeCeiling(950.0), 0.001)
        assertEquals(1.0, Stats.gaugeCeiling(0.0), 0.001)
    }

    @Test
    fun `числа округляются для показа`() {
        assertEquals(12.4, Stats.tidy(12.399999999999999), 0.0001)
        assertEquals(0.0, Stats.tidy(-5.0), 0.0001)
    }
}

class RampTest {

    private fun fast(mbps: Double): Sample = Sample(
        bytes = (mbps * 1_000_000 / 8).toLong(),
        millis = 1_000,
    )

    private fun slow(mbps: Double): Sample = Sample(
        bytes = (mbps * 1_000_000 / 8).toLong(),
        millis = 6_000,
    )

    @Test
    fun `первый размер — килобайтный`() {
        assertEquals(1_000_000L, Ramp().size)
    }

    @Test
    fun `быстрый замер удваивает размер`() {
        val ramp = Ramp()
        assertEquals(2_000_000L, ramp.next(fast(100.0)))
        assertEquals(4_000_000L, ramp.next(fast(100.0)))
    }

    @Test
    fun `медленный замер оставляет размер прежним`() {
        val ramp = Ramp()
        ramp.next(fast(5.0))
        val before = ramp.size
        assertEquals(before, ramp.next(slow(2.0)))
    }

    @Test
    fun `размер не уходит за потолок`() {
        val ramp = Ramp(maxBytes = 8_000_000)
        repeat(10) { ramp.next(fast(500.0)) }
        assertEquals(8_000_000L, ramp.size)
    }

    @Test
    fun `размер не падает ниже минимума`() {
        val ramp = Ramp(minBytes = 500_000)
        repeat(5) { ramp.next(fast(1.0)) }
        assertTrue(ramp.size >= 500_000)
    }

    @Test
    fun `без замера размер не меняется`() {
        val ramp = Ramp()
        assertEquals(1_000_000L, ramp.next(null))
    }
}

class EndpointTest {

    @Test
    fun `адрес загрузки собирается из базы и размера`() {
        val url = Endpoint.CLOUDFLARE.downloadUrl(5_000_000)
        assertEquals("https://speed.cloudflare.com/__down?bytes=5000000", url)
    }

    @Test
    fun `шаблон в базе подставляется`() {
        val custom = Endpoint("Свой", "https://home.local/speed-{bytes}.bin")
        assertEquals("https://home.local/speed-10.bin", custom.downloadUrl(10))
    }

    @Test
    fun `адрес задержки самый маленький`() {
        assertTrue(Endpoint.CLOUDFLARE.latencyUrl().contains("bytes=0"))
    }

    @Test
    fun `имя сервера ищется среди известных`() {
        assertEquals("Cloudflare", Endpoint.find("Cloudflare").name)
        assertEquals("Cloudflare", Endpoint.find("Нет такого").name)
        assertEquals("Cloudflare", Endpoint.find(null).name)
    }

    @Test
    fun `в списке только рабочие адреса`() {
        // Не-HTTP схему движок не умеет: он приводит соединение к
        // HttpURLConnection. Такие варианты нельзя показывать в настройках.
        assertTrue(Endpoint.BY_NAME.isNotEmpty())
        Endpoint.BY_NAME.forEach { endpoint ->
            assertTrue(
                "не-HTTP адрес: ${endpoint.baseUrl}",
                endpoint.baseUrl.startsWith("https://") || endpoint.baseUrl.startsWith("http://"),
            )
        }
    }

    @Test
    fun `свой сервер по шаблону отдаёт нужный объём`() {
        val lan = Endpoint("Дом", "http://192.168.1.10/speed-{bytes}.bin")
        assertEquals("http://192.168.1.10/speed-2000.bin", lan.downloadUrl(2000))
        // Отдача всегда идёт по контракту /__up: свой сервер должен
        // понимать и его, иначе отдачу в настройках надо выключить.
        assertEquals("http://192.168.1.10/speed-{bytes}.bin/__up", lan.uploadUrl())
    }
}
