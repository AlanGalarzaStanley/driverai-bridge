# Compilar, descargar e instalar

## Descarga directa en el teléfono

El repositorio es **privado**. Estos enlaces funcionan en Chrome del teléfono solo si la sesión de GitHub es la de esta cuenta. Sin sesión responden 404. No se publicó el repositorio.

1. Abrí, con la sesión iniciada: <https://github.com/AlanGalarzaStanley/driverai-bridge/releases/download/latest-debug/driverai-debug.apk>
2. Aceptá la descarga. El archivo ya es el APK: no hay que descomprimir un zip.
3. Abrí la notificación de descarga o el archivo en Descargas.
4. Si Android lo pide, permití "Instalar apps desconocidas" solo para Chrome o para Archivos.
5. Instalar → Abrir. Play Protect puede avisar: es un APK de depuración, fuera de Play. "Instalar de todos modos".

Copia fija, que no se reemplaza, del commit `95c4590` (run 22 de Android CI, verificada el 2026-11-21):

<https://github.com/AlanGalarzaStanley/driverai-bridge/releases/download/apk-22/driverai-debug-95c4590.apk>

SHA-256 de ese archivo: `919649f7f645d5b2aa9ac0e16774ce797ebe3ddee21e569e297eed2656911e4f`

Cada push a `main` que deje en verde la compilación, el contrato con el backend y el emulador publica además `apk-<N>` y reemplaza el archivo de `latest-debug`. La build validada de esta tarea se publicó manualmente como `apk-22` y también reemplazó `latest-debug`; el contrato live se omitió y no se publicó en Play. La firma de depuración la genera el runner y **no está fijada**. Si al actualizar dice que el paquete está en conflicto, desinstalá DriverAI y volvé a instalar (se pierden los ajustes).

Google Play Console y Firebase App Distribution no están conectados. No se creó una cuenta ni se autorizó el pago de Play (25 USD). Play además exige un Android App Bundle firmado para producción y una declaración de la AccessibilityService API, sin garantía de aprobación.

## Compilación en la nube (no hace falta computadora)
Cada push a `main` ejecuta `.github/workflows/android.yml`:
1. JDK 17 + SDK de Android del runner + Gradle 8.11.1 (wrapper).
2. Escaneo de secretos.
3. Pruebas unitarias → Lint → `assembleDebug`.
4. Verifica que el APK exista y pese entre 1 y 150 MB; imprime paquete, permisos, SDK y SHA-256 en `build-info.txt` junto con el commit exacto.
5. Publica el artefacto `driverai-debug-apk-<N>` (APK + `build-info.txt`) y los reportes.
6. Jobs aparte: contrato con el backend real y pruebas en emulador **Android 11 (API 30, imagen AOSP)**. El changelog viejo decía Android 12; el workflow y `android-version.txt` del run 8 dicen 11.
7. Si los tres jobs terminan en verde en un push a `main`, el job `release` adjunta el APK a una GitHub Release.

Para correr a mano: GitHub → Actions → Android CI → Run workflow.

## Descargar el APK desde el celular (Chrome Android)
1. Abrí `https://github.com/AlanGalarzaStanley/driverai-bridge/actions` con tu sesión de GitHub iniciada (el repo es privado; los artefactos solo se descargan logueado).
2. Tocá la última ejecución con tilde verde de "Android CI".
3. Bajá hasta **Artifacts** y tocá `driverai-debug-apk-<N>`. Se descarga un `.zip`.
4. Abrí el `.zip` con "Archivos" (Files de Google o Mis Archivos de Samsung) y extraé `driverai-debug-<commit>.apk`.

## Instalar
1. Tocá el `.apk`. Android pedirá permitir "Instalar apps desconocidas" para la app desde la que lo abriste (Mis Archivos o Chrome). Activalo solo para esa app.
2. Instalar → Abrir.
3. Para actualizar, instalá el APK nuevo encima (misma firma de debug del runner). Si dice "conflicto con paquete existente", desinstalá la versión anterior primero (se pierden los ajustes).

## Problemas habituales
| Síntoma | Solución |
|---|---|
| "No se puede abrir el archivo" | Descomprimí el `.zip` primero. |
| "App no instalada" / conflicto | Desinstalá DriverAI anterior (cada runner puede usar otra clave de debug). |
| Play Protect avisa | Es normal para APK de debug fuera de Play. "Instalar de todos modos". |
| La opción de accesibilidad aparece gris ("configuración restringida", Android 13+) | Ajustes > Apps > DriverAI > ⋮ > "Permitir configuración restringida", y volver a intentar. (El S10 con Android 12 no lo tiene.) |
| No aparece la tarjeta | Diagnóstico: revisar accesibilidad conectada o permiso de superposición. Samsung: desactivar optimización de batería para DriverAI. |
| Voz no funciona | Instalar/actualizar "Servicios de voz de Google" y la voz en español. |

## Compilación local (opcional)
```bash
export JAVA_HOME=/ruta/jdk17 ANDROID_HOME=/ruta/sdk
./gradlew testDebugUnitTest lintDebug assembleDebug
```

## Firma
Solo firma de depuración. No hay keystores ni contraseñas en el repo. La firma de producción queda para la fase de publicación con custodia definida (p. ej., Play App Signing + secretos de GitHub).
