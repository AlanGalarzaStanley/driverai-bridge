package ar.driverai.bridge.intel

/**
 * Decide qué pico avisar antes de que empiece. Solo picos con una señal concreta (partido, show,
 * paro, informe IA, carga propia): el patrón general o el historial no generan alertas.
 */
object PeakAlerts {
    const val LEAD_MS = 40 * 60_000L
    const val MIN_INDEX = 60

    data class Alert(val key: String, val zoneName: String, val reason: String, val slotStart: Long, val index: Int, val unverified: Boolean) {
        fun text(now: Long): String {
            val min = ((slotStart - now) / 60_000L).coerceAtLeast(0)
            val whenTxt = if (min <= 5) "ahora" else "en $min min"
            return "Pico $whenTxt en $zoneName: $reason" + if (unverified) " (informe IA, verificá)" else ""
        }
    }

    fun due(forecast: RadarForecast, now: Long, alreadySent: Set<String>, leadMs: Long = LEAD_MS, minIndex: Int = MIN_INDEX): Alert? =
        forecast.windows.asSequence()
            .filter { it.index >= minIndex }
            .mapNotNull { w -> w.topReason?.takeIf { it.signalId != null && it.origin != SignalOrigin.PATRON_GENERAL && it.origin != SignalOrigin.TU_HISTORIAL }?.let { w to it } }
            .groupBy { (w, r) -> "${r.signalId}@${w.zoneId}" }
            .mapNotNull { (key, list) -> list.minByOrNull { it.first.slotStart }?.let { key to it } }
            .filter { (key, pair) -> key !in alreadySent && pair.first.slotStart - now in -forecast.slotMinutes * 60_000L..leadMs }
            .maxByOrNull { (_, pair) -> pair.first.index }
            ?.let { (key, pair) ->
                val (w, r) = pair
                Alert(key, w.zoneName, r.label, w.slotStart, w.index, r.origin == SignalOrigin.IA_EXTERNA)
            }
}
