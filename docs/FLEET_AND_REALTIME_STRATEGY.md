# Estrategia: DriverAI en tiempo real y producto premium para flotas

**Estado:** propuesta para validar. Se basa en la auditoría del Android actual; no modifica el backend productivo ni presupone autorización de las plataformas de viajes.

## Lo que existe hoy

DriverAI lee texto de la pantalla mediante Accesibilidad o captura/OCR. El flujo por Accesibilidad observa cambios de ventana y recorre un número limitado de nodos visibles, pero `OfferTextParser` solo publica una oferta cuando reconoce contexto de solicitud, tarifa, distancia y duración. No hay un clasificador que reconozca una pantalla de viaje aceptado ni un vínculo fiable entre esa pantalla y la oferta original. Por eso, el lector automático todavía no se puede considerar validado en el uso real.

El cliente Android persiste ajustes, historial, billetera, odómetro y mantenimiento en DataStore local. Para contexto estratégico consulta las funciones existentes de reporte y posición, y consulta clima a Open-Meteo. No encontré identidad multiusuario, cuenta de flota, asignación vehículo-conductor ni una API de ingestión de observaciones de conductores. Hoy no se envía al backend ni el historial de ofertas ni los registros del vehículo.

## Tesis del producto

DriverAI puede ser la herramienta diaria del conductor y, para empresas de alquiler, una consola operativa que reduce vehículos parados, mantenimientos omitidos y trabajo administrativo. El conductor recibe valor individual aunque su flota no use el sistema; la flota paga por ordenar vehículos, asignaciones, mantenimiento y utilización. La capa colaborativa de demanda puede convertirse en una ventaja de red después, no debería ser el requisito para que el primer producto sea útil.

## Premium para flotas: empezar por el vehículo

El problema pagable más claro es conocer el estado de cada unidad y anticipar cuándo estará fuera de servicio. Un primer paquete para flotas podría incluir:

- Registro de vehículos, conductores habilitados, sucursales y asignaciones por turno o contrato de alquiler.
- Lecturas de odómetro y registro de entrega/devolución del vehículo, con modo sin conexión y corrección auditable.
- Planes por fecha o kilometraje para aceite, filtros, correas, frenos, cubiertas, VTV, seguro y otras habilitaciones; alertas de próximo vencimiento y tareas vencidas.
- Orden de servicio con taller/proveedor, fecha, kilometraje, costo, piezas y comprobante opcional. Para el MVP conviene guardar primero metadatos; fotos de documentos pueden contener datos personales y requieren una política separada.
- Panel de disponibilidad, días fuera de servicio, utilización, costo de mantenimiento por unidad y vehículos próximos a revisión.
- Exportación CSV y recordatorios para responsables de flota. Evitar al comienzo la contabilidad completa, pagos de alquiler o integración con cada plataforma.

El producto individual conserva su billetera y vehículo. La flota añade cuentas y permisos: administrador, operador de mantenimiento y conductor. El conductor debería poder ver qué datos comparte y con quién; el administrador no necesita leer la pantalla personal del conductor para gestionar un cambio de aceite.

**Modelo comercial para probar, no precio cerrado:** plan básico por vehículo activo al mes; plan profesional por vehículo con sucursales, roles, exportaciones, alertas y métricas. El precio y los mínimos deben surgir de entrevistas con dueños y encargados de flotas de alquiler, comparando el costo de una unidad parada y el tiempo administrativo actual. No fijar tarifas hasta validar uso, soporte y disposición de pago.

## Señal colaborativa de actividad y demanda

Las observaciones de varios conductores podrían crear un indicador temporal de actividad por zona, pero no son una lectura directa de toda la demanda ni del mapa completo de conductores. DriverAI ve solo lo que aparece en el dispositivo de quienes participan. Los indicadores iniciales posibles son cantidad y rango de ofertas observadas, tarifa/distancia/tiempo agregados, ofertas aceptadas o rechazadas de forma voluntaria y tiempo hasta la siguiente oferta. “Actividad observada” debe mantenerse separada de “demanda estimada”.

No recomiendo transmitir posiciones precisas continuas ni mostrar puntos en vivo de cada chofer. Empezaría con zonas aproximadas y ventanas de 15–30 minutos. El servidor solo publicaría una celda cuando haya suficientes aportantes independientes —umbral inicial a validar, por ejemplo cinco— y mostraría cantidad de señales, antigüedad y confianza. El umbral reduce el riesgo de destacar a una persona, pero no sustituye una evaluación de privacidad ni una revisión legal.

