package ru.speedmeter.app.engine

/**
 * Точка, откуда качаем и куда шлём.
 *
 * Схема адресов — как у Cloudflare: `/__down?bytes=N` на приём и `/__up`
 * на отдачу, либо явный шаблон `{bytes}`. По шаблону можно указать любой
 * свой сервер: `http://192.168.1.10/speed-{bytes}.bin` покажет скорость
 * линии без интернета, что полезно, когда провайдер ни при чём, а Wi-Fi
 * подводит.
 *
 * В списке только Cloudflare: он открыт, без ключа и без регистрации.
 * Запасные адреса держать в приложении незачем — если он недоступен,
 * честнее сказать об этом, чем молча переключиться на неизвестный сервер
 * и показать чужое число.
 */
data class Endpoint(
    val name: String,
    val baseUrl: String,
    val note: String = "",
    val isCustom: Boolean = false,
) {
    /** Ссылка на заданный объём. */
    fun downloadUrl(bytes: Long): String =
        if (baseUrl.contains("{bytes}")) {
            baseUrl.replace("{bytes}", bytes.toString())
        } else {
            baseUrl.trimEnd('/') + "/__down?bytes=" + bytes
        }

    /**
     * Ссылка на отдачу. Для серверов-шаблонов ({bytes}) путь отдачи неизвестен —
     * возвращается пустая строка, и замер отдачи для них пропускается: шлём
     * POST в никуда — это не замер, а генерация трафика.
     */
    fun uploadUrl(): String =
        if (baseUrl.contains("{bytes}")) "" else baseUrl.trimEnd('/') + "/__up"

    /** Маленький запрос для замера задержки: ответ быстрый, тело пустое. */
    fun latencyUrl(): String = downloadUrl(0)

    companion object {
        val CLOUDFLARE = Endpoint(
            name = "Cloudflare",
            baseUrl = "https://speed.cloudflare.com",
            note = "открытый эндпоинт, без ключа",
        )

        /** CopyOnWriteArrayList: читают из IO-потоков замера, пишут из UI — без блокировок и гонок. */
        val BY_NAME: MutableList<Endpoint> = java.util.concurrent.CopyOnWriteArrayList(listOf(CLOUDFLARE))

        fun registerCustom(endpoint: Endpoint): Boolean {
            if (endpoint.name.isBlank()) return false
            // Встроенный сервер перекрывать нельзя: иначе «Cloudflare»
            // в списке будет вести на чужой адрес.
            if (BY_NAME.any { !it.isCustom && it.name == endpoint.name }) return false
            BY_NAME.removeAll { it.isCustom && it.name == endpoint.name }
            BY_NAME.add(endpoint)
            return true
        }

        fun unregisterCustoms() {
            BY_NAME.removeAll { it.isCustom }
        }

        fun find(name: String?): Endpoint = BY_NAME.firstOrNull { it.name == name } ?: CLOUDFLARE
    }
}