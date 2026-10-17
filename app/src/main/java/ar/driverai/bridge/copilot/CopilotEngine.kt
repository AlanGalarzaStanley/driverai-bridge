package ar.driverai.bridge.copilot

import ar.driverai.bridge.backend.StrategicRepository
import ar.driverai.bridge.data.ShiftStats
import ar.driverai.bridge.engine.DriverSettings
import ar.driverai.bridge.engine.Evaluation
import ar.driverai.bridge.engine.Fmt
import ar.driverai.bridge.engine.ReasonKind
import ar.driverai.bridge.engine.Verdict
import java.text.Normalizer

object Zones {
    val ALL = listOf("Microcentro", "Palermo", "Belgrano", "Recoleta", "San Telmo", "Caballito", "Flores",
        "Liniers", "Quilmes", "Bernal", "Lomas", "Lanús", "Avellaneda", "San Isidro")

    /** Agrupación geográfica aproximada (para "terminar cerca de casa"). Es una heurística, no ruteo. */
    val GROUPS = mapOf(
        "Norte" to listOf("San Isidro", "Belgrano", "Palermo", "Recoleta"),
        "Centro" to listOf("Microcentro", "San Telmo", "Recoleta", "Puerto Madero"),
        "Oeste" to listOf("Caballito", "Flores", "Liniers"),
        "Sur" to listOf("Avellaneda", "Lanús", "Lomas", "Quilmes", "Bernal"),
    )

    fun groupOf(zone: String): String? = GROUPS.entries.firstOrNull { (_, z) -> z.any { it.equals(zone, true) } }?.key
}

enum class Intent { CONVIENE, POR_QUE, POR_HORA, DONDE_ESPERAR, EVENTOS, CERCA_CASA, DESCONOCIDA }

data class CopilotContext(
    val lastEvaluation: Evaluation?,
    val shift: ShiftStats?,
    val strategic: StrategicRepository.State,
    val settings: DriverSettings,
    val now: Long,
)

/**
 * Copiloto por reglas: entiende consultas cortas y responde con frases breves para no distraer.
 * Distingue datos (del motor o del backend) de estimaciones/predicciones.
 */
object CopilotEngine {

