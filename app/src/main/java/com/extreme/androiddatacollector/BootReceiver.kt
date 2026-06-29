package com.extreme.androiddatacollector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "Получен broadcast: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_USER_PRESENT,
            "com.extreme.androiddatacollector.TEST_BOOT" -> {
                Log.d("BootReceiver", "Запуск периодической задачи сбора данных")
                scheduleDataCollection(context)
            }
        }
    }

    companion object {
        private const val WORK_NAME = "data_collection_work"

        fun scheduleDataCollection(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)  // Только при наличии сети
                .setRequiresBatteryNotLow(false)                // Работает даже при низком заряде
                .build()

            val workRequest = PeriodicWorkRequestBuilder<DataCollectionWorker>(
                15, TimeUnit.MINUTES  // Минимальный интервал для PeriodicWorkRequest
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,  // Не дублировать, если уже есть
                workRequest
            )

            Log.d("BootReceiver", "Задача WorkManager запланирована (интервал: 15 мин)")
        }

        fun cancelDataCollection(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d("BootReceiver", "Задача WorkManager отменена")
        }
    }
}