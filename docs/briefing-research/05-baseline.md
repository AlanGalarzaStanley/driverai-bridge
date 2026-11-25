# Patrones horarios, calendario y movilidad habitual — línea base AMBA

**Corte de revisión: 21 de noviembre de 2026.** En el código actual de DriverAI, las zonas son Microcentro, Palermo, Belgrano, Recoleta, San Telmo, Caballito, Flores, Liniers, Quilmes, Bernal, Lomas, Lanús, Avellaneda y San Isidro. La agrupación Norte/Centro/Oeste/Sur en `app/src/main/java/ar/driverai/bridge/copilot/CopilotEngine.kt` es una heurística de proximidad, no una geometría cartográfica.

## Conclusión y recomendación

La fuente primaria con mejor resolución conjunta de hora y zona que se verificó es el dataset de la Secretaría de Transporte sobre operaciones SUBE en hexágonos para RMBA: el recurso CSV observado contiene `id_h3`, `hora`, `modo` y `cantidad_trx`; la ficha lo describe como un día hábil promedio, incluye colectivos, trenes, subte y premetro, y actualmente identifica un recurso «día tipo octubre 2025».[1] [2] Es un patrón histórico de transporte público, no una lectura en tiempo real ni una medición de viajes solicitados en apps. Además, la ficha indica expresamente que **no se especificó licencia**. No lo incorporaría a producción ni lo redistribuiría antes de conseguir una aclaración/licencia por escrito.[1]

Para una primera versión con condiciones de reutilización publicadas, usaría el calendario oficial 2026 como etiqueta de contexto y el CSV diario SUBE marcado `AMBA=SI` para aprender o mostrar diferencias entre días; el portal asigna a ese dataset Creative Commons Attribution 4.0. En la descarga inspeccionada, el rango va del 1 de enero al 18 de noviembre de 2026, aunque la ficha reporta como última modificación el 8 de abril de 2026. Esa discrepancia se debe registrar como metadato de frescura y volver a medir en cada ingestión.[3] [4] Esta serie es por fecha, empresa y línea (más modo y jurisdicción), no por zona horaria, así que **no resuelve por sí sola la matriz día×hora×zona**.

Por tanto, presentaría el hexágono SUBE como referencia contextual condicionada a licencia, no como proxy validado de demanda de viajes por aplicación. Hasta aclarar los términos, la línea base defendible es un perfil auditable de día de semana/fin de semana/feriado apoyado en el calendario y en series diarias de movilidad con atribución, rotulado como **actividad del transporte público**, no «demanda Uber» ni pronóstico de viajes/ganancias. La calibración propiamente dicha exige resultados locales voluntarios del conductor —por zona y franja— y validación posterior.

## Fuentes e integración por tipo

### 1. Calendario nacional y días especiales

La página oficial de feriados es una **página manual**, no una API documentada. Al revisarla el 21-11-2026 muestra el calendario nacional 2026, los feriados trasladables, los días no laborables religiosos y días especiales por jurisdicción. La Resolución 164/2025 formaliza como no laborables con fines turísticos el 23 de marzo, 10 de julio y 7 de diciembre de 2026.[5] [6] La página incluye días de alcance local, por ejemplo feriado especial del 10 de noviembre en CABA y del 11 de noviembre en Provincia de Buenos Aires; por eso no basta un booleano «feriado nacional» para etiquetar zonas del AMBA.[5]

**Flujo sugerido:** mantener un calendario versionado por fecha y jurisdicción (nacional, CABA, provincia/municipio cuando la norma se verifique), con categorías separadas: feriado, día no laborable, fin de semana turístico, festividad religiosa y excepción local. La fuente pública revisada no publica endpoint, formato de descarga, autenticación, precio, cuota ni licencia específica para reutilización automatizada. La carga inicial debe ser revisión manual de la publicación/normativa oficial, no scraping presentado como API. Los cambios posteriores y los días religiosos con fecha lunar aproximada requieren actualización y validación.

### 2. Operaciones SUBE por hexágono y hora — mejor resolución espacial/horaria, licencia pendiente

La ficha de Transporte describe operaciones de un día hábil promedio en RMBA para colectivos, trenes, subtes y premetro, con soporte de grilla hexagonal; en el archivo 2025 observado hay registros `id_h3`, `hora`, `modo` y `cantidad_trx`.[1] [2] El recurso más reciente que aparece disponible en la ficha es un día tipo octubre de 2025, pese a que la ficha/dataset se modificó en abril de 2026. La distribución directa se ofrece como archivo CSV sin login aparente; también hay una capa geográfica H3 de resolución 8 publicada junto al dataset.[1] [2]

El valor de esta fuente es servir de prior espacial/horario de **actividad del transporte público**. Antes de compararla con las zonas DriverAI, hay que intersectar los hexágonos H3 con polígonos de zonas reales; los grupos Norte/Centro/Oeste/Sur del código no equivalen a polígonos ni deben tratarse como tales. La ficha dice que la licencia no está especificada y la actualización es eventual; el portal no publica precio o cuota para el archivo.[1] Solicitar autorización y atribución exacta antes de redistribuir, precargar o exponer sus valores en una app.

