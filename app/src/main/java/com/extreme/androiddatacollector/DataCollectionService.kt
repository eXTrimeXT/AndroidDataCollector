package com.extreme.androiddatacollector

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.seconds

class DataCollectionService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isCollecting = false

    companion object {
        const val CHANNEL_ID = "DataCollectionChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"

        // Через сколько минут повторять запрос
        val TIME_DELAY = 10.seconds

        fun startService(context: Context) {
            val intent = Intent(context, DataCollectionService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, DataCollectionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.d("DataCollectionService", "Запуск службы сбора данных")
                startForeground(NOTIFICATION_ID, createNotification())
                startDataCollection()
            }
            ACTION_STOP -> {
                Log.d("DataCollectionService", "Остановка службы сбора данных")
                stopDataCollection()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY // Перезапуск службы, если система её убьёт
    }

    private fun startDataCollection() {
        if (isCollecting) return
        isCollecting = true

        serviceScope.launch {
            while (isCollecting) {
                try {
                    Log.d("DataCollectionService", "Сбор и отправка данных...")
                    val deviceInfo = DeviceDataCollector.collect(this@DataCollectionService)
                    val result = DataSender.sendData(deviceInfo)

                    result.fold(
                        onSuccess = { Log.d("DataCollectionService", "Данные успешно отправлены") },
                        onFailure = { Log.e("DataCollectionService", "Ошибка отправки: ${it.message}") }
                    )
                } catch (e: Exception) {
                    Log.e("DataCollectionService", "Ошибка в цикле сбора: ${e.message}")
                }

                // Ждём
                delay(TIME_DELAY)
            }
        }
    }

    private fun stopDataCollection() {
        isCollecting = false
        serviceScope.coroutineContext.cancelChildren()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Служба сбора данных",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Фоновый сбор и отправка данных о устройстве"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        // 1. Создаем Intent для запуска MainActivity
        val intent = Intent(this, MainActivity::class.java).apply {
            // Флаги гарантируют, что активность откроется корректно из службы
            // и не создаст дубликат, если приложение уже запущено
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        // 2. Создаем PendingIntent
        // FLAG_IMMUTABLE обязателен для совместимости с Android 12 (API 31) и выше
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Добавляем PendingIntent в построитель уведомления
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPS Data Collector")
            .setContentText("Служба активна")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent) // <-- Ключевое добавление: привязка клика к приложению
            .setAutoCancel(false) // Запрещаем смахивание/закрытие при клике, так как служба фоновая
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopDataCollection()
        serviceScope.cancel()
    }
}