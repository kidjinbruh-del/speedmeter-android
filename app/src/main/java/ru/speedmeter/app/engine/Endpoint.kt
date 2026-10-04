package ru.speedmeter.app.engine

/**
 * Точки, откуда качаем и куда шлём.
 *
 * По умолчанию — Cloudflare: у него открытый эндпоинт замера без ключа
 * и без регистрации, отдаёт настоящий гигабит. Остальные варианты нужны
 * для сетей, где Cloudflare недоступен, и для локальной проверки: свой
 * сервер в той же сети показывает реальную скорость линии без интернета.
 *
 * Порядок важен: первый рабочий и становится основным, остальные — запасные.
 */
data class Endpoint(
    val name: String,
    val baseUrl: String,
    val note: String = "",
) {
    /** Ссылка на заданный объём. Проценты поддерживает и сам Cloudflare. */
    fun downloadUrl(bytes: Long): String =
        if (baseUrl.contains("{bytes}")) {
            baseUrl.replace("{bytes}", bytes.toString())
        } else {
            baseUrl.trimEnd('/') + "/__down?bytes=" + bytes
        }

    fun uploadUrl(): String = baseUrl.trimEnd('/') + "/__up"

    /** Маленький запрос для замера задержки: ответ 204, тело пустое. */
    fun latencyUrl(): String = downloadUrl(0)

    val supportsUpload: Boolean get() = !note.contains("без загрузки")

    companion object {
        val CLOUDFLARE = Endpoint(
            name = "Cloudflare",
            baseUrl = "https://speed.cloudflare.com",
            note = "открытый эндпоинт, без ключа",
        )

        /**
         * Локальный сервер: полезно, чтобы отделить скорость линии от
         * скорости интернета. Схема file:// — для проверки с ПК, обычный
         * HTTP-сервер на любой машине в сети подходит без изменений.
         */
        val LOCAL_FILE = Endpoint(
            name = "Файл на диске",
            baseUrl = "file:///android_asset/speedtest.bin",
            note = "проверка без сети, только чтение",
        )

        val BY_NAME = listOf(CLOUDFLARE, LOCAL_FILE)

        fun find(name: String?): Endpoint = BY_NAME.firstOrNull { it.name == name } ?: CLOUDFLARE
    }
}
