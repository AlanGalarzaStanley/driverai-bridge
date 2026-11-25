# Radar de demanda y copiloto multi-app

**Estado:** implementado en la rama `feature/copiloto-radar-latam` (2026-11-25). El cálculo es local, determinístico y explicable. No es demanda medida ni promesa de ganancia.

## Por qué existe

En LATAM el mercado tiene dos tipos de herramientas que funcionan por separado:

| Referencia | Qué resuelve | Qué falta para el conductor de LATAM |
|---|---|---|
| Gridwise (EE.UU.) | Seguimiento de ingresos y kilometraje, informes, tendencias de demanda, eventos con hora de salida y arribos al aeropuerto ([funciones](https://gridwise.io/features), [ayuda](https://help.gridwise.io/hc/en-us/articles/360017606594-What-is-Gridwise-and-How-Does-Using-Gridwise-Help-Me-Drive-Smarter)) | No evalúa la oferta en vivo. Está pensado para EE.UU. en USD y millas ([planes](https://gridwise.io/blog/best-mileage-tracker-app)) |
| Ruta Rentable, Viaje Rentable, UDDI | Semáforo de la oferta por $/km y $/h en Uber, DiDi, Cabify, inDrive y Bolt ([Ruta Rentable](https://rutarentable.app/), [App Store](https://apps.apple.com/us/app/ruta-rentable/id6802318360?l=es-MX), [Viaje Rentable](https://viajerentable.com.ar/)) | No predicen dónde y cuándo estar: no cruzan eventos, paros, vuelos ni el propio historial |

DriverAI junta las dos cosas en tiempo real:

1. Evaluación automática de la oferta, con lectura de cinco apps.
2. Radar de dónde y cuándo conviene estar.
3. Demanda prevista en el destino de cada oferta.
4. Informes y exportación al estilo de un rastreador de ingresos.

## Modelo del índice (0–100)

Para cada zona del catálogo y cada franja de 30 minutos de las próximas 6 horas, se suman puntos de varias fuentes:

1. **Patrón general.** Una curva por perfil de zona (centro laboral, nocturno, residencial, aeropuerto, terminal, etc.) y tipo de día (laboral, viernes, sábado, domingo o feriado). Tiene confianza fija 0,3 y se rotula siempre como "Patrón general".
2. **Señales concretas.** Cada tipo tiene su propia ventana de impacto:
   - Partido o show: desde 10 minutos antes del final hasta 70 minutos después. El final se estima con 110 o 150 minutos si no hay dato. El pico es la salida del público, no el inicio.
   - Vuelos: de 25 a 80 minutos después del aterrizaje.
   - Crucero: de 60 a 300 minutos después del arribo.
   - Paro de transporte y lluvia: suman a toda la ciudad durante su vigencia.
   - Feriados: no suman puntos directos; cambian el tipo de día.
3. **Tu historial.** Si a esa hora tu bruto/h promedio supera tu media general, suma hasta +15 puntos; si es peor, resta hasta −10. Se necesitan al menos 5 ofertas en esa hora y 10 en total.

La **confianza** se calcula aparte: es el promedio de la confianza de cada aporte, ponderado por sus puntos. Un índice alto con confianza baja se muestra como tal.

Los niveles son: baja (menos de 35), media (35 a 59), alta (60 a 79) y muy alta (80 o más).

Código: `intel/DemandRadar.kt`, `intel/DemandSignals.kt`, `intel/CityCatalog.kt` y `intel/RadarController.kt`.

## Fuentes

| Fuente | Uso | Estado |
|---|---|---|
| Patrón por perfil de zona, día y hora | Base del índice | Siempre activo, local |
| Feriados de ArgentinaDatos (`api.argentinadatos.com/v1/feriados/{año}`) | Tipo de día | Activo. Si falla, se usan los feriados inamovibles de fecha fija |
| TheSportsDB `eventsnextleague.php` (liga 4406, Primera División) | Partidos en estadios del AMBA | En la build privada se usa la clave de prueba `123`, con cobertura limitada. En release requiere la clave propia del conductor, cargada en Ajustes |
| Open-Meteo | Lluvia de 50 % o más se convierte en señal de ciudad | Solo en la build privada, como ya ocurría con el clima |
| Carga manual del conductor | Paros de colectivos, subte o trenes; partidos, recitales, vuelos (EZE/AEP), cruceros, lluvia u otros | Activo. Se guarda en el teléfono y vence sola |
| Historial propio | Patrón personal por hora | Activo y local |

Todavía no hay feeds gratuitos y con licencia clara de vuelos, cruceros ni paros para Argentina. Por eso esas señales se cargan a mano con plantillas rápidas, y no se simulan datos.

## Destino de la oferta

Con el texto visible de la oferta, `ZoneMatcher` reconoce barrios del catálogo:

- Prefiere la mención que aparece más tarde, porque en una dirección el barrio suele ir al final.
- Ignora nombres precedidos por "Av.", "Calle", "Pasaje" o "Diagonal", para no confundir la avenida Belgrano con el barrio.

Con eso consulta el radar para la hora estimada de llegada (ahora + minutos a la recogida + minutos de viaje). En la tarjeta aparece, por ejemplo: "Destino Palermo: demanda alta (68) al llegar ~23:10".

**Privacidad:** la dirección no se guarda. Al historial solo va el identificador de zona (`destinationZoneId`). Se puede desactivar en Ajustes.

## Multi-app

`RidePlatform` lista Uber (`com.ubercab.driver`, la única con lectura real verificada), DiDi (`com.didiglobal.driver`), Cabify (`com.cabify.driver`), inDrive (`sinet.startup.inDriver`) y Bolt (`ee.mtakso.driver`).

- El servicio de accesibilidad declara solo esos paquetes. Cada app se puede desactivar en Ajustes.
- En OCR, la app se deduce por palabras visibles solo si hay una única coincidencia.
- Las apps distintas de Uber figuran como "beta" hasta tener muestras reales anonimizadas.

## Propuesta de servidor: `driverai-senales` (solo diseño, no desplegado)

Para la versión comercial se recomienda una función edge de Supabase que agregue señales públicas. Así las claves quedan del lado del servidor y la app consume un único endpoint.

- `GET /driverai-senales?city=amba&from=…&to=…` devuelve `DemandSignal[]` con el mismo esquema serializable de la app: fuente, URL, `fetchedAt` y confianza por señal.
- Fuentes candidatas, cada una con su licencia por validar:
  - plan pago de TheSportsDB o API-Football;
  - ticketeras o agendas oficiales de recintos;
  - plan comercial de Open-Meteo o alertas del SMN;
  - AA2000 para arribos;
  - AGP para el cronograma de cruceros;
  - comunicados oficiales de la UTA y de Metrovías para paros.
- La app no envía al servidor ubicación, historial ni texto de pantalla: pide la ciudad y el rango horario.
- Antes de cualquier señal colaborativa entre conductores, hay que diseñar consentimiento, autenticación, agregación con k-anonimato, retención y borrado (ver `FLEET_AND_REALTIME_STRATEGY.md`).

## Pruebas

`IntelTest` cubre:

- el patrón general y los feriados;
- la ventana de salida de un partido y su efecto en otras zonas;
- vuelos, cruceros y paros que afectan toda la ciudad;
- señales inválidas y el rango 0–100;
- el patrón personal;
- la asociación de estadios, incluido el caso "Estadio Único Diego Armando Maradona";
- TheSportsDB y feriados con MockWebServer;
- la detección de zona de destino que ignora avenidas;
- la detección de plataformas;
- los informes y la protección del CSV contra inyección de fórmulas.

`DataAndCopilotTest` comprueba que el copiloto prioriza el radar local frente al servidor.
