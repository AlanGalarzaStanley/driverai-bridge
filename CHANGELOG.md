# Changelog

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
- CI: escaneo de secretos, unit tests, lint, APK verificado como artefacto, contrato en vivo, emulador Android 12.
- Documentación en `docs/`.
### Corregido
- Reemplazo de regex con `$` literal en la corrección OCR (detectado por test).
- CI: `setup-android` fallaba por licencias; se usa el SDK del runner; `pipefail` para no ocultar fallas.
