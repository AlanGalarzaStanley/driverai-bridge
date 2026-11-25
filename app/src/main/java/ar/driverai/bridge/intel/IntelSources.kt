package ar.driverai.bridge.intel

import ar.driverai.bridge.backend.ApiResult
import ar.driverai.bridge.data.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

internal fun defaultIntelHttp(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(12, TimeUnit.SECONDS)
    .callTimeout(15, TimeUnit.SECONDS)
    .build()

/**
 * Fixtures de fútbol desde TheSportsDB. La clave "123" es la clave pública de prueba (resultados
 * limitados); para producción el conductor u operador carga su propia clave. Solo se usan
 * nombre del partido, estadio y horario: datos públicos sin información personal.
 */
class SportsFixturesClient(
    private val client: OkHttpClient = defaultIntelHttp(),
    private val baseUrl: String = "https://www.thesportsdb.com/api/v1/json",
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Serializable
    private data class Event(
        val idEvent: String? = null,
        val strEvent: String? = null,
        val strHomeTeam: String? = null,
        val strAwayTeam: String? = null,
        val strVenue: String? = null,
        val strTimestamp: String? = null,
        val dateEvent: String? = null,
        val strTime: String? = null,
        val strStatus: String? = null,
        val strLeague: String? = null,
    )

    @Serializable
    private data class Response(val events: List<Event>? = null)

    suspend fun upcoming(city: CityPack, apiKey: String): ApiResult<List<DemandSignal>> = withContext(Dispatchers.IO) {
        val key = apiKey.trim().ifEmpty { DEMO_KEY }
        if (!key.matches(Regex("^[A-Za-z0-9]{1,64}$"))) return@withContext ApiResult.Fail(ApiResult.Kind.BACKEND, "Clave de TheSportsDB inválida")
        val out = mutableListOf<DemandSignal>()
        var lastError: ApiResult.Fail? = null
        for (league in city.sportsLeagueIds) {
            val url = "$baseUrl/$key/eventsnextleague.php?id=$league"
            val req = Request.Builder().url(url).get().header("Accept", "application/json").build()
            try {
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) { lastError = ApiResult.Fail(ApiResult.Kind.HTTP, "HTTP ${resp.code}"); return@use }
                    val parsed = runCatching { AppJson.decodeFromString(Response.serializer(), body) }.getOrNull()
                    if (parsed == null) { lastError = ApiResult.Fail(ApiResult.Kind.PARSE, "Respuesta de fixtures inesperada"); return@use }
                    out += parsed.events.orEmpty().mapNotNull { toSignal(it, city) }
                }
            } catch (_: IOException) {
                lastError = ApiResult.Fail(ApiResult.Kind.NETWORK, "Sin conexión con TheSportsDB")
            }
        }
        if (out.isEmpty() && lastError != null) lastError!! else ApiResult.Ok(out.distinctBy { it.id })
    }

    private fun toSignal(e: Event, city: CityPack): DemandSignal? {
        val id = e.idEvent?.takeIf { it.isNotBlank() } ?: return null
        val status = e.strStatus?.uppercase()
        if (status != null && status in setOf("FT", "AET", "PEN", "CANC", "PST", "ABD")) return null
        val start = parseUtc(e.strTimestamp) ?: parseUtc(e.dateEvent?.let { d -> e.strTime?.let { "${d}T$it" } }) ?: return null
        val venue = city.matchVenue(e.strVenue, e.strHomeTeam) ?: return null
        val title = e.strEvent?.takeIf { it.isNotBlank() } ?: listOfNotNull(e.strHomeTeam, e.strAwayTeam).joinToString(" vs ")
        return DemandSignal(
            id = "tsdb-$id",
            kind = SignalKind.EVENTO_DEPORTIVO,
            origin = SignalOrigin.API_PUBLICA,
            title = "$title · ${venue.name}",
            zoneId = venue.zoneId,
            startAt = start,
            intensity = DemandRadar.intensityForCapacity(venue.capacity),
            // Horario de inicio publicado; la hora de salida y la asistencia son estimaciones.
            confidence = 0.75,
            sourceName = "TheSportsDB",
            sourceUrl = "https://www.thesportsdb.com/event/$id",
            fetchedAt = clock(),
            venueId = venue.id,
            capacity = venue.capacity,
        )
    }

    companion object {
        const val DEMO_KEY = "123"

        /** TheSportsDB publica strTimestamp en UTC sin zona ("2026-11-26T00:00:00"). */
        fun parseUtc(raw: String?): Long? {
            if (raw.isNullOrBlank()) return null
            val clean = raw.trim().removeSuffix("Z").substringBefore("+")
            return runCatching { LocalDateTime.parse(clean).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
        }
    }
}

/**
 * Feriados nacionales desde ArgentinaDatos (API abierta). Si no hay red, se usan los
 * feriados inamovibles de fecha fija, que no dependen de decretos anuales.
 */
class HolidaysClient(
    private val client: OkHttpClient = defaultIntelHttp(),
    private val baseUrl: String = "https://api.argentinadatos.com/v1/feriados",
) {
    @Serializable
    data class Holiday(val fecha: String, val tipo: String? = null, val nombre: String? = null)

    suspend fun year(year: Int): ApiResult<List<Holiday>> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$baseUrl/$year").get().header("Accept", "application/json").build()
        try {
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext ApiResult.Fail(ApiResult.Kind.HTTP, "HTTP ${resp.code}")
                val list = runCatching { AppJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Holiday.serializer()), body) }.getOrNull()
                    ?: return@withContext ApiResult.Fail(ApiResult.Kind.PARSE, "Respuesta de feriados inesperada")
                ApiResult.Ok(list.filter { runCatching { LocalDate.parse(it.fecha) }.isSuccess })
            }
        } catch (_: IOException) {
            ApiResult.Fail(ApiResult.Kind.NETWORK, "Sin conexión con ArgentinaDatos")
        }
    }

    companion object {
        /** Feriados nacionales inamovibles de fecha fija (Ley 27.399). */
        fun fixedDates(year: Int): Set<LocalDate> = listOf(
            1 to 1, 3 to 24, 4 to 2, 5 to 1, 5 to 25, 6 to 20, 7 to 9, 12 to 8, 12 to 25,
        ).map { (m, d) -> LocalDate.of(year, m, d) }.toSet()
    }
}

/** Convierte el pronóstico de lluvia en una señal de ciudad (solo si la fuente de clima está habilitada). */
object WeatherSignal {
    fun fromRainProbability(probabilityPct: Int?, now: Long, fetchedAt: Long): DemandSignal? {
        val p = probabilityPct ?: return null
        if (p < 50) return null
        return DemandSignal(
            id = "rain-${now / 3_600_000L}",
            kind = SignalKind.CLIMA,
            origin = SignalOrigin.API_PUBLICA,
            title = "Probabilidad de lluvia $p % en las próximas horas",
            zoneId = null,
            startAt = now,
            endAt = now + 3 * 3_600_000L,
            intensity = ((p - 50) / 50.0).coerceIn(0.2, 1.0),
            confidence = (p / 100.0 * 0.8).coerceIn(0.0, 1.0),
            sourceName = "Open-Meteo (CC BY 4.0)",
            sourceUrl = "https://open-meteo.com/",
            fetchedAt = fetchedAt,
        )
    }
}
