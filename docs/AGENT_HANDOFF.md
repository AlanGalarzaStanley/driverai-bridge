# Handoff para continuar DriverAI

**Actualizado:** 2026-11-26. Lee primero este resumen y `../AGENTS.md`; luego confirma la rama, el HEAD y el estado del árbol. Es una instantánea, no reemplaza a Git.

## Última continuación: lectura automática y tarjeta (2026-11-26)

- Rama `manus/offer-reading-card-cleanup`, commit `ac4efb9`, basada en `06fa260`. Está subida a `origin`; `main` no se modificó y no se abrió PR.
- Accesibilidad hace hasta dos reintentos breves, conserva el aislamiento de paquetes y usa solo texto del evento cuando el árbol activo pertenece a otra app. El parser no se relajó. La tarjeta prioriza veredicto y cuatro métricas colorizadas por los umbrales del conductor; valores aproximados quedan ámbar y la vista compacta es el valor predeterminado para ajustes nuevos.
- CI [run 45756555582, verde](https://github.com/AlanGalarzaStanley/driverai-bridge/actions/runs/45756555582): unit tests, lint, APK debug y pruebas instrumentadas en emulador API 30 pasaron. El contrato productivo se omitió; no se publicó Release. [Artefacto APK debug 71](https://github.com/AlanGalarzaStanley/driverai-bridge/actions/runs/45756555582/artifacts/15088112272) (retención de 30 días). La compilación local no se pudo ejecutar porque el sandbox no tiene Android SDK.
- **Pendiente:** probar lectura automática con Uber real en un teléfono. Las capturas que el conductor mencionó no llegaron al entorno; pedir que las vuelva a adjuntar anonimizadas antes de ajustar el parser. La CI del simulador no prueba Uber real.

## Último trabajo: MVP para piloto con flota (2026-11-25)

- Rama `feature/copiloto-radar-latam`, commits `208d2ad` y `7896670` (fix de test). **`main` no se tocó y no hay PR abierto.**
- Entregado:
  - Inicio simplificado: tarjetas Ahora, Turno, Última oferta y Hoy, más primeros pasos (`home_first_steps`) que se pueden ocultar. Cuatro pestañas: Inicio, Radar, Billetera y Más. Evaluar (`home_open_evaluate`) y el copiloto (`home_open_copilot`) son rutas sin pestaña.
  - **Informe IA** (`intel/AiReport.kt`, `ui/AiUpdateScreen.kt`): pedido para una IA externa, respuesta pegada o compartida (intent SEND text/plain), validación local y carga al radar con origen `IA_EXTERNA` y confianza máxima de 0,55 (0,35 sin URL https).
  - Alertas de pico (`intel/PeakAlerts.kt`, canal `radar`, notificación 31 y voz con el turno activo).
  - Mínimos rápidos (`engine/QuickThresholds.kt`, `ui/QuickSetupDialog.kt`).
  - Resumen semanal para flota con CSV (`insights/FleetReport.kt`, Informes).
  - Ajustes con `settings_advanced`. El Radar pliega fuentes, contexto del servidor y carga manual en `radar_details` (`ui/ContextCards.kt`).
- Documentación: [`PILOTO_FLOTA.md`](PILOTO_FLOTA.md), brief del MVP en `PRODUCT_VISION.md` y la sección Informe IA en `RADAR_DEMANDA.md`.
- Validación local: `testDebugUnitTest`, `lintDebug`, `assembleDebug` y `compileDebugAndroidTestKotlin` en verde.
- CI: [run 45694670753, verde](https://github.com/AlanGalarzaStanley/driverai-bridge/actions/runs/45694670753). Pasaron compilación, unit tests, lint, APK y 15 pruebas en el emulador API 30. El contrato se omitió y no hubo Release. [APK debug](https://github.com/AlanGalarzaStanley/driverai-bridge/actions/runs/45694670753/artifacts/15060910822).
  - El intento previo ([run 45692863004](https://github.com/AlanGalarzaStanley/driverai-bridge/actions/runs/45692863004)) falló porque Evaluar ahora viene precargado con la última oferta y el test escribía encima. Se corrigió usando `performTextReplacement`.
- Próximos pasos:
  1. Fase 0 del piloto en el teléfono del usuario: lectura de Uber, alertas e Informe IA con 2 o 3 IAs reales.
  2. Fases 1 y 2 con 5 y luego 15 autos; juntar muestras anonimizadas de otras apps.
  3. Implementar el tablero de aciertos (previsto vs observado) y los umbrales aprendidos del historial.
  4. Decidir con el usuario si se abre un PR a `main`.

## Trabajo anterior: copiloto multi-app y radar de demanda (2026-11-25)

- Rama `feature/copiloto-radar-latam`, creada desde `feature/realtime-fleet-and-brand`. **`main` no se tocó y no hay PR abierto.**
- Entregado:
  - Radar de demanda local: paquete `intel/` y pestaña Radar.
  - Demanda en la zona de destino dentro de la tarjeta.
  - Lectura de Uber, DiDi, Cabify, inDrive y Bolt (todas salvo Uber en beta).
  - Límite de recogida en km y minutos.
  - Informes con exportación CSV.
  - Copiloto que consulta el radar primero.
  - Vehículo movido a Más.
- Modelo, fuentes y propuesta de servidor: [`RADAR_DEMANDA.md`](RADAR_DEMANDA.md).
- Validación local: `testDebugUnitTest`, `lintDebug` y `assembleDebug` en verde, con OpenJDK 17 y Android SDK 35.
- CI: [run 45682532136, verde](https://github.com/AlanGalarzaStanley/driverai-bridge/actions/runs/45682532136). Pasaron compilación, unit tests, lint, APK y emulador API 30. El contrato se omitió y no hubo Release.
  - El primer intento ([run 45680677989](https://github.com/AlanGalarzaStanley/driverai-bridge/actions/runs/45680677989)) falló en el emulador: las tarjetas nuevas de Más dejaban Ajustes y Chequeo fuera de pantalla. Se corrigió reordenando las tarjetas.
- Próximos pasos:
  1. Conseguir muestras anonimizadas de ofertas de DiDi, Cabify, inDrive y Bolt, y ajustar el parser con pruebas.
  2. Probar el radar y la línea de destino en un teléfono real.
  3. Calibrar los pesos de `DemandRadar` con el historial.
  4. Diseñar e implementar `driverai-senales` solo con fuentes licenciadas y sin datos del conductor.
  5. Decidir con el usuario si se abre un PR a `main`.

## Estado de trabajo (anterior)

- Repositorio: `AlanGalarzaStanley/driverai-bridge`; app nativa Android en Kotlin y Jetpack Compose.
- Rama con el estado más reciente: `feature/realtime-fleet-and-brand`. El commit de funcionalidad/documentación `c12f718` está subido a `origin`; contiene el ciclo local de diario que venía de `feature/trip-journal-lifecycle` (`444fe07`), el icono alineado a Cockpit y la propuesta de flotas/demanda. `main` no se modificó y no hay PR abierto. La guía de agentes se añade en esta misma rama.
- CI de Android para `c12f718`: [run 45652876845, verde](https://github.com/AlanGalarzaStanley/driverai-bridge/actions/runs/45652876845). Pasaron unit tests, lint, APK debug y emulador API 30. El contrato contra Supabase quedó omitido intencionalmente; no se publicó Release.
- [Artefacto APK debug](https://github.com/AlanGalarzaStanley/driverai-bridge/actions/runs/45652876845/artifacts/15041348483). El entorno local usado en esa tarea no tenía Android SDK; la validación válida fue la CI.

## Qué existe y qué no

- El icono `app/src/main/res/drawable/ic_launcher.xml` usa los colores `#070B09` y `#62E6A4` del tema Cockpit.
- El diario registra localmente oferta observada → decisión del conductor → completado/cancelado → cobro confirmado. No deriva aceptación ni liquidación de Uber; no duplica cobros.
- `DriverAccessibilityService` observa texto y nodos visibles; no ejecuta gestos. `OfferTextParser` reconoce ofertas nuevas con contexto de solicitud, tarifa, distancia y duración. **No hay clasificador de viaje aceptado ni sincronización de ese estado.** El OCR real solo está verificado en una muestra.
- Historial, billetera, vehículo, odómetro y mantenimiento permanecen local-first en DataStore. El cliente llama las funciones estratégicas existentes de reporte/posición; no hay cuentas multiusuario/tenant, API de flota ni ingestión de observaciones de demanda.
- La estrategia premium de flotas y señal colaborativa está en [`FLEET_AND_REALTIME_STRATEGY.md`](FLEET_AND_REALTIME_STRATEGY.md). Es una propuesta para validar, no una capacidad entregada ni investigación de precios/mercado.

## Próximo paso recomendado

1. Pedir/usar una captura **anonimizada** de la pantalla de Uber Driver justo después de aceptar; ocultar nombre, teléfono, dirección, identificadores y mapa. Sin esa muestra, no inventar patrones de texto ni implementar detección específica.
2. Si se dispone de evidencia, diseñar una detección local conservadora de transición con estados ambiguos/falsos positivos en pruebas. Marcar como “probable” hasta confirmación; nunca tocar la app de viajes.
3. Actualizar `docs/KNOWN_LIMITATIONS.md` y el changelog solo cuando cambie el comportamiento real. Para backend o compartir datos, primero definir consentimiento, autenticación, aislamiento por flota, retención, corrección y eliminación; no enviar datos de producción por adelantado.
4. Validar el problema de flotas con responsables de alquiler antes de implementar sincronización o telemetría. La estrategia existente propone empezar por asignación de vehículos, odómetro y mantenimiento.

## Documentos de referencia

- [`PRODUCT_VISION.md`](PRODUCT_VISION.md): visión, límites y secuencia de producto.
- [`KNOWN_LIMITATIONS.md`](KNOWN_LIMITATIONS.md): funciones verificadas y pendientes.
- [`FLEET_AND_REALTIME_STRATEGY.md`](FLEET_AND_REALTIME_STRATEGY.md): propuesta para flotas y observaciones agregadas.
- [`README.md`](../README.md) y [`CHANGELOG.md`](../CHANGELOG.md): instalación y cambios entregados.

## Pruebas y workflow

Comandos locales: `./gradlew testDebugUnitTest lintDebug assembleDebug`; requieren Android SDK. El workflow manual puede iniciarse en una rama con:

```bash
gh workflow run android.yml --ref <rama> -f run_live_contract=false
```

En `android.yml`, `pull_request` y `push` a `main` sí ejecutan el contrato contra backend real; el Release solo corre en `push` a `main`. Revisa la definición del workflow antes de iniciarlo. Para trabajo documental sin cambios Android, no repitas CI solo por costumbre.
