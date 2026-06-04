package com.extreme.androiddatacollector

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ============================================================================
// DATA CLASSES & STATES
// ============================================================================

sealed class SendStatus {
    object Idle : SendStatus()
    object Loading : SendStatus()
    data class Success(val message: String) : SendStatus()
    data class Error(val message: String) : SendStatus()
}

data class InfoItem(
    val label: String,
    val value: String,
    val icon: ImageVector? = null
)

// ============================================================================
// MAIN SCREEN
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var deviceInfo by remember { mutableStateOf<DeviceInfo?>(null) }
    var permissionsGranted by remember { mutableStateOf(false) }
    var sendStatus by remember { mutableStateOf<SendStatus>(SendStatus.Idle) }
    var isServiceRunning by remember { mutableStateOf(false) }

    val requiredPermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        permissionsGranted = allGranted

        if (allGranted) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                val info = DeviceDataCollector.collect(context)
                withContext(Dispatchers.Main) {
                    deviceInfo = info
                    // Запускаем службу и обновляем индикатор
                    DataCollectionService.startService(context)
                    isServiceRunning = true
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        // Проверяем, были ли разрешения выданы ранее
        val isGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (isGranted) {
            // Разрешения УЖЕ есть (например, при повторном запуске)
            permissionsGranted = true
            collectData(context) { deviceInfo = it }
            DataCollectionService.startService(context)
            isServiceRunning = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Smartphone,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "GPS Data Collector",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                actions = {
                    // Индикатор состояния службы
                    ServiceStatusIndicator(isRunning = isServiceRunning)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!permissionsGranted) {
                PermissionRequestContent(
                    onRequestPermission = { permissionLauncher.launch(requiredPermissions) }
                )
            } else if (deviceInfo == null) {
                LoadingContent()
            } else {
                deviceInfo?.let { info ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        // Control Cards
                        item {
                            ServiceControlCard(
                                isRunning = isServiceRunning,
                                onStart = {
                                    DataCollectionService.startService(context)
                                    isServiceRunning = true
                                },
                                onStop = {
                                    DataCollectionService.stopService(context)
                                    isServiceRunning = false
                                }
                            )
                        }

                        item {
                            SendButton(
                                status = sendStatus,
                                onClick = {
                                    scope.launch {
                                        sendStatus = SendStatus.Loading

                                        // 1. Собираем СВЕЖИЕ данные в фоновом потоке прямо перед отправкой
                                        val freshInfo = withContext(Dispatchers.IO) {
                                            DeviceDataCollector.collect(context)
                                        }

                                        // 2. Обновляем UI, чтобы пользователь видел актуальные цифры
                                        // (например, изменившийся Uptime или реальный статус батареи)
                                        deviceInfo = freshInfo

                                        // 3. Отправляем именно свежие данные на сервер
                                        val result = DataSender.sendData(freshInfo)

                                        sendStatus = result.fold(
                                            onSuccess = { SendStatus.Success(it) },
                                            onFailure = { SendStatus.Error(it.message ?: "Неизвестная ошибка") }
                                        )
                                    }
                                }
                            )
                        }

                        // Device Header
                        item {
                            (info.deviceName ?: info.model)?.let {
                                DeviceHeaderCard(
                                    deviceName = it,
                                    androidVersion = info.androidVersion,
                                    androidId = info.androidId
                                )
                            }
                        }

                        // System Section
                        item {
                            SectionCard(
                                title = "Система",
                                icon = Icons.Outlined.Settings,
                                items = listOf(
                                    InfoItem("Версия Android", info.androidVersion ?: "Неизвестно", Icons.Outlined.Android),
                                    InfoItem("API Level", info.androidApiVersion ?: "Неизвестно", Icons.Outlined.Code),
                                    InfoItem("Номер сборки", info.buildNumber ?: "Неизвестно", Icons.Outlined.Build),
                                    InfoItem("Язык", info.systemLanguage ?: "Неизвестно", Icons.Outlined.Language),
                                    InfoItem("Часовой пояс", info.timezone ?: "Неизвестно", Icons.Outlined.Schedule),
                                    InfoItem("Время работы", info.uptime ?: "Неизвестно", Icons.Outlined.Timer),
                                    InfoItem("Последнее время работы", info.requestTime, Icons.Outlined.Timelapse),
                                )
                            )
                        }

                        // Hardware Section
                        item {
                            SectionCard(
                                title = "Железо",
                                icon = Icons.Outlined.Memory,
                                items = listOf(
                                    InfoItem("Процессор", "${info.cpuCores} ядер (${info.cpuArchitecture})", Icons.Outlined.Memory),
                                    InfoItem("ОЗУ (Всего)", info.totalRam ?: "Неизвестно", Icons.Outlined.SdStorage),
                                    InfoItem("ОЗУ (Свободно)", info.availableRam ?: "Неизвестно", Icons.Outlined.SdStorage),
                                    InfoItem("Память (Всего)", info.totalStorage ?: "Неизвестно", Icons.Outlined.Storage),
                                    InfoItem("Память (Свободно)", info.availableStorage ?: "Неизвестно", Icons.Outlined.Storage),
                                    InfoItem("Камеры", "${info.cameraCount} шт.", Icons.Outlined.CameraAlt),
                                    InfoItem("Разрешение экрана", info.screenResolution ?: "Неизвестно", Icons.Outlined.Smartphone)
                                )
                            )
                        }

                        // Network Section
                        item {
                            SectionCard(
                                title = "Сеть",
                                icon = Icons.Outlined.Wifi,
                                items = listOf(
                                    InfoItem("Тип подключения", info.networkType ?: "Нет", Icons.Outlined.NetworkCheck),
                                    InfoItem("Wi-Fi SSID", info.wifiSsid ?: "Нет подключения", Icons.Outlined.Wifi),
                                    InfoItem("Wi-Fi BSSID", info.wifiBssid ?: "Недоступно", Icons.Outlined.Router),
                                    InfoItem("MAC (WLAN)", info.macAddress ?: "Неизвестно", Icons.Outlined.DevicesOther),
                                    InfoItem("IP-адреса", info.ipAddresses.joinToString().ifEmpty { "Не определены" }, Icons.Default.Dns),
                                    InfoItem("Wi-Fi Шлюз", info.wifiGateway ?: "Не определен", Icons.Default.Dns),
                                    InfoItem("Bluetooth", "${info.bluetoothName ?: "Нет"} (${info.bluetoothMac ?: "Скрыт системой"})", Icons.Outlined.Bluetooth)
                                )
                            )
                        }

                        // Battery Section
                        item {
                            SectionCard(
                                title = "Батарея",
                                icon = Icons.Outlined.BatteryFull,
                                items = listOf(
                                    InfoItem("Уровень заряда", "${info.batteryLevel}%", Icons.Outlined.BatteryFull),
                                    InfoItem("Статус", info.batteryStatus ?: "Неизвестно", Icons.Outlined.Power),
                                    InfoItem("Температура", info.batteryTemp ?: "Неизвестно", Icons.Outlined.Thermostat)
                                )
                            )
                        }

                        // Spacer at bottom
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// UI COMPONENTS
// ============================================================================

@Composable
private fun ServiceStatusIndicator(isRunning: Boolean) {
    val color by animateColorAsState(
        targetValue = if (isRunning) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
        label = "statusColor"
    )

    Box(
        modifier = Modifier
            .padding(end = 16.dp)
            .size(16.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun PermissionRequestContent(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.Security,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Требуются разрешения",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Для работы приложения необходимо предоставить доступ к геолокации и Bluetooth.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Предоставить разрешения", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            strokeWidth = 6.dp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Сбор данных...",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeviceHeaderCard(
    deviceName: String,
    androidVersion: String?,
    androidId: String?
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp), // Чуть строже, чем 20.dp, для компактности
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                )
                .padding(16.dp) // Увеличили общий отступ для "воздуха", это делает дизайн дороже
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 1. Уменьшили иконку для компактности (было 48.dp)
                Icon(
                    imageVector = Icons.Default.Smartphone,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = Color.White
                )

                Spacer(modifier = Modifier.width(16.dp))

                // 2. Используем spacedBy вместо ручных Spacer для компактности
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 3. Уменьшили размер шрифта заголовка (было headlineSmall)
                    Text(
                        text = deviceName,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )

                    Text(
                        text = androidVersion ?: "Неизвестно",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )


                    // 4. Стилизовали ID под современный полупрозрачный "бейдж" (chip)
                    if (androidId != null) {
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "ID: $androidId",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    items: List<InfoItem>
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Section Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(bottom = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Info Items
            items.forEach { item ->
                InfoRowWithIcon(
                    label = item.label,
                    value = item.value,
                    icon = item.icon
                )
                if (item != items.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun InfoRowWithIcon(label: String, value: String, icon: ImageVector?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2
        )
    }
}

@Composable
private fun ServiceControlCard(
    isRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isRunning)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isRunning) Icons.Default.PlayArrow else Icons.Default.Stop,
                    contentDescription = null,
                    tint = if (isRunning)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isRunning) "Служба\nактивна" else "Служба\nостановлена",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Button(
                onClick = if (isRunning) onStop else onStart,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isRunning) "Остановить" else "Запустить")
            }
        }
    }
}

@Composable
private fun SendButton(status: SendStatus, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = when (status) {
                is SendStatus.Success -> MaterialTheme.colorScheme.primaryContainer
                is SendStatus.Error -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    when (status) {
                        is SendStatus.Success -> Icons.Default.CheckCircle
                        is SendStatus.Error -> Icons.Default.Error
                        else -> Icons.Default.CloudUpload
                    },
                    contentDescription = null,
                    tint = when (status) {
                        is SendStatus.Success -> MaterialTheme.colorScheme.primary
                        is SendStatus.Error -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (status) {
                            is SendStatus.Idle -> "Ручная\nотправка"
                            is SendStatus.Loading -> "Отправка..."
                            is SendStatus.Success -> "Успешно"
                            is SendStatus.Error -> "Ошибка"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val messageText = when (status) {
                        is SendStatus.Success -> status.message
                        is SendStatus.Error -> status.message
                        else -> null
                    }

                    if (messageText != null && status is SendStatus.Error) {
                        Text(
                            text = messageText,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )
                    }
                }
            }

            if (status is SendStatus.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp
                )
            } else {
                Button(
                    onClick = onClick,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Отправить")
                }
            }
        }
    }
}

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

