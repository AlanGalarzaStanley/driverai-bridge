package ar.driverai.bridge.copilot

import ar.driverai.bridge.backend.StrategicRepository
import ar.driverai.bridge.data.MoneyDaySummary
import ar.driverai.bridge.data.ShiftStats
import ar.driverai.bridge.engine.DriverSettings
import ar.driverai.bridge.engine.Evaluation
import ar.driverai.bridge.engine.Fmt
import ar.driverai.bridge.engine.ReasonKind
import ar.driverai.bridge.engine.Verdict
import ar.driverai.bridge.intel.RadarForecast
import ar.driverai.bridge.intel.SignalKind
import ar.driverai.bridge.intel.SignalOrigin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

enum class Intent { RESUMEN_JORNADA, OBJETIVO, CONVIENE, POR_QUE, POR_HORA, DONDE_ESPERAR, EVENTOS, PAROS, MEJOR_HORA, CERCA_CASA, DESCONOCIDA }

data class CopilotContext(
    val lastEvaluation: Evaluation?,
    val shift: ShiftStats?,
    val strategic: StrategicRepository.State,
    val settings: DriverSettings,
    val now: Long,
    val moneyToday: MoneyDaySummary? = null,
    val odometerTodayKm: Double? = null,
    /** Radar local de demanda; tiene prioridad sobre el reporte del servidor. */
    val radar: RadarForecast? = null,
    /** Mejores horas del historial propio: hora → bruto/h promedio y cantidad de ofertas. */
    val bestHours: List<Triple<Int, Double, Int>> = emptyList(),
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
            q.contains("objetivo") || q.contains("meta diaria") -> Intent.OBJETIVO
            q.contains("resumen") || q.contains("jornada") || q.contains("mi dia") || q.contains("hoy") && q.contains("como va") -> Intent.RESUMEN_JORNADA
            q.contains("por que") || q.contains("porque") || q.contains("motivo") -> Intent.POR_QUE
            q.contains("por hora") || q.contains("estoy haciendo") || q.contains("llevo") || q.contains("cuanto hice") -> Intent.POR_HORA
            q.contains("casa") || q.contains("terminar") -> Intent.CERCA_CASA
            q.contains("paro") || q.contains("huelga") || q.contains("medida de fuerza") -> Intent.PAROS
            q.contains("mejor hora") || q.contains("mejores horas") || q.contains("a que hora") || q.contains("cuando salgo") || q.contains("cuando conviene salir") -> Intent.MEJOR_HORA
            q.contains("evento") || q.contains("esta noche") || q.contains("partido") || q.contains("recital") -> Intent.EVENTOS
            q.contains("donde") || q.contains("esperar") || q.contains("zona") || q.contains("me muevo") || q.contains("me quedo") -> Intent.DONDE_ESPERAR
            q.contains("conviene") || q.contains("acepto") || q.contains("este viaje") || q.contains("la oferta") -> Intent.CONVIENE
            else -> Intent.DESCONOCIDA
        }
    }

    fun answer(query: String, ctx: CopilotContext): String = when (intentOf(query)) {
        Intent.RESUMEN_JORNADA -> resumenJornada(ctx)
        Intent.OBJETIVO -> objetivo(ctx)
        Intent.CONVIENE -> ctx.lastEvaluation?.let { e ->
            val m = e.metrics
            buildString {
                append(e.verdict.label.lowercase().replaceFirstChar { it.uppercase() }).append(". ")
                m.grossPerKm?.let { append("${Fmt.ars(it)} por kilómetro. ") }
                m.grossPerHour?.let { append("${Fmt.ars(it)} por hora, bruto.") }
                if (ctx.settings.minPassengerRating != null) {
                    e.offer.passengerRating.value?.let { append(" Calificación del pasajero ${Fmt.num(it, 2)} sobre 5.") }
                        ?: append(" Falta leer la calificación del pasajero.")
                }
                if (e.verdict == Verdict.REVISAR) append(" Faltan datos o hay dudas en la lectura.")
                alternativeText(e)?.let { append(" ").append(it) }
            }.trim()
        } ?: "Todavía no evalué ninguna oferta."
        Intent.POR_QUE -> ctx.lastEvaluation?.let { e ->
            val ratingReason = e.reasons.firstOrNull {
                it.text.startsWith("Calificación del pasajero") && (it.kind == ReasonKind.FALLA || it.kind == ReasonKind.AVISO)
            }
            val otherReasons = e.reasons.filter { it.kind == ReasonKind.FALLA || it.kind == ReasonKind.AVISO }
                .filterNot { it == ratingReason }
            val top = (listOfNotNull(ratingReason) + otherReasons).take(2)
                .ifEmpty { e.reasons.filter { it.kind == ReasonKind.PASA }.take(2) }
            val reason = top.joinToString(" ") { it.text }
            val alternative = alternativeText(e)
            when {
                reason.isEmpty() && alternative == null -> "No tengo motivos para esa oferta."
                reason.isEmpty() -> alternative.orEmpty()
                alternative == null -> reason
                else -> "$reason $alternative"
            }
        } ?: "Todavía no evalué ninguna oferta."
        Intent.POR_HORA -> {
            val s = ctx.shift
            when {
                s == null || s.hours == null -> "Iniciá el turno en la pantalla principal para medir cuánto hacés por hora."
                s.trips == 0 -> "No marcaste ofertas como tomadas en este turno."
                s.paidTrips > 0 -> "Aceptaste ${s.trips} ofertas; tarifas ${Fmt.ars(s.grossFare)} (${s.grossPerHour?.let { Fmt.ars(it) + "/h bruto estimado" } ?: "tiempo insuficiente para calcular /h"}). Cobros vinculados: ${s.paidTrips} viajes, ${Fmt.ars(s.actualIncome)}${s.actualIncomePerHour?.let { " (${Fmt.ars(it)}/h desde el inicio)" } ?: ""}. No es ganancia neta."
                s.grossPerHour == null -> "Marcaste ${s.trips} ofertas como aceptadas; sus tarifas leídas suman ${Fmt.ars(s.grossFare)}. No confirma cobros."
                else -> "Ofertas aceptadas: ${s.trips}; tarifas leídas ${Fmt.ars(s.grossFare)} (${Fmt.ars(s.grossPerHour)}/h bruto estimado). No confirma cobros."
            }
        }
        Intent.DONDE_ESPERAR -> donde(ctx)
        Intent.EVENTOS -> eventos(ctx)
        Intent.PAROS -> paros(ctx)
        Intent.MEJOR_HORA -> mejorHora(ctx)
        Intent.CERCA_CASA -> cercaCasa(ctx)
        Intent.DESCONOCIDA -> "Podés preguntarme: ¿me conviene?, ¿por qué?, ¿cómo va mi jornada?, ¿cómo va mi objetivo diario?, ¿cuánto hago por hora?, ¿dónde espero?, ¿qué eventos hay?, ¿hay paro?, ¿cuál es mi mejor hora?"
    }

    private fun resumenJornada(ctx: CopilotContext): String {
        val wallet = ctx.moneyToday
        val parts = mutableListOf<String>()
        if (wallet != null) {
            parts += "En Billetera anotaste ${Fmt.ars(wallet.incomeArs)} de ingresos y ${Fmt.ars(wallet.expensesArs)} de gastos; diferencia ${Fmt.ars(wallet.cashBalanceArs)}, no ganancia neta."
        } else parts += "No tengo el resumen de la billetera de hoy."
        parts += ctx.odometerTodayKm?.let { "Odómetro manual de hoy: ${Fmt.num(it, 1)} km." } ?: "No hay odómetro manual de hoy."
        val shift = ctx.shift
        if (shift != null) {
            val estimate = "En el turno: ${shift.trips} ofertas aceptadas; sus tarifas suman ${Fmt.ars(shift.grossFare)} (estimado, no confirma cobros)."
            val paid = if (shift.paidTrips > 0) " Cobros vinculados y anotados: ${shift.paidTrips} viajes por ${Fmt.ars(shift.actualIncome)}; no es ganancia neta." else " Todavía no anotaste cobros vinculados a esas ofertas."
            parts += estimate + paid
        }
        val target = ctx.settings.dailyTargetArs?.takeIf { it.isFinite() && it > 0.0 }
        if (target != null && wallet != null) {
            parts += "Meta de ingresos registrados: ${Fmt.ars(target)}; progreso ${(wallet.incomeArs / target * 100.0).let { Fmt.num(it, 0) }} %."
        }
        return parts.joinToString(" ")
    }

    private fun objetivo(ctx: CopilotContext): String {
        val target = ctx.settings.dailyTargetArs?.takeIf { it.isFinite() && it > 0.0 }
            ?: return "No configuraste una meta diaria. Si querés, definila en Ajustes; no afecta el veredicto de una oferta."
        val income = ctx.moneyToday?.incomeArs ?: return "Tu meta es ${Fmt.ars(target)}, pero no tengo el ingreso de hoy de Billetera."
        val remaining = (target - income).coerceAtLeast(0.0)
        return "Meta ${Fmt.ars(target)}; ingresos anotados ${Fmt.ars(income)} (${Fmt.num(income / target * 100.0, 0)} %). " +
            if (remaining > 0.0) "Faltan ${Fmt.ars(remaining)} según la billetera. No es una predicción de viajes ni de ganancia neta."
            else "Meta alcanzada según los ingresos anotados; no es una medida de ganancia neta."
    }

    private fun staleNote(ctx: CopilotContext, at: Long? = ctx.strategic.reporteAt): String {
        val stale = at == null || ctx.now - at > StrategicRepository.STALE_MS || ctx.strategic.lastError != null
        return if (stale) " (dato posiblemente desactualizado)" else ""
    }

    private fun alternativeText(e: Evaluation): String? = e.fareAlternative?.let { alternative ->
        val assumptions = listOfNotNull(
            alternative.assumedPickupKm?.let { "${Fmt.num(it)} km a la recogida estimados" },
            alternative.assumedPickupMin?.let { "${Fmt.num(it, 0)} min a la recogida estimados" },
            if (alternative.emptyReturnFraction > 0.0) "retorno vacío ${Fmt.num(alternative.emptyReturnFraction * 100.0, 0)} %" else null,
        ).joinToString(" y ")
        "Piso teórico (no oferta futura): ${Fmt.ars(alternative.minimumFare)} para cumplir tus umbrales" +
            if (assumptions.isNotEmpty()) " (incluye $assumptions)." else "."
    }

    private val hhmm get() = SimpleDateFormat("HH:mm", Locale("es", "AR"))

    private fun radarDonde(r: RadarForecast): String? {
        val now = r.now().take(3).ifEmpty { return null }
        val top = now.first()
        val reason = top.topReason?.label?.let { " por $it" } ?: ""
        val others = now.drop(1).joinToString(" y ") { "${it.zoneName} (${it.index})" }
        val conf = if (top.confidence >= 0.55) "" else " Confianza baja: es sobre todo el patrón general."
        val peak = r.upcomingPeaks().firstOrNull { it.slotStart > r.generatedAt }?.let { " Próximo pico: ${it.zoneName} a las ${hhmm.format(Date(it.slotStart))}." } ?: ""
        return "Radar local: ${top.zoneName}, demanda ${top.level.label.lowercase()} (${top.index})$reason." +
            (if (others.isNotEmpty()) " Después $others." else "") + peak + conf
    }

    private fun donde(ctx: CopilotContext): String {
        ctx.radar?.let { r -> radarDonde(r)?.let { local -> return local + serverHint(ctx) } }
        return dondeServidor(ctx)
    }

    /** Agrega la recomendación del servidor como dato secundario y sin verificar. */
    private fun serverHint(ctx: CopilotContext): String {
        val p = ctx.strategic.posicion
        val zone = p?.zona_recomendada ?: ctx.strategic.reporte?.reporte?.mejor_zona_ahora ?: return ""
        return " Servidor (no verificado): $zone.${staleNote(ctx, ctx.strategic.posicionAt ?: ctx.strategic.reporteAt)}"
    }

    private fun radarSignals(ctx: CopilotContext, kinds: Set<SignalKind>): List<String> {
        val r = ctx.radar ?: return emptyList()
        return r.signalsUsed.filter { it.kind in kinds && it.origin != SignalOrigin.PATRON_GENERAL }
            .sortedBy { it.startAt }
            .take(3)
            .map { "${it.title} (${hhmm.format(Date(it.startAt))}, ${it.sourceName})" }
    }

    private fun paros(ctx: CopilotContext): String {
        val list = radarSignals(ctx, setOf(SignalKind.PARO_TRANSPORTE))
        return if (list.isEmpty()) "No tengo paros cargados para las próximas horas. Si te enterás de uno, cargalo en Radar: sube la demanda en toda la ciudad."
        else "Paros en el radar: ${list.joinToString("; ")}. Suele subir la demanda en horas pico."
    }

    private fun mejorHora(ctx: CopilotContext): String {
        val parts = mutableListOf<String>()
        if (ctx.bestHours.isNotEmpty()) {
            parts += "Según tu historial, tus mejores horas son " + ctx.bestHours.joinToString(", ") { (h, perHour, n) -> "%02d h (%s/h, %d ofertas)".format(h, Fmt.ars(perHour), n) } + "."
        } else parts += "Todavía no tengo suficientes ofertas tuyas por hora para comparar."
        ctx.radar?.bestPerSlot()?.maxByOrNull { it.index }?.let { w ->
            parts += "En las próximas horas el radar marca el pico a las ${hhmm.format(Date(w.slotStart))} en ${w.zoneName} (${w.index})."
        }
        return parts.joinToString(" ")
    }

    private fun dondeServidor(ctx: CopilotContext): String {
        val p = ctx.strategic.posicion
        if (p != null && p.zona_recomendada != null) {
            return if (p.decision == "moverte") "Estimación del servidor: movete a ${p.zona_recomendada}, puntaje ${Fmt.num(p.score_recomendado ?: 0.0)} contra ${Fmt.num(p.score_actual ?: 0.0)}.${staleNote(ctx, ctx.strategic.posicionAt)}"
            else "Estimación del servidor: quedate en ${p.zona_actual ?: "tu zona"}.${staleNote(ctx, ctx.strategic.posicionAt)}"
        }
        val best = ctx.strategic.reporte?.reporte?.mejor_zona_ahora
        return if (best != null) "Estimación del servidor: la mejor zona ahora es $best.${staleNote(ctx)}"
        else "No tengo contexto del servidor. Elegí tu zona y actualizá."
    }

    private fun eventos(ctx: CopilotContext): String {
        val local = radarSignals(ctx, setOf(SignalKind.EVENTO_DEPORTIVO, SignalKind.EVENTO_SHOW, SignalKind.VUELOS, SignalKind.CRUCERO))
        if (local.isNotEmpty()) return "En el radar: ${local.joinToString("; ")}. La salida del público suele generar el pico." + (ctx.strategic.reporte?.let { " " + eventosServidor(ctx) } ?: "")
        if (ctx.radar != null && ctx.strategic.reporte == null) return "No hay eventos cargados en el radar para las próximas horas. Podés actualizar fuentes o cargar uno en Radar."
        return eventosServidor(ctx)
    }

    private fun eventosServidor(ctx: CopilotContext): String {
        val r = ctx.strategic.reporte ?: return "No tengo el reporte del servidor. Actualizalo en Inicio."
        val parts = mutableListOf<String>()
        val ev = r.eventos
        parts += when {
            ev == null -> "El servidor no informó una lista de eventos."
            ev.isEmpty() -> "El servidor no listó eventos."
            else -> "El servidor informa ${ev.size} eventos."
        }
        if (r.hay_partido == true) parts += "El reporte indica un partido para hoy."
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
