# Limitaciones conocidas y funciones no verificadas

## Clasificación
| Función | Implementada | Probada en dispositivo | Viable distribución privada | Depende de revisión Google Play |
|---|---|---|---|---|
| Motor económico y umbrales | Sí | Unitarias + emulador | Sí | No |
| Evaluación manual / demo | Sí | Emulador | Sí | No |
| Backend Supabase (reporte/posición) | Sí | Unitarias + contrato en vivo | Sí | No |
| Clima Open-Meteo de CABA Centro (debug privado) | Sí | CI pendiente; no validado en teléfono | Solo prueba personal; release desactivado hasta plan/fuente autorizados | No |
| Lectura por accesibilidad (simulador) | Sí | Emulador | Sí | Sí (declaración) |
| **Lectura por accesibilidad de Uber real** | Sí | **Pendiente de revalidar** (un intento previo no detectó) | Sí, si Uber expone el texto | Sí |
| Lectura por captura + OCR | Sí | **Una oferta real leída con APK `713f1fe`** (requiere consentimiento humano) | Sí, si Uber no bloquea la captura | Revisión de foreground service |
| Tarjeta flotante | Sí | Emulador | Sí | No |
| Voz (TTS) / dictado | Sí | **No** en teléfono | Sí | No |
| Copiloto por reglas | Sí | Unitarias | Sí | No |
| Briefing de turno | Parcial en `manus/briefing-end-to-end` | Pruebas de código pendientes | Solo con el reporte heredado | No |
| Diario de viaje observado → decisión → completado → cobro vinculado | Sí, local-first | Unitarias, lint, APK y emulador API 30 pasaron en CI (2026-11-25); falta teléfono real | Sí, registros solo en el dispositivo | No |
| Detección automática de viaje aceptado | No | No | No | No |
| Radar de demanda local (patrón + feriados + fixtures + manual + historial) | Sí | Unitarias; falta teléfono real | Sí | No |
| Partidos TheSportsDB | Sí | Unitarias con MockWebServer; consulta real verificada manualmente | Clave de prueba solo en debug; en release, clave propia | No |
| Lectura de DiDi, Cabify, inDrive y Bolt | Sí (beta) | **No**: sin muestras reales | Sí, si la app expone texto | Sí (declaración) |
| Demanda en zona de destino | Sí | Unitarias | Sí | No |
| Informes y exportación CSV | Sí | Unitarias | Sí | No |
| Cuentas de flota, sincronización de vehículos e ingestión de observaciones | No | No | No | No |

