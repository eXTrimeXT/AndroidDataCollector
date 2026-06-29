package com.extreme.androiddatacollector

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object DataSender {
    private const val SERVER_URL = "http://10.168.143.7:8800/api/android-data/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun sendData(info: DeviceInfo): Result<String> = withContext(Dispatchers.IO) {
        try {
            val jsonMap = mapDeviceInfoToJson(info)
            val jsonString = gson.toJson(jsonMap)

            Log.d("DataSender", "Отправка JSON: $jsonString")

            val requestBody = jsonString.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(SERVER_URL)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: "Пустой ответ"
                if (response.isSuccessful) {
                    Log.d("DataSender", "Успех: $responseBody")
                    Result.success("Данные успешно отправлены! Код: ${response.code}")
                } else {
                    Log.e("DataSender", "Ошибка сервера: ${response.code}, $responseBody")
                    Result.failure(Exception("Сервер вернул ошибку: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Log.e("DataSender", "Сетевая ошибка: ${e.message}")
            Result.failure(Exception("Не удалось связаться с сервером: ${e.message}"))
        }
    }

    private fun mapDeviceInfoToJson(info: DeviceInfo): Map<String, Any?> {
        return mapOf(
            "serial_number" to info.serialNumber,
            "request_time" to info.requestTime,

            "device" to mapOf(
                "model" to info.model,
                "name" to info.deviceName,
            ),
            "system" to mapOf(
                "android_version" to info.androidVersion,
                "android_api_version" to info.androidApiVersion,
                "build_number" to info.buildNumber,
                "language" to info.systemLanguage,
                "timezone" to info.timezone,
                "uptime" to info.uptime,
            ),
            "hardware" to mapOf(
                "processor" to "${info.cpuCores} cores",
                "processor_architecture" to info.cpuArchitecture,
                "ram_total" to info.totalRam,
                "ram_free" to info.availableRam,
                "storage_total" to info.totalStorage,
                "storage_free" to info.availableStorage,
                "cameras" to info.cameraCount.toString(),
                "screen_resolution" to info.screenResolution
            ),
            "network" to mapOf(
                "connection_type" to info.networkType,
                "wifi_ssid" to info.wifiSsid,
                "wifi_bssid" to info.wifiBssid,
                "mac_address" to (info.macAddress ?: "Hidden"),
                "ip_addresses" to info.ipAddresses.joinToString(", "),
                "wifi_gateway" to info.wifiGateway,
                "bluetooth" to "${info.bluetoothName ?: "Not found"} (${info.bluetoothMac ?: "Hidden"})"
            ),
            "battery" to mapOf(
                "level" to "${info.batteryLevel}%",
                "status" to info.batteryStatus,
                "temperature" to info.batteryTemp
            )
        )
    }
}