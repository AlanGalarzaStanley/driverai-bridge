# Clima y alertas meteorológicas para AMBA

**Corte de verificación: 21 de noviembre de 2026.** Para el pronóstico de rutina sugiero integrar Open-Meteo mediante su endpoint comercial, manteniendo los avisos de seguridad vinculados a la página oficial del Sistema de Alerta Temprana (SAT) del SMN. Open-Meteo ofrece un JSON por coordenada que se pudo consultar para CABA y aclara que su servicio gratuito no puede usarse comercialmente. No recomiendo depender en producción del ZIP abierto del SMN como feed de alertas hasta confirmar una licencia de redistribución y que los recursos estén entregando registros útiles: el catálogo deja la licencia sin especificar y las descargas verificadas devolvieron archivos de texto vacíos.[6] [7] [11] [15]

## SMN: fuente primaria, pero automatización limitada

El SAT es la fuente primaria argentina para advertencias, alertas y avisos a muy corto plazo. La página pública del SMN es una interfaz de consulta con leyenda de niveles —amarillo, naranja y rojo—, no una API de alertas documentada para integradores.[1] [2] El manual institucional del SAT indica que las alertas rutinarias se actualizan a las 06:00 y 18:00, con actualizaciones extraordinarias cuando la situación lo requiere; los avisos a muy corto plazo (ACP) pueden aparecer o actualizarse en cualquier momento. Los ACP dependen de que haya información de radar para la zona.[8] El SMN describe alertas y advertencias con anticipación habitual de 24, 48 o 72 horas, frente a ACP para fenómenos que pueden ocurrir en menos de tres horas.[2]

Sí existe un canal oficial de descarga asociado al catálogo argentino, pero es un recurso ZIP, no una API JSON documentada. Para pronóstico, la ficha “Pronóstico del Tiempo a 5 días” enlaza `https://ssl.smn.gob.ar/dpd/zipopendata.php?dato=pron5d`; declara pronóstico por estación, con temperatura, viento y precipitación, y periodicidad nominal diaria. Para alertas, la ficha “Alertas Meteorológicas (365 días)” enlaza `https://ssl.smn.gob.ar/dpd/zipopendata.php?dato=alertas` y describe campos como hora, zona, fenómeno y situación.[4] [5] [6] [7]

**Frescura y salud del feed observadas:** al consultar ambos recursos directamente el 21 de noviembre de 2026, las respuestas HTTP fueron 200 y el pronóstico generó un ZIP con nombre fechado `pronostico_5dias20261121.txt`, pero el único archivo interno tenía cero bytes; el ZIP de alertas también contenía un TXT de cero bytes. Es una observación de una consulta puntual, no prueba de indisponibilidad sostenida, pero impide tratar esos recursos como feed operativo confiable sin validación y monitoreo propios. En metadatos, la ficha de pronóstico muestra recurrencia diaria y una fecha `modified` previa, además de un rango temporal 1993–2013 que no parece describir ese recurso vigente; la ficha de alertas figura con metadatos modificados en 2021, recurrencia diaria y una nota de cobertura de los últimos 365 días.[4] [5] [6] [7]

El acceso a los ZIP observados no requirió credenciales. Eso no equivale a una especificación de API, disponibilidad garantizada ni autorización clara para redistribuir datos dentro de una app comercial. En las fichas CKAN consultadas la licencia aparece como “No se especificó la licencia” y `isopen` es falso. Por prudencia, atribuir al SMN, enlazar a su SAT y pedir confirmación escrita sobre reutilización/redistribución comercial antes de automatizar la republicación de sus alertas. La descarga manual o visualización pública del SAT sigue siendo útil para que el producto remita a la advertencia oficial.[1] [6] [7]

La cobertura de la información del SMN es nacional y las alertas se expresan por zonas o municipios; el feed declara el campo de zona. La página pública comprobada ofrece el mapa SAT. No pude verificar en la respuesta extraída una consulta API oficial estable que permita resolver un polígono/municipio de AMBA contra una alerta activa, ni una cadencia de actualización para una API automatizable distinta de la guía de horarios del SAT.[1] [7] [8]

