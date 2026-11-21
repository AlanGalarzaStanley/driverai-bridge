# Limitaciones conocidas y funciones no verificadas

## Clasificación
| Función | Implementada | Probada en dispositivo | Viable distribución privada | Depende de revisión Google Play |
|---|---|---|---|---|
| Motor económico y umbrales | Sí | Unitarias + emulador | Sí | No |
| Evaluación manual / demo | Sí | Emulador | Sí | No |
| Backend Supabase (reporte/posición) | Sí | Unitarias + contrato en vivo | Sí | No |
| Lectura por accesibilidad (simulador) | Sí | Emulador | Sí | Sí (declaración) |
| **Lectura por accesibilidad de Uber real** | Sí | **Pendiente de revalidar** (un intento previo no detectó) | Sí, si Uber expone el texto | Sí |
| Lectura por captura + OCR | Sí | **Una oferta real leída con APK `713f1fe`** (requiere consentimiento humano) | Sí, si Uber no bloquea la captura | Revisión de foreground service |
| Tarjeta flotante | Sí | Emulador | Sí | No |
| Voz (TTS) / dictado | Sí | **No** en teléfono | Sí | No |
| Copiloto por reglas | Sí | Unitarias | Sí | No |

## Limitaciones
1. **Compatibilidad real aún parcial.** Una primera versión no reconoció el formato `11.405 ARS`; el parser se corrigió en `713f1fe`. Después, el conductor confirmó que esa versión leyó una oferta real por OCR (tarifa, km y minutos); recibió REVISAR porque no había configurado umbrales. Es solo una muestra. La lectura automática por accesibilidad sigue pendiente de revalidación y no se afirma compatibilidad general.
2. El diseño de la tarjeta de Uber puede cambiar; el parser usa patrones genéricos y avisa con baja confianza en vez de inventar.
3. Si Uber usa `FLAG_SECURE`, el OCR verá la pantalla negra; si no expone texto por accesibilidad, solo quedará OCR o manual.
4. La tarifa leída es la que muestra Uber (normalmente ya neta de comisión). Si tu cuenta muestra otra cosa, ajustá los umbrales.
5. "Bruto por hora del turno" cuenta solo los viajes que marcás con "La tomé"; la app no sabe qué aceptaste en Uber.
6. La zona actual se elige a mano (no se usa GPS, por privacidad).
7. Los textos del backend son generados por IA y tienen inconsistencias (ver `INITIAL_AUDIT.md`); se muestran como estimaciones.
8. Cada runner de CI puede firmar con distinta clave debug → a veces hay que desinstalar antes de actualizar. No hay keystore en el repo. No se pudo guardar uno en los secretos de Actions: la integración respondió HTTP 403 (`actions/secrets` no accesible).
9. Sin sincronización con la web: los ajustes viven solo en el teléfono.
10. Chequeo muestra solo fragmentos reducidos de tarifa, tiempo y distancia, etiquetas permitidas, campos detectados y veredicto. El texto libre de accesibilidad no se conserva; el registro permanece en memoria y solo se copia al portapapeles si el usuario toca el botón. No se envía el texto ni imágenes al backend.
11. La web y el backend productivo no se modificaron.
