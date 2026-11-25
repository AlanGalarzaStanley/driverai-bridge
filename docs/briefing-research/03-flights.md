# Llegadas y salidas en AEP y EZE

**Corte de investigación: 21 de noviembre de 2026.** El objetivo es ofrecer a conductores AMBA contexto explicable de actividad aeroportuaria, no pronosticar que conseguirán un viaje ni cuánto ganarán.

## Recomendación

No encontré un feed/API aeroportuaria pública, documentada y con derechos de redistribución para una app de terceros. La fuente primaria es Aeropuertos Argentina (AA2000): su sitio permite consultar vuelos y su asistente ADA anuncia notificaciones en tiempo real para Ezeiza y Aeroparque. Pero la página no publica una especificación de API y sus condiciones reservan el copyright y prohíben reproducir o distribuir su contenido sin consentimiento expreso. Solicitaría a AA2000 un feed/licencia FIDS para ambos aeropuertos antes de integrar su información. La consulta web disponible sirve como referencia humana, no como autorización para extraer o redistribuir los datos. [1] [2] [3]

Si se necesita una fuente comercial mientras se negocia el acceso, **FlightAware AeroAPI Standard** es la candidata mejor documentada para un piloto de DriverAI dirigido a conductores individuales: publica endpoints separados para llegadas recientes y llegadas/salidas programadas, precios por endpoint, y su tabla permite distribución de derivados en productos business-to-consumer. La página pública confirma vuelos para AEP/SABE y EZE/SAEZ, pero clasifica ambos como *secondary service area*. Antes de contratar, pedir confirmación escrita de cobertura/calidad argentina, del uso concreto de DriverAI y de las condiciones de retención: el contrato estándar descargable y los términos generales de julio de 2026 no son plenamente coherentes entre sí. [9] [10] [11] [12] [13] [14]

ANAC es la alternativa oficial abierta y redistribuible para análisis retrospectivo, no para coordinar conductores en tiempo real. Su recurso CSV registra movimientos ya procesados y se publica mensualmente bajo CC BY 4.0; no incluye horas programadas/estimadas ni un campo de demora que permita determinar el retraso frente al horario publicado. [4] [5] [6] [8]

## Fuentes y evaluación

### Aeropuertos Argentina / AA2000: fuente primaria, página manual, sin API pública verificada

La página oficial «Buscá tu vuelo» ofrece selección de aeropuerto y consulta de arribos o partidas. En la visita de investigación no devolvió vuelos y no permitió observar una llegada real, una programada ni una hora de actualización. Esto es una limitación de la consulta observada, no prueba que no hubiera vuelos. El sitio sí presenta vuelos en su producto para pasajeros, y AA2000 describe ADA por WhatsApp como capaz de notificar en tiempo real el estado de arribos y partidas en Ezeiza y Aeroparque; la publicación no ofrece un contrato de API para esa función. [1] [3]

La página legal de AA2000 dice que la información procede de fuentes internas y externas, se actualiza conforme a lo que recibe de terceros y puede estar incompleta, equivocada o desfasada. También indica que el material está protegido y que el acceso al sitio no concede licencia de reproducción o distribución sin consentimiento previo y expreso. Por ello, no automatizaría consultas del tablero ni copiaría sus filas, gráficos o marcas sin autorización contractual. [2]

**Autenticación y coste:** el sitio consultable por una persona no requiere clave ni publica tarifa. No se encontró documentación de API pública, límites de llamadas, SLA ni precio de un feed FIDS. Que ADA ofrezca avisos por WhatsApp no acredita que exista una API accesible para DriverAI.

### ANAC / datos abiertos de Transporte: movimientos históricos mensuales

El portal oficial ofrece un recurso anual descargable en CSV. El último recurso 2026 que pude identificar se llama `202609-informe-ministerio-vf-matricula.csv`; sus metadatos indican frecuencia mensual y último cambio el 19 de octubre de 2026. El fichero descargado pesa aproximadamente 47 MB y usa columnas separadas por punto y coma, entre ellas `Fecha UTC`, `Hora UTC`, `Tipo de Movimiento`, `Aeropuerto`, `Origen / Destino`, `Aerolinea Nombre`, `Matrícula` y `Calidad dato`. La muestra contiene movimientos etiquetados `Aterrizaje` o `Despegue`, con registros `DEFINITIVO`; también contiene códigos `AER` y `EZE`, por lo que distingue Aeroparque y Ezeiza dentro del conjunto. [4] [5] [6]

El propio carácter de «aterrizajes y despegues procesados», la fecha de publicación mensual y el nombre del fichero de septiembre hacen que sirva para recuentos retrospectivos —por aeropuerto, movimiento, operador y origen/destino—, no para indicar la situación de una operación que está por ocurrir. No contiene un número de vuelo ni el par horario programado/real necesario para calcular una demora de pasajero; tampoco es un feed live. No pude verificar un archivo más reciente que septiembre en el recurso 2026 visto al corte.

