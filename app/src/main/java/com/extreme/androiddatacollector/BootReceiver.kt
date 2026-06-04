package com.extreme.androiddatacollector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot completed. Scheduling periodic work.")
            scheduleDataCollection(context)
        }
    }

    private fun scheduleDataCollection(context: Context) {
        val constraints = Constraints.Builder()
//            .setRequiredNetworkType(NetworkType.CONNECTED) // только при наличии интернета
            .build()

        val workRequest = PeriodicWorkRequestBuilder<DataCollectionWorker>(
            15, TimeUnit.SECONDS  // минимальный интервал — 15 минут на продакшене
        )
            .setConstraints(constraints)
            .setInitialDelay(15, TimeUnit.SECONDS) // первый запуск через 15 секунд
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "data_collection",
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }
}