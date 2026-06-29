package com.extreme.androiddatacollector

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DataCollectionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!isNetworkAvailable()) {
            Log.d("DataCollectionWorker", "Нет сети, повторяем позже")
            return Result.retry()
        }

        // Единый идентификатор — больше никакой дублирующей логики
        val deviceIdentifier = DeviceIdentifier.getDeviceIdentifier(applicationContext)
        Log.i("DataCollectionWorker", "Идентификатор устройства: $deviceIdentifier")

        return try {
            Log.d("DataCollectionWorker", "Сбор и отправка данных...")
            val deviceInfo = DeviceDataCollector.collect(applicationContext)
            val result = DataSender.sendData(applicationContext, deviceInfo)

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