private suspend fun collectData(context: Context, onResult: (DeviceInfo) -> Unit) {
    withContext(Dispatchers.IO) {
        val info = DeviceDataCollector.collect(context)
        withContext(Dispatchers.Main) { onResult(info) }
    }
}


// ============================================================================
// PREVIEWS
// ============================================================================

// Мок-данные для превью
private fun mockDeviceInfo(): DeviceInfo {
    return DeviceInfo(
        deviceName = "Honeywell EDA52",
        model = "Honeywell EDA52",
        androidVersion = "Android 11",
        androidApiVersion = "API 30",
        buildNumber = "218.02.18.0299",
        androidId = "adb816e29cfa6e24",
        cpuCores = 8,
        cpuArchitecture = "arm64-v8a",
        totalRam = "3,6 GB",
        availableRam = "1,9 GB",
        totalStorage = "47,9 GB",
        availableStorage = "45,8 GB",
        cameraCount = 2,
        screenResolution = "720 x 1440",
        networkType = "Wi-Fi",
        wifiSsid = "GPS_Network",
        wifiBssid = "bc:1e:85:c7:8e:52",
        macAddress = "A1:B2:C3:D4:E5:F6",
        ipAddresses = listOf("10.168.135.61", "192.168.1.100"),
        bluetoothName = "EDA52",
        bluetoothMac = "AA:BB:CC:DD:EE:FF",
        batteryLevel = 85,
        batteryStatus = "Заряжается",
        batteryTemp = "32.5 °C",
        uptime = "13d 20:33:31",
        systemLanguage = "ru-RU",
        timezone = "Europe/Moscow",
        wifiGateway = "wifiGateway",
        requestTime = "24.09.2026 18:34:23",
        serialNumber = "2756y7gyy654rd"
    )
}

