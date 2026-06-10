package com.extreme.androiddatacollector

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DataCollectionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private fun getDeviceSerialNumber(): String {
        return try {
            Build.getSerial()
        } catch (e: SecurityException) {
            Log.e("DataCollector", "Нет прав (SecurityException): ${e.message}")
            "Unknown"
        } catch (e: Exception) {
            Log.e("DataCollector", "Ошибка получения: ${e.message}")
            "Unknown"
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