## Open-Meteo: opción sugerida para pronóstico consumible

El endpoint documentado es `https://api.open-meteo.com/v1/forecast`, que recibe `latitude` y `longitude` y devuelve JSON. Acepta variables actuales, horarias y diarias, zona horaria IANA y hasta 16 días de pronóstico. La documentación explica que selecciona y combina modelos meteorológicos por ubicación; la frecuencia y resolución dependen del modelo elegido, desde escalas locales hasta modelos globales. Su API de pronóstico es global. Para validar cobertura real en la zona, hice una consulta en CABA el 21 de noviembre de 2026: respondió JSON con una celda de grilla próxima a la coordenada indicada, hora local `2026-11-21T02:30`, variables actuales y pronóstico horario. En esa respuesta `current.interval` fue 900 segundos; esto describe el intervalo informado en ese resultado, no una garantía de que todas las variables o modelos se actualicen cada 15 minutos.[9] [15]

Ejemplo consultado para CABA —ajustar las coordenadas a la ubicación o celda de interés—:

```text
https://api.open-meteo.com/v1/forecast?latitude=-34.6037&longitude=-58.3816&current=temperature_2m,precipitation,wind_speed_10m&hourly=precipitation_probability,rain,wind_gusts_10m&forecast_days=2&timezone=America%2FSao_Paulo
```

La API gratuita es solo para uso no comercial: los términos incluyen expresamente apps comerciales como caso que requiere suscripción. El plan comercial utiliza `https://customer-api.open-meteo.com/v1/forecast` con `apikey`; el endpoint y los parámetros son los mismos. Los límites publicados para el acceso gratuito son 600 solicitudes por minuto, 5.000 por hora y 10.000 por día (la tabla también indica 300.000 al mes). La página lista presupuestos comerciales de 1 millón de llamadas/mes en Standard y 5 millones en Professional, y más de 50 millones en Enterprise; no pude confirmar un precio monetario por plan en la tabla renderizada que consulté. Para clima histórico, la Historical Weather API ofrece reanálisis desde 1940 según conjunto; el archivo de pronósticos pasados es otro producto, con cobertura desde 2021/2022 según modelo. La página de planes indica que las APIs históricas requieren Professional o superior.[10] [11] [12] [13]

Los datos de la API se ofrecen bajo CC BY 4.0, que permite uso y redistribución comercial con crédito apropiado, enlace a la licencia e indicación de cambios. La guía solicita además un enlace visible junto a los datos mostrados, por ejemplo “Weather data by Open-Meteo.com”. Una suscripción comercial habilita su servicio/API key, pero no reemplaza ese crédito.[10] [12]

Open-Meteo es la alternativa que sugiero para temperatura, precipitación/probabilidad de lluvia, ráfagas y evolución horaria en AMBA: endpoint reproducible, respuesta JSON comprobada para CABA, historial documentado y condiciones comerciales explícitas. No ofrece por sí sola la autoridad de alertas oficiales del SMN; el pronóstico numérico no debe presentarse como una alerta del SMN ni convertirse en garantía de seguridad vial.[9] [10] [12]

## WeatherAPI.com: alternativa con alertas integradas, sujeta a validar Argentina

WeatherAPI documenta endpoints JSON/XML autenticados por clave. La forma de consulta de pronóstico es `https://api.weatherapi.com/v1/forecast.json?key=API_KEY&q=LAT,LON&days=N&alerts=yes`; también documenta pronóstico actual, histórico y un objeto de alertas dentro del forecast. Su página de alertas dice que las alertas son avisos gubernamentales retransmitidos a partir de feeds CAP, incluyendo el Severe Weather Information Centre de la OMM; describe cobertura de Estados Unidos, Reino Unido, Europa y “rest of the world”. Sin embargo, no pude comprobar durante esta investigación un aviso activo de AMBA o una prueba específica de recepción de alertas argentinas. La cobertura global declarada no basta para presumir disponibilidad en cada zona o evento: validar con el proveedor y probar el comportamiento sin alertas antes de implementarlo.[16] [17] [18]

