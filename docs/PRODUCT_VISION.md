# Visión de producto: DriverAI como copiloto del conductor

**Estado:** propuesta de producto; la mayor parte de lo descrito es dirección futura, no capacidad ya entregada. El sitio y el backend productivo permanecen intactos.

## La idea central

DriverAI no debería ser solo un lector de ofertas. Debe ayudar al conductor a responder durante todo el turno: **¿dónde y cuándo conviene trabajar hoy?, ¿me conviene esta oferta dadas mis metas?, ¿cómo va mi resultado real?, y qué debo preparar para el próximo turno?**

El copiloto empieza el turno con un panorama del día —eventos, clima, vuelos, barcos, picos habituales y otras señales disponibles— y lo convierte en un **índice de oportunidad por zona y ventana horaria**. Después cruza ese contexto con la economía del conductor y la oferta que acaba de llegar. Al terminar cada viaje y turno, contrasta estimaciones con resultados reales para mejorar su contexto.

La ventaja no nace de una respuesta genérica de IA. Nace de combinar deprisa datos oportunos, rentabilidad verificable, historial propio y herramientas útiles para el trabajo, indicando siempre fuente, vigencia e incertidumbre.

## Posicionamiento: Gridwise + Ruta Rentable para LATAM

- **Gridwise** domina en EE.UU. el seguimiento de ingresos, la demanda por eventos y aeropuertos, y los informes. No evalúa ofertas en vivo ni opera en LATAM.
- **Ruta Rentable, Viaje Rentable y UDDI** resuelven el semáforo de la oferta en LATAM, pero no anticipan dónde y cuándo trabajar.
- **DriverAI** une las dos cosas en un copiloto en tiempo real: evalúa la oferta en cinco apps, muestra el radar de demanda por zona y hora, la demanda prevista en el destino, el límite de recogida, los informes y la exportación. Todo funciona local-first y sin automatizar toques.

Detalle del modelo y las fuentes: [`RADAR_DEMANDA.md`](RADAR_DEMANDA.md).

### Hoja de ruta propuesta
1. Validar en teléfonos reales la lectura de DiDi, Cabify, inDrive y Bolt con muestras anonimizadas.
2. Calibrar el índice: comparar el radar con las ofertas observadas por franja (historial propio) y ajustar los pesos.
3. Desplegar `driverai-senales` con fuentes licenciadas (fixtures pagos, AA2000, AGP, comunicados de paros) para que la release no dependa de la carga manual.
4. Agregar paquetes de ciudad (Córdoba, Rosario, Santiago, CDMX, Bogotá, Lima) con zonas, recintos y moneda.
5. Ofrecer señal colaborativa entre conductores solo con consentimiento, autenticación y agregación anónima.
6. Plan premium: radar completo, alertas proactivas por voz antes de cada pico e informes contables.

## Punto de partida confirmado

- El APK `713f1fe` pasó Android CI y el emulador.
- El conductor confirmó una lectura real exitosa por **OCR**: detectó tarifa, kilómetros y minutos. Mostró **REVISAR** porque todavía no había configurado umbrales.
- La lectura automática por Accesibilidad todavía debe volver a probarse con esa versión.
- La app ya tiene umbrales de tarifa, bruto por km/hora y margen; puede derivar costo variable por km de consumo y precio de combustible; guarda historial numérico, permite marcar una oferta como “La tomé” y estima bruto/hora del turno.
- En la rama `manus/briefing-end-to-end` se está incorporando un primer paso explícito al iniciar el turno: consultar el reporte estratégico heredado y guardar un resumen mínimo local. Ese endpoint no identifica fuente, vigencia ni confianza de cada dato; por tanto, no es el escaneo diario de señales ni un índice de demanda nuevo y calibrado.
- El historial local ya separa oferta observada, decisión del conductor, viaje completado y cobro confirmado; los cobros asociados se reflejan una sola vez en la billetera. Distancia/tiempo reales siguen siendo opcionales y manuales.
- Siguen pendientes el briefing alimentado por fuentes autorizadas, índice de demanda por zona/franja calibrado, cierre contable completo (incluidos impuestos y costos fijos), sincronización con Gridwise LATAM y expansión de monedas/mercados más allá del piloto ARS.

## El producto en cuatro piezas

### 1. DriverAI Bridge: copiloto en el teléfono

Lee la oferta localmente con Accesibilidad u OCR explícito, muestra una evaluación determinística y breve, y deja la decisión en manos del conductor. La tarjeta debe indicar tarifa, recogida, tramo del viaje, tiempo, costos usados, destino/zona cuando sea necesario, confianza y qué dato falta. No acepta ni rechaza viajes.

### 2. Inicio del turno: escaneo e índice de demanda

Al iniciar el turno, el conductor elige o autoriza una zona aproximada y un horario previsto. La app reúne señales del día y las muestra por zona y franja horaria:

