# Arquitectura

```
 Uber Driver (en pantalla)
        │ texto visible                         captura de 1 cuadro (solo al tocar "Leer oferta")
        ▼                                                   ▼
 DriverAccessibilityService ──────────┐        ScreenCaptureService (MediaProjection) → OcrEngine (ML Kit local)
                                      ▼                                   │
                               OfferPipeline  ◄───────────────────────────┘
                     (deduplicación 90 s, estados, historial, voz)
                                      │
                OfferTextParser ──► OfferEvaluator (reglas determinísticas)
                                      │
                    OverlayManager (tarjeta flotante) / notificación
                                      │
 UI Compose: Inicio · Evaluar · Ajustes · Historial · Diagnóstico
                                      │ inicio/actualización explícita del turno
 StrategicRepository ──► DriverAiApi (OkHttp) ──► Supabase Edge Functions (existentes)
          │ respuesta heredada                    │
          └── ShiftBriefingRepository (snapshot local mínimo) ──► ShiftBriefingCard
```

## Decisiones

| Tema | Decisión | Motivo |
|---|---|---|
| Plataforma | Kotlin + Jetpack Compose, minSdk 26, target 35 | Acceso nativo a AccessibilityService/MediaProjection/overlays; compila en la nube sin dependencias raras. El S10 llega a Android 12 (API 31), dentro del rango. |
| Lectura de ofertas | Híbrida: 1) accesibilidad (automática, menos invasiva, sin imágenes); 2) OCR bajo demanda; 3) entrada manual | No hay garantía de que Uber exponga el texto; el OCR cubre ese caso sin captura continua. |
| Parser | Independiente de la fuente, patrones genéricos ("$ 8.540", "N min (X km)", etiquetas es/en), confianza por dato | Tolerante a cambios de diseño; se ajusta con tests. |
| Motor | Reglas puras en Kotlin, sin LLM, umbrales del usuario | Resultados reproducibles y testeables. |
| Tarjeta | Vistas nativas en `WindowManager`; `TYPE_ACCESSIBILITY_OVERLAY` si el servicio está activo, si no `TYPE_APPLICATION_OVERLAY`, si no notificación | Menos permisos; robusta; no enfocable (no bloquea Uber). |
| Backend | Se reutilizan las 2 funciones tal cual. Sin claves en la app. | Están públicas; no hace falta secreto. El contexto nunca bloquea la evaluación. |
| Briefing en esta rama | Consulta explícita al reporte heredado al iniciar/actualizar turno; conserva solo campos mínimos en DataStore | No inventa índice ni confianza. La hora local es la recepción de la respuesta, no la actualización de sus fuentes; backend y feeds no se modifican. |
| Voz | `TextToSpeech` es-AR → es-419 → es; dictado vía `RecognizerIntent` (solo al tocar "Hablar") | No requiere permiso de micrófono propio; nunca escucha en segundo plano. |
| Copiloto | Reglas por intención, respuestas < 220 caracteres | Breve y sin distraer; diferencia datos de estimaciones. |
| Persistencia | DataStore local; historial solo numérico (máx. 100) | Minimización de datos. Backup deshabilitado. |
| CI | GitHub Actions: secret-scan → unit tests → lint → APK → verificación → artefactos; contrato en vivo; emulador API 31 | APK descargable desde el teléfono. |

## Módulos (paquete `ar.driverai.bridge`)
- `engine/` Modelos, `OfferEvaluator`, `OfferTextParser`, formatos.
- `capture/` `OfferPipeline`, `DriverAccessibilityService`, `ScreenCaptureService`, `OcrEngine`.
- `overlay/` `OverlayManager`.
- `backend/` `DriverAiApi`, `StrategicRepository`, `ShiftBriefingRepository` (snapshot local, no feed nuevo).
- `data/` Configuración, historial, cálculo de turno.
- `copilot/` `CopilotEngine`, zonas.
- `voice/` `TtsManager`.
- `ui/` Pantallas Compose y `SimulatorActivity` (solo debug).

## Ciclo de vida y batería
- Accesibilidad: solo eventos de `com.ubercab.driver`; antirrebote 350 ms; mínimo 300 ms entre escaneos; máximo 400 nodos por escaneo; sin trabajo cuando Uber no emite eventos.
- OCR: no hay captura continua; un cuadro por toque; la sesión se cierra a los 10 min sin uso.
- Tarjeta: se oculta a los 25 s, o 8 s después de que la oferta desaparece. Ofertas idénticas no se repiten durante 90 s.
