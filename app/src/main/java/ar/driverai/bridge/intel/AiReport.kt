package ar.driverai.bridge.intel

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "Actualizar con IA": DriverAI no navega la web ni llama a un LLM. Arma un pedido para que el
 * conductor lo pegue en la IA de su confianza (con búsqueda web) y luego importa la respuesta.
 *
 * Reglas de seguridad del import:
 * - Solo se lee un bloque JSON con un esquema cerrado; el texto libre se ignora (no se ejecuta nada).
 * - Cada señal exige fuente, horario parseable dentro de la ventana y zona del catálogo (salvo
 *   tipos que afectan a toda la ciudad).
 * - La confianza se limita a [MAX_CONFIDENCE] (sin URL: [MAX_CONFIDENCE_NO_URL]): una IA externa
 *   puede equivocarse y la app no lo verificó.
 * - El prompt no incluye ubicación GPS, nombres, teléfonos ni capturas.
 */
object AiReport {
    const val MAX_CONFIDENCE = 0.55
    const val MAX_CONFIDENCE_NO_URL = 0.35
    const val MAX_SIGNALS = 25
    const val MAX_INPUT_CHARS = 60_000
    /** Pasado este tiempo el informe se marca como viejo en la UI. */
    const val STALE_AFTER_MS = 6 * 3_600_000L

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val es = Locale.forLanguageTag("es-AR")

    fun prompt(city: CityPack, now: Long, horizonHours: Int = 12): String {
        val tz = ZoneId.of(city.timeZone)
        val local = Instant.ofEpochMilli(now).atZone(tz)
        val until = local.plusHours(horizonHours.toLong())
        val human = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM yyyy, HH:mm", es)
        val iso = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        val zones = city.zones.joinToString("\n") { z ->
            "- ${z.id} = ${z.name}" + if (z.aliases.isNotEmpty()) " (${z.aliases.take(5).joinToString(", ")})" else ""
        }
        val venues = city.venues.joinToString("\n") { v -> "- ${v.name} → ${v.zoneId}" }
        return """Sos un asistente de investigación para un conductor de aplicaciones (Uber, DiDi, Cabify, inDrive) en ${city.name}.
Buscá en la web información VIGENTE que pueda mover la demanda de viajes entre ${human.format(local)} y ${human.format(until)} (hora local, ${city.timeZone}).

Buscá y verificá con fuentes actuales:
1. Partidos de fútbol y otros deportes en estadios de la zona (hora de inicio).
2. Recitales, shows, festivales, ferias y eventos masivos (hora de inicio y fin estimado).
3. Paros, medidas de fuerza o interrupciones de colectivos, subte o trenes.
4. Alertas meteorológicas oficiales (lluvia, tormenta, calor extremo).
5. Arribos de cruceros y picos de vuelos en Aeroparque o Ezeiza, solo si hay datos publicados.
6. Cortes de calles o manifestaciones grandes confirmadas.

REGLAS
- No inventes nada. Si no encontrás datos confiables para un punto, no lo incluyas.
- Cada señal debe tener una fuente concreta (medio, organismo, club, ticketera) y su enlace.
- Usá horarios en ISO-8601 con zona horaria (ejemplo: ${iso.format(local.withMinute(0).withSecond(0).withNano(0))}).
- "zona" debe ser uno de los ids de la lista. Usá "ciudad" si afecta a todo el AMBA (paro, clima).

ZONAS (id = nombre, referencias)
$zones

ESTADIOS Y LUGARES CONOCIDOS (lugar → id de zona)
$venues

FORMATO DE RESPUESTA
Respondé con un único bloque de código JSON con exactamente esta estructura, y después, si querés, un resumen de 3 líneas:
```json
{
  "driverai": 1,
  "ciudad": "${city.id}",
  "generado": "${iso.format(local.withSecond(0).withNano(0))}",
  "senales": [
    {
      "tipo": "partido | show | paro | clima | vuelos | crucero | corte | otro",
      "titulo": "texto corto, por ejemplo: River vs Boca",
      "lugar": "nombre del estadio o lugar, si aplica",
      "zona": "id de zona o ciudad",
      "inicio": "ISO-8601 con zona horaria",
      "fin": "ISO-8601 con zona horaria o null",
      "intensidad": "baja | media | alta",
      "confianza": "baja | media | alta",
      "fuente": "nombre de la fuente",
      "url": "https://..."
    }
  ],
  "resumen": "una frase con lo más importante"
}
```
Si no encontrás nada confiable, devolvé "senales": [] y explicalo en "resumen"."""
    }

    data class Rejected(val title: String, val reason: String)

