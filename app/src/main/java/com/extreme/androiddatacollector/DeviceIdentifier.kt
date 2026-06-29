package com.extreme.androiddatacollector

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import java.io.File

object DeviceIdentifier {
    private const val TAG = "DeviceIdentifier"
    private const val FILE_NAME = "device_serial.txt"
    private const val PREFIX_ANDROID_ID = "androidId:"

    @SuppressLint("HardwareIds")
    fun getDeviceIdentifier(context: Context): String {
        // 1. Защищаем приложение от очистки данных (только для Device Owner)
        protectAppData(context)

        // 2. Пробуем прочитать сохранённый идентификатор
        val saved = readFromFile(context)
        if (saved != null) {
            Log.d(TAG, "Используем сохранённый идентификатор: $saved")
            return saved
        }

        // 3. Получаем серийный номер
        val serial = try {
            Build.getSerial()
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось получить серийный номер: ${e.message}")
            null
        }

        // 4. Если сериал получен — сохраняем и возвращаем
        if (!serial.isNullOrBlank()) {
            saveToFile(context, serial)
            return serial
        }

        // 5. Fallback — androidId
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        val fallback = if (!androidId.isNullOrBlank()) {
            "$PREFIX_ANDROID_ID$androidId"
        } else {
            "unknown_${System.currentTimeMillis()}"
        }

        Log.w(TAG, "Используем fallback: $fallback")
        saveToFile(context, fallback)
        return fallback
    }

    private fun readFromFile(context: Context): String? {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists()) {
                file.readText().takeIf { it.isNotBlank() }
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Ошибка чтения файла: ${e.message}")
            null
        }
    }

    private fun saveToFile(context: Context, value: String) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(value)
            Log.d(TAG, "Идентификатор сохранён в файл: $value")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка записи в файл: ${e.message}")
        }
    }

    /**
     * Запрещает пользователю очищать данные приложения через настройки.
     * Работает только для Device Owner.
     */
    private fun protectAppData(context: Context) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(context, MyDeviceAdminReceiver::class.java)

            if (dpm.isDeviceOwnerApp(context.packageName)) {
                // Запрещаем пользователю управлять приложениями (включая очистку данных)
                dpm.addUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL)
                Log.d(TAG, "Защита от очистки данных активирована")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось активировать защиту: ${e.message}")
        }
    }

    fun clearSavedIdentifier(context: Context) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Файл с идентификатором удалён")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка удаления: ${e.message}")
        }
    }
}