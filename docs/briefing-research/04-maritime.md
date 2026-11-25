# Arribos de barcos y cruceros — Puerto Buenos Aires

**Corte de investigación: 21 de noviembre de 2026.** Para DriverAI conviene tratar la agenda publicada de cruceros como un aviso de actividad planificada y la señal AIS como una observación de movimiento de buques. No son intercambiables: la agenda no confirma que un barco haya arribado realmente, y una posición AIS no equivale a un desembarque ni a una cantidad de pasajeros. Ninguna fuente revisada permite prometer viajes, demanda de pasajeros, ingresos o ganancias a un conductor.

## Fuente oficial para agenda de cruceros

La Administración General de Puertos (AGP) mantiene una página oficial con informes por temporada. Al corte consultado publica la temporada **2026–2027** y enlaza un PDF identificado como actualizado el **22/09/2026**. El documento lleva encabezado «PTO. BUENOS AIRES», presenta fechas y horas de entrada y salida, procedencia, destino, agencia marítima y campos de movimiento de pasajeros, y consigna **101 buques crucero** en el total de temporada. Por tanto, es un calendario/aviso portuario útil para alimentar un piloto de avisos; la hora consignada es programada, no una confirmación de llegada observada. [1] [2]

La actualización visible del PDF tenía unos 60 días al corte de esta investigación. La página de AGP no publica en lo inspeccionado un API, esquema de datos, formato estructurado o frecuencia automática de actualización; tampoco se pudo confirmar una notificación por cambios. El archivo es un PDF que requiere descarga y extracción/ingesta manual o una tarea interna controlada. La página separada de «Reservas de Cruceros» de ePuertos dice que sirve para seguir cruceros que ingresan o egresan, pero la interfaz no quedó visible en la inspección y no se encontró documentación pública de API/descarga o contrato de datos. No tomar esa aplicación como endpoint abierto ni automatizar scraping sin autorización. [3]

La página general de AGP describe el movimiento estacional típico —más de 400.000 pasajeros y más de 100 cruceros en promedio de los últimos diez años, excluida la temporada 2020/2021—. Es contexto histórico institucional, no pronóstico de pasajeros para un día o escala concretos. [4]

## AIS: condición actual, no agenda

Prefectura Naval Argentina (PNA) describe su Servidor Nacional AIS como un servicio de tráfico marítimo y fluvial en tiempo real, derivado de AIS en estaciones costeras de centros de control. La vista disponible ofrece información reducida de libre acceso con sesión limitada; para información completa y sesiones más largas pide registro exclusivo a personas físicas con DNI vinculadas a actividades marítimas, fluviales o portuarias. El mapa digital exige CAPTCHA antes de acceder. En la revisión no se halló una API documentada, dataset descargable, clave para desarrolladores ni licencia explícita que autorice republicación dentro de DriverAI. Es la fuente primaria local que merece consulta para validar una señal, pero su página web no debe confundirse con un feed de integración autorizado. [5] [6]

AIS puede ser incompleto: PNA explica que la obligación de portar equipo aplica a categorías específicas de buques (por ejemplo, buques de pasajeros cualquiera sea su tamaño, bajo las reglas reseñadas en su página), y también contempla que la información de navegación pueda protegerse. Por eso una ausencia en una vista AIS no debe presentarse como ausencia confirmada de buques o pasajeros. [5]

## Alternativas de integración

### AISHub — API documentada, condicionada a aportar una estación

AISHub ofrece un servicio web con posiciones y campos de viaje en **JSON, XML o CSV**, filtros geográficos por latitud/longitud y campos como hora de mensaje, posición, velocidad, destino y ETA. La URL documentada es `https://data.aishub.net/ws.php` con parámetros `username`, `format`, `output`, `latmin`, `latmax`, `lonmin`, `lonmax` e `interval`; requiere un usuario otorgado tras afiliarse. La documentación pide no consultar más de una vez por minuto: consultas más frecuentes pueden devolver vacío. [7]

No es un API público anónimo y la posibilidad gratuita está condicionada a compartir un feed NMEA crudo de una estación propia. Para recibir la clave de acceso a datos combinados, la estación debe cumplir, en promedio de siete días, al menos 10 buques en cobertura, 90% de disponibilidad, no más de 60 segundos de submuestreo y 10 segundos de demora de mensajes. AISHub afirma que sus miembros pueden crear aplicaciones y productos propios, incluso comerciales, pero prohíbe retransmitir los datos AIS crudos. Exige atribución **«Data: AISHub contributors»** con enlace a AISHub. [8]

Es la ruta de bajo costo más clara para un prototipo si DriverAI o un colaborador ya dispone de una estación costera apropiada en AMBA y se logra la cobertura requerida. No verifiqué que hoy haya una estación AISHub con cobertura útil en la terminal de cruceros del Puerto Buenos Aires: la lista/estado de estaciones usa credenciales y la documentación no certifica cobertura local. No presupuestar disponibilidad AMBA antes de verificarla o instalar una estación autorizada.

### VesselFinder — API de port calls y arribos esperados, comercial

VesselFinder documenta los métodos `portcalls` (eventos de arribo/salida para puerto, buque o flota) y `expectedarrivals` (buques cuya señal AIS tiene como destino el puerto). La integración usa `https://api.vesselfinder.com/portcalls` y `https://api.vesselfinder.com/expectedarrivals`, con `userkey`; admite JSON por defecto y XML opcional. Para `portcalls`, se pide al menos IMO, MMSI o código UN/LOCODE, se puede filtrar por tipo de evento/intervalo y usar datos extra AIS/Voyage. Para `expectedarrivals`, se consulta un período futuro con `interval` o `fromdate`/`todate` y `locode`. La documentación cobra **1 crédito por registro de AIS, 1 por Voyage y 2 por PortCall** en `portcalls`; `expectedarrivals` cobra **5 créditos por registro AIS**, y Voyage/Master pueden añadir cargos publicados en la tabla del servicio. [9] [10]