### 3. Serie diaria SUBE por fecha — datos abiertos con licencia explícita

El dataset «SUBE - Cantidad de transacciones (usos) por fecha» publica recursos anuales CSV. Su esquema incluye `dia_transporte`, empresa, línea, marca `amba`, tipo de transporte, jurisdicción, provincia/municipio, `cantidad` y `dato_preliminar`; la descripción aclara que la cantidad puede agrupar usos, check-in, check-out sin check-in o venta de boletos, netos de reversas. La ficha publica frecuencia diaria, licencia CC BY 4.0 y actualización de catálogo al 8 de abril de 2026.[3] El recurso 2026 está disponible como CSV directo.[4]

Se descargó y revisó la distribución: datos desde 2026-01-01 hasta 2026-11-18 al corte de esta investigación (última observación, tres días antes del 21-11-2026); la respuesta de descarga tenía unos 62,7 MB. Es una observación efectiva del archivo, no una garantía de que el catálogo se actualice diariamente. Registrar `observed_at`, `max(dia_transporte)` y fecha de cambio del catálogo, y alertar si dejan de avanzar o si aumentan mucho los preliminares.[3] [4]

**Flujo sugerido:** consumir el CSV anual; filtrar `AMBA=SI`; mantener día y agregaciones documentadas por modo, empresa/línea y jurisdicción; convertir fechas al calendario local argentino; contrastar día común, fin de semana y feriado. No atribuir cada fila de línea a un barrio si la geografía no está incluida. Tampoco convertir recuentos de transacciones, que pueden incluir varios tipos de operación, en personas únicas o viajes individuales de pasajeros.

### 4. API Transporte CABA / GTFS — servicio actual, no volumen de demanda

La documentación oficial de API Transporte define información estática y dinámica de colectivos, trenes y subtes **dentro de CABA**, e identifica datos de horarios, planificación de viaje, estado, interrupciones, predicciones de llegada/salida y ubicaciones.[7] [8] La documentación dice que se requiere registro para obtener token. La ficha de API Console publica base URI `https://apitransporte.buenosaires.gob.ar/` y anuncia GTFS dinámico/estático; la descripción de Plataforma Datos Transporte informa feeds Protocol Buffers y otros datos de estacionamiento, cortes, semáforos y eventos.[8] [9] La documentación de la API unificada también describe respuestas JSON/XML, consultas on-demand/tiempo real y actualizaciones regulares desde sistemas fuente.[10]

Hay cierta ambigüedad documental entre feeds GTFS en Protocol Buffers y la API unificada JSON/XML; la consola leída expone de forma visible una búsqueda de estacionamiento, no el inventario completo de recursos de transporte. No se pudo confirmar mediante una respuesta de feed una frecuencia real de actualización, cobertura de cada endpoint, endpoint estable para todas las familias ni límites de cuota. No inventar una ruta: antes de implementar, registrarse y validar el token, especificación vigente y feeds que efectivamente entrega.

GTFS Schedule aporta horarios y calendarios de servicio; GTFS Realtime describe posiciones de vehículos, demoras/actualizaciones de viaje y alertas de servicio.[11] [12] Pueden explicar oferta planificada, funcionamiento, interrupciones y fricción de acceso a nodos, pero no indican cuántos usuarios piden un auto ni la disponibilidad de conductores de apps. Los feeds de API son operativos/actuales; no se verificó un archivo histórico que permita reconstruirlos en el pasado.

**Acceso y uso:** el registro/token es requisito documentado; precio y cuota/rate limit no se encontraron publicados. Tampoco se encontró licencia abierta específica ni política pública de atribución para la API. No asumir que «API abierta» equivale a permiso de redistribución o caché indefinida; obtener términos y límites del responsable. La cobertura documentada es CABA, no todo AMBA, por lo que no llena Quilmes, Bernal, Lomas, Lanús, Avellaneda o San Isidro como datos live aunque existan otras fuentes agregadas para RMBA.[7] [8]

### 5. Turismo y alojamiento — señal estacional, baja resolución

El Observatorio Turístico de CABA publica una página de **informes mensuales** sobre indicadores del turismo nacional e internacional en la Ciudad; es una navegación manual hacia reportes, no una API documentada en lo consultado.[13] INDEC publica la Encuesta de Ocupación Hotelera (EOH) y cuadros estadísticos mensuales que incluyen CABA y la Región Buenos Aires, con indicadores de oferta y demanda hotelera.[14] Esos datos sirven para contexto de temporada, fines de semana y cambios mensuales, no para detectar demanda por hora o barrio. Ocupación, pernoctaciones y visitantes no equivalen a solicitudes de viajes ni informan los horarios exactos de salida/llegada de cada huésped.