El catálogo declara licencia **Creative Commons Attribution 4.0**. La licencia permite compartir y adaptar el material con atribución; al redistribuirlo hay que conservar la información de autoría/licencia suministrada, enlazar la licencia cuando sea razonable e indicar modificaciones. La licencia no implica respaldo de ANAC a DriverAI ni garantiza exactitud o disponibilidad. El recurso se descarga sin API key; el catálogo está servido por CKAN y su endpoint de metadatos es útil para descubrir el recurso mensual nuevo, pero el dato en sí es el archivo CSV, no una API de estados de vuelo. [5] [6] [7] [8]

**Uso recomendado:** analítica agregada para explicar estacionalidad o validar volúmenes históricos; no mostrarlo como horario del día ni inferir que un vuelo próximo generará demanda de viajes.

### FlightAware AeroAPI: API comercial con cobertura observada, pero secundaria en ambos aeropuertos

La página de precios publica `GET /airports/{id}/flights/arrivals` para vuelos que llegaron recientemente, además de `.../scheduled_arrivals` y `.../scheduled_departures` para operaciones futuras. En la lista de precios, cada uno cuesta **USD 0,005 por result set**; el endpoint general de vuelos del aeropuerto cuesta USD 0,020/result set. La API ofrece consultas por clave y respuesta de datos de estado; el portal de documentación oficial indicó en su índice que la clave va en `x-apikey`, aunque el acceso directo a esa documentación devolvió una pantalla de navegador no compatible en esta verificación, así que hay que confirmar el encabezado con el portal de cuenta antes de implementar. [9] [10]

La opción Personal tiene hasta USD 5 de crédito mensual de consultas y límite de 10 result sets/minuto, pero su uso/distribución se limita a propósitos personales o académicos: no es licencia para integrar DriverAI comercialmente. Standard tiene mínimo de **USD 100/mes**, tarifas por consulta y hasta **5 result sets/segundo**; la tabla permite distribución/uso comercial de derivados en B2C, pero no B2B. Premium tiene mínimo de USD 1.000/mes, 100 result sets/segundo y habilita distribución B2B. Las consultas a `arrivals`, `scheduled_arrivals` y `scheduled_departures` se cobran por result set, por lo que las llamadas repetidas por aeropuerto, ventana y paginación importan en el coste. [9]

**Términos/atribución:** la licencia Standard descargable de AeroAPI, versión visible noviembre de 2022, autoriza incorporar datos como datos embebidos o derivados a una aplicación B2C, pero no transferir o sublicenciar el acceso ni entregar el feed crudo como servicio; tampoco permite uso en pantallas comerciales de situación de aeronaves. Contempla atribución en ciertos usos académicos publicados, pero no establece en el texto consultado una regla general inequívoca de crédito para toda pantalla B2C. Por otro lado, los términos generales de FlightAware de julio de 2026 dicen que la retención de datos es de hasta 24 horas salvo autorización distinta en la orden; la licencia AeroAPI Standard enlazada desde precios dice máximo de 30 días para datos crudos. Obtener en la orden comercial vigente los derechos de presentación, atribución, almacenamiento/caché, usuarios permitidos y la clasificación B2C/B2B de DriverAI, en vez de resolver la discrepancia por cuenta propia. [13] [14]

**Cobertura/frescura observadas:** FlightAware publica páginas en vivo diferenciadas para EZE/SAEZ y AEP/SABE, con llegadas, partidas y vuelos en ruta o programados. Las dos páginas las identifican expresamente como *secondary service area*. En la consulta del 21 de noviembre, EZE mostraba “Last updated at 12:49AM EST” y AEP “12:45AM EST” —aproximadamente 02:49 y 02:45 ART, respectivamente—. Es el sello de actualización mostrado por las páginas públicas, no una garantía de latencia de la API ni una medida de que todos los estados individuales se actualizaran en ese momento. Los listados incluyen casos con horarios, un estado “Delayed” y campos que distinguen llegadas de operaciones programadas; no constituyen confirmación de completitud para cada aerolínea argentina. [11] [12]

La propia licencia prohíbe usar datos crudos más allá del plazo autorizado y limita el uso a lo indicado en la orden; los términos del sitio gratuito restringen sus productos web/API sin cargo a uso personal e impiden extracción automatizada de la web pública. Por tanto, la página pública es una referencia manual para contraste, no el origen de una integración ni un sustituto del acceso contratado. [13] [14] [15]

### Aviationstack: API REST/JSON global, términos de redistribución por cerrar

La documentación ofrece `/v1/flights` para datos reales o históricos, `/v1/flight_schedules` y `/v1/flightsFuture` para horarios futuros (estos últimos disponibles desde Basic). Las respuestas son JSON; las solicitudes son HTTP GET. Para AEP/EZE, la FAQ documenta filtros como `dep_iata` y `arr_iata`, que permitirían pedir llegadas por aeropuerto de destino y partidas por aeropuerto de salida. Se requiere una cuenta y la clave se pasa como parámetro `access_key` en la URL. Conviene hacer estas llamadas desde el servidor de DriverAI y no poner esa clave en el cliente Android. [16] [17] [20]