- eventos, deportes y horarios de salida de recintos;
- clima y alertas meteorológicas;
- llegadas/salidas de vuelos y ventanas de actividad aeroportuaria;
- arribos y desembarques de barcos/cruceros;
- hora y día de semana, feriados, picos habituales y patrones propios;
- cortes o cambios relevantes de movilidad, cuando exista una fuente confiable.

El resultado puede ser un **índice de demanda relativa (por ejemplo 0–100)** y un nivel de confianza aparte, con las señales que lo explican, su fuente y su hora de actualización. El índice no es una probabilidad de conseguir viaje ni una promesa de ganancia. Un vuelo, barco o evento es una señal potencial, no evidencia de que habrá viajes: también importan la oferta de conductores, el tráfico y lo observado por el propio usuario.

La vista de inicio del turno debería responder: **qué ventanas parecen más activas, en qué zonas y por qué**. Las alertas para cambios durante el turno serán optativas, oportunas y breves.

### 3. Billetera y diario del turno

El turno es el puente entre una predicción y la realidad. El diario debe distinguir:

- **Oferta observada** y valores estimados.
- **Aceptada / rechazada / cancelada**, solo cuando el conductor lo confirme; la app no toca Uber.
- **Viaje completado** y, por separado, ingreso real recibido o corregido por el conductor. La versión Android ahora permite registrar este recorrido localmente y vincular el cobro confirmado a Billetera; no deriva pagos de ofertas.
- **Gastos** del turno: combustible, peajes, estacionamiento y otros costos que quiera registrar.
- **Resultado del turno**: bruto, gastos variables, costos asignados, neto estimado, horas conectadas/trabajadas, km, objetivo diario y rendimiento por hora/km.

La billetera debe mostrar la diferencia entre pronóstico, oferta y resultado real. Nunca presentar ganancia neta si faltan costos; todas las correcciones tienen que ser visibles y editables.

### 4. Gridwise LATAM: perspectiva de varios turnos

Como dirección propuesta, Gridwise LATAM puede dar la vista de varios días: billetera, tendencias, gastos, desempeño, documentos del vehículo y planificación. DriverAI Bridge aporta decisiones oportunas y datos mínimos resumidos del turno. La conexión entre ambos debe usar un contrato versionado, explícito y consentido. **Hoy no hay sincronización de billetera ni índice de demanda implementados**, y no se deben modificar las funciones productivas actuales para simular que existen.

## Cómo se decide si conviene una oferta

La demanda y la rentabilidad son dimensiones complementarias, no sustitutas. La oferta debe evaluarse en tres capas que se explican por separado:

1. **Economía del viaje:** tarifa, recogida, distancia y tiempo totales atribuibles, combustible/costo por km, retorno vacío configurado y objetivo personal. Diferenciar bruto y neto estimado.
2. **Contexto al llegar:** índice y confianza de demanda en destino durante la ventana posterior al arribo; una zona activa podría reducir espera, pero solo si las señales están vigentes.
3. **Alternativa de no tomarla:** demanda prevista desde la zona actual y costo de oportunidad del tiempo, si existen datos suficientes.

El destino puede mejorar la perspectiva de una oferta, pero no debe borrar un piso de rentabilidad, una recogida excesiva o incertidumbre alta. Al principio, aplicar límites económicos configurados por el conductor y explicar el contexto como factor adicional. Aprender un valor esperado que combine esos factores requiere comparar muchas ofertas con viajes y resultados reales; no inventar pesos ni prometer ganancias antes de tener evidencia.

La respuesta del copiloto puede decir, por ejemplo: **“La oferta cubre tu mínimo por hora; el destino tiene demanda prevista alta entre 19–20 h, con confianza media por dos señales. No incluí el costo del regreso.”** Si falta tarifa, confianza, costo o contexto, debe decir **REVISAR** y señalarlo. No debe esconder la incertidumbre detrás de un único puntaje.

## Herramientas para el conductor

### Economía personal, no umbrales genéricos

El conductor debería configurar vehículo, consumo, combustible, reserva de mantenimiento, costos fijos y meta neta por hora/turno. A partir de eso, DriverAI puede proponer umbrales editables de bruto/km, bruto/hora, tarifa mínima y margen. La tarjeta debe separar:

- bruto mostrado por la plataforma;
- costos variables estimados;
- costos fijos distribuidos mediante una regla visible;
- neto estimado y supuestos utilizados.

La lectura real confirmada hasta ahora es una sola y no alcanza para fijar rentabilidad ni umbrales. Los importes, costos y metas deben venir del conductor o de una fuente verificable.

### Mantenimiento y habilitaciones

Un perfil de vehículo debería registrar fechas y kilometraje de services, aceite, filtros, neumáticos, VTV, GNC, seguro y habilitaciones. Primera versión: recordatorios locales por fecha/km y estado **pendiente / hecho**. Guardar vencimientos y metadatos; no almacenar fotos de documentos personales salvo necesidad clara y consentimiento separado.

### Predicción de demanda que aprende

