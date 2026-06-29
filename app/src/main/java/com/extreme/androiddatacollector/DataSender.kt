package com.extreme.androiddatacollector

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import androidx.core.content.edit

object DataSender {
    private const val TAG = "DataSender"
    private const val SERVER_URL = "http://10.168.143.7:8800/api/android-data/"
    private const val AUTH_URL = "http://10.168.143.7:8800/api/login"

    // Учётные данные пользователя android
    private const val USERNAME = "android"
    private const val PASSWORD = "android"

    private const val PREFS_NAME = "auth_prefs"
    private const val KEY_TOKEN = "jwt_token"
    private const val KEY_TOKEN_EXPIRES = "token_expires"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // Mutex для предотвращения одновременного обновления токена из разных потоков
    private val tokenMutex = Mutex()

    /**
     * Получает актуальный токен. Если токен истёк или отсутствует — обновляет его.
     */
    private suspend fun getValidToken(context: Context): String? {
        return tokenMutex.withLock {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedToken = prefs.getString(KEY_TOKEN, null)
            val expiresAt = prefs.getLong(KEY_TOKEN_EXPIRES, 0)

            // Проверяем, действителен ли текущий токен (с запасом 1 минута)
            val now = System.currentTimeMillis() / 1000
            if (savedToken != null && expiresAt > now + 60) {
                Log.d(TAG, "Используем сохранённый токен")
                return@withLock savedToken
            }

            // Токен истёк или отсутствует — получаем новый
            Log.d(TAG, "Токен истёк, получаем новый...")
            return@withLock refreshToken(context)
        }
    }

    /**
     * Получает новый JWT-токен через логин/пароль
     */
    private fun refreshToken(context: Context): String? {
        return try {
            val loginBody = """{"login":"$USERNAME","password":"$PASSWORD"}"""
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(AUTH_URL)
                .post(loginBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Ошибка авторизации: ${response.code}")
                    return null
                }

                val responseBody = response.body?.string() ?: return null
                Log.d(TAG, "Ответ авторизации: $responseBody")

                // Парсим ответ — структура зависит от вашего API
                // Обычно это {"access_token": "...", "token_type": "Bearer", ...}
                val json = gson.fromJson(responseBody, Map::class.java)

                // Пробуем разные варианты структуры ответа
                val token = json["access_token"] as? String
                    ?: json["token"] as? String
                    ?: responseBody

                if (token.isBlank()) {
                    Log.e(TAG, "Пустой токен в ответе")
                    return null
                }

                // Декодируем JWT, чтобы получить срок жизни
                val expiresAt = decodeJwtExpiration(token) ?: (System.currentTimeMillis() / 1000 + 3600)

                // Сохраняем токен
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putString(KEY_TOKEN, token)
                    .putLong(KEY_TOKEN_EXPIRES, expiresAt)
                    .apply()

                Log.d(TAG, "Новый токен получен, истекает через ${(expiresAt - System.currentTimeMillis() / 1000) / 60} мин")
                token
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения токена: ${e.message}")
            null
        }
    }

    /**
     * Декодирует JWT и извлекает срок жизни (exp)
     */
    private fun decodeJwtExpiration(token: String): Long? {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return null

            val payload = String(
                android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING),
                Charsets.UTF_8
            )
            val json = gson.fromJson(payload, Map::class.java)
            (json["exp"] as? Double)?.toLong()
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось декодировать JWT: ${e.message}")
            null
        }
    }

    /**
     * Отправляет данные на сервер с автоматическим обновлением токена
     */
    suspend fun sendData(context: Context, info: DeviceInfo): Result<String> = withContext(Dispatchers.IO) {
        try {
            val jsonMap = mapDeviceInfoToJson(info)
            val jsonString = gson.toJson(jsonMap)
            Log.d(TAG, "Отправка JSON: $jsonString")

            // Получаем актуальный токен
            val token = getValidToken(context)
                ?: return@withContext Result.failure(Exception("Не удалось получить токен авторизации"))

            val requestBody = jsonString.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(SERVER_URL)
                .addHeader("Authorization", "Bearer $token")  // Bearer-токен
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: "Пустой ответ"

                if (response.code == 401) {
                    // Токен невалиден — пробуем обновить и повторить запрос
                    Log.w(TAG, "Токен отклонён (401), пробуем обновить...")
                    tokenMutex.withLock {
                        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        prefs.edit { remove(KEY_TOKEN).remove(KEY_TOKEN_EXPIRES) }
                    }
                    // Рекурсивный вызов с новым токеном (но только один раз, чтобы не зациклиться)
                    return@withContext retryWithNewToken(context, info)
                }

                if (response.isSuccessful) {
                    Log.d(TAG, "Успех: $responseBody")
                    Result.success("Данные успешно отправлены! Код: ${response.code}")
                } else {
                    Log.e(TAG, "Ошибка сервера: ${response.code}, $responseBody")
                    Result.failure(Exception("Сервер вернул ошибку: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Сетевая ошибка: ${e.message}")
            Result.failure(Exception("Не удалось связаться с сервером: ${e.message}"))
        }
    }

    /**
     * Повторная отправка с новым токеном (защита от зацикливания)
     */
    private suspend fun retryWithNewToken(context: Context, info: DeviceInfo): Result<String> {
        return try {
            val token = getValidToken(context) ?: return Result.failure(Exception("Не удалось обновить токен"))

            val jsonMap = mapDeviceInfoToJson(info)
            val jsonString = gson.toJson(jsonMap)
            val requestBody = jsonString.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(SERVER_URL)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: "Пустой ответ"
                if (response.isSuccessful) {
                    Log.d(TAG, "Успех после обновления токена: $responseBody")
                    Result.success("Данные успешно отправлены! Код: ${response.code}")
                } else {
                    Result.failure(Exception("Сервер вернул ошибку: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка при повторной отправке: ${e.message}"))
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