El plan gratuito incluye 100 solicitudes al mes y es **no comercial**. Basic se publica a **USD 49,99/mes por 10.000 solicitudes**, con uso comercial y consultas adicionales anunciadas a USD 0,019996 cada una; incluye datos en tiempo real, vuelos históricos y futuros/horarios. La página del proveedor dice que los estados globales pueden tener retrasos de 30–60 segundos y que FlightsFuture consulta horarios hasta 12 meses. Son afirmaciones del proveedor, no una medición independiente en AEP/EZE. La documentación pública no permitió confirmar un límite de llamadas por segundo ni una muestra real consultada para AEP y EZE. [18] [20]

La cobertura anunciada —10.000+ aeropuertos, 250+ países— hace plausible que incluya ambos códigos, pero no probé una respuesta autenticada y no hay evidencia revisada aquí de completitud por aerolínea o equivalencia con el tablero aeroportuario. La página de precios confirma «Commercial Use» en Basic, pero no encontré términos específicos suficientemente claros sobre sublicencia, redistribución de datos crudos/derivados, caché y atribución para una app de conductores. Los términos web generales de Aviationstack no sustituyen esas condiciones de licencia de datos. Tratarlo como **pendiente de validación comercial/legal** hasta recibir confirmación escrita para el uso B2C de DriverAI y confirmar muestras de `AEP` y `EZE`. [16] [18] [19]

## Integración y presentación prudentes

Para una primera prueba comercial, pediría cotización/permiso FIDS a AA2000 y, en paralelo, un piloto AeroAPI Standard limitado a los dos aeropuertos. Separaría explícitamente `real/reciente`, `estimado/en ruta` y `programado`; mostraría fuente y hora de actualización, y no convertiría la presencia de un arribo en una promesa de viajes, pasajeros disponibles, tarifa ni ganancia. Si la cobertura secundaria no pasa una prueba comparando una muestra de vuelos de AEP y EZE con los tableros de las aerolíneas/aeropuerto, degradar el mensaje a señal informativa o desactivarlo en vez de rellenar huecos con horarios de otro proveedor. ANAC puede respaldar contexto histórico, no completar huecos live.

## Referencias

[1]: https://www.aeropuertosargentina.com/es/vuelos "Aeropuertos Argentina, buscador de vuelos"
[2]: https://www.aeropuertosargentina.com/es/limitacion-de-responsabilidad "Aeropuertos Argentina, limitación de responsabilidad"
[3]: https://www.aeropuertosargentina.com/es/novedades/novedad?titulo=Conoce-a-ADA,-tu-asistente-virtual-en-el-aeropuerto?id=4 "Aeropuertos Argentina, asistente virtual ADA"
[4]: https://datos.transporte.gob.ar/dataset/aterrizajes-y-despegues-procesados-por-la-administracion-nacional-de-aviacion-civil-anac/archivo/7b6d0103-0a69-4c67-aed9-33cb58ae12f5 "ANAC, recurso de aterrizajes y despegues 2026"
[5]: https://datos.gob.ar/km/dataset/transporte-aterrizajes-despegues-procesados-por-administracion-nacional-aviacion-civil-anac "Datos Abiertos, catálogo del dataset ANAC"
[6]: https://datos.transporte.gob.ar/dataset/21038a1a-c3c7-4494-b76a-3a2a8fbb83b5/resource/7b6d0103-0a69-4c67-aed9-33cb58ae12f5/download/202609-informe-ministerio-vf-matricula.csv "ANAC, descarga CSV septiembre de 2026"
[7]: https://datos.transporte.gob.ar/api/3/action/package_show?id=aterrizajes-y-despegues-procesados-por-la-administracion-nacional-de-aviacion-civil-anac "CKAN API, metadatos del dataset ANAC"
[8]: https://creativecommons.org/licenses/by/4.0/legalcode "Creative Commons Attribution 4.0 International"
[9]: https://www.flightaware.com/commercial/aeroapi/ "FlightAware AeroAPI, funciones y precios"
[10]: https://www.flightaware.com/aeroapi/portal/documentation "FlightAware AeroAPI, portal de documentación"
[11]: https://es.flightaware.com/live/airport/SAEZ "FlightAware, vuelos de Ezeiza EZE"
[12]: https://es.flightaware.com/live/airport/SABE "FlightAware, vuelos de Aeroparque AEP"
[13]: https://www.flightaware.com/commercial/aeroapi/AeroAPI_Standard_License.pdf "FlightAware AeroAPI Standard License Agreement"
[14]: https://www.flightaware.com/commercial/flightaware-terms-conditions-Jul2026.pdf "FlightAware, términos y condiciones de julio de 2026"
[15]: https://www.flightaware.com/about/terms-of-use "FlightAware, términos de uso del sitio y productos gratuitos"
[16]: https://docs.apilayer.com/aviationstack/docs/api-documentation "Aviationstack, documentación de API"
[17]: https://docs.apilayer.com/aviationstack/docs/getting-started "Aviationstack, autenticación y acceso"
[18]: https://aviationstack.com/pricing "Aviationstack, planes y precios"
[19]: https://aviationstack.com/terms "Aviationstack, términos web"
[20]: https://aviationstack.com/faq "Aviationstack, preguntas frecuentes"
