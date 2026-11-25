# Eventos, deportes y recintos en AMBA

**Conclusión para el producto:** no encontré un feed oficial único, público y verificable que cubra en tiempo real conciertos, partidos y eventos de recintos en todo AMBA. Para un primer alcance explicable, usaría las publicaciones oficiales de Buenos Aires Ciudad como señal editorial curada y el catálogo abierto de Buenos Aires Data como complemento geográfico/estructurado; los horarios de partidos y recintos se completarían desde sus propios sitios y se mantendrían como alertas de contexto, no como pronóstico de demanda. La mejor vía de consulta automatizable de terceros es Ticketmaster Discovery API, pero su cobertura efectiva en Argentina y la autorización para una aplicación comercial deben confirmarse antes de integrarla. [1] [2] [3] [4]

## Fuentes evaluadas

### Buenos Aires Data y Data Cultura: APIs de catálogo y archivos descargables, no un calendario unificado

Buenos Aires Data publica conjuntos abiertos y una API CKAN pública de catálogo. Verifiqué la consulta `https://data.buenosaires.gob.ar/api/3/action/package_search` y el endpoint `package_show`; ambos devuelven metadatos en JSON sin credenciales en las páginas consultadas. El patrón permite descubrir datasets y sus URLs de recursos, pero no convierte automáticamente los archivos en una API de búsqueda de eventos: hay que descargar y procesar cada recurso. La ficha de Danza Contemporánea señala que el dataset fue pasado a frecuencia histórica porque sus programas ya no están vigentes. Su recurso de eventos incluye título, fecha, hora de inicio y fin, lugar, y `UPDATED_TS`, y se ofrece en CSV, pero corresponde a una edición histórica, no a agenda futura. [1] [2] [3] [4] [8]

Data Cultura enumera datasets de actividades, eventos y lugares por disciplina. La página sirve como índice para descarga; no documenta un endpoint de agenda operativa ni un feed en vivo. En las fichas revisadas, la licencia es CC-BY-2.5-AR, que permite compartir y adaptar, incluso con fines comerciales, siempre que se atribuya adecuadamente, se enlace la licencia y se indiquen cambios, sin sugerir aval del Gobierno. Conviene conservar para cada registro la URL de origen y la licencia del recurso concreto, ya que las condiciones pueden diferir entre conjuntos. [3] [4] [5]

**Aplicación a DriverAI:** sí para lugares, datos de referencia y quizá horarios recurrentes de programas deportivos; no es fuente suficiente para saber qué espectáculo puntual comienza hoy ni para medir el flujo de personas que generará. El catálogo API es útil como mecanismo de descubrimiento e ingestión periódica, y los archivos deben versionarse con la fecha de descarga y `metadata_modified` para identificar cambios. [1] [2] [4]

### Dataset de Programas Deportivos de GCBA: reutilizable y georreferenciado, pero no fixtures

La ficha oficial ofrece CSV, XLSX, SHP y GeoJSON. Sus campos documentados incluyen latitud y longitud WGS84, sede, actividad, `horario`, dirección, barrio, comuna y contactos. La metadata declara actualización trimestral; al verificarla el 21 de noviembre de 2026, el `package_show` indicaba actualización de metadatos el 13 de noviembre de 2026, mientras la página mostraba modificaciones de recursos recientes. Tiene licencia CC-BY-2.5-AR. [6] [7]

Es una fuente práctica para localizar clases, actividades recreativas y sedes deportivas de la Ciudad. No se debe interpretar como fixture o calendario de partidos: el horario describe programas regulares, no horarios de salida de equipos, puertas de estadios ni cierres viales. Su cobertura explícita es CABA, no los municipios del conurbano; para AMBA se requieren fuentes separadas de partidos, clubes y recintos metropolitanos. [6] [7]

### Buenos Aires Ciudad / Turismo: agenda pública editorial sin API visible

