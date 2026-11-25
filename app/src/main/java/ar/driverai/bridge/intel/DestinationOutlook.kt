package ar.driverai.bridge.intel

/**
 * Lo que el radar espera en la zona de destino de una oferta al momento estimado de llegada.
 * Solo conserva el identificador de zona: el texto de la dirección se descarta en el acto.
 */
data class DestinationOutlook(
    val zoneId: String,
    val zoneName: String,
    val arrivalAt: Long,
    val index: Int?,
    val level: DemandLevel?,
    val confidence: Double?,
    val reason: String?,
    /** Confianza de que la zona detectada sea realmente el destino (no la recogida). */
    val matchConfidence: Double,
) {
    fun shortLine(formatTime: (Long) -> String): String = buildString {
        append("Destino $zoneName")
        if (index != null && level != null) {
            append(": demanda ${level.label.lowercase()} ($index) al llegar ~${formatTime(arrivalAt)}")
            reason?.let { append(" · ").append(it) }
        } else append(": sin pronóstico para esa hora")
        if (matchConfidence < 0.7) append(" (zona dudosa)")
    }
}

/** Reconoce zonas del catálogo mencionadas en el texto visible de una oferta. */
object ZoneMatcher {

    data class Match(val zone: CityZone, val fragmentIndex: Int)

    private val streetPrefix = Regex("(?:\\bav\\.?|\\bavenida|\\bcalle|\\bpje\\.?|\\bpasaje|\\bdiagonal|\\bpasaje)\\s*$")

    fun mentioned(fragments: List<String>, city: CityPack): List<Match> {
        val out = mutableListOf<Match>()
        fragments.forEachIndexed { i, raw ->
            val f = CityPack.fold(raw)
            if (f.length < 3) return@forEachIndexed
            // En direcciones el barrio suele ir al final ("Av. Belgrano 1234, Monserrat"): se elige
            // la coincidencia que termina más tarde y se ignoran nombres precedidos por "Av."/"Calle".
            val best = city.zones.flatMap { z -> (listOf(z.name) + z.aliases).map { z to CityPack.fold(it) } }
                .filter { (_, alias) -> alias.length >= 3 }
                .flatMap { (z, alias) ->
                    Regex("(?<![\\p{L}])" + Regex.escape(alias) + "(?![\\p{L}])").findAll(f)
                        .filterNot { m -> streetPrefix.containsMatchIn(f.substring(0, m.range.first)) }
                        .map { m -> Triple(z, m.range.last, alias.length) }
                        .toList()
                }
                .maxWithOrNull(compareBy<Triple<CityZone, Int, Int>> { it.second }.thenBy { it.third })
                ?.let { it.first to it.third }
            if (best != null && out.lastOrNull()?.zone?.id != best.first.id) out += Match(best.first, i)
        }
        return out
    }

    /**
     * Elige el destino: en las tarjetas de oferta la recogida suele aparecer primero y el destino
     * al final. Con una sola zona mencionada la asignación es dudosa.
     */
    fun destination(fragments: List<String>, city: CityPack): Pair<CityZone, Double>? {
        val m = mentioned(fragments, city)
        return when {
            m.isEmpty() -> null
            m.size == 1 -> m.first().zone to 0.55
            else -> m.last().zone to 0.8
        }
    }

    fun outlook(
        fragments: List<String>,
        city: CityPack,
        forecast: RadarForecast?,
        arrivalAt: Long,
    ): DestinationOutlook? {
        val (zone, matchConf) = destination(fragments, city) ?: return null
        val w = forecast?.at(zone.id, arrivalAt)
        return DestinationOutlook(
            zoneId = zone.id,
            zoneName = zone.name,
            arrivalAt = arrivalAt,
            index = w?.index,
            level = w?.level,
            confidence = w?.confidence,
            reason = w?.topReason?.takeIf { it.origin != SignalOrigin.PATRON_GENERAL }?.label,
            matchConfidence = matchConf,
        )
    }
}
