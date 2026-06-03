package com.extreme.androiddatacollector

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

data class DeviceInfo(
    val deviceName: String?,
    val model: String?,
    val androidVersion: String?,
    val androidApiVersion: String?,
    val buildNumber: String?,
    val androidId: String?,

    // === ДОБАВЛЕНЫ НОВЫЕ ПОЛЯ ===
    val wifiGateway: String?,
    val requestTime: String,
    // ============================

    // Железо
    val cpuCores: Int,
    val cpuArchitecture: String?,
    val totalRam: String?,
    val availableRam: String?,
    val totalStorage: String?,
    val availableStorage: String?,
    val cameraCount: Int,

    // Экран
    val screenResolution: String?,

    // Сеть
    val networkType: String?,
    val wifiSsid: String?,
    val wifiBssid: String?,
    val macAddress: String?,
    val ipAddresses: List<String>,

    // Bluetooth
    val bluetoothName: String?,
    val bluetoothMac: String?,

    // Батарея
    val batteryLevel: Int,
    val batteryStatus: String?,
    val batteryTemp: String?,

    // Система
    val uptime: String?,
    val systemLanguage: String?,
    val timezone: String?
)

object DeviceDataCollector {
    @SuppressLint("HardwareIds", "MissingPermission", "DiscouragedApi", "Deprecation")
    fun collect(context: Context): DeviceInfo {
        Log.d("DataCollector", "Начало расширенного сбора данных")

        // 1. Базовая информация
        val deviceName = runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
        val model = "${Build.MANUFACTURER} ${Build.MODEL}"
        val androidVersion = "Android ${Build.VERSION.RELEASE}"
        val androidApiVersion = "API ${Build.VERSION.SDK_INT}"
        val buildNumber = Build.DISPLAY
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

        // === СБОР НОВЫХ ПОЛЕЙ ===
        // 1. Время запроса (ГГГГ-ММ-ДД ЧЧ:ММ:СС) <--- ДОБАВИТЬ ЭТО
        val requestTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        // 2. Серийный номер
        val serialNumber = runCatching {
            Build.getSerial()
        }.getOrNull()?.takeIf { it != "unknown" }

        // 3. Шлюз Wi-Fi
        val wifiGateway = runCatching {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val dhcpInfo = wifiManager?.dhcpInfo
            val gatewayInt = dhcpInfo?.gateway ?: 0
            if (gatewayInt != 0) {
                // Преобразование Int в IP-адрес
                "${gatewayInt and 0xff}.${gatewayInt shr 8 and 0xff}.${gatewayInt shr 16 and 0xff}.${gatewayInt shr 24 and 0xff}"
            } else {
                null
            }
        }.getOrNull()
        // =======================

        // 4. Процессор
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val cpuArchitecture = Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown"

        // 5. ОЗУ (RAM)
        var totalRam: String? = null
        var availableRam: String? = null
        runCatching {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            totalRam = formatBytes(memInfo.totalMem)
            availableRam = formatBytes(memInfo.availMem)
        }

        // 6. Память (Storage)
        var totalStorage: String? = null
        var availableStorage: String? = null
        runCatching {
            val statFs = StatFs(context.filesDir.absolutePath)
            totalStorage = formatBytes(statFs.totalBytes)
            availableStorage = formatBytes(statFs.availableBytes)
        }

        // 7. Камеры
        val cameraCount = runCatching {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cameraManager.cameraIdList.size
        }.getOrDefault(0)

        // 8. Экран
        val screenResolution = runCatching {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val metrics = windowManager.currentWindowMetrics
                "${metrics.bounds.width()} x ${metrics.bounds.height()}"
            } else {
                val displayMetrics = DisplayMetrics()
                windowManager.defaultDisplay.getMetrics(displayMetrics)
                "${displayMetrics.widthPixels} x ${displayMetrics.heightPixels}"
            }
        }.getOrNull()

        // 9. Сеть (Тип подключения)
        val networkType = runCatching {
            val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connManager.activeNetwork
            val capabilities = connManager.getNetworkCapabilities(network)
            when {
                capabilities == null -> "Нет подключения"
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "Мобильная сеть"
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Неизвестно"
            }
        }.getOrNull()

