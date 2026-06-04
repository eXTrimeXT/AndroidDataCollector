package com.extreme.androiddatacollector

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Минимальный начальный экран (опционально)
 */

class MainActivity : ComponentActivity() {

    private var isServiceRunning by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        isServiceRunning = isServiceRunning,
                        onStartClick = { startService() },
                        onStopClick = { stopService() }
                    )
                }
            }
        }
    }

    private fun startService() {
        val intent = Intent(this, DataCollectionService::class.java).apply {
            action = DataCollectionService.ACTION_START
        }
        startService(intent)
        isServiceRunning = true
    }

    private fun stopService() {
        val intent = Intent(this, DataCollectionService::class.java).apply {
            action = DataCollectionService.ACTION_STOP
        }
        startService(intent)
        isServiceRunning = false
    }
}

@Composable
fun MainScreen(
    isServiceRunning: Boolean,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Android Data Collector",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Сбор данных об устройстве",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Статус сервиса
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isServiceRunning) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isServiceRunning) "✓ Сервис работает" else "✗ Сервис остановлен",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isServiceRunning) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Кнопки управления
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onStartClick,
                modifier = Modifier.weight(1f),
                enabled = !isServiceRunning
            ) {
                Text("Запустить")
            }

            OutlinedButton(
                onClick = onStopClick,
                modifier = Modifier.weight(1f),
                enabled = isServiceRunning
            ) {
                Text("Остановить")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Интервал: 15 минут",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}