El sitio oficial de Turismo mantiene una página de eventos con entradas y ventanas de fechas. En la consulta del 21 de noviembre de 2026 se veían, entre otros, República Folklore (21 y 22 de noviembre), Primavera Sound (28 y 29 de noviembre) y eventos pasados marcados como finalizados. Esto demuestra una cartelera editorial activa y útil como señal de próximos eventos, aunque no encontré allí documentación de API, descarga, feed RSS/JSON ni un compromiso de frecuencia de actualización. Las entradas no siempre exponen en la página de listado hora de inicio, dirección normalizada o coordenadas; hay que abrir cada detalle y completar datos faltantes. [9]

La sección de Deportes de Buenos Aires Ciudad publicó el 20 de noviembre de 2026 una noticia de Copa Potreritos con cronograma explícito: cuartos y semifinales desde las 9:30 del 22 de noviembre, y final a las 12:00 del 23 de noviembre en Ciudad Universitaria. Es evidencia de que las páginas/noticias oficiales pueden contener señales recientes y horarios puntuales, pero el mecanismo observado es una página web/noticia, no una API documentada. La fecha de publicación y el cronograma deben extraerse por separado; no asumir que una página de noticias equivale a calendario exhaustivo. [10] [11]

El sitio denominado Agenda Cultural Buenos Aires Ciudad solo mostró una pantalla de inicio de sesión al abrirse. No pude verificar acceso anónimo a su agenda, endpoint o exportación, así que no lo trataría como fuente integrable hasta obtener documentación o autorización institucional. Data Cultura y esta agenda son superficies distintas y no deben confundirse. [3] [12]

### Calendarios oficiales de recintos y clubes: páginas manuales con valor local, cobertura fragmentada

Movistar Arena publica su cartelera en `/shows`, pero la página devolvió un error de conexión cuando intenté abrirla directamente; por ello no puedo certificar desde contenido leído la cartelera vigente, campos, endpoint o actualización. Los resultados de búsqueda sugerían fechas de noviembre de 2026, pero no se usan aquí como prueba. No verifiqué API, feed público, términos de scraping ni coordenadas embebidas. Para integración, consultar al recinto o su ticketera y enlazar la ficha oficial; no depender de scraping frágil sin permiso. [13]

River Plate mantiene un calendario oficial de partidos que presenta rival, torneo, fecha, hora y estadio. En la versión accesible que abrí, los registros visibles eran de octubre de 2026 y algunos partidos en el Monumental; no pude confirmar una lista completa de próximos fixtures al 21 de noviembre ni una API/ICS/RSS. Es una página manual con buena señal para partidos del club, no una fuente completa de eventos del estadio: recitales y otros organizadores pueden publicarse en otros canales. La geolocalización del recinto puede resolverse como entidad estable por separado, pero no inferirla de cada evento si la fuente no la da. [14] [15]

### Ticketmaster Discovery API: alternativa programática, cobertura argentina a probar y autorización comercial a revisar

La Discovery API ofrece `GET https://app.ticketmaster.com/discovery/v2/events.json` y recursos de eventos, artistas y recintos; acepta filtros geográficos y temporales. La respuesta documentada contempla dirección, ciudad, país y coordenadas de lugar/recinto. La autenticación requiere `apikey` como parámetro de consulta. La documentación enumera `AR` entre los códigos de país aceptados por la API, por lo que Argentina es filtrable en principio. Sin embargo, no pude probar una respuesta de eventos en Buenos Aires porque no dispongo de una clave de desarrollador ni existe prueba de cobertura local en la documentación: no asumir catálogo completo para AMBA. [16]

La cuota publicada para Discovery es 5.000 llamadas por día y 5 solicitudes por segundo; se informa que pueden solicitarse aumentos caso por caso, sujeto a conformidad con términos, marca y representación de los datos. Es un endpoint de consultas y debe usarse desde un backend para proteger la clave; no incrustar la clave privada en el APK. [17]

La página de términos de Ticketmaster concede una licencia no exclusiva, intransferible y no sublicenciable, permite caché/almacenamiento solo por períodos razonables para prestar el servicio, exige quitar contenido dentro de 24 horas si el titular lo pide y prohíbe, entre otras cosas, vender, arrendar o sublicenciar la API o derivar ingresos directos por su uso salvo lo previsto en los términos. Dado que DriverAI es un producto comercial, esto requiere revisión de elegibilidad, branding y posible acuerdo/afiliación antes de mostrar o monetizar Event Content. Los términos leídos no especifican una regla general de atribución breve para todos los campos; sí protegen marcas y propiedad intelectual. No considerar la cuota técnica como autorización comercial. [18]

