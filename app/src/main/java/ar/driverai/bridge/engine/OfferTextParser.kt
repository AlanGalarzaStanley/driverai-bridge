package ar.driverai.bridge.engine

/**
 * Extrae datos de una oferta a partir de fragmentos de texto visibles (nodos de accesibilidad
 * o líneas de OCR). Es independiente de la fuente y del motor económico.
 *
 * No hay dependencia del diseño exacto de Uber: busca patrones genéricos (importe, "N min (X km)")
 * y palabras clave en español e inglés. Si el diseño cambia, se ajustan las reglas acá y sus tests.
 */
object OfferTextParser {

    data class Amount(val value: Double, val confidence: Double)

    data class ParseResult(
        val offer: OfferData,
        val isLikelyOffer: Boolean,
        val notes: List<String>,
    )

    private val moneyRegex = Regex(
        """([+])?\s*(?:(?:ARS|\$)\s*(\d[\d.,]*)(\s*/\s*\w+)?|(\d[\d.,]*)\s*ARS\b(\s*/\s*\w+)?)""",
        RegexOption.IGNORE_CASE,
    )
    private val passengerRatingRegex = Regex(
        """(?i)(?:calificaci[oó]n\s+(?:del\s+)?pasajero|pasajero\s+(?:calificaci[oó]n|rating)|(?:rider|passenger)\s+rating|rating\s+(?:del\s+)?(?:pasajero|rider|passenger))\s*[:·\-]?\s*([1-5](?:[.,]\d{1,2})?)\b""",
    )
    private val ratingLabelOnly = Regex(
        """(?i)^(?:calificaci[oó]n\s+(?:del\s+)?pasajero|pasajero\s+(?:calificaci[oó]n|rating)|(?:rider|passenger)\s+rating|rating\s+(?:del\s+)?(?:pasajero|rider|passenger))\s*[:·\-]?$""",
    )
    private val ratingValueOnly = Regex("""^\s*([1-5](?:[.,]\d{1,2})?)\s*(?:★|⭐)?\s*$""")
    private val timeDistRegex = Regex(
        """(?:(\d{1,2})\s*h(?:s|oras?)?\.?\s*)?(\d{1,3})\s*min(?:s|utos)?\.?\s*\(\s*(\d+(?:[.,]\d+)?)\s*(km|mi|m)\s*\)""",
        RegexOption.IGNORE_CASE,
    )
    private val timeDistLooseRegex = Regex(
        """(?:(\d{1,2})\s*h(?:s|oras?)?\.?\s*)?(\d{1,3})\s*min(?:s|utos)?\.?\s*[•·\-|–—]\s*(\d+(?:[.,]\d+)?)\s*(km|mi)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val distTimeRegex = Regex(
        """(\d+(?:[.,]\d+)?)\s*(km|mi)\b[^\d\n]{0,6}(?:(\d{1,2})\s*h(?:s|oras?)?\.?\s*)?(\d{1,3})\s*min""",
        RegexOption.IGNORE_CASE,
    )
    /** Resumen de tarjeta Uber: «16 min · 6.6 km · 515 ARS/km est.» */
    private val totalSummaryRegex = Regex(
        """(?i)(\d{1,3})\s*min(?:s|utos)?\.?\s*[•·\-|–—]\s*(\d+(?:[.,]\d+)?)\s*km\b(?:\s*[•·\-|–—]\s*[\d.,]+\s*(?:ARS|\$)?\s*/\s*km)?""",
    )
    private val pickupKw = listOf("away", "de distancia", "recog", "para buscar", "hasta el pasajero", "pickup", "pick-up", "llegada")
    private val tripKw = listOf("trip", "viaje", "recorrido", "destino", "con pasajero")
    private val requestContextRegex = Regex(
        """(?i)(?<![\p{L}\p{N}])(?:solicitud|request|nuevo viaje|new trip|aceptar|accept|toca para aceptar|tap to accept|acepta en|accept in|me interesa|ofrecer|ofrecé|tomar viaje|nuevo pedido|propon[eé]|propón|comfort|uberx|uber\s*x)(?![\p{L}\p{N}])""",
    )
    private val bonusKw = listOf("incluid", "incl.", "incluye", "promo", "bonif", "propina", "tip", "dinámica", "boost", "surge", "+")
    private val products = listOf(
        "Uber Priority", "UberX", "Comfort", "Black", "Flash", "Moto", "XL", "Planet", "Uber Pet", "Envíos",
        "DiDi Express", "DiDi Moto", "DiDi Entrega", "Cabify Lite", "Cabify Taxi", "Bolt", "inDrive",
    )

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

        data class Cand(val amount: Amount, val standalone: Boolean, val raw: String)
        val cands = mutableListOf<Cand>()
        val moneyInputs = fragments.toMutableList()
        val amountOnly = Regex("""^\d[\d.,]*$""")
        fragments.zipWithNext().forEach { (left, right) ->
            val a = left.trim()
            val b = right.trim()
            if ((a == "$" || a.equals("ARS", true)) && amountOnly.matches(b)) moneyInputs += "$a $b"
            else if (amountOnly.matches(a) && (b == "$" || b.equals("ARS", true))) moneyInputs += "$a $b"
        }
        moneyInputs.forEach { f ->
            moneyRegex.findAll(f).forEach { m ->
                val plus = m.groupValues[1].isNotEmpty()
                val amountText = m.groupValues[2].ifEmpty { m.groupValues[4] }
                val perUnit = m.groupValues[3].isNotEmpty() || m.groupValues[5].isNotEmpty()
                val lower = f.lowercase()
                val bonus = plus || bonusKw.any { kw -> kw != "+" && lower.contains(kw) }
                if (!perUnit && !bonus) {
                    parseArsAmount(amountText)?.let { a ->
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

        val ratingCandidates = mutableListOf<kotlin.Pair<Double, String>>()
        fragments.forEachIndexed { i, f ->
            passengerRatingRegex.findAll(f).forEach { m ->
                parseDecimal(m.groupValues[1])?.takeIf { it in 1.0..5.0 }?.let { ratingCandidates += it to m.value }
            }
            if (ratingLabelOnly.matches(f)) {
                ratingValueOnly.matchEntire(fragments.getOrNull(i + 1).orEmpty())?.groupValues?.get(1)
                    ?.let(::parseDecimal)?.takeIf { it in 1.0..5.0 }?.let { ratingCandidates += it to "${f.trim()} ${fragments[i + 1].trim()}" }
            }
        }
        val distinctRatings = ratingCandidates.map { it.first }.distinct()
        val passengerRating = when {
            distinctRatings.size == 1 -> {
                val chosen = ratingCandidates.first()
                Reading(chosen.first, (0.95 * srcFactor).coerceIn(0.0, 1.0), source, chosen.second)
            }
            distinctRatings.size > 1 -> {
                notes += "Se vieron varias calificaciones de pasajero distintas; no se eligió ninguna."
                Reading.MISSING
            }
            else -> Reading.MISSING
        }

        data class PairInput(val text: String, val previousLabel: String, val sourceIndex: Int)
        val pairInputs = fragments.mapIndexed { i, f ->
            PairInput(f, fragments.getOrNull(i - 1)?.takeIf { it.length < 40 && it.none(Char::isDigit) }.orEmpty(), i)
        }.toMutableList()
        val timeOnly = Regex("""(?i)^\s*(?:(\d{1,2})\s*h(?:s|oras?)?\.?\s*)?\d{1,3}\s*min(?:s|utos)?\.?\s*$""")
        val distanceOnly = Regex("""(?i)^\s*\d+(?:[.,]\d+)?\s*(?:km|mi|m)\s*$""")
        fragments.forEachIndexed { i, left ->
            val right = fragments.getOrNull(i + 1) ?: return@forEachIndexed
            if ((timeOnly.matches(left) && distanceOnly.matches(right)) ||
                (distanceOnly.matches(left) && timeOnly.matches(right))) {
                val previous = fragments.getOrNull(i - 1)?.takeIf { it.length < 40 && it.none(Char::isDigit) }.orEmpty()
                pairInputs += PairInput("$left · $right", previous, i)
            }
        }
        val pairs = mutableListOf<Pair>()
        pairInputs.forEach { input ->
            val i = input.sourceIndex
            val f = input.text
            val label = input.previousLabel
            val ctx = "$label $f"
            val seen = mutableListOf<IntRange>()
            fun addPair(m: MatchResult) {
                val h = m.groupValues[1].toDoubleOrNull() ?: 0.0
                val min = m.groupValues[2].toDouble() + h * 60
                val km = toKm(parseDecimal(m.groupValues[3]) ?: return, m.groupValues[4])
                pairs += Pair(min, km, m.value, ctx.lowercase(), i)
                seen += m.range
            }
            timeDistRegex.findAll(f).forEach { addPair(it) }
            timeDistLooseRegex.findAll(f).forEach { m ->
                if (seen.any { it.first <= m.range.last && m.range.first <= it.last }) return@forEach
                addPair(m)
            }
            distTimeRegex.findAll(f).forEach { m ->
                if (seen.any { it.first <= m.range.last && m.range.first <= it.last }) return@forEach
                val km = toKm(parseDecimal(m.groupValues[1]) ?: return@forEach, m.groupValues[2])
                val h = m.groupValues[3].toDoubleOrNull() ?: 0.0
                val min = m.groupValues[4].toDouble() + h * 60
                pairs += Pair(min, km, m.value, ctx.lowercase(), i)
            }
        }

        // Resumen total tipo Uber (sin etiqueta Viaje/Recogida).
        var totalSummary: Pair? = null
        fragments.forEachIndexed { i, f ->
            totalSummaryRegex.find(f)?.let { m ->
                val min = m.groupValues[1].toDouble()
                val km = parseDecimal(m.groupValues[2]) ?: return@let
                val candidate = Pair(min, km, m.value, f.lowercase(), i)
                if (totalSummary == null || candidate.minutes >= totalSummary!!.minutes) totalSummary = candidate
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
                trip = unclassified[0]; tripConf = 0.45
                notes += "Un solo par tiempo/distancia sin etiqueta: puede ser recogida, viaje o resumen total; verificá manualmente."
            }
            pickup == null && trip == null && unclassified.size > 2 -> {
                pickup = unclassified[0]; pickupConf = 0.5
                trip = unclassified[1]; tripConf = 0.5
                notes += "Demasiados pares tiempo/distancia: lectura ambigua."
            }
            pickup == null && trip != null && unclassified.isNotEmpty() -> { pickup = unclassified[0]; pickupConf = 0.85 }
            trip == null && pickup != null && unclassified.isNotEmpty() -> { trip = unclassified[0]; tripConf = 0.85 }
        }

        // Si falta viaje etiquetado pero hay total + recogida, derivar viaje.
        if (trip == null && totalSummary != null && pickup != null) {
            val tripMin = (totalSummary!!.minutes - pickup!!.minutes).coerceAtLeast(1.0)
            val tripKm = (totalSummary!!.km - pickup!!.km).coerceAtLeast(0.3)
            if (tripMin <= totalSummary!!.minutes && tripKm <= totalSummary!!.km) {
                trip = Pair(tripMin, tripKm, "derivado de total − recogida", "viaje", -1)
                tripConf = 0.7
                notes += "Viaje estimado como total menos recogida (resumen de tarjeta)."
            }
        } else if (trip == null && totalSummary != null && pickup == null) {
            // Sin recogida clara: usar el total como viaje con menor confianza (mejor que no leer).
            trip = totalSummary
            tripConf = 0.55
            notes += "Se usó el resumen total de la tarjeta como duración/distancia del viaje."
        }

        fun r(v: Double?, conf: Double, raw: String?) =
            if (v == null) Reading.MISSING else Reading(v, (conf * srcFactor).coerceIn(0.0, 1.0), source, raw)

        val offer = OfferData(
            fare = fare,
            pickupKm = r(pickup?.km, pickupConf, pickup?.raw),
            pickupMin = r(pickup?.minutes, pickupConf, pickup?.raw),
            tripKm = r(trip?.km, tripConf, trip?.raw),
            tripMin = r(trip?.minutes, tripConf, trip?.raw),
            product = fragments.firstNotNullOfOrNull { f -> products.firstOrNull { p -> f.equals(p, true) || f.startsWith("$p ", true) } },
            passengerRating = passengerRating,
        )
        val hasRequestContext = fragments.any { requestContextRegex.containsMatchIn(it) }
        val hasCoreTrip = offer.tripKm.isPresent && offer.tripMin.isPresent
        val hasPickupAndMoney = offer.pickupKm.isPresent && fare.isPresent
        // CTA + tarifa + (viaje completo o recogida con total ya derivado arriba).
        val likely = hasRequestContext && fare.isPresent && (hasCoreTrip || (hasPickupAndMoney && totalSummary != null))
        return ParseResult(offer, likely, notes)
    }

    private fun toKm(v: Double, unit: String): Double = when (unit.lowercase()) {
        "mi" -> v * 1.609344
        "m" -> v / 1000.0
        else -> v
    }
}
