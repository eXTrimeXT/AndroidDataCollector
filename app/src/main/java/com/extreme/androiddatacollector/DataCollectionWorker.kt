package com.extreme.androiddatacollector

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.ui.text.font.FontVariation
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DataCollectionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @SuppressLint("HardwareIds")
    private fun getDeviceSerialNumber(): String {
        val androidId = Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID)
            return try {
                // Пытаемся получить серийный номер
                val serial = Build.getSerial()
                Log.d("DataCollector", "Получен серийный номер: $serial")
                serial
            } catch (e: SecurityException) {
                Log.w("DataCollector", "Нет прав на серийный номер, используем Android ID")
                "androidId: $androidId"
            } catch (e: Exception) {
                Log.w("DataCollector", "Ошибка получения серийного номера, используем Android ID: ${e.message}")
                "androidId: $androidId"
            }
        }

    override suspend fun doWork(): Result {
        // Проверяем наличие сети вручную
        if (!isNetworkAvailable()) {
            Log.d("DataCollectionWorker", "Нет сети, повторяем позже")
            return Result.retry()
        }

        val serialNumber = getDeviceSerialNumber()
        Log.i("DataCollectionWorker", "Серийный номер: $serialNumber")

        return try {
            Log.d("DataCollectionWorker", "Сбор и отправка данных...")
            val deviceInfo = DeviceDataCollector.collect(applicationContext)
            val result = DataSender.sendData(deviceInfo)

            result.fold(
                onSuccess = {
                    Log.d("DataCollectionWorker", "Успешно: $it")
                    Result.success()
                },
                onFailure = {
                    Log.e("DataCollectionWorker", "Ошибка: ${it.message}")
                    Result.retry()
                }
            )
        } catch (e: Exception) {
            Log.e("DataCollectionWorker", "Исключение: ${e.message}", e)
            Result.retry()
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}