Ticketmaster ofrece además una Discovery Feed en archivos JSON/CSV por país. Su propia documentación enumera países admitidos para el feed y **Argentina no figura en esa lista**, aunque `AR` sí aparece en la lista de códigos de país de la API Discovery. Por lo tanto, no elegir el feed como mecanismo para AMBA sin confirmar incorporación del país. La API de búsqueda y el feed por país tienen coberturas diferentes. [16] [19]

### Eventbrite API: API documentada, pero no verificada como directorio público local

Eventbrite documenta API REST, OAuth 2.0 y respuestas JSON; la documentación de inicio indica que se puede pedir una API key gratuita con una cuenta. La API sirve para integrar eventos de organizadores/recintos y gestionar o recuperar recursos; la página consultada describe webhooks para recibir cambios en integraciones. No encontré evidencia oficial suficiente de un endpoint de descubrimiento anónimo que cubra todos los eventos públicos de terceros en CABA/AMBA ni de una cobertura geográfica exhaustiva. Por eso no se recomienda como índice general sin prueba de acceso a inventario ajeno y evaluación contractual. [20] [21]

Los términos de API de Eventbrite permiten distribuir una integración dentro de una aplicación, pero prohíben redistribuir la API o su contenido por separado, guardarlo indefinidamente y usar el contenido para ganancias directas comerciales; la licencia sí contempla ganancias indirectas asociadas a la aplicación. Si DriverAI muestra una ficha de evento, debe mostrar título y vínculo directo rastreable a la página del evento. Los términos limitan a 1.000 llamadas por hora por token OAuth, mientras la documentación de API consultada conserva una referencia de 2.000 por hora; ante la discrepancia, tratar 1.000/h como máximo conservador y confirmar al registrar la app. [21] [22]

## Propuesta de integración inicial

1. **Señal editorial curada de eventos puntuales:** monitorear páginas oficiales de Buenos Aires Ciudad/Turismo y las páginas/noticias oficiales de recintos y clubes cubiertos, registrando título, fecha, hora local si está publicada, venue, dirección, fuente, URL, hora de verificación y nivel de confianza. En esta fase son páginas manuales, no APIs; revisar cambios y bajas con periodicidad explícita y enlaces a la fuente. No se pudo verificar permiso general de scraping para estas páginas; preferir permiso o mecanismo institucional y no republicar textos, imágenes o marcas innecesarias. [9] [10] [13] [14]
2. **Datos reutilizables para capa base:** consumir CKAN `package_search`/`package_show` para localizar recursos; descargar archivos CSV/GeoJSON/XLSX de Programas Deportivos y campos geográficos; atribuir bajo CC-BY-2.5-AR. Evitar presentar programas permanentes como eventos puntuales. [1] [2] [6] [7]
3. **Piloto de cobertura comercial:** solicitar API key de Ticketmaster, llamar desde backend a `/discovery/v2/events.json?countryCode=AR&...`, reducir por fechas y bounding box AMBA, y contrastar resultados con carteleras oficiales durante varias semanas. Normalizar coordenadas y hora local, mantener URL del evento y fecha de última verificación, respetar límites, revisar licencia con legal y confirmar la cobertura real antes de mostrar el feed a conductores. La lista `AR` permite la consulta, pero no demuestra que aparezcan todos los eventos locales. [16] [17] [18]
4. **Salida del conductor:** describirlo como “evento programado cerca de esta zona” y, si corresponde, indicar una ventana de horario reportada por la fuente. No afirmar que habrá viajes, pasajeros, tarifa dinámica ni ganancias; el calendario no mide demanda ni disponibilidad de pedidos, y horarios/cambios del evento no equivalen a una predicción operacional. Separar además “inicio del evento”, “apertura de puertas” y “horario de partido” cuando la fuente no los distingue.

## Limitaciones verificadas

