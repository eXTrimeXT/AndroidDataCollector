package com.extreme.androiddatacollector

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Единый механизм получения и сохранения идентификатора устройства.
 * Использует DevicePolicyManager для записи в Settings.Secure (требует Device Owner).
 */
object DeviceIdentifier {
    private const val TAG = "DeviceIdentifier"
    private const val KEY_SERIAL = "MySerialNumber"
    private const val PREFIX_ANDROID_ID = "androidId:"

    @SuppressLint("HardwareIds")
    fun getDeviceIdentifier(context: Context): String {
        // 1. Пробуем прочитать ранее сохранённый идентификатор
        val saved = getSavedIdentifier(context)
        if (saved != null) {
            Log.d(TAG, "Используем сохранённый идентификатор: $saved")
            return saved
        }

        // 2. Пытаемся получить серийный номер
        val serial = try {
            val s = Build.getSerial()
            Log.d(TAG, "Получен серийный номер: $s")
            s
        } catch (e: SecurityException) {
            Log.w(TAG, "Нет прав на серийный номер: ${e.message}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Ошибка получения серийного номера: ${e.message}")
            null
        }

        // 3. Если сериал получен — сохраняем его
        if (!serial.isNullOrBlank()) {
            saveIdentifier(context, serial)
            return serial
        }

        // 4. Fallback — используем androidId
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )

        val fallback = if (!androidId.isNullOrBlank()) {
            "$PREFIX_ANDROID_ID$androidId"
        } else {
            "unknown_${System.currentTimeMillis()}"
        }

        Log.w(TAG, "Серийный номер недоступен, используем fallback: $fallback")
        saveIdentifier(context, fallback)
        return fallback
    }

    /**
     * Читает сохранённый идентификатор.
     * Сначала через DevicePolicyManager (если Device Owner),
     * потом через Settings.Secure (для чтения не нужны особые права).
     */
    private fun getSavedIdentifier(context: Context): String? {
        // Способ 1: Через DevicePolicyManager (если приложение — Device Owner)
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(context, MyDeviceAdminReceiver::class.java)
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                val value = Settings.Secure.getString(context.contentResolver,KEY_SERIAL)
                if (!value.isNullOrBlank()) {
                    return value
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось прочитать через DevicePolicyManager: ${e.message}")
        }

        // Способ 2: Напрямую из Settings.Secure (на случай если Device Owner снят)
        return try {
            Settings.Secure.getString(context.contentResolver, KEY_SERIAL)?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка чтения из Settings.Secure: ${e.message}")
            null
        }
    }

    /**
     * Сохраняет идентификатор.
     * Сначала через DevicePolicyManager (требует Device Owner),
     * потом через Settings.Secure (как fallback).
     */
    private fun saveIdentifier(context: Context, value: String) {
        var saved = false

        // Способ 1: Через DevicePolicyManager (основной)
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(context, MyDeviceAdminReceiver::class.java)
            if (dpm.isDeviceOwnerApp(context.packageName)) {
//                dpm.setSecureSetting(admin, KEY_SERIAL, value)
                Settings.Secure.putString(context.contentResolver, KEY_SERIAL, value)
                Log.d(TAG, "Идентификатор сохранён через DevicePolicyManager: $value")
                saved = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось сохранить через DevicePolicyManager: ${e.message}")
        }

        // Способ 2: Через Settings.Secure (fallback, если не Device Owner)
        if (!saved) {
            try {
                Settings.Secure.putString(context.contentResolver, KEY_SERIAL, value)
                Log.d(TAG, "Идентификатор сохранён через Settings.Secure: $value")
            } catch (e: SecurityException) {
                Log.e(TAG, "Нет прав WRITE_SECURE_SETTINGS. Идентификатор не сохранён: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка сохранения в Settings.Secure: ${e.message}")
            }
        }
    }

    /**
     * Сбрасывает сохранённый идентификатор (для отладки)
     */
    fun clearSavedIdentifier(context: Context) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(context, MyDeviceAdminReceiver::class.java)
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                dpm.setSecureSetting(admin, KEY_SERIAL, "")
                Log.d(TAG, "Сохранённый идентификатор очищен через DevicePolicyManager")
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось очистить через DevicePolicyManager: ${e.message}")
        }

        try {
            Settings.Secure.putString(context.contentResolver, KEY_SERIAL, "")
            Log.d(TAG, "Сохранённый идентификатор очищен через Settings.Secure")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка очистки: ${e.message}")
        }
    }
}