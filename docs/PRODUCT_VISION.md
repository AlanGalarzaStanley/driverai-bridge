# Visión de producto: DriverAI como copiloto del conductor

**Estado:** propuesta de producto; describe dirección futura, no capacidades ya entregadas. El sitio y el backend productivo permanecen intactos.

## La idea central

DriverAI no debería ser solo un lector de ofertas. Debe ayudar al conductor a responder tres preguntas durante todo el ciclo de trabajo: **¿me conviene este viaje?, ¿cómo va realmente mi turno?, y qué debo preparar para trabajar mejor mañana?**

La ventaja no nace de una respuesta genérica de IA, sino de combinar rápidamente una lectura fiable del viaje, los costos y objetivos propios del conductor, lo que realmente ocurrió durante el turno, el estado del vehículo y señales de demanda con su fuente y nivel de incertidumbre.

## Punto de partida confirmado

- El APK `713f1fe` pasó Android CI y el emulador.
- El conductor confirmó una lectura real exitosa por **OCR**: detectó tarifa, kilómetros y minutos. Mostró **REVISAR** porque todavía no había configurado umbrales.
- La lectura automática por Accesibilidad todavía debe volver a probarse con esa versión.
- La app ya tiene umbrales de tarifa, bruto por km/hora y margen; puede derivar costo variable por km de consumo y precio de combustible; guarda historial numérico, permite marcar una oferta como “La tomé” y estima bruto/hora del turno.
- Ese historial es un inicio, no una billetera: registra la oferta estimada, no confirma el pago real ni sus gastos. No se deben confundir oferta, viaje aceptado, viaje terminado e ingreso liquidado.

## El producto en tres piezas

### 1. DriverAI Bridge: copiloto en el teléfono

Lee la oferta localmente con Accesibilidad u OCR explícito, presenta una evaluación determinística y breve, y deja la decisión en manos del conductor. La tarjeta debería explicar tarifa, kilómetros y tiempo considerados, qué costos se incluyeron, qué falta y cuánta confianza tiene cada lectura. Si los datos o el objetivo faltan, debe decir **REVISAR**, no inventar una recomendación.

### 2. Billetera y diario del turno

Convierte las oportunidades en una contabilidad operativa que distinga, como mínimo:

- **Oferta observada** y sus valores estimados.
- **Aceptada / rechazada / cancelada**, solo cuando el conductor lo confirme; la app no toca Uber.
- **Viaje completado** y, por separado, ingreso real recibido o corregido por el conductor.
- **Gastos** del turno: combustible, peajes, estacionamiento y otros costos que el conductor decida registrar.
- **Resultado del turno**: bruto, gastos variables, costos asignados, neto estimado, horas conectadas/trabajadas, km y rendimiento por hora y por km.

La pantalla de cierre debería responder “cuánto hice hoy” con la diferencia visible entre estimaciones y datos reales. El costo y el ingreso deben ser editables y trazables; nunca presentar ganancia neta si faltan datos.

### 3. Gridwise LATAM: planificación y panorama de varios turnos

Como dirección propuesta, Gridwise LATAM puede ofrecer la vista de varios días: billetera, gastos, desempeño, documentos del vehículo y planificación de turnos. DriverAI Bridge aporta decisiones oportunas y datos resumidos del turno. La conexión entre ambos debe construirse mediante un contrato versionado y explícito; **hoy no hay sincronización de billetera implementada** y no se deben cambiar las funciones productivas actuales para simular que existe.

## Herramientas para el conductor

### Economía personal, no umbrales genéricos

El conductor debería configurar vehículo, consumo, combustible, reserva de mantenimiento, costos fijos y meta neta por hora o por turno. A partir de esa información, la app puede proponer umbrales editables de bruto/km, bruto/hora, tarifa mínima y margen. La tarjeta debe distinguir:

- bruto mostrado por la plataforma;
- costos variables estimados;
- costos fijos distribuidos según una regla visible;
- estimación neta, con los supuestos señalados.

La lectura real confirmada hasta ahora es una sola y no alcanza para fijar rentabilidad ni umbrales. Los importes, costos de combustible y metas deben venir del conductor o de una fuente verificable.

### Mantenimiento y habilitaciones