    data class Result(
        val signals: List<DemandSignal>,
        val rejected: List<Rejected>,
        val summary: String?,
        /** Error global (no se encontró JSON, ciudad equivocada…). Si no es null, [signals] está vacío. */
        val error: String? = null,
    ) {
        val ok: Boolean get() = error == null
    }

    fun parse(raw: String, city: CityPack, now: Long): Result {
        val text = raw.take(MAX_INPUT_CHARS)
        val block = extractJson(text) ?: return Result(emptyList(), emptyList(), null, "No encontré el bloque JSON en la respuesta. Pedile a la IA que responda con el formato del pedido.")
        val root = runCatching { json.parseToJsonElement(block) }.getOrNull()
        val obj = when (root) {
            is JsonObject -> root
            is JsonArray -> JsonObject(mapOf("senales" to root))
            else -> null
        } ?: return Result(emptyList(), emptyList(), null, "El JSON de la respuesta está incompleto o mal formado.")
        val declaredCity = obj.str("ciudad")
        if (declaredCity != null && declaredCity.isNotBlank() && CityPack.fold(declaredCity) != city.id && !CityPack.fold(city.name).contains(CityPack.fold(declaredCity))) {
            return Result(emptyList(), emptyList(), null, "El informe es de otra ciudad ($declaredCity).")
        }
        val items = (obj["senales"] ?: obj["señales"] ?: obj["signals"]) as? JsonArray
            ?: return Result(emptyList(), emptyList(), obj.str("resumen")?.take(280), "El informe no tiene la lista \"senales\".")
        val tz = ZoneId.of(city.timeZone)
        val accepted = mutableListOf<DemandSignal>()
        val rejected = mutableListOf<Rejected>()
        items.forEach { el ->
            val o = el as? JsonObject
            if (o == null) { rejected += Rejected("(sin datos)", "formato inválido"); return@forEach }
            val title = clean(o.str("titulo") ?: o.str("title")).take(80)
            val label = title.ifBlank { "(sin título)" }
            val kind = kindOf(o.str("tipo") ?: o.str("kind"))
            val source = clean(o.str("fuente") ?: o.str("source")).take(60)
            val url = (o.str("url") ?: o.str("sourceUrl"))?.trim()?.takeIf { it.startsWith("https://") && it.length <= 300 && !it.any(Char::isWhitespace) }
            val start = time(o.str("inicio") ?: o.str("startAt"), tz, now)
            val end = time(o.str("fin") ?: o.str("endAt"), tz, now)
            val reason = when {
                title.isBlank() -> "sin título"
                source.isBlank() -> "sin fuente"
                kind == null -> "tipo desconocido"
                start == null -> "horario ilegible"
                start < now - 12 * 3_600_000L || start > now + 48 * 3_600_000L -> "fuera de la ventana de 48 h"
                end != null && end < start -> "fin anterior al inicio"
                else -> null
            }
            if (reason != null) { rejected += Rejected(label, reason); return@forEach }
            val venue = city.matchVenue(o.str("lugar") ?: o.str("venue"), null)
                ?: city.matchVenue(title, null)
            val zoneRaw = o.str("zona") ?: o.str("zoneId")
            val zone = resolveZone(city, zoneRaw) ?: venue?.let { city.zone(it.zoneId) }
            val cityWide = zoneRaw == null || CityPack.fold(zoneRaw) in CITY_WIDE
            if (zone == null && !(cityWide && kind in CITY_WIDE_KINDS)) {
                rejected += Rejected(label, "zona fuera del catálogo")
                return@forEach
            }
            val conf = level(o["confianza"] ?: o["confidence"], 0.45)
                .coerceAtMost(if (url == null) MAX_CONFIDENCE_NO_URL else MAX_CONFIDENCE)
            val intensity = level(o["intensidad"] ?: o["intensity"], 0.6)
            val id = "ia-" + Integer.toHexString("${kind!!.name}|${CityPack.fold(title)}|$start|${zone?.id}".hashCode())
            if (accepted.any { it.id == id }) return@forEach
            accepted += DemandSignal(
                id = id,
                kind = kind,
                origin = SignalOrigin.IA_EXTERNA,
                title = title,
                zoneId = zone?.id,
                startAt = start!!,
                endAt = end,
                intensity = intensity,
                confidence = conf,
                sourceName = "IA · $source",
                sourceUrl = url,
                fetchedAt = now,
                venueId = venue?.takeIf { it.zoneId == zone?.id }?.id,
                capacity = venue?.takeIf { it.zoneId == zone?.id }?.capacity?.takeIf { it > 0 },
            )
        }
        val list = accepted.filter { it.isValid }.sortedBy { it.startAt }.take(MAX_SIGNALS)
        return Result(list, rejected, clean(obj.str("resumen") ?: obj.str("summary")).take(280).ifBlank { null })
    }

