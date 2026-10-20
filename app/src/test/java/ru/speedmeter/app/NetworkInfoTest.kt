package ru.speedmeter.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.speedmeter.app.engine.Endpoint
import ru.speedmeter.app.engine.NetworkInfo

class NetworkInfoTest {

    @Test
    fun `kindLabel возвращает читаемое название`() {
        assertEquals("Wi-Fi", NetworkInfo(NetworkInfo.Kind.WIFI).kindLabel)
        assertEquals("5G", NetworkInfo(NetworkInfo.Kind.CELLULAR_5G).kindLabel)
        assertEquals("4G", NetworkInfo(NetworkInfo.Kind.CELLULAR_4G).kindLabel)
        assertEquals("3G", NetworkInfo(NetworkInfo.Kind.CELLULAR_3G).kindLabel)
        assertEquals("2G", NetworkInfo(NetworkInfo.Kind.CELLULAR_2G).kindLabel)
        assertEquals("Ethernet", NetworkInfo(NetworkInfo.Kind.ETHERNET).kindLabel)
        assertEquals("Нет сети", NetworkInfo(NetworkInfo.Kind.NONE).kindLabel)
        assertEquals("Сеть", NetworkInfo(NetworkInfo.Kind.UNKNOWN).kindLabel)
    }

    @Test
    fun `summary включает тип сети и оператора`() {
        val info = NetworkInfo(NetworkInfo.Kind.CELLULAR_4G, operator = "МТС")
        assertEquals("4G · МТС", info.summary)
    }

    @Test
    fun `summary Wi-Fi включает SSID`() {
        val info = NetworkInfo(NetworkInfo.Kind.WIFI, ssid = "Домашний")
        assertEquals("Wi-Fi · Домашний", info.summary)
    }

    @Test
    fun `summary без оператора только тип сети`() {
        val info = NetworkInfo(NetworkInfo.Kind.CELLULAR_5G)
        assertEquals("5G", info.summary)
    }
}

class EndpointCustomTest {

    @Test
    fun `registerCustom добавляет сервер в список`() {
        val before = Endpoint.BY_NAME.size
        Endpoint.registerCustom(Endpoint("Тест", "http://10.0.0.1/speed-{bytes}.bin", isCustom = true))
        assertEquals(before + 1, Endpoint.BY_NAME.size)
        assertEquals("Тест", Endpoint.find("Тест").name)
        Endpoint.BY_NAME.removeAll { it.isCustom }
    }

    @Test
    fun `registerCustom заменяет существующий кастомный`() {
        Endpoint.registerCustom(Endpoint("Тест", "http://10.0.0.1/speed-{bytes}.bin", isCustom = true))
        Endpoint.registerCustom(Endpoint("Тест", "http://10.0.0.2/speed-{bytes}.bin", isCustom = true))
        val custom = Endpoint.BY_NAME.filter { it.isCustom }
        assertEquals(1, custom.size)
        assertEquals("http://10.0.0.2/speed-{bytes}.bin", custom[0].baseUrl)
        Endpoint.BY_NAME.removeAll { it.isCustom }
    }

    @Test
    fun `registerCustom не перекрывает встроенный сервер`() {
        val before = Endpoint.BY_NAME.size
        val added = Endpoint.registerCustom(
            Endpoint("Cloudflare", "http://evil.example", isCustom = true),
        )
        assertFalse(added)
        assertEquals(before, Endpoint.BY_NAME.size)
        assertEquals(Endpoint.CLOUDFLARE.baseUrl, Endpoint.find("Cloudflare").baseUrl)
    }

    @Test
    fun `unregisterCustoms удаляет только свои серверы`() {
        Endpoint.registerCustom(Endpoint("Домашний", "http://10.0.0.1/speed-{bytes}.bin", isCustom = true))
        Endpoint.unregisterCustoms()
        assertTrue(Endpoint.BY_NAME.none { it.isCustom })
        assertTrue(Endpoint.BY_NAME.any { it == Endpoint.CLOUDFLARE })
    }
}
