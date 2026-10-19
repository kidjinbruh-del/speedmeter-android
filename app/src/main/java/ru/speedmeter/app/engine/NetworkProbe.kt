package ru.speedmeter.app.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager

/**
 * Сводка о сети на момент замеру.
 *
 * Без новых разрешений: тип сети и оператор доступны по ACCESS_NETWORK_STATE,
 * которое уже есть. SSID и уровень сигнала Wi-Fi требуют ACCESS_WIFI_STATE
 * (normal-разрешение, не требует запроса у пользователя).
 *
 * Уровень сотового сигнала требует READ_PHONE_STATE (dangerous) — его
 * приложение намеренно не запрашивает, чтобы не ломать позиционирование
 * «без разрешений». Тип сети (4G/5G) и оператор доступны и без него.
 */
data class NetworkInfo(
    val kind: Kind,
    val operator: String = "",
    val ssid: String? = null,
    val signalDbm: Int? = null,
) {
    enum class Kind {
        WIFI, CELLULAR_5G, CELLULAR_4G, CELLULAR_3G, CELLULAR_2G, ETHERNET, NONE, UNKNOWN
    }

    val kindLabel: String
        get() = when (kind) {
            Kind.WIFI -> "Wi-Fi"
            Kind.CELLULAR_5G -> "5G"
            Kind.CELLULAR_4G -> "4G"
            Kind.CELLULAR_3G -> "3G"
            Kind.CELLULAR_2G -> "2G"
            Kind.ETHERNET -> "Ethernet"
            Kind.NONE -> "Нет сети"
            Kind.UNKNOWN -> "Сеть"
        }

    /** Короткая строка для шапки: «Wi-Fi · Домашний» или «4G · МТС». */
    val summary: String
        get() {
            val parts = mutableListOf(kindLabel)
            if (operator.isNotBlank()) parts += operator
            if (!ssid.isNullOrBlank()) parts += ssid
            return parts.joinToString(" · ")
        }
}

object NetworkProbe {
    fun current(context: Context): NetworkInfo {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkInfo(NetworkInfo.Kind.UNKNOWN)
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val kind = when {
            caps == null -> NetworkInfo.Kind.NONE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkInfo.Kind.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> cellularKind(tm)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkInfo.Kind.ETHERNET
            else -> NetworkInfo.Kind.UNKNOWN
        }

        val operator = if (kind == NetworkInfo.Kind.WIFI) "" else readOperator(tm)
        val ssid = if (kind == NetworkInfo.Kind.WIFI) readSsid(wm) else null
        val signal = if (kind == NetworkInfo.Kind.WIFI) readWifiSignal(wm) else null

        return NetworkInfo(kind, operator, ssid, signal)
    }

    private fun cellularKind(tm: TelephonyManager?): NetworkInfo.Kind = runCatching {
        when (tm?.dataNetworkType) {
            TelephonyManager.NETWORK_TYPE_NR -> NetworkInfo.Kind.CELLULAR_5G
            TelephonyManager.NETWORK_TYPE_LTE -> NetworkInfo.Kind.CELLULAR_4G
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSPAP -> NetworkInfo.Kind.CELLULAR_3G
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE -> NetworkInfo.Kind.CELLULAR_2G
            else -> NetworkInfo.Kind.UNKNOWN
        }
    }.getOrDefault(NetworkInfo.Kind.UNKNOWN)

    private fun readOperator(tm: TelephonyManager?): String = runCatching {
        tm?.networkOperatorName?.takeIf { it.isNotBlank() } ?: ""
    }.getOrDefault("")

    @Suppress("DEPRECATION")
    private fun readSsid(wm: WifiManager?): String? = runCatching {
        val raw = wm?.connectionInfo?.ssid
        raw?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    }.getOrNull()

    private fun readWifiSignal(wm: WifiManager?): Int? = runCatching {
        wm?.connectionInfo?.rssi?.takeIf { it > -100 }
    }.getOrNull()
}
