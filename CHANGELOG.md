# Changelog

## En desarrollo
### Agregado
- Cliente **Open-Meteo** en el teléfono (sin API key) para clima real de BA.
- Sección **Clima (verificado)** en Inicio, separada del reporte del servidor.
- `docs/TRUTH_LAYER.md`: contrato de hechos vs estimaciones.

### Mejorado
- UI más sobria (theme, tarjetas redondeadas).
- Contexto del servidor etiquetado como **no verificado**; no se presenta la demanda inventada como dato.
- Copiloto: sin eventos verificados dice la verdad en lugar de rellenar.
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
- CI: escaneo de secretos, unit tests, lint, APK verificado como artefacto, contrato en vivo, emulador Android 11 (API 30, AOSP).
- Publicación: GitHub Release `v0.1.0-cc64438` y tag móvil `latest-debug`.
- Documentación en `docs/`.
### Corregido
- Reemplazo de regex con `$` literal en la corrección OCR (detectado por test).
- CI y emulador estabilizados en API 30 AOSP.
### Mejorado
- Accesibilidad: escaneos espaciados a 900 ms cuando no hay oferta en pantalla.
- OCR: la tarjeta flotante se oculta antes de capturar.