En las páginas comerciales visitadas, el plan gratuito publica 100.000 llamadas mensuales, uso comercial permitido y pronóstico de tres días; no requiere tarjeta según la página principal. Ese nivel ofrece solo alertas limitadas, por lo que no conviene asumir que cubre la necesidad de alertas operativas. La opción publicada de USD 7/mes incluye 3 millones de llamadas, pronóstico de siete días, siete días de histórico y alertas; el siguiente nivel publicado es USD 25/mes por cinco millones de llamadas, previsión futura hasta 300 días e histórico de 365 días. La página indica que el tiempo actual se actualiza cada 10–15 minutos y el pronóstico cada 4–6 horas. El nivel gratuito conserva solo el día anterior de histórico; los siguientes planes amplían el archivo. La documentación de la tabla no expone un número de ráfaga por minuto que se pueda citar, aunque los términos indican que aplica un límite por minuto según el plan.[17] [19] [21]

Sus términos autorizan el uso de datos en aplicaciones web y móviles, incluso comerciales, sujeto a plan y a una sola app por suscripción/clave; prohíben revender los datos. Para usuarios gratuitos requieren acreditar WeatherAPI.com por nombre o logo en todos los usos de los datos. Los términos también dicen que la clave debe mantenerse confidencial y no incluirse en código accesible públicamente sin controles adecuados. En una app Android eso implica no incluir una clave de servidor directamente en el APK: usar un backend/proxy y controles de cuotas.[20]

WeatherAPI puede ser más conveniente si se prioriza una sola API de pronóstico y alertas, y su precio/llamadas comerciales están publicados con más claridad que Open-Meteo. Para DriverAI lo dejaría como alternativa a evaluar, no como fuente primaria de avisos AMBA: la disponibilidad de alertas argentinas no quedó demostrada. Cualquier aviso mostrado debería conservar emisor, severidad, vigencia y hora de emisión cuando el proveedor los entregue, y facilitar el acceso a la advertencia original del SMN.[18] [20]

## Integración recomendada para DriverAI

1. **Pronóstico de apoyo:** consumir el plan comercial de Open-Meteo desde backend, no desde el cliente Android; solicitar solamente variables necesarias para conducción (temperatura, precipitación/probabilidad, viento y ráfagas) y ubicaciones relevantes. Cachear según ventana de actualización útil y presupuesto de llamadas. Conservar `timezone=America/Argentina/Buenos_Aires` o convertir de forma explícita a hora local para presentación; la llamada de prueba utilizó una zona GMT-3 válida y devolvió valores horarios locales.[9] [10]
2. **Avisos oficiales:** mostrar en la sección de clima un enlace claro al SAT del SMN y comunicar si no se pudo obtener una alerta estructurada. No extraer mediante scraping la página dinámica ni copiar su contenido como si fuera un feed autorizado. Reconsiderar integración automática directa del SMN solo con recurso reparado/validado y licencia o autorización confirmada.[1] [2] [4] [5] [7]
3. **Alternativa de alertas:** si se necesita una API estructurada en la primera versión, probar WeatherAPI con coordenadas representativas de CABA y partidos del conurbano, obtener confirmación de cobertura argentina, usar plan compatible con alertas y mantener al SMN como referencia oficial. Mostrar proveedor y timestamp, y manejar respuestas vacías o vencidas como “sin dato confirmado”, no como “sin riesgo”.[18] [19] [20]
4. **Lenguaje de producto:** ofrecer información descriptiva y explicable (qué variable, para qué zona, período y fuente), con separación visible entre “pronóstico” y “alerta oficial”. No afirmar que el clima hace conveniente un viaje, que un trayecto es seguro, ni que producirá ganancias o viajes. El pronóstico es incierto y el aviso oficial debe prevalecer ante una alerta o instrucción de autoridad.

