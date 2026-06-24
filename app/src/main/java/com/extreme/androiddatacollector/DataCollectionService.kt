package com.extreme.androiddatacollector

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DataCollectionService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isCollecting = false

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"

        val HOURS_DELAY = 15.minutes

        fun startService(context: Context) {
            val intent = Intent(context, DataCollectionService::class.java).apply {
                action = ACTION_START
            }
            // Используем обычный startService вместо startForegroundService
            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, DataCollectionService::class.java).apply {
                action = ACTION_STOP
            }
            // Используем обычный startService вместо startForegroundService
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Создание канала уведомлений полностью удалено
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.d("DataCollectionService", "Запуск службы (интервал: $HOURS_DELAY)")
                startDataCollection()
            }
            ACTION_STOP -> {
                Log.d("DataCollectionService", "Остановка службы")
                stopDataCollection()
                stopSelf()
            }
        }
        // START_STICKY = Автоперезапуск, если система убьёт процесс
        // START_STICKY заставляет систему перезапускать сервис, и на некоторых версиях
        // Android это может вызывать повторный запуск через startForegroundService()
        return START_NOT_STICKY //
    }

    private fun startDataCollection() {
        if (isCollecting) return
        isCollecting = true

        serviceScope.launch {
            // Первая отправка сразу при старте
            sendOnce()

            // Дальше — по расписанию
            while (isCollecting) {
                delay(HOURS_DELAY)
                sendOnce()
            }
        }
    }

    private suspend fun sendOnce() {
        try {
            Log.d("DataCollectionService", "Сбор и отправка данных...")
            val deviceInfo = DeviceDataCollector.collect(this@DataCollectionService)
            val result = DataSender.sendData(deviceInfo)

            result.fold(
                onSuccess = { Log.d("DataCollectionService", "Успешно: $it") },
                onFailure = { Log.e("DataCollectionService", "Ошибка: ${it.message}") }
            )
        } catch (e: Exception) {
            Log.e("DataCollectionService", "Исключение: ${e.message}", e)
        }
    }

    private fun stopDataCollection() {
        isCollecting = false
        serviceScope.coroutineContext.cancelChildren()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopDataCollection()
        serviceScope.cancel()
    }
}