El evento que pudiera compartirse debería ser mínimo: tipo de observación, zona aproximada elegida o derivada con permiso, ventana temporal, métricas numéricas necesarias, origen (OCR/Accesibilidad/manual), confianza y versión del esquema. La pantalla cruda, texto libre, dirección, nombre, teléfono, identificador de viaje y coordenada exacta no deberían salir del teléfono. Las señales externas —eventos, clima, partidos o interrupciones de transporte— entrarían como fuentes distintas con proveedor, hora de actualización y cobertura; no se mezclan con observaciones de choferes sin indicar de dónde provienen.

## Arquitectura incremental

1. **Captura local primero:** añadir una máquina de estados para oferta observada, aceptación probable/confirmada, viaje en curso y finalización. El estado detectado automáticamente se etiqueta como “probable” hasta que el conductor lo confirme; la app nunca toca botones de la plataforma. La detección debe combinar textos visibles y transiciones de pantalla, con confianza y descarte de duplicados.
2. **Sincronización optativa y explícita:** definir eventos versionados y consentimiento por finalidad. El usuario puede activar compartir actividad agregada sin activar Accesibilidad ni compartir datos de flota. Reintentos offline deben ser idempotentes y el conductor debe poder retirar o corregir lo enviado.
3. **Backend con límites de tenant:** agregar identidad autenticada, flotas, membresías/roles, vehículos, asignaciones, registros de odómetro/mantenimiento y un endpoint de ingestión. Aplicar aislamiento por flota, validación del lado servidor, límites de frecuencia, expiración/retención y auditoría de cambios. Las funciones públicas actuales de reporte/posición no bastan para recibir telemetría segura; no agregar credenciales secretas dentro del APK.
4. **Agregación antes del mapa:** generar indicadores por zona/ventana en el servidor, incluir fuente, cantidad de observaciones, frescura y confianza, y suprimir celdas con pocos aportantes. El mapa muestra intensidad agregada, no ubicación en vivo de choferes.
5. **Fuentes externas verificadas:** integrar eventos, clima, partidos y paros solo con fuente permitida, licencia y cobertura documentadas. Medir si cada señal anticipa observaciones reales antes de convertirla en recomendación.

## Orden recomendado

- **P0 — Cerrar el lector:** revisar una captura posterior a la aceptación, sin datos personales visibles; probar en más de un flujo; medir falsos positivos. La aceptación automática debe aparecer como sugerencia confirmable, no como hecho.
- **P1 — Probar flota sin telemetría:** entrevistar a 5–10 dueños/encargados y a conductores; pilotear vehículos, asignaciones, odómetro y service con una flota pequeña. Validar que recordatorios y disponibilidad ahorren trabajo antes de construir un panel extenso.
- **P2 — Multiusuario seguro:** implementar identidad, roles, tenant, sincronización offline y correcciones. Mantener desactivado el envío de datos de viaje por defecto; separar el consentimiento de flota del consentimiento de investigación de demanda.
- **P3 — Señal de ciudad:** piloto voluntario en una sola ciudad y con cohortes suficientes. Comparar el índice con actividad observada, publicar métricas de calidad y permitir borrar o dejar de aportar.
- **P4 — Escala regional:** ampliar zonas, monedas y fuentes únicamente después de comprobar precisión, permisos de datos, soporte operativo y economía por flota.

## Métricas para decidir

Para flotas: porcentaje de vehículos con odómetro reciente, servicios vencidos, tiempo fuera de servicio, utilización por unidad, tiempo dedicado a administración y retención de flotas. Para el copiloto: ofertas completas reconocidas por fuente, falsos positivos de aceptación, correcciones del usuario, demora de detección y consumo de batería. Para demanda: cobertura espacial/horaria, número de aportantes independientes, antigüedad, error frente a actividad observada y valor incremental sobre calendario/clima/eventos. No medir éxito por cantidad de puntos GPS recolectados.

## Decisión

Priorizar **mantenimiento y operaciones de flota** como primer producto premium, porque aprovecha datos ya presentes en el teléfono y puede entregar valor sin vigilar al conductor. En paralelo, validar con captura real el estado de viaje aceptado. Mantener la red de actividad como piloto de consentimiento, agregación y calidad; no desplegar un mapa de ubicaciones individuales ni empezar a subir datos de producción hasta definir autenticación, permisos, retención y mecanismo de baja.
