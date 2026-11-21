package ar.driverai.bridge.engine

/**
 * Extrae datos de una oferta a partir de fragmentos de texto visibles (nodos de accesibilidad
 * o líneas de OCR). Es independiente de la fuente y del motor económico.
 *
 * No hay dependencia del diseño exacto de Uber: busca patrones genéricos (importe, "N min (X km)")
 * y palabras clave en español e inglés. Si el diseño cambia, se ajustan las reglas acá y sus tests.
 *
 * Formato real de Uber Driver Argentina (nov 2026): "15.723 ARS", "32 min · 24.2 km · 650 ARS/km est.",
 * "A 6 min (2.0 km) de distancia", "Viaje: 26 min (22.2 km)".
 */
object OfferTextParser {

    data class Amount(val value: Double, val confidence: Double)

    data class ParseResult(
        val offer: OfferData,
        val isLikelyOffer: Boolean,
        val notes: List<String>,
    )

    // Moneda antes o después del número. El grupo de "/unidad" descarta tarifas por km/h.
    private val moneyRegex = Regex(
        """([+])?\s*(?:(?:ARS|\$)\s?(\d[\d.,]*)|(\d[\d.,]*)\s*(?:ARS|\$))(\s*/\s*\w+)?""",
        RegexOption.IGNORE_CASE,
    )
    private val timeDistRegex = Regex(
        """(?:(\d{1,2})\s*h(?:s|oras?)?\.?\s*)?(\d{1,3})\s*min(?:s|utos)?\.?\s*\(\s*(\d+(?:[.,]\d+)?)\s*(km|mi|m)\s*\)""",
        RegexOption.IGNORE_CASE,
    )
    // Resumen de Uber AR: "32 min · 24.2 km" o "32 min • 24.2 km" (sin paréntesis).
    private val timeDistLooseRegex = Regex(
        """(?:(\d{1,2})\s*h(?:s|oras?)?\.?\s*)?(\d{1,3})\s*min(?:s|utos)?\.?\s*[•·\-|–—]\s*(\d+(?:[.,]\d+)?)\s*(km|mi)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val distTimeRegex = Regex(
        """(\d+(?:[.,]\d+)?)\s*(km|mi)\b[^\d\n]{0,6}(?:(\d{1,2})\s*h(?:s|oras?)?\.?\s*)?(\d{1,3})\s*min""",
        RegexOption.IGNORE_CASE,
    )
    private val pickupKw = listOf("away", "de distancia", "recog", "para buscar", "hasta el pasajero", "pickup", "pick-up", "llegada")
    private val tripKw = listOf("trip", "viaje", "recorrido", "destino", "con pasajero")
    private val offerKw = listOf("aceptar", "accept", "solicitud", "request", "nuevo viaje", "toca para aceptar", "tap to accept")
    private val bonusKw = listOf("incluid", "incl.", "incluye", "promo", "bonif", "propina", "tip", "dinámica", "boost", "surge", "+")
    private val products = listOf(
        "Uber Priority", "UberX", "Comfort", "Black", "Flash", "Moto", "XL", "Planet", "Uber Pet", "Envíos", "Priority",
    )

    /** Interpreta importes con convención argentina (punto de miles, coma decimal). */
    fun parseArsAmount(rawIn: String): Amount? {
        val raw = rawIn.trim().trimEnd('.', ',')
        if (raw.isEmpty() || !raw.first().isDigit()) return null
        val arThousands = Regex("""^\d{1,3}(\.\d{3})+(,\d{1,2})?$""")
        val arDecimal = Regex("""^\d+,\d{1,2}$""")
        val integer = Regex("""^\d+$""")
        val usThousands = Regex("""^\d{1,3}(,\d{3})+(\.\d{1,2})?$""")
        val dotDecimal = Regex("""^\d+\.\d{1,2}$""")
        return when {
            arThousands.matches(raw) -> Amount(raw.replace(".", "").replace(',', '.').toDouble(), 1.0)
            arDecimal.matches(raw) -> Amount(raw.replace(',', '.').toDouble(), 0.95)
            integer.matches(raw) -> Amount(raw.toDouble(), 0.95)
            usThousands.matches(raw) -> Amount(raw.replace(",", "").toDouble(), 0.7)
            // "12.50" puede ser 12,50 o un "12.500" truncado: ambiguo
            dotDecimal.matches(raw) -> Amount(raw.toDouble(), 0.5)
            else -> null
        }
    }

    fun parseDecimal(raw: String): Double? = raw.trim().replace(',', '.').toDoubleOrNull()

    fun normalize(text: String, source: DataSource): String {
        var t = text.replace('\u00A0', ' ').replace('\u202F', ' ').replace(Regex("""\s+"""), " ").trim()
        if (source == DataSource.OCR) {
            t = t.replace(Regex("""(?<=\d)\s?krn\b""", RegexOption.IGNORE_CASE), " km")
                .replace(Regex("""(?<=\d)\s?rnin""", RegexOption.IGNORE_CASE), " min")
                .replace(Regex("""(?<![A-Za-z0-9])S\s?(?=\d{1,3}(\.\d{3})+)"""), "\\$")
                .replace(Regex("""(?<=\d)[oO](?=\d)"""), "0")
        }
        return t
    }

    private data class Pair(val minutes: Double, val km: Double, val raw: String, val context: String, val index: Int)

    fun parse(fragmentsIn: List<String>, source: DataSource): ParseResult {
        val notes = mutableListOf<String>()
        val fragments = fragmentsIn.map { normalize(it, source) }.filter { it.isNotBlank() }
        val srcFactor = if (source == DataSource.OCR) 0.9 else 1.0

        // --- Tarifa ---
        data class Cand(val amount: Amount, val standalone: Boolean, val raw: String)
        val cands = mutableListOf<Cand>()
        fragments.forEach { f ->
            moneyRegex.findAll(f).forEach { m ->
                val plus = m.groupValues[1].isNotEmpty()
                val amountRaw = m.groupValues[2].ifEmpty { m.groupValues[3] }
                val perUnit = m.groupValues[4].isNotEmpty()
                val lower = f.lowercase()
                val bonus = plus || bonusKw.any { kw -> kw != "+" && lower.contains(kw) }
                if (!perUnit && !bonus && amountRaw.isNotEmpty()) {
                    parseArsAmount(amountRaw)?.let { a ->
                        cands += Cand(a, standalone = f.trim().length <= m.value.trim().length + 2, raw = m.value.trim())
                    }
                }
            }
        }
        var fare = Reading.MISSING
        if (cands.isNotEmpty()) {
            val distinct = cands.map { it.amount.value }.distinct()
            val standalone = cands.filter { it.standalone }
            val chosen = standalone.firstOrNull() ?: cands.first()
            var conf = chosen.amount.confidence * (if (chosen.standalone) 1.0 else 0.9)
            if (distinct.size > 1) {
                val distinctStandalone = standalone.map { it.amount.value }.distinct()
                conf *= if (distinctStandalone.size > 1 || standalone.isEmpty()) 0.6 else 0.9
                notes += "Se vieron ${distinct.size} importes distintos; se eligió ${chosen.raw}."
            }
            fare = Reading(chosen.amount.value, (conf * srcFactor).coerceIn(0.0, 1.0), source, chosen.raw)
        }

        // --- Tiempo y distancia ---
        val pairs = mutableListOf<Pair>()
        fragments.forEachIndexed { i, f ->
            // El fragmento anterior solo cuenta como contexto si es una etiqueta (corta y sin números)
            val label = fragments.getOrNull(i - 1)?.takeIf { it.length < 40 && it.none(Char::isDigit) } ?: ""
            val ctx = "$label $f"
            val seen = mutableListOf<IntRange>()
            fun addPair(m: MatchResult, hIdx: Int, minIdx: Int, distIdx: Int, unitIdx: Int) {
                val h = m.groupValues[hIdx].toDoubleOrNull() ?: 0.0
                val min = m.groupValues[minIdx].toDouble() + h * 60
                val km = toKm(parseDecimal(m.groupValues[distIdx]) ?: return, m.groupValues[unitIdx])
                pairs += Pair(min, km, m.value, ctx.lowercase(), i)
                seen += m.range
            }
            timeDistRegex.findAll(f).forEach { m -> addPair(m, 1, 2, 3, 4) }
            timeDistLooseRegex.findAll(f).forEach { m ->
                if (seen.any { it.first <= m.range.last && m.range.first <= it.last }) return@forEach
                addPair(m, 1, 2, 3, 4)
            }
            distTimeRegex.findAll(f).forEach { m ->
                if (seen.any { it.first <= m.range.last && m.range.first <= it.last }) return@forEach
                val km = toKm(parseDecimal(m.groupValues[1]) ?: return@forEach, m.groupValues[2])
                val h = m.groupValues[3].toDoubleOrNull() ?: 0.0
                val min = m.groupValues[4].toDouble() + h * 60
                pairs += Pair(min, km, m.value, ctx.lowercase(), i)
            }
        }

        fun classify(p: Pair): String? {
            val c = p.context
            val isPickup = pickupKw.any { c.contains(it) } || Regex("""(^|\s)a\s+\d""").containsMatchIn(c)
            val isTrip = tripKw.any { c.contains(it) }
            return when {
                isPickup && !isTrip -> "pickup"
                isTrip && !isPickup -> "trip"
                else -> null
            }
        }

        var pickup: Pair? = null; var pickupConf = 0.0
        var trip: Pair? = null; var tripConf = 0.0
        val classified = pairs.map { it to classify(it) }
        classified.firstOrNull { it.second == "pickup" }?.let { pickup = it.first; pickupConf = 0.95 }
        classified.firstOrNull { it.second == "trip" }?.let { trip = it.first; tripConf = 0.95 }
        val unclassified = classified.filter { it.second == null }.map { it.first }
        when {
            pickup == null && trip == null && unclassified.size == 2 -> {
                pickup = unclassified[0]; pickupConf = 0.75
                trip = unclassified[1]; tripConf = 0.75
                notes += "Recogida y viaje asignados por orden de aparición."
            }
            pickup == null && trip == null && unclassified.size == 1 -> {
                // En el resumen de Uber AR a menudo es el total (recogida + viaje).
                trip = unclassified[0]; tripConf = 0.45
                notes += "Un solo par tiempo/distancia sin etiqueta: puede ser recogida o viaje."
            }
            pickup == null && trip == null && unclassified.size > 2 -> {
                pickup = unclassified[0]; pickupConf = 0.5
                trip = unclassified[1]; tripConf = 0.5
                notes += "Demasiados pares tiempo/distancia: lectura ambigua."
            }
            pickup == null && trip != null && unclassified.isNotEmpty() -> { pickup = unclassified[0]; pickupConf = 0.85 }
            trip == null && pickup != null && unclassified.isNotEmpty() -> { trip = unclassified[0]; tripConf = 0.85 }
        }

        fun r(v: Double?, conf: Double, raw: String?) =
            if (v == null) Reading.MISSING else Reading(v, (conf * srcFactor).coerceIn(0.0, 1.0), source, raw)

        val offer = OfferData(
            fare = fare,
            pickupKm = r(pickup?.km, pickupConf, pickup?.raw),
            pickupMin = r(pickup?.minutes, pickupConf, pickup?.raw),
            tripKm = r(trip?.km, tripConf, trip?.raw),
            tripMin = r(trip?.minutes, tripConf, trip?.raw),
            product = fragments.firstNotNullOfOrNull { f ->
                products.firstOrNull { p -> f.equals(p, true) || f.startsWith("$p ", true) || f.contains(p, true) }
            },
        )
        val hasKw = fragments.any { f -> offerKw.any { f.lowercase().contains(it) } }
        val likely = fare.isPresent && (pairs.isNotEmpty() || hasKw)
        return ParseResult(offer, likely, notes)
    }

    private fun toKm(v: Double, unit: String): Double = when (unit.lowercase()) {
        "mi" -> v * 1.609344
        "m" -> v / 1000.0
        else -> v
    }
}