En las páginas examinadas no se verificaron endpoints API para estas publicaciones. Deben tratarse como informes/tablas descargables manualmente, con su período de referencia y fecha de publicación registrados; no asumir tiempo real ni una granularidad municipal uniforme para toda el área metropolitana. La licencia/condiciones de redistribución para cada informe/cuadro no quedó verificada en esas páginas, así que confirmar antes de reproducir tablas o visualizaciones.

## Qué puede y no puede inferirse

- **Sí puede observarse:** calendario oficial de cada día; volumen de operaciones de transporte público por fecha y marcas AMBA; para una muestra promedio histórica, intensidad relativa de operaciones SUBE por hexágono, modo y hora; estado/horario/interrupciones de transporte CABA mientras el feed publicado esté accesible; indicadores turísticos mensuales.[1] [3] [5] [7] [14]
- **No se verificó abierto:** pedidos de Uber/Didi/Cabify, rechazos/cancelaciones de app, oferta concurrente de conductores, tarifa dinámica, tiempo de espera de la app, destinos reales de sus pasajeros, ni datos históricos de viajes por plataforma. Sin acceso autorizado a esos registros, ninguna de las fuentes revisadas mide directamente demanda privada de viajes.[1] [3] [7]
- SUBE es un sustituto imperfecto: mide actividad de una red y operación de boletos, no todos los desplazamientos, taxis/remises ni viajes por aplicación. Sus transacciones agregadas no se pueden equiparar automáticamente con pasajeros únicos, origen-destino o demanda insatisfecha. La serie diaria tampoco tiene granularidad horaria/zonal.[1] [3]
- Un promedio de día hábil no representa fin de semana, feriado, temporada, huelga, partido, recital, clima extraordinario ni cambios estructurales. El recurso más reciente es día tipo octubre de 2025, no un pronóstico para hoy.[1]
- Los feeds GTFS muestran oferta programada u operación de transporte público; más servicio no demuestra más demanda de autos. Posiciones y tiempos de llegada pueden volverse obsoletos o estar incompletos, y no equivalen a tráfico vial total.[8] [11] [12]
- Informes mensuales turísticos no permiten asignar demanda a cada una de las 14 zonas actuales ni anticipar horas punta con precisión. No asumir que la muestra hotelera representa todo el AMBA.[13] [14]
- Los festivos deben modelarse por jurisdicción y clase; un feriado mueve patrones, no necesariamente reduce o aumenta viajes de forma uniforme. Días religiosos pueden ser aproximados y eventos extraordinarios pueden tener alcance local.[5] [6]
- El briefing debe mostrar origen, fecha/ventana de vigencia, ámbito espacial y tipo de señal; distinguir medición pública de inferencia. Recoger de forma voluntaria y minimizada actividad del conductor por zona/franja para evaluar sesgo. Decir «patrón histórico de actividad del transporte público» o «señales contextuales de movilidad», no prometer viajes, pedidos, ingresos ni ganancias.

## Fuentes

[1]: https://datos.transporte.gob.ar/dataset/operaciones-sube-hexagonos "Operaciones de un día hábil promedio agregados en hexágonos por modo y hora"
[2]: https://datos.transporte.gob.ar/dataset/operaciones-sube-hexagonos/archivo/160a3ece-ccbd-4719-8406-8f28131a25e8 "CSV de operaciones SUBE por hora y hexágono"
[3]: https://datos.transporte.gob.ar/dataset/sube-cantidad-de-transacciones-usos-por-fecha "SUBE - Cantidad de transacciones por fecha"
[4]: https://datos.transporte.gob.ar/dataset/sube-cantidad-de-transacciones-usos-por-fecha/archivo/cccb523a-c752-420c-9021-899f215459d4 "Recurso de transacciones SUBE por día en 2026"
[5]: https://www.argentina.gob.ar/feriados "Feriados nacionales de la República Argentina"
[6]: https://www.argentina.gob.ar/normativa/nacional/norma-421799 "Resolución 164/2025: días no laborables con fines turísticos"
[7]: https://buenosaires.gob.ar/gcaba_historico/infraestructura/movilidad/api-transporte "API Transporte de Buenos Aires"
[8]: https://apitransporte.buenosaires.gob.ar/console/ "API Transporte — Datos Abiertos, consola"
[9]: https://anypoint.mulesoft.com/exchange/portals/api-gcba/083c0681-d272-40f7-b1ff-90dd5952a0de/api-plataforma-datos-transporte/ "Plataforma Datos Transporte API"
[10]: https://api-transporte.buenosaires.gob.ar/ "API unificada de Transporte de Buenos Aires"
[11]: https://gtfs.org/documentation/schedule/reference/ "GTFS Schedule Reference"
[12]: https://gtfs.org/documentation/realtime/reference/ "GTFS Realtime Reference"
[13]: https://turismo.buenosaires.gob.ar/es/observatorio/informes-mensuales "Informes mensuales del Observatorio Turístico de Buenos Aires"
[14]: https://www.indec.gob.ar/indec/web/Nivel4-Tema-3-13-56 "Encuesta de Ocupación Hotelera — INDEC"
