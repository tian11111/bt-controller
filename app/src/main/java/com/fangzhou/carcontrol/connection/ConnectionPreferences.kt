package com.fangzhou.carcontrol.connection

import android.content.Context
import android.content.SharedPreferences
import com.fangzhou.carcontrol.wifi.WifiConfig

class ConnectionPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("fangzhou_connection", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IP = "wifi_ip"
        private const val KEY_PORT = "wifi_port"
        private const val KEY_BAUD = "wifi_baud_rate"
        private const val DEFAULT_BAUD = 9600

        val COMMON_BAUD_RATES = listOf(
            1200, 2400, 4800, 9600, 19200, 38400, 57600,
            115200, 230400, 460800, 921600
        )
    }

    fun saveWifiConfig(config: WifiConfig) {
        prefs.edit()
            .putString(KEY_IP, config.ip)
            .putInt(KEY_PORT, config.port)
            .putInt(KEY_BAUD, config.baudRate)
            .apply()
    }

    fun loadWifiConfig(): WifiConfig {
        return WifiConfig(
            ip = prefs.getString(KEY_IP, "192.168.4.1") ?: "192.168.4.1",
            port = prefs.getInt(KEY_PORT, 9000),
            baudRate = prefs.getInt(KEY_BAUD, DEFAULT_BAUD).coerceAtLeast(1200)
        )
    }
}