## Limitaciones
1. **Compatibilidad real aún parcial.** Una primera versión no reconoció el formato `11.405 ARS`; el parser se corrigió en `713f1fe`. Después, el conductor confirmó que esa versión leyó una oferta real por OCR (tarifa, km y minutos); recibió REVISAR porque no había configurado umbrales. Es solo una muestra. La lectura automática por accesibilidad sigue pendiente de revalidación y no se afirma compatibilidad general.
2. El parser solo declara una oferta entendida cuando detecta una señal textual de solicitud entrante y tarifa, kilómetros y minutos del viaje; no completa valores faltantes. Esto reduce falsos positivos de historiales, pero puede dejar sin reconocer una tarjeta que no exponga esas señales. El resumen compacto sin detalle puede quedar con baja confianza y REVISAR. El diseño de Uber puede cambiar; no se afirma compatibilidad general.
3. Si Uber usa `FLAG_SECURE`, el OCR verá la pantalla negra; si no expone texto por accesibilidad, solo quedará OCR o manual.
4. La tarifa leída es la que muestra Uber (normalmente ya neta de comisión). Si tu cuenta muestra otra cosa, ajustá los umbrales.
5. "Bruto por hora del turno" cuenta solo los viajes que marcás con "La tomé"; la app no sabe qué aceptaste en Uber.
6. La zona actual se elige a mano (no se usa GPS, por privacidad).
7. Los textos del backend son generados por IA y tienen inconsistencias (ver `INITIAL_AUDIT.md`); se muestran como estimaciones.
8. El primer briefing consulta el endpoint estratégico existente y guarda un resumen local mínimo. El backend no devuelve fuente ni hora de actualización por señal, y el snapshot solo marca cuándo se recibió la respuesta. Sus scores/textos se tratan como estimaciones. No existe todavía un índice de demanda calibrado ni feeds verificados de eventos, vuelos, barcos o patrones por día/hora.
9. Cada runner de CI puede firmar con distinta clave debug → a veces hay que desinstalar antes de actualizar. No hay keystore en el repo. No se pudo guardar uno en los secretos de Actions: la integración respondió HTTP 403 (`actions/secrets` no accesible).
10. Sin sincronización con la web: los ajustes viven solo en el teléfono.
11. Chequeo muestra fragmentos reducidos de tarifa, tiempo, distancia y rating etiquetado explícitamente, más confianza y veredicto. El texto libre no se conserva; el registro es temporal en memoria y solo se copia si el usuario toca el botón. No incluye nombres/direcciones ni se envía al backend. El rating no se añade al historial persistente.
12. El clima de referencia es una estimación modelada de Open-Meteo, habilitada solo en la build privada de depuración para un punto fijo de CABA Centro; no usa GPS ni representa necesariamente la zona seleccionada. La precipitación se presenta como acumulado informado y su intervalo, no como observación instantánea. Incluye atribución y enlace CC BY 4.0. El endpoint gratuito no se habilita en release; una distribución comercial necesita un plan comercial autorizado y una arquitectura de servidor/proxy. El dato meteorológico no equivale a alerta del SMN ni demanda.
13. El radar integra partidos (TheSportsDB) y feriados (ArgentinaDatos). La clave de prueba gratuita de TheSportsDB devuelve pocos eventos por liga, así que la cobertura es parcial sin clave propia. Vuelos, cruceros, recitales y paros todavía se cargan a mano: no hay feeds gratuitos con licencia clara. El índice es una heurística explicable, no está calibrado con viajes reales y no es demanda medida.
14. La web y el backend productivo no se modificaron.
15. El historial local conserva como máximo 100 evaluaciones. El cálculo del turno usa esas entradas, por lo que una jornada con más de 100 evaluaciones puede quedar truncada; los cobros ya registrados permanecen en Billetera, pero no se debe interpretar el historial como contabilidad completa.
16. Los estados del diario y los importes asociados son confirmaciones manuales del conductor; DriverAI no observa si Uber aceptó, terminó o liquidó un viaje. Los km/minutos reales son opcionales y no se estiman a partir del GPS.
17. DataStore usa el almacenamiento privado de la app, pero todavía no cifra los registros localmente. La billetera, el historial y los datos del vehículo requieren protección adicional si el dispositivo está comprometido o se comparte.
18. La app no detecta si el conductor aceptó un viaje: el diario actual requiere confirmación manual. La lectura de una pantalla posterior a la aceptación debe validarse con una muestra real y anonimizada antes de agregar reglas.
19. No existe todavía backend multiusuario/tenant para flotas ni una API para sincronizar vehículos, mantenimiento o señales colaborativas de demanda. El memo `FLEET_AND_REALTIME_STRATEGY.md` es una propuesta, no una capacidad desplegada.
20. Los package names de DiDi, Cabify, inDrive y Bolt salen de sus fichas públicas. Su diseño de pantalla no se probó con ofertas reales: pueden no leerse o leerse parcialmente. En ese caso, usá Oferta manual.
21. La zona de destino se infiere del texto visible con un catálogo de barrios del AMBA. Si la oferta no muestra el barrio, o lo abrevia, no hay pronóstico de destino. Con una sola mención se marca como "zona dudosa".
22. No hay registro automático de kilometraje por GPS (por privacidad). Para el monotributo no hay deducción por kilómetro, a diferencia del modelo de Gridwise en EE.UU.; se usa el odómetro manual.
23. El catálogo de ciudades incluye solo el AMBA. Otras ciudades de LATAM requieren su propio paquete de zonas y recintos.
