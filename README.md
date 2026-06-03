# Android Data Collector

./gradlew installDebug

adb shell pm grant com.gps.gpsdatacollector android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.gps.gpsdatacollector android.permission.ACCESS_COARSE_LOCATION

adb logcat -s DataCollectionService:D DataSender:D DataCollector:D                   

Принудительный запуск службы через ADB:
adb shell am startservice -a ACTION_START -n com.extreme.androiddatacollector/.DataCollectionService
