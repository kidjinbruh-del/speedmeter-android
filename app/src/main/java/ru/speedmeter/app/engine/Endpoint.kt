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
) {
    /** Ссылка на заданный объём. */
    fun downloadUrl(bytes: Long): String =
        if (baseUrl.contains("{bytes}")) {
            baseUrl.replace("{bytes}", bytes.toString())
        } else {
            baseUrl.trimEnd('/') + "/__down?bytes=" + bytes
        }

    fun uploadUrl(): String = baseUrl.trimEnd('/') + "/__up"

    /** Маленький запрос для замера задержки: ответ быстрый, тело пустое. */
    fun latencyUrl(): String = downloadUrl(0)

    companion object {
        val CLOUDFLARE = Endpoint(
            name = "Cloudflare",
            baseUrl = "https://speed.cloudflare.com",
            note = "открытый эндпоинт, без ключа",
        )

        val BY_NAME = listOf(CLOUDFLARE)

        fun find(name: String?): Endpoint = BY_NAME.firstOrNull { it.name == name } ?: CLOUDFLARE
    }
}