# Changelog

## En desarrollo
### Mejorado
- Chequeo conserva y copia únicamente métricas de oferta y etiquetas permitidas; nombres, direcciones y texto libre se descartan en el dispositivo.
- El registro de diagnóstico incluye veredicto y campos detectados, se borra al desactivar el diagnóstico y no se transmite al backend.

## 0.1.0 — 2026-10-17
### Agregado
- App Android nativa (Kotlin + Compose, minSdk 26, target 35).
- Motor económico determinístico: tarifa mínima, $/km, $/h, costo por km, combustible, margen, recogida por defecto, retorno vacío, tolerancia a incertidumbre; veredictos CONVIENE / NO CONVIENE / REVISAR con motivos.
- Parser de ofertas independiente de la fuente con confianza por dato (es/en, km/mi/m, correcciones OCR).
- Servicio de accesibilidad limitado a Uber Driver, con divulgación destacada y sin acciones automáticas.
- Lectura por captura (MediaProjection) bajo demanda + OCR local ML Kit.
- Tarjeta flotante movible, cerrable, compacta y con auto-ocultamiento; alternativa por notificación.
- Integración con `driverai-reporte` y `driverai-posicion` con timeouts y modo desactualizado.
- Copiloto por reglas con voz es-AR y dictado bajo demanda.
- Ajustes, historial numérico, turno, diagnóstico y simulador de oferta (debug).
- CI: escaneo de secretos, unit tests, lint, APK verificado como artefacto, contrato en vivo, emulador Android 11 (API 30, AOSP). Una nota anterior decía Android 12; el emulador que realmente arranca es API 30.
- Publicación: GitHub Release `v0.1.0-cc64438` (APK fijo del run 8) y tag móvil `latest-debug`. Los pushes siguientes a `main` publican `apk-<N>` cuando CI queda en verde.
- Documentación en `docs/`.
### Corregido
- Reemplazo de regex con `$` literal en la corrección OCR (detectado por test).
- CI: `setup-android` fallaba por licencias; se usa el SDK del runner; `pipefail` para no ocultar fallas.
- CI: el emulador Google APIs API 31 no arrancaba en 15 min en runners de 2 núcleos → AOSP API 30.
- Pruebas instrumentadas: `setUp()` debía ser `Unit`; UiAutomator suspendía los servicios de accesibilidad (se usa `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`).
- UI: etiquetas de la barra inferior se cortaban en pantallas angostas.
### Mejorado
- Accesibilidad: escaneos espaciados a 900 ms cuando no hay oferta en pantalla (ahorro de batería con el mapa animado).
- OCR: la tarjeta flotante se oculta antes de capturar para no tapar la oferta.