@Preview(showBackground = true, showSystemUi = true, name = "Основной экран - Светлая тема")
@Composable
fun DeviceInfoScreenPreview_Light() {
    MaterialTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DeviceInfoScreenPreviewContent(deviceInfo = mockDeviceInfo())
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Экран запроса разрешений")
@Composable
fun DeviceInfoScreenPreview_Permissions() {
    MaterialTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PermissionRequestContent(onRequestPermission = {})
        }
    }
}


// Вспомогательная функция для превью (имитация экрана с данными)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceInfoScreenPreviewContent(deviceInfo: DeviceInfo?) {
    Column(modifier = Modifier.fillMaxSize()) {
        // TopAppBar
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Smartphone, contentDescription = null, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("GPS Data Collector", style = MaterialTheme.typography.titleLarge)
                }
            },
            actions = {
                ServiceStatusIndicator(isRunning = true)
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )

        if (deviceInfo == null) {
            LoadingContent()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    ServiceControlCard(
                        isRunning = true,
                        onStart = {},
                        onStop = {}
                    )
                }

                item {
                    SendButton(status = SendStatus.Idle, onClick = {})
                }

                item {
                    (deviceInfo.deviceName ?: deviceInfo.model)?.let {
                        DeviceHeaderCard(
                            deviceName = it,
                            androidVersion = deviceInfo.androidVersion,
                            androidId = deviceInfo.androidId
                        )
                    }
                }

                item {
                    SectionCard(
                        title = "Система",
                        icon = Icons.Outlined.Settings,
                        items = listOf(
                            InfoItem("Версия Android", deviceInfo.androidVersion ?: "Неизвестно", Icons.Outlined.Android),
                            InfoItem("API Level", deviceInfo.androidApiVersion ?: "Неизвестно", Icons.Outlined.Code),
                            InfoItem("Номер сборки", deviceInfo.buildNumber ?: "Неизвестно", Icons.Outlined.Build),
                            InfoItem("Язык", deviceInfo.systemLanguage ?: "Неизвестно", Icons.Outlined.Language),
                            InfoItem("Часовой пояс", deviceInfo.timezone ?: "Неизвестно", Icons.Outlined.Schedule),
                            InfoItem("Время работы", deviceInfo.uptime ?: "Неизвестно", Icons.Outlined.Timer)
                        )
                    )
                }

                item {
                    SectionCard(
                        title = "Железо",
                        icon = Icons.Outlined.Memory,
                        items = listOf(
                            InfoItem("Процессор", "${deviceInfo.cpuCores} ядер (${deviceInfo.cpuArchitecture})", Icons.Outlined.Memory),
                            InfoItem("ОЗУ (Всего)", deviceInfo.totalRam ?: "Неизвестно", Icons.Outlined.SdStorage),
                            InfoItem("ОЗУ (Свободно)", deviceInfo.availableRam ?: "Неизвестно", Icons.Outlined.SdStorage),
                            InfoItem("Память (Всего)", deviceInfo.totalStorage ?: "Неизвестно", Icons.Outlined.Storage),
                            InfoItem("Память (Свободно)", deviceInfo.availableStorage ?: "Неизвестно", Icons.Outlined.Storage),
                            InfoItem("Камеры", "${deviceInfo.cameraCount} шт.", Icons.Outlined.CameraAlt),
                            InfoItem("Разрешение экрана", deviceInfo.screenResolution ?: "Неизвестно", Icons.Outlined.Smartphone)
                        )
                    )
                }

                item {
                    SectionCard(
                        title = "Сеть",
                        icon = Icons.Outlined.Wifi,
                        items = listOf(
                            InfoItem("Тип подключения", deviceInfo.networkType ?: "Нет", Icons.Outlined.NetworkCheck),
                            InfoItem("Wi-Fi SSID", deviceInfo.wifiSsid ?: "Нет подключения", Icons.Outlined.Wifi),
                            InfoItem("Wi-Fi BSSID", deviceInfo.wifiBssid ?: "Недоступно", Icons.Outlined.Router),
                            InfoItem("MAC (WLAN)", deviceInfo.macAddress ?: "Рандомизирован", Icons.Outlined.DevicesOther),
                            InfoItem("IP-адреса", deviceInfo.ipAddresses.joinToString().ifEmpty { "Не определены" }, Icons.Outlined.Checklist),
                            InfoItem("Bluetooth", "${deviceInfo.bluetoothName ?: "Нет"} (${deviceInfo.bluetoothMac ?: "Скрыт"})", Icons.Outlined.Bluetooth)
                        )
                    )
                }

                item {
                    SectionCard(
                        title = "Батарея",
                        icon = Icons.Outlined.BatteryFull,
                        items = listOf(
                            InfoItem("Уровень заряда", "${deviceInfo.batteryLevel}%", Icons.Outlined.BatteryFull),
                            InfoItem("Статус", deviceInfo.batteryStatus ?: "Неизвестно", Icons.Outlined.Power),
                            InfoItem("Температура", deviceInfo.batteryTemp ?: "Неизвестно", Icons.Outlined.Thermostat)
                        )
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}