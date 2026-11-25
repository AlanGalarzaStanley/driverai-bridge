# Piloto con flota (hasta 50 autos)

**Objetivo:** probar DriverAI como MVP con conductores reales, medir si mejora el $/hora aceptado y juntar evidencia para calibrar el radar y la lectura multi-app. **Sin backend nuevo ni sincronización:** cada teléfono guarda sus datos y el conductor comparte un resumen semanal por decisión propia.

## Fases
| Fase | Autos | Duración | Qué se valida | Pasa a la siguiente si… |
|---|---|---|---|---|
| 0 · Interna | 1–2 (vos) | 3–5 días | Instalación, lectura de Uber (accesibilidad u OCR), tarjeta flotante, alertas de pico | La tarjeta aparece en ≥ 8 de cada 10 ofertas y no hay cierres de la app |
| 1 · Piloto chico | 5 | 2 semanas | Comprensión del semáforo, umbrales, Informe IA, resumen semanal | ≥ 4 de 5 conductores lo usan ≥ 3 turnos por semana |
| 2 · Ampliación | 15 | 2–3 semanas | Diferencias por app (DiDi, Cabify, inDrive), zonas, horarios | Adhesión ≥ 60 % y sin quejas de batería o privacidad sin resolver |
| 3 · Flota completa | 50 | continuo | Comparación entre conductores, ahorro en mantenimiento | Decisión de pagar backend de flota con consentimiento |

## Checklist de instalación por conductor (10 minutos)
1. Instalar el APK (ver [`BUILD_AND_INSTALL.md`](BUILD_AND_INSTALL.md)). Android pide permitir "orígenes desconocidos".
2. Abrir DriverAI. En **Primeros pasos**:
   - **Tus mínimos**: precio del litro/m³, consumo cada 100 km y $/hora deseado. La app sugiere el mínimo $/km (3 × costo de combustible por km, redondeado).
   - **Lectura**: activar la lectura automática (accesibilidad). Si Uber no expone el texto, usar la lectura por captura (OCR).
   - **Datos con IA**: probar una vez el flujo de "Actualizar con IA".
3. **Más › Ajustes › Opciones avanzadas › Flota**: nombre de la flota, alias del conductor (sin nombre real) y alias del auto (por ejemplo, la patente parcial "AB-12").
4. Conceder permiso de notificaciones para las alertas de pico.
5. Hacer una oferta de prueba con **Inicio › Probar una oferta a mano**.

## Consentimiento (texto sugerido)
> "Vas a usar DriverAI en modo piloto. La app lee las ofertas que aparecen en tu pantalla solo para recomendarte; **no acepta ni rechaza nada** y no envía capturas, nombres, teléfonos ni direcciones. Tus datos quedan en tu teléfono. Una vez por semana te pedimos que compartas el resumen desde Informes; podés revisar el texto antes de enviarlo y dejar de participar cuando quieras."

## Resumen semanal (sin servidor)
- En **Más › Informes › Resumen para tu flota**, el conductor toca **Compartir** (semana actual o "Semana pasada") y lo envía por WhatsApp o correo al responsable.
- El mensaje incluye: ofertas vistas, aceptadas, adhesión a la recomendación, $/km y $/h de las aceptadas, ingresos de billetera, km recorridos, cómo se leyó cada oferta (automática/OCR/manual) y mantenimiento próximo (500 km o 7 días).
- Al final trae una **fila CSV** con encabezado fijo. Pegá las filas en una Google Sheet (Datos › Dividir texto en columnas) para comparar conductores y semanas. Las celdas están protegidas contra fórmulas.

## KPIs del piloto
| KPI | Cómo se mide | Meta orientativa |
|---|---|---|
| Adhesión | % de ofertas aceptadas que DriverAI marcó CONVIENE | ≥ 60 % |
| $/h aceptado | Del resumen semanal, contra la semana 0 sin la app | +10 % |
| Lectura automática | % de ofertas leídas sin OCR ni carga manual | ≥ 80 % en Uber |
| Uso del radar | Conductores que cargaron un Informe IA en la semana | ≥ 50 % |
| Retención | Conductores activos en la semana 3 | ≥ 70 % |

## Bucle de mejora
1. Recolectar **capturas anonimizadas** (nombre, teléfono, direcciones y mapa tapados) de ofertas de DiDi, Cabify, inDrive y Bolt donde la tarjeta no apareció. Con eso se ajusta el parser y se agregan pruebas.
2. Comparar el pico previsto por el radar con las ofertas observadas en el historial de cada conductor.
3. Reunión de 15 minutos por semana con 2–3 conductores: qué alerta sirvió, qué sobró y qué pantalla no se entendió.

## Lo que NO hace el piloto
- No hay panel de flota en tiempo real ni envío automático de datos. Eso requiere un diseño de consentimiento, autenticación y aislamiento por flota (ver [`FLEET_AND_REALTIME_STRATEGY.md`](FLEET_AND_REALTIME_STRATEGY.md)).
- No automatiza toques ni acepta viajes.