    fun fold(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    fun intentOf(query: String): Intent {
        val q = fold(query)
        return when {
            q.contains("por que") || q.contains("porque") || q.contains("motivo") -> Intent.POR_QUE
            q.contains("por hora") || q.contains("estoy haciendo") || q.contains("llevo") || q.contains("cuanto hice") -> Intent.POR_HORA
            q.contains("casa") || q.contains("terminar") -> Intent.CERCA_CASA
            q.contains("evento") || q.contains("esta noche") || q.contains("partido") || q.contains("recital") -> Intent.EVENTOS
            q.contains("donde") || q.contains("esperar") || q.contains("zona") || q.contains("me muevo") || q.contains("me quedo") -> Intent.DONDE_ESPERAR
            q.contains("conviene") || q.contains("acepto") || q.contains("este viaje") || q.contains("la oferta") -> Intent.CONVIENE
            else -> Intent.DESCONOCIDA
        }
    }

    fun answer(query: String, ctx: CopilotContext): String = when (intentOf(query)) {
        Intent.CONVIENE -> ctx.lastEvaluation?.let { e ->
            val m = e.metrics
            buildString {
                append(e.verdict.label.lowercase().replaceFirstChar { it.uppercase() }).append(". ")
                m.grossPerKm?.let { append("${Fmt.ars(it)} por kilómetro. ") }
                m.grossPerHour?.let { append("${Fmt.ars(it)} por hora, bruto.") }
                if (e.verdict == Verdict.REVISAR) append(" Faltan datos o hay dudas en la lectura.")
            }.trim()
        } ?: "Todavía no evalué ninguna oferta."
        Intent.POR_QUE -> ctx.lastEvaluation?.let { e ->
            val top = e.reasons.filter { it.kind == ReasonKind.FALLA || it.kind == ReasonKind.AVISO }.take(2)
                .ifEmpty { e.reasons.filter { it.kind == ReasonKind.PASA }.take(2) }
            if (top.isEmpty()) "No tengo motivos para esa oferta." else top.joinToString(" ") { it.text }
        } ?: "Todavía no evalué ninguna oferta."
        Intent.POR_HORA -> {
            val s = ctx.shift
            when {
                s == null || s.hours == null -> "Iniciá el turno en la pantalla principal para medir cuánto hacés por hora."
                s.trips == 0 -> "No marcaste viajes tomados en este turno."
                s.grossPerHour == null -> "Llevás ${s.trips} viajes y ${Fmt.ars(s.grossFare)} brutos."
                else -> "Llevás ${Fmt.ars(s.grossFare)} brutos en ${s.trips} viajes: ${Fmt.ars(s.grossPerHour)} por hora."
            }
        }
        Intent.DONDE_ESPERAR -> donde(ctx)
        Intent.EVENTOS -> eventos(ctx)
        Intent.CERCA_CASA -> cercaCasa(ctx)
        Intent.DESCONOCIDA -> "Podés preguntarme: ¿me conviene?, ¿por qué?, ¿cuánto hago por hora?, ¿dónde espero?, ¿hay eventos?"
    }

    private fun staleNote(ctx: CopilotContext) = if (ctx.strategic.isStale(ctx.now)) " (dato posiblemente desactualizado)" else ""

    private fun donde(ctx: CopilotContext): String {
        val p = ctx.strategic.posicion
        if (p != null && p.zona_recomendada != null) {
            return if (p.decision == "moverte") "Estimación del servidor: movete a ${p.zona_recomendada}, puntaje ${Fmt.num(p.score_recomendado ?: 0.0)} contra ${Fmt.num(p.score_actual ?: 0.0)}.${staleNote(ctx)}"
            else "Estimación del servidor: quedate en ${p.zona_actual ?: "tu zona"}.${staleNote(ctx)}"
        }
        val best = ctx.strategic.reporte?.reporte?.mejor_zona_ahora
        return if (best != null) "Estimación del servidor: la mejor zona ahora es $best.${staleNote(ctx)}"
        else "No tengo contexto del servidor. Elegí tu zona y actualizá."
    }

    private fun eventos(ctx: CopilotContext): String {
        val r = ctx.strategic.reporte ?: return "No tengo el reporte del servidor. Actualizalo en Inicio."
        val parts = mutableListOf<String>()
        val ev = r.eventos.size
        parts += if (ev == 0) "El servidor no informa eventos." else "El servidor informa $ev eventos."
        if (r.hay_partido == true) parts += "Hay partido hoy."
        r.reporte?.nivel_demanda_general?.let { parts += "Demanda estimada: ${it.replace('_', ' ')}." }
        return parts.joinToString(" ") + staleNote(ctx)
    }

    private fun cercaCasa(ctx: CopilotContext): String {
        val home = ctx.settings.homeZone ?: return "Configurá tu zona de casa en Ajustes."
        val group = Zones.groupOf(home) ?: return "No conozco la zona $home."
        val near = Zones.GROUPS[group].orEmpty()
        val ranked = ctx.strategic.reporte?.let { r ->
            (r.reporte?.zonas_rankeadas?.map { it.zona to (it.score ?: 0.0) }.orEmpty() + r.demanda_por_zona.map { it.zona to (it.score ?: 0.0) })
        }.orEmpty().filter { (z, _) -> near.any { it.equals(z, true) } }.sortedByDescending { it.second }
        val best = ranked.firstOrNull()
            ?: return "Tu casa está en zona $group. No tengo puntajes del servidor para esa zona."
        return "Estimación: para terminar cerca de $home, probá ${best.first} (puntaje ${Fmt.num(best.second)}).${staleNote(ctx)}"
    }
}
