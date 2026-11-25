package ar.driverai.bridge.capture

/**
 * Reduce los textos de accesibilidad a fragmentos útiles para calibrar ofertas sin conservar
 * texto libre, nombres, direcciones ni destinos. Solo devuelve etiquetas explícitamente permitidas
 * y tokens numéricos de tarifa, duración o distancia.
 */
object DiagnosticTextSanitizer {
    private val money = Regex("""(?i)(?:ARS\s*)?\$\s*\d[\d.,]*|\d[\d.,]*\s*ARS\b""")
    private val duration = Regex("""(?i)(?:\d{1,2}\s*h(?:s|oras?)?\.?\s*)?\d{1,3}\s*min(?:s|utos)?\.?""")
    private val distance = Regex("""(?i)\d+(?:[.,]\d+)?\s*(?:km|mi|m)\b""")
    private val passengerRating = Regex(
        """(?i)(?:calificaci[oó]n\s+(?:del\s+)?pasajero|pasajero\s+(?:calificaci[oó]n|rating)|(?:rider|passenger)\s+rating|rating\s+(?:del\s+)?(?:pasajero|rider|passenger))\s*[:·\-]?\s*[1-5](?:[.,]\d{1,2})?""",
    )
    private val safeLabels = listOf(
        "tarifa", "fare", "precio", "oferta", "offer", "viaje", "trip", "recogida", "pickup",
        "uberx", "uber x", "comfort", "black", "xl", "solicitud", "request", "tiempo", "distancia",
    )
    private val safeLabelPattern = Regex(
        "(?i)(?<![\\p{L}\\p{N}])(?:${safeLabels.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }})(?![\\p{L}\\p{N}])"
    )

    /** Devuelve un máximo de 40 líneas y nunca incluye texto arbitrario de la interfaz. */
    fun sanitize(fragments: List<String>): List<String> {
        val output = linkedSetOf<String>()
        fragments.take(200).forEach { raw ->
            val text = raw
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ')
                .replace(Regex("\\s+"), " ")
                .trim()
            if (text.isEmpty()) return@forEach

            val numericTokens = buildList {
                money.findAll(text).forEach { add(it.range.first to it.value.trim()) }
                duration.findAll(text).forEach { add(it.range.first to it.value.trim()) }
                distance.findAll(text).forEach { add(it.range.first to it.value.trim()) }
                passengerRating.findAll(text).forEach { add(it.range.first to it.value.trim()) }
            }.sortedBy { it.first }.map { it.second }.distinct()

            val labels = safeLabelPattern.findAll(text)
                .map { it.value.lowercase().replace(Regex("\\s+"), " ") }
                .distinct()
                .toList()

            when {
                numericTokens.isNotEmpty() -> {
                    val safeParts = (labels + numericTokens).distinct()
                    output += safeParts.joinToString(" · ")
                }
                labels.size == 1 && text.equals(labels.single(), ignoreCase = true) -> output += labels.single()
            }
        }
        return output.take(40)
    }
}
