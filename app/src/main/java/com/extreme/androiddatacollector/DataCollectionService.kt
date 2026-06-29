package com.extreme.androiddatacollector

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class DataCollectionService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"

        fun startService(context: Context) {
            val intent = Intent(context, DataCollectionService::class.java).apply {
                action = ACTION_START
            }
            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, DataCollectionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.d("DataCollectionService", "Запуск задачи через WorkManager")
                BootReceiver.scheduleDataCollection(this)

                // Сразу выполняем одну отправку
                val workRequest = OneTimeWorkRequestBuilder<DataCollectionWorker>().build()
                WorkManager.getInstance(this).enqueue(workRequest)
            }
            ACTION_STOP -> {
                Log.d("DataCollectionService", "Остановка задачи")
                BootReceiver.cancelDataCollection(this)
            }
        }
        stopSelf()  // Сервис сразу завершается, работу делает WorkManager
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}