- No se confirmó ningún feed público único de agenda cultural o de recintos que cubra CABA y conurbano con horarios, coordenadas y cambios en tiempo real.
- El catálogo CKAN está accesible, pero varios datasets culturales hallados son históricos; no equivalen a cartelera vigente.
- La ficha de Programas Deportivos está georreferenciada y actualizada trimestralmente; representa programas/sedes de CABA, no partidos, conciertos ni todo AMBA.
- La agenda Cultural requirió inicio de sesión en la consulta realizada; no se verificó su API.
- Las páginas de Turismo, Deportes, River y los recintos son páginas manuales cuya estructura y disponibilidad pueden cambiar. En Movistar Arena no se pudo leer el calendario directo.
- Ticketmaster documenta filtro `AR`, pero la cobertura efectiva de eventos en AMBA no quedó probada; su feed de archivos no incluye AR entre países publicados.
- Eventbrite no quedó verificado como buscador abierto y exhaustivo de eventos AMBA; los términos limitan almacenamiento, distribución y monetización directa.
- La geocodificación externa y cualquier inferencia de concentración de viajes requieren fuentes y permisos separados; no se incorporan ni se prometen predicciones de viajes o ingresos.

## Referencias

[1]: https://buenosaires.gob.ar/innovacionytransformaciondigital/datos-abiertos-de-buenos-aires "Buenos Aires Data: datos abiertos de Buenos Aires"
[2]: https://data.buenosaires.gob.ar/api/3/action/package_search "API CKAN package_search de Buenos Aires Data"
[3]: https://buenosaires.gob.ar/gcaba_historico/cultura/datacultura/accede-datos-y-estadisticas-de-la-actividad-cultural-en-la-ciudad "Data Cultura: bases de datos y estadísticas culturales"
[4]: https://data.buenosaires.gob.ar/api/3/action/package_show?id=buenos-aires-danza-contemporanea "Metadata CKAN de Buenos Aires Danza Contemporánea"
[5]: https://creativecommons.org/licenses/by/2.5/ar/ "Creative Commons Atribución 2.5 Argentina"
[6]: https://data.buenosaires.gob.ar/dataset/programas-deportivos "Dataset oficial de Programas Deportivos"
[7]: https://data.buenosaires.gob.ar/api/3/action/package_show?id=programas-deportivos "Metadata CKAN de Programas Deportivos"
[8]: https://data.buenosaires.gob.ar/dataset/buenos-aires-danza-contemporanea/resource/juqdkmgo-274-resource "Recurso de eventos de Buenos Aires Danza Contemporánea"
[9]: https://turismo.buenosaires.gob.ar/es/eventos "Eventos: sitio oficial de turismo de Buenos Aires"
[10]: https://buenosaires.gob.ar/deportes "Deportes: Buenos Aires Ciudad"
[11]: https://buenosaires.gob.ar/noticias/se-viene-la-copa-potreritos "Cronograma oficial de Copa Potreritos"
[12]: https://agendaculturalba.org/ "Agenda Cultural Buenos Aires Ciudad"
[13]: https://www.movistararena.com.ar/shows "Shows: Movistar Arena"
[14]: https://www.riverplate.com/calendario-de-partidos "Calendario de partidos: River Plate"
[15]: https://www.riverplate.com/fixtures "Fixtures: River Plate"
[16]: https://developer.ticketmaster.com/products-and-docs/apis/discovery-api/v2/ "Ticketmaster Discovery API v2"
[17]: https://developer.ticketmaster.com/products-and-docs/apis/getting-started/ "Ticketmaster Developer Portal: getting started y rate limits"
[18]: https://developer.ticketmaster.com/support/terms-of-use/partner/ "Ticketmaster API Partner Terms of Use"
[19]: https://developer.ticketmaster.com/products-and-docs/apis/discovery-feed/ "Ticketmaster Discovery Feed 2.0"
[20]: https://www.eventbrite.com/platform/new/api "Eventbrite API: documentación y autenticación"
[21]: https://www.eventbrite.com/help/en-us/articles/833731/eventbrite-api-terms-of-use/ "Eventbrite API Terms of Use"
[22]: https://www.eventbrite.com/platform/docs "Eventbrite: Platform API documentation"