## Limitaciones que deben quedar en la implementación

- El recurso de alertas del SMN se anuncia con 365 días en metadatos, pero la descarga observada en esta comprobación vino vacía; no se deduce de ello que el SAT manual estuviera vacío o que ese endpoint esté siempre fuera de servicio.[5] [7]
- La licencia del catálogo SMN no especifica condiciones; publicación pública no resuelve por sí sola la autorización de redistribución comercial en DriverAI.[6] [7]
- WeatherAPI afirma una cobertura global de alertas por feeds CAP, pero no se verificó alerta actual ni histórico específico para Buenos Aires/AMBA.[18]
- Open-Meteo es idóneo para previsión y variables meteorológicas; no lo trato como fuente de alertas oficiales del SMN. Su endpoint gratuito no está permitido para producto comercial.[9] [11]
- Ninguna fuente consultada garantiza ausencia de riesgo vial. Mantener atribución, timestamp, fuente, vigencia y estado de error/frescura; remitir al SMN para instrucciones ante alertas.[2] [11] [20]

## Referencias

[1]: https://www.smn.gob.ar/alertas "Alertas del Servicio Meteorológico Nacional"
[2]: https://www.smn.gob.ar/qu%C3%A9-es-el-nuevo-sistema-de-alerta-temprana-sat "Qué es el Sistema de Alerta Temprana"
[3]: https://datos.gob.ar/dataset?organization=smn&_tags_limit=0 "Catálogo de datasets del SMN"
[4]: https://ssl.smn.gob.ar/dpd/zipopendata.php?dato=pron5d "Recurso ZIP de pronóstico SMN a cinco días"
[5]: https://ssl.smn.gob.ar/dpd/zipopendata.php?dato=alertas "Recurso ZIP de alertas meteorológicas SMN"
[6]: https://datos.gob.ar/api/3/action/package_show?id=smn-pronostico-tiempo-5-dias "Ficha CKAN de pronóstico SMN"
[7]: https://datos.gob.ar/api/3/action/package_show?id=smn-alertas-meteorologicas-365-dias "Ficha CKAN de alertas SMN"
[8]: https://repositorio.smn.gob.ar/bitstream/handle/20.500.12160/2594/C9%20-%20Manual%20de%20uso%20para%20tomadores%20de%20decisi%C3%B3n.pdf?sequence=1&isAllowed=y "Manual de uso del SAT para tomadores de decisión"
[9]: https://open-meteo.com/en/docs "Documentación de Weather Forecast API de Open-Meteo"
[10]: https://open-meteo.com/en/pricing "Planes y límites de Open-Meteo"
[11]: https://open-meteo.com/en/terms "Términos de Open-Meteo"
[12]: https://open-meteo.com/en/license "Licencia de datos de Open-Meteo"
[13]: https://open-meteo.com/en/docs/historical-weather-api "Historical Weather API de Open-Meteo"
[14]: https://open-meteo.com/en/docs/historical-forecast-api "Historical Forecast API de Open-Meteo"
[15]: https://api.open-meteo.com/v1/forecast?latitude=-34.6037&longitude=-58.3816&current=temperature_2m,precipitation,wind_speed_10m&hourly=precipitation_probability,rain,wind_gusts_10m&forecast_days=2&timezone=America%2FSao_Paulo "Consulta de prueba de Open-Meteo para CABA"
[16]: https://www.weatherapi.com/docs/ "Documentación principal de WeatherAPI.com"
[17]: https://www.weatherapi.com/docs/forecast-api/ "Forecast API de WeatherAPI.com"
[18]: https://www.weatherapi.com/docs/alerts-api/ "Alerts API de WeatherAPI.com"
[19]: https://www.weatherapi.com/pricing "Planes y límites de WeatherAPI.com"
[20]: https://www.weatherapi.com/terms "Términos de uso de WeatherAPI.com"
[21]: https://www.weatherapi.com/ "WeatherAPI.com y plan gratuito"
