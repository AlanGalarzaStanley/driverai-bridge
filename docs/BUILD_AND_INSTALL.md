# Compilar, descargar e instalar

## Compilación en la nube (no hace falta computadora)
Cada push a `main` ejecuta `.github/workflows/android.yml`:
1. JDK 17 + SDK de Android del runner + Gradle 8.11.1 (wrapper).
2. Escaneo de secretos.
3. Pruebas unitarias → Lint → `assembleDebug`.
4. Verifica que el APK exista y pese entre 1 y 150 MB; imprime paquete, permisos, SDK y SHA-256 en `build-info.txt` junto con el commit exacto.
5. Publica el artefacto `driverai-debug-apk-<N>` (APK + `build-info.txt`) y los reportes.
6. Jobs aparte: contrato con el backend real y pruebas en emulador Android 12.

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
