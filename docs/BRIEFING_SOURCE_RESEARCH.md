# Fuentes para el briefing de turno en AMBA

**Verificado:** 21 de noviembre de 2026. El inventario parte de las zonas existentes en DriverAI Bridge (CABA y zona sur/norte del conurbano), no de una ubicación GPS del conductor. Ninguna fuente consultada mide directamente los pedidos de Uber, Didi, Cabify u otra plataforma; las señales siguientes son contexto de movilidad, no demanda real ni promesa de viajes o ganancias.

## Qué puede alimentar la primera versión

**Clima.** El SMN es la referencia para alertas oficiales y avisos a muy corto plazo. Su portal SAT permite consultar avisos, pero no se verificó una API pública de alertas estable y licenciada para una app. Los ZIP de datos enlazados desde el catálogo respondieron correctamente durante la inspección, aunque los archivos de texto internos llegaron vacíos en ese corte; no deben conectarse a producción sin validar el contenido y los derechos de reutilización.[1] [2] Open-Meteo respondió con pronóstico horario para una coordenada de CABA. Sus datos se ofrecen con atribución CC BY 4.0, pero el endpoint gratuito excluye el uso comercial; DriverAI debe contratar el plan comercial y llamar al proveedor desde un backend, nunca exponer una clave en el APK.[3] [4] [5]

**Eventos.** Buenos Aires Data publica catálogos y recursos descargables. El dataset de Programas Deportivos incluye ubicaciones y horarios de actividades regulares, pero no representa un calendario exhaustivo de partidos, recitales ni salidas de recintos.[6] [7] La agenda de Turismo ofrece eventos puntuales en páginas web, pero no se encontró un API común ni una frecuencia formal de actualización.[8] Para el piloto, conviene una curaduría explícita de fuentes oficiales con URL y fecha de revisión; si no se verificó el horario, no mostrar el evento como vigente.

**Vuelos.** Aeropuertos Argentina publica información de vuelos en su sitio, pero no se halló una API pública documentada ni autorización para redistribuir esos datos.[9] [10] El dataset de ANAC consultado es una descarga estadística mensual, no un tablero en tiempo real.[11] FlightAware documenta endpoints para llegadas recientes y vuelos programados y muestra cobertura de AEP y EZE, pero sus términos, el nivel de servicio local y el derecho a mostrar la información en DriverAI deben confirmarse en un contrato vigente antes de integrar.[12] Si no se obtiene una fuente licenciada, el briefing debe omitir vuelos actuales en vez de inferirlos de una página incompleta.

**Barcos y cruceros.** La Administración General de Puertos publica informes de cruceros por temporada; la lista observada para 2026–2027 contiene horarios planificados, no confirmaciones de atraque ni desembarque.[13] [14] Prefectura publica un mapa AIS, pero no se encontró una API abierta con licencia de redistribución comprobada; no se debe automatizar el scraping del mapa.[15] Para comenzar, la agenda de AGP puede cargarse y revisarse manualmente, rotulada como «planificada» y con fecha de versión. Para observaciones AIS hace falta autorización de PNA/AGP o un proveedor con contrato que permita mostrar el dato.

**Patrones horarios y calendario.** La serie diaria de operaciones SUBE ofrece un contexto agregado para AMBA y declara CC BY 4.0; no tiene granularidad horaria o por barrio, así que no se debe presentar como demanda por zona.[16] [17] Existe además un dataset SUBE por hexágono, hora y modo de transporte, más útil espacialmente, pero su ficha no especifica licencia; queda fuera de producción hasta obtener permiso.[18] El calendario oficial de feriados puede separar días hábiles, fines de semana y feriados, pero no determina por sí mismo si subirán o bajarán los viajes.[19] La API de Transporte de Buenos Aires puede servir para información del transporte público de CABA, sujeta a registro y verificación de términos; tampoco mide pedidos de viajes de apps.[20]

## Contrato mínimo antes de generar un índice

Cada señal debería conservar un envoltorio verificable con `source_id`, URL de origen, tipo (`observed`, `scheduled`, `forecast`, `historical` o `unavailable`), zona y resolución realmente cubiertas, franja y zona horaria cuando aplique, `observed_at`, `source_updated_at` si existe, `valid_until`, atribución/licencia, estado de validación y motivo de fallback. Si una fuente no expone hora de actualización, se debe indicar esa ausencia y aplicar una caducidad interna conservadora; la hora en que DriverAI consultó al proveedor no debe hacerse pasar por la hora de actualización del dato.

