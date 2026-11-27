# Firma del APK

## Situación actual
La CI genera un APK **debug** firmado con el keystore de debug del runner. Ese keystore cambia entre máquinas: Android exige desinstalar antes de instalar otra firma.

## Cómo dejar un keystore fijo
1. Generar: `keytool -genkeypair -v -keystore driverai-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias driverai`
2. Secrets: `DRIVERAI_KEYSTORE_BASE64`, `DRIVERAI_KEYSTORE_PASSWORD`, `DRIVERAI_KEY_ALIAS`, `DRIVERAI_KEY_PASSWORD`
3. En CI decodificar y firmar release.

Hasta tener secrets, usá el APK debug de Actions; si falla la instalación, desinstalá la versión anterior.