Las señales externas necesitan fuente permitida, hora de actualización, cobertura y calidad. No depender de scraping que viole términos. Para cada señal registrar: zona aproximada, ventana horaria, intensidad observada/estimada y fiabilidad. El índice inicial será contextual y explicable; su calibración personalizada vendrá de comparar predicción con actividad real de cada conductor.

Clima puede modificar demanda y también oferta/tráfico; vuelos y barcos requieren estimar cuándo se libera público, no solo contar arribos; eventos deben incluir hora probable de salida y capacidad; horarios pico se deben estimar por zona y día. La disponibilidad de datos de oferta de conductores puede ser limitada y debe reconocerse como incertidumbre.

### Copiloto conversacional

La IA explica un cálculo determinístico, resume el turno y contesta preguntas breves. No calcula dinero por su cuenta, completa datos faltantes ni presenta predicción como hecho. Toda respuesta numérica proviene del motor y debe indicar fuente y supuestos. Durante la conducción: voz opcional, mensajes cortos y sin invitar a mirar la pantalla más tiempo.

## Secuencia recomendada

1. **Cerrar el ciclo del copiloto.** Repetir OCR en más ofertas y revalidar Accesibilidad automática; separar tarifa, recogida, viaje y confianza. Validar una transición de “viaje aceptado” con texto visible, sin ejecutar interacciones, antes de automatizar cualquier estado. Configurar umbrales personales.
2. **Diseñar el “Briefing de turno” como la primera función estratégica.** Elegir zona y horario; mostrar clima, eventos, vuelos/barcos y patrones horarios con fuentes, caducidad y confianza. Empezar en una zona y sin predicciones de ganancia.
3. **Evolucionar el diario y la billetera mínima local.** La app ya registra qué oferta llegó, qué decidió el conductor, si se completó y qué cobro confirmó; gastos y cobros quedan local-first. Falta mejorar correcciones/observación de resultados y luego usar datos agregados solo con consentimiento para calibrar demanda y economía.
4. **Cruzar demanda y rentabilidad gradualmente.** Primero presentar índices independientes; después evaluar si las señales del destino mejoran resultados reales, manteniendo pisos económicos y explicaciones.
5. **Validar el producto premium de flotas.** Empezar por vehículos, asignaciones, odómetro, mantenimiento y vencimientos; priorizar datos locales y probar el flujo con encargados antes de construir telemetría o un panel extenso.
6. **Conectar DriverAI Bridge y Gridwise LATAM.** Diseñar identidad, API, consentimiento, retención, corrección y eliminación antes de cambiar backend o sitio. Añadir fuentes externas con licencias/contratos verificados.

La propuesta de flotas y la señal colaborativa de actividad están detalladas en [`FLEET_AND_REALTIME_STRATEGY.md`](FLEET_AND_REALTIME_STRATEGY.md). Es una hipótesis de producto; no implica que haya backend multiusuario, ingestión de eventos o mapa de demanda implementados.

## Modelo de datos que conviene preservar

- `ShiftPlan`: fecha, franja prevista, zona inicial aproximada, objetivo y hora de inicio.
- `DemandForecast`: zona, ventana, índice relativo, confianza, fuentes, señales, vigencia y versión del cálculo.
- `OfferObservation`: instante, fuente (OCR/Accesibilidad/manual), tarifa, distancia/tiempo de recogida y viaje, confianza y veredicto.
- `TripLifecycle`: estado confirmado por el conductor; métricas estimadas y reales en campos separados.
- `MoneyEntry`: ingreso/gasto, importe y moneda, categoría, fecha, turno y método de corrección.
- `VehicleProfile` y `MaintenanceEvent`: configuración económica, odómetro, tarea y fecha/vencimiento.

La primera versión debe ser **local-first**. No persistir texto crudo de Uber, capturas, nombres de pasajeros ni direcciones completas. Para predicción, usar zona aproximada y franja horaria; ubicación precisa o sincronización solo con consentimiento, opción de revocar y datos mínimos necesarios.

## Cómo sabremos si mejora el trabajo

- Porcentaje de ofertas leídas completamente y datos faltantes/confianza por fuente.
- Diferencia entre el índice de demanda previsto y actividad observada por zona/franja.
- Diferencia entre oferta y métricas corregidas, y entre ingreso estimado y real.
- Neto confirmado por turno, costo por km, rendimiento/hora y cumplimiento de la meta.
- Errores de pronóstico por señal (evento, clima, vuelo/barco, horario) y por horizonte.
- Recordatorios de mantenimiento cumplidos y vencimientos evitados.

## Decisión de producto

**El siguiente bloque es validar el ciclo de oferta y estado aceptado con una muestra real, mientras se valida en paralelo el problema operativo de las flotas.** Después, priorizar el briefing de turno con fuentes y confianza y medirlo contra resultados del diario. La decisión de una oferta debe combinar economía del viaje, demanda probable en destino y alternativa en la zona actual, siempre separando datos, predicción y confianza.
