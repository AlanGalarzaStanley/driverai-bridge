package ar.driverai.bridge.copilot

import ar.driverai.bridge.engine.Evaluation
import ar.driverai.bridge.engine.Fmt
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Construye instrucciones para que el conductor las use en una IA externa con navegación. */
object ExternalBriefingPrompt {
    fun build(
        now: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        selectedWorkZone: String?,
        dailyTargetArs: Double?,
        shiftStart: Long?,
        lastEvaluation: Evaluation?,
    ): String {
        val timestamp = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM yyyy, HH:mm z", Locale.forLanguageTag("es-AR"))
            .format(Instant.ofEpochMilli(now).atZone(zoneId))
        val area = selectedWorkZone?.let { "Zona elegida manualmente: $it. No es ubicación GPS ni confirma dónde está el conductor." }
            ?: "No hay ciudad o ubicación elegida en DriverAI; no infieras una ubicación."
        val target = dailyTargetArs?.let { "Objetivo de ingresos registrado por el conductor: ${Fmt.ars(it)} ARS. Es una meta, no un resultado." }
            ?: "Objetivo diario: no configurado."
        val shift = shiftStart?.let {
            val started = DateTimeFormatter.ofPattern("HH:mm z", Locale.forLanguageTag("es-AR"))
                .format(Instant.ofEpochMilli(it).atZone(zoneId))
            "Inicio de turno registrado: $started. DriverAI no descuenta pausas ni confirma horas efectivamente trabajadas."
        } ?: "No hay un turno activo registrado."
        val offer = lastEvaluation?.let { evaluation ->
            val m = evaluation.metrics
            buildString {
                append("Última oferta evaluada localmente (no implica que haya sido tomada ni cobrada): ")
                append("veredicto determinista ${evaluation.verdict.label}; tarifa ${m.fare?.let(Fmt::ars) ?: "no disponible"} ARS; ")
                append("distancia total ${m.totalKm?.let { Fmt.num(it) + " km" } ?: "no disponible"}; ")
                append("duración total ${m.totalMin?.let { Fmt.num(it, 0) + " min" } ?: "no disponible"}; ")
                append("bruto/h ${m.grossPerHour?.let(Fmt::ars) ?: "no disponible"}; bruto/km ${m.grossPerKm?.let(Fmt::ars) ?: "no disponible"}. ")
                append("Estos cálculos describen esa oferta y no son datos de demanda.")
            }
        } ?: "No hay una evaluación de oferta disponible."

        return """Prepará un informe operativo actualizado para un conductor de aplicaciones de transporte.

CONTEXTO QUE APORTA EL CONDUCTOR
- Fecha y hora local del dispositivo: $timestamp
- Ubicación/zona: $area
- $target
- $shift
- $offer

DriverAI no realizó esta búsqueda ni consultó fuentes web. Buscá información vigente para la fecha y el área indicadas. Si la ubicación no alcanza para identificar con certeza una ciudad, preguntá o explicitá esa limitación; no la supongas.

Investiga solo lo que puedas respaldar y prioriza:
1. Clima y alertas oficiales relevantes, con fuente y hora de actualización.
2. Eventos deportivos, conciertos y espectáculos cercanos, con horarios, lugar y hora estimada de salida del público.
3. Actividad aeroportuaria y de terminales/puertos/trenes/ómnibus si existen datos públicos confiables.
4. Cortes, tráfico o desvíos únicamente si una fuente actual los confirma.
5. Horarios pico habituales como patrones históricos o generales, nunca como demanda observada en tiempo real.
6. Factores que podrían influir en la actividad y alternativas prácticas de posicionamiento, expresadas como hipótesis cuando corresponda.

FORMATO DE RESPUESTA
- Indica fecha, hora y zona horaria de cada dato actual relevante.
- Cita enlaces directos a fuentes verificables; distingue fuente primaria de cobertura secundaria.
- Separa claramente HECHOS CONFIRMADOS, ESTIMACIONES/PATRONES e HIPÓTESIS.
- Explica qué no pudiste verificar y evita inventar eventos, vuelos, demanda, tráfico o cifras.
- No interpretes la tarifa de la oferta como ingreso cobrado ni como rentabilidad neta.
- Cierra con un resumen breve y accionable, incluyendo nivel de confianza y límites.
""".trimIndent()
    }
}