La FAQ indica que las posiciones se refrescan cada pocos segundos, aunque cada barco transmite según su propio comportamiento; los límites de frecuencia para uso con suscripción dependen del contrato. Se anuncia prueba gratuita previa solicitud. No encontré precio público del crédito o tarifa de suscripción: el API key, precio y límites aplicables deben confirmarse con ventas. El proveedor cubre posiciones en tiempo real, datos de viaje, escalas e información de buques, pero no verifiqué cobertura/latencia específica en el Puerto Buenos Aires mediante una cuenta activa. [9] [11]

Sus términos permiten acceso API según el plan, pero restringen redistribución, sublicencia o puesta a disposición del contenido a terceros salvo autorización expresa; la muestra externa en una app solo está permitida si el plan lo autoriza y con atribución/branding definidos en la orden. DriverAI debe pactar expresamente la exhibición a usuarios y el uso de alertas derivadas, límites de retención y caché antes de producción. [12]

### MarineTraffic/Kpler — API comercial, contrato y términos restrictivos

MarineTraffic ofrece AIS vía API y flujo NMEA en vivo y documenta APIs de posiciones y eventos portuarios; el sitio describe más de 13.000 receptores globales, incluidos terrestres, oceánicos y satelitales. La API requiere credenciales/clave y la referencia OpenAPI está publicada. No se encontró precio, cuota de llamadas o cobertura garantizada para Buenos Aires en las páginas consultadas: deben pedirse al proveedor y verificarse en contrato. La cifra global de receptores no demuestra cobertura concreta en el estuario ni una frecuencia garantizada para una embarcación. [13] [14]

Los términos de Kpler/MarineTraffic prohíben scraping y, salvo acuerdo escrito, copiar, sublicenciar, revender, publicar o redistribuir datos; la explotación comercial requiere licencia, y la atribución no puede asignar los datos a otra entidad que Kpler. La página también indica que los servicios/datos se entregan «as is» y sin garantía de disponibilidad o exactitud. No usar una cuenta/página gratuita como sustituto de licencia de API para una función pública de DriverAI. [15]

## Recomendación para DriverAI

Para la primera versión, usar el **PDF estacional de AGP como agenda planificada**, cargada y revisada manualmente contra la versión oficial más reciente, con texto explícito del tipo «arribo programado según agenda de AGP; sujeto a cambios» y fecha de actualización de la fuente. Es la fuente primaria más directa, local y con referencia temporal verificable para cruceros; no requiere interpretar el movimiento AIS ni promete desembarques observados. Mantener el dato informativo y de baja confianza operacional, no como predicción de solicitudes de viaje.

Si el producto necesita detectar **arribos efectivos** o cambios de última hora, validar primero el mapa del Servidor Nacional AIS de PNA por vías permitidas y pedir a PNA/AGP autorización y condiciones de acceso para un feed. En paralelo, solicitar prueba y oferta de API a VesselFinder como alternativa comercial documentada, incluyendo la autorización contractual para mostrar resultados en una app de terceros. AISHub es una opción de prueba de costo monetario bajo solo si se puede aportar una estación receptora que cumpla los requisitos; su cobertura actual en Buenos Aires está sin verificar. No integrar mediante scraping el mapa de PNA, ePuertos o sitios de seguimiento de terceros.

## Referencias

[1]: https://www.argentina.gob.ar/economia/administracion-general-de-puertos-sau/puertos/buques/informes-de-cruceros-por-temporada "AGP: informes de cruceros por temporada"
[2]: https://www.argentina.gob.ar/sites/default/files/2018/08/listado_26-27_actualizado_22-9.pdf "AGP: buques en cruceros turísticos, temporada 2026/2027"
[3]: https://agp-ports.gob.ar/reservas "AGP ePuertos: reservas de cruceros"
[4]: https://www.argentina.gob.ar/administracion-general-de-puertos/puertos/buques/cruceros "AGP: información general de cruceros"
[5]: https://www.argentina.gob.ar/prefecturanaval/ais "Prefectura Naval Argentina: Servidor nacional AIS"
[6]: https://ais.prefecturanaval.gob.ar/ "Prefectura Naval Argentina: portal del Servidor Nacional AIS"
[7]: https://www.aishub.net/api "AISHub: documentación del API"
[8]: https://www.aishub.net/join-us "AISHub: requisitos de membresía y condiciones de uso"
[9]: https://api.vesselfinder.com/docs/portcalls.html "VesselFinder: documentación de PortCalls"
[10]: https://api.vesselfinder.com/docs/expectedarrivals.html "VesselFinder: documentación de ExpectedArrivals"
[11]: https://api.vesselfinder.com/docs/faq.html "VesselFinder: FAQ del API"
[12]: https://www.vesselfinder.com/terms "VesselFinder: términos de uso"
[13]: https://www.marinetraffic.com/en/ais-api-services "MarineTraffic: servicios API AIS"
[14]: https://servicedocs.marinetraffic.com/ "MarineTraffic: documentación AIS Data API"
[15]: https://www.marinetraffic.com/en/p/terms "Kpler/MarineTraffic: términos de uso"