La confianza describe calidad, completitud, frescura y adecuación geográfica de cada fuente. No es el índice de demanda. El índice debe tener metodología independiente y permanecer contextual hasta calibrarse con datos reales de los turnos; no debe describirse como probabilidad de recibir un viaje. Si faltan geometrías validadas, fuentes vigentes o una metodología defendible, mostrar las señales por separado y dejar el índice sin calcular.

## Decisión de producción

La aplicación puede avanzar con un piloto informativo, pero hoy no es responsable afirmar que el briefing automatizado está listo para producción. Para clima comercial hay que contratar y configurar el proveedor. Eventos, vuelos y AIS requieren fuentes estructuradas y permisos que no quedaron resueltos; los datos históricos SUBE no sustituyen demanda de apps. También falta asegurar el backend nuevo: el repositorio Android no incluye el código de las funciones Supabase existentes, y la [auditoría previa](INITIAL_AUDIT.md) encontró endpoints públicos sin autenticación ni límite de uso.

El primer alcance de producto puede mostrar un calendario oficial curado y patrones diarios SUBE atribuidos, junto con clima contratado si se habilita. Cada tarjeta debe conservar su estado y fecha. Vuelos y barcos actuales solo se agregan al contar con feed/licencia; de lo contrario se muestran como no disponibles, no como cero. El reporte estratégico heredado puede ser un fallback separado, marcado como estimación del servidor sin fuente, vigencia ni confianza por señal.

## References

[1]: https://www.smn.gob.ar/alertas "Alertas del Servicio Meteorológico Nacional"
[2]: https://repositorio.smn.gob.ar/bitstream/handle/20.500.12160/2594/C9%20-%20Manual%20de%20uso%20para%20tomadores%20de%20decisi%C3%B3n.pdf?sequence=1&isAllowed=y "Manual del Sistema de Alerta Temprana"
[3]: https://open-meteo.com/en/docs "Documentación de pronóstico de Open-Meteo"
[4]: https://open-meteo.com/en/pricing "Planes y límites de Open-Meteo"
[5]: https://open-meteo.com/en/license "Licencia de datos de Open-Meteo"
[6]: https://data.buenosaires.gob.ar/api/3/action/package_search "Catálogo de datos abiertos de Buenos Aires"
[7]: https://data.buenosaires.gob.ar/api/3/action/package_show?id=programas-deportivos "Dataset de Programas Deportivos"
[8]: https://turismo.buenosaires.gob.ar/es/eventos "Agenda de eventos de Buenos Aires Turismo"
[9]: https://www.aeropuertosargentina.com/es/vuelos "Vuelos de Aeropuertos Argentina"
[10]: https://www.aeropuertosargentina.com/es/limitacion-de-responsabilidad "Limitación de responsabilidad de Aeropuertos Argentina"
[11]: https://datos.transporte.gob.ar/dataset/aterrizajes-y-despegues-procesados-por-la-administracion-nacional-de-aviacion-civil-anac/archivo/7b6d0103-0a69-4c67-aed9-33cb58ae12f5 "Dataset de movimientos procesados por ANAC"
[12]: https://www.flightaware.com/commercial/aeroapi/ "FlightAware AeroAPI"
[13]: https://www.argentina.gob.ar/economia/administracion-general-de-puertos-sau/puertos/buques/informes-de-cruceros-por-temporada "Informes de cruceros de la Administración General de Puertos"
[14]: https://www.argentina.gob.ar/sites/default/files/2018/08/listado_26-27_actualizado_22-9.pdf "Cruceros planificados para Puerto Buenos Aires 2026–2027"
[15]: https://ais.prefecturanaval.gob.ar/ "Servidor AIS de Prefectura Naval Argentina"
[16]: https://datos.transporte.gob.ar/dataset/sube-cantidad-de-transacciones-usos-por-fecha "Dataset SUBE de transacciones por fecha"
[17]: https://creativecommons.org/licenses/by/4.0/legalcode "Licencia Creative Commons Atribución 4.0 Internacional"
[18]: https://datos.transporte.gob.ar/dataset/operaciones-sube-hexagonos "Dataset SUBE agregado por hexágono, hora y modo"
[19]: https://www.argentina.gob.ar/feriados "Calendario oficial de feriados de Argentina"
[20]: https://buenosaires.gob.ar/gcaba_historico/infraestructura/movilidad/api-transporte "API de Transporte de la Ciudad de Buenos Aires"
