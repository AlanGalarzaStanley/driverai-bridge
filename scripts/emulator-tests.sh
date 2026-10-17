#!/usr/bin/env bash
# Pruebas en emulador: instala, concede permisos de prueba vía adb (equivalente a que el usuario
# los active a mano) y corre las pruebas instrumentadas. Guarda capturas y logcat como evidencia.
set -x
OUT=emulator-artifacts; mkdir -p $OUT
PKG=ar.driverai.bridge
adb install -r app/build/outputs/apk/debug/app-debug.apk || exit 1
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk || exit 1
adb shell getprop ro.build.version.release > $OUT/android-version.txt
adb logcat -c
# Permisos de prueba: superposición y servicio de accesibilidad
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb shell settings put secure enabled_accessibility_services $PKG/$PKG.capture.DriverAccessibilityService
adb shell settings put secure accessibility_enabled 1
sleep 3
adb shell dumpsys accessibility | grep -i driverai > $OUT/a11y-dumpsys.txt || true
adb shell am instrument -w -r $PKG.test/androidx.test.runner.AndroidJUnitRunner | tee $OUT/instrument.txt
RC=0
grep -q "FAILURES!!!" $OUT/instrument.txt && RC=1
grep -q "OK (" $OUT/instrument.txt || RC=1
# Evidencia visual tomada por las pruebas (UiDevice.takeScreenshot)
adb pull /sdcard/Android/data/$PKG/files/ $OUT/screens || true
adb shell am start -n $PKG/.ui.MainActivity
sleep 3
adb exec-out screencap -p > $OUT/inicio.png
adb shell dumpsys meminfo $PKG | head -30 > $OUT/meminfo.txt
adb shell dumpsys batterystats $PKG | head -60 > $OUT/batterystats.txt || true
adb logcat -d | grep -iE "driverai|AndroidRuntime|FATAL" > $OUT/logcat.txt || true
exit $RC