        // 10. Wi-Fi детали
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiInfo = wifiManager?.connectionInfo
        val wifiSsid = wifiInfo?.ssid?.takeIf { it != "<unknown ssid>" }?.replace("\"", "")
        val wifiBssid = wifiInfo?.bssid?.takeIf { it != "02:00:00:00:00:00" }
        val macAddress = runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .firstOrNull { it.name.equals("wlan0", ignoreCase = true) || it.name.equals("eth0", ignoreCase = true) }
                ?.hardwareAddress?.joinToString(":") { "%02X".format(it) }
        }.getOrNull()
        val ipAddresses = getIpAddresses()

        // 11. Bluetooth
        var btName: String? = null
        var btMac: String? = null
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter != null) {
                btName = adapter.name
                btMac = getMacByInterface("bt-pan") ?: getMacByInterface("bluetooth") ?: adapter.address.takeIf { it != "02:00:00:00:00:00" }
            }
        } catch (e: SecurityException) {
            Log.w("DataCollector", "Нет разрешения BLUETOOTH_CONNECT")
        }

        // 12. Батарея (детали)
        var batteryLevel = -1
        var batteryStatus = "Неизвестно"
        var batteryTemp = "0.0 °C"
        runCatching {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val intent = ContextCompat.registerReceiver(context, null, filter, ContextCompat.RECEIVER_EXPORTED)
            batteryLevel = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            batteryStatus = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Заряжается"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Разряжается"
                BatteryManager.BATTERY_STATUS_FULL -> "Заряжен"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Не заряжается"
                else -> "Неизвестно"
            }
            val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            batteryTemp = "${temp / 10.0f} °C"
        }

        // 13. Системные метрики
        val uptime = formatUptime(SystemClock.elapsedRealtime())
        val systemLanguage = Locale.getDefault().toLanguageTag()
        val timezone = java.util.TimeZone.getDefault().id

        return DeviceInfo(
            deviceName = deviceName,
            model = model,
            androidVersion = androidVersion,
            androidApiVersion = androidApiVersion,
            buildNumber = buildNumber,
            androidId = androidId,
            wifiGateway = wifiGateway,
            cpuCores = cpuCores,
            cpuArchitecture = cpuArchitecture,
            totalRam = totalRam,
            availableRam = availableRam,
            totalStorage = totalStorage,
            availableStorage = availableStorage,
            cameraCount = cameraCount,
            screenResolution = screenResolution,
            networkType = networkType,
            wifiSsid = wifiSsid,
            wifiBssid = wifiBssid,
            macAddress = macAddress,
            ipAddresses = ipAddresses,
            bluetoothName = btName,
            bluetoothMac = btMac,
            batteryLevel = batteryLevel,
            batteryStatus = batteryStatus,
            batteryTemp = batteryTemp,
            uptime = uptime,
            systemLanguage = systemLanguage,
            timezone = timezone,
            requestTime = requestTime
        )
    }

    private fun getMacByInterface(interfaceName: String): String? {
        return try {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .firstOrNull { it.name.equals(interfaceName, ignoreCase = true) }
                ?.hardwareAddress?.joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) { null }
    }

    private fun getIpAddresses(): List<String> {
        return try {
            val ips = mutableListOf<String>()
            for (intf in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        ips.add(addr.hostAddress ?: continue)
                    }
                }
            }
            ips
        } catch (e: Exception) { emptyList() }
    }

    private fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return if (gb >= 1) "%.1f GB".format(gb)
        else if (mb >= 1) "%.1f MB".format(mb)
        else "%.1f KB".format(kb)
    }

    private fun formatUptime(millis: Long): String {
        val sec = millis / 1000
        val days = sec / 86400
        val hours = (sec % 86400) / 3600
        val mins = (sec % 3600) / 60
        val secs = sec % 60
        return if (days > 0) "%dd %02d:%02d:%02d".format(days, hours, mins, secs)
        else "%02d:%02d:%02d".format(hours, mins, secs)
    }
}