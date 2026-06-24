package com.extreme.androiddatacollector

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
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
                        onStopClick = { stopService() },
                        onRemoveDeviceOwner = { removeDeviceOwner() }
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

        val serialNumber = try {
            Build.getSerial()
        } catch (e: SecurityException) {
            Log.e("DataCollector", "Нет прав (SecurityException): ${e.message}")
            "Unknown"
        } catch (e: Exception) {
            Log.e("DataCollector", "Ошибка получения: ${e.message}")
            "Unknown"
        }
        Log.i("DataCollector", "SERIAL NUMBER $serialNumber")
    }

    private fun stopService() {
        val intent = Intent(this, DataCollectionService::class.java).apply {
            action = DataCollectionService.ACTION_STOP
        }
        startService(intent)
        isServiceRunning = false
    }

    private fun removeDeviceOwner() {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (dpm.isDeviceOwnerApp(packageName)) {
            dpm.clearDeviceOwnerApp(packageName)
            Log.d("DataCollector", "Device Owner успешно снят изнутри приложения!")
            Toast.makeText(this, "Device Owner успешно снят изнутри приложения", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Приложение не является Device Owner", Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    isServiceRunning: Boolean,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onRemoveDeviceOwner: () -> Unit
) {
    // Состояние для диалога подтверждения
    var showConfirmDialog by remember { mutableStateOf(false) }

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

        // КРАСНАЯ кнопка снятия Device Owner с иконкой предупреждения
        Button(
            onClick = { showConfirmDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFBD2121),  // Красный
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFFFCDD2),
                disabledContentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Предупреждение",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Снять права владельца",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Интервал: 15 минут",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // Диалог подтверждения снятия Device Owner
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFD32F2F)
                )
            },
            title = {
                Text(text = "Подтвердите действие")
            },
            text = {
                Text(
                    text = "Вы уверены, что хотите снять права Device Owner?\n\n" +
                            "После этого приложение потеряет административные привилегии."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onRemoveDeviceOwner()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F),
                        contentColor = Color.White
                    )
                ) {
                    Text("Снять права")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MaterialTheme {
        MainScreen(
            isServiceRunning = true,
            onStartClick = {},
            onStopClick = {},
            onRemoveDeviceOwner = {}
        )
    }
}