    /** Toma el primer bloque ```json …```; si no hay, el primer objeto/array balanceado. */
    internal fun extractJson(text: String): String? {
        Regex("```(?:json|JSON)?\\s*([\\s\\S]*?)```").findAll(text).map { it.groupValues[1].trim() }
            .firstOrNull { it.startsWith("{") || it.startsWith("[") }?.let { return it }
        val start = text.indexOfFirst { it == '{' || it == '[' }.takeIf { it >= 0 } ?: return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                if (escape) escape = false
                else if (c == '\\') escape = true
                else if (c == '"') inString = false
                continue
            }
            when (c) {
                '"' -> inString = true
                '{', '[' -> depth++
                '}', ']' -> { depth--; if (depth == 0) return text.substring(start, i + 1) }
            }
        }
        return null
    }

    private val CITY_WIDE = setOf("ciudad", "amba", "todas", "toda la ciudad", "general", "")
    private val CITY_WIDE_KINDS = setOf(SignalKind.PARO_TRANSPORTE, SignalKind.CLIMA, SignalKind.OTRO)

    internal fun kindOf(raw: String?): SignalKind? {
        val k = raw?.let(CityPack::fold) ?: return null
        return when {
            listOf("partido", "futbol", "deport", "match", "basquet", "rugby").any { k.contains(it) } -> SignalKind.EVENTO_DEPORTIVO
            listOf("show", "recital", "concierto", "festival", "evento", "teatro", "feria", "expo").any { k.contains(it) } -> SignalKind.EVENTO_SHOW
            listOf("paro", "huelga", "medida de fuerza", "transporte").any { k.contains(it) } -> SignalKind.PARO_TRANSPORTE
            listOf("clima", "lluvia", "tormenta", "alerta", "calor", "meteo").any { k.contains(it) } -> SignalKind.CLIMA
            listOf("vuelo", "aeropuerto", "aeroparque", "arribo").any { k.contains(it) } -> SignalKind.VUELOS
            listOf("crucero", "buque", "barco", "puerto").any { k.contains(it) } -> SignalKind.CRUCERO
            listOf("corte", "manifest", "marcha", "piquete", "transito", "otro").any { k.contains(it) } -> SignalKind.OTRO
            else -> null
        }
    }

    private fun resolveZone(city: CityPack, raw: String?): CityZone? {
        if (raw.isNullOrBlank()) return null
        city.zone(raw.trim().lowercase())?.let { return it }
        return city.zoneByName(raw)
    }

    private fun level(el: JsonElement?, default: Double): Double {
        val p = el as? JsonPrimitive ?: return default
        if (p is JsonNull) return default
        p.doubleOrNull?.let { v -> return (if (v > 1.0) v / 100.0 else v).takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: default }
        return when (CityPack.fold(p.contentOrNull.orEmpty())) {
            "muy baja" -> 0.15
            "baja" -> 0.35
            "media", "moderada" -> 0.6
            "alta" -> 0.85
            "muy alta" -> 1.0
            else -> default
        }
    }

    internal fun time(raw: String?, tz: ZoneId, now: Long): Long? {
        val s = raw?.trim()?.takeIf { it.isNotBlank() && it != "null" } ?: return null
        runCatching { return OffsetDateTime.parse(s).toInstant().toEpochMilli() }
        runCatching { return ZonedDateTime.parse(s).toInstant().toEpochMilli() }
        runCatching { return LocalDateTime.parse(s.replace(' ', 'T')).atZone(tz).toInstant().toEpochMilli() }
        runCatching {
            val t = LocalTime.parse(if (s.length == 4) "0$s" else s)
            val today = Instant.ofEpochMilli(now).atZone(tz).toLocalDate()
            var at = t.atDate(today).atZone(tz)
            if (at.toInstant().toEpochMilli() < now - 6 * 3_600_000L) at = at.plusDays(1)
            return at.toInstant().toEpochMilli()
        }
        runCatching { return LocalDate.parse(s).atStartOfDay(tz).plusHours(12).toInstant().toEpochMilli() }
        return null
    }

    /** Quita controles y marcas: el texto se muestra tal cual y nunca se interpreta como instrucciones. */
    private fun clean(s: String?): String =
        s.orEmpty().replace(Regex("[\\p{Cntrl}<>`]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun JsonObject.str(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
}