Un perfil de vehículo debería registrar fechas y kilometraje de services, aceite, filtros, neumáticos, VTV, GNC, seguro y habilitaciones. Primera versión: recordatorios locales configurables por fecha/km y estado **pendiente / hecho**. Guardar vencimientos y metadatos; no almacenar fotos de documentos personales salvo que exista una necesidad clara y consentimiento separado.

### Demanda y planificación

Un planificador puede sugerir zona y horario combinando historial propio con eventos, clima, tráfico u otras fuentes autorizadas. Cada señal necesita fuente, fecha de actualización, área aproximada, ventana horaria y confianza. Una predicción no debe prometer ganancias ni mandar al conductor a otra zona como certeza. Empezar con información contextual y comparar después el pronóstico con resultados reales; personalizar solo cuando haya historial suficiente.

### Copiloto conversacional

La IA puede explicar en lenguaje natural un cálculo determinístico, resumir el turno y contestar preguntas breves. No debe calcular dinero por su cuenta, completar datos faltantes ni presentar una predicción como hecho. Toda respuesta numérica se deriva del motor y muestra de qué dato proviene. Durante la conducción: mensajes cortos, voz opcional y sin interacción que invite a mirar la pantalla más tiempo.

## Secuencia recomendada

1. **Cerrar el ciclo de calibración.** Repetir OCR en varias ofertas y probar la lectura automática por Accesibilidad; verificar tarifa, recogida, tramo del viaje y confianza por separado. Configurar al menos un umbral. No publicar como compatible de forma general basándose en una muestra.
2. **Construir el diario de turno y una billetera mínima local.** Separar oferta, aceptación manual, viaje completado, ingreso real y gasto. Añadir cierre de turno, neto estimado y meta diaria. Reutilizar los parámetros económicos existentes, pero no tratar “La tomé” como pago recibido.
3. **Personalizar costos y metas.** Completar un asistente de configuración del vehículo y gastos típicos; generar umbrales sugeridos pero editables y mostrar cómo se calcularon.
4. **Agregar mantenimiento y vencimientos.** Alertas locales basadas en fecha/km, con confirmación de hecho y registro mínimo.
5. **Agregar planificación de demanda.** Integrar fuentes identificadas, áreas aproximadas y horarios; empezar como asistente de planificación y medir error antes de recomendar movimientos.
6. **Conectar Bridge y Gridwise LATAM.** Diseñar primero el esquema, identidad, consentimiento, retención, corrección y eliminación de datos; después evaluar un endpoint nuevo y separado. No alterar el sitio ni las funciones de Supabase existentes hasta autorizar y revisar el contrato.

## Modelo de datos que conviene preservar

- `OfferObservation`: instante, fuente (OCR/Accesibilidad/manual), tarifa, distancias/tiempos separados para recogida y viaje, confianza y veredicto.
- `TripLifecycle`: estado confirmado por el conductor; conserva métricas **estimadas** y **reales** en campos distintos.
- `MoneyEntry`: ingreso/gasto, importe y moneda, categoría, fecha, turno y método de corrección.
- `VehicleProfile` y `MaintenanceEvent`: configuración económica, odómetro, tarea y fecha/vencimiento.
- `DemandSignal`: zona aproximada, ventana horaria, fuente, actualización y confianza.

La primera versión debe ser **local-first**. No persistir texto crudo de Uber, capturas, nombres de pasajeros ni direcciones completas. La sincronización de métricas debe ser opcional, revocable y limitada a los datos necesarios; cualquier ubicación compartida debe ser aproximada y consentida.

## Cómo sabremos si mejora el trabajo

- Porcentaje de ofertas leídas completamente y tasa de campos ausentes, por fuente.
- Diferencia entre oferta detectada y métricas corregidas/completadas por el conductor.
- Diferencia entre ingreso estimado e ingreso real del viaje.
- Neto estimado y neto confirmado por turno, costo por km y rendimiento por hora.
- Recordatorios de mantenimiento cumplidos y vencimientos evitados.
- Error de las señales de demanda por zona/ventana, siempre comparado con resultados y no solo con impresiones.

## Decisión de producto

**El siguiente bloque de valor recomendado es “Turno + billetera mínima”, después de la segunda ronda de lectura real.** La lectura ya produjo una primera señal útil; el diario aporta la verdad de terreno necesaria para que el copiloto aprenda qué viajes, costos y horarios funcionan para esa persona. Construir predicción de demanda antes de ese diario produciría recomendaciones vistosas, pero menos confiables.