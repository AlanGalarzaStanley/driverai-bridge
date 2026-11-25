package ar.driverai.bridge.intel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import ar.driverai.bridge.backend.ApiResult
import ar.driverai.bridge.data.AppJson
import ar.driverai.bridge.data.HistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Estado de cada fuente para mostrar en la app: nada se presenta como verificado si falló. */
@Serializable
data class SourceStatus(
    val name: String,
    val ok: Boolean,
    val message: String,
    val checkedAt: Long,
    val items: Int = 0,
)

@Serializable
data class IntelCache(
    val remoteSignals: List<DemandSignal> = emptyList(),
    val manualSignals: List<DemandSignal> = emptyList(),
    val holidays: List<String> = emptyList(),
    val holidayNames: Map<String, String> = emptyMap(),
    val sources: List<SourceStatus> = emptyList(),
    val refreshedAt: Long? = null,
    /** Último informe importado desde una IA externa: reemplaza al anterior en cada import. */
    val aiSignals: List<DemandSignal> = emptyList(),
    val aiImportedAt: Long? = null,
    val aiSummary: String? = null,
)

/**
 * Orquesta las señales del radar. Local-first: el caché vive en DataStore y el radar funciona
 * sin red con señales guardadas, feriados fijos, carga manual y tu historial.
 * No envía ubicación ni datos del conductor a ninguna fuente.
 */
class IntelRepository(
    private val store: DataStore<Preferences>,
    private val sports: SportsFixturesClient = SportsFixturesClient(),
    private val holidaysClient: HolidaysClient = HolidaysClient(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val key = stringPreferencesKey("intel_cache_v1")
    val cache: Flow<IntelCache> = store.data.map { decode(it[key]) }

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    suspend fun current(): IntelCache = cache.first()

    /**
     * Actualiza las fuentes públicas. [sportsEnabled] permite desactivar fixtures en builds sin clave.
     * Las fallas se registran por fuente y no borran el último dato bueno.
     */
    suspend fun refresh(city: CityPack, sportsApiKey: String?, sportsEnabled: Boolean): IntelCache {
        _refreshing.value = true
        try {
            val now = clock()
            val prev = current()
            val statuses = mutableListOf<SourceStatus>()
            var remote = prev.remoteSignals.filter { DemandRadar.impactWindow(it).second > now }

            if (sportsEnabled) {
                when (val r = sports.upcoming(city, sportsApiKey.orEmpty())) {
                    is ApiResult.Ok -> {
                        remote = (remote.filterNot { it.sourceName == "TheSportsDB" } + r.value)
                        statuses += SourceStatus(
                            "Fixtures de fútbol (TheSportsDB)", true,
                            if (r.value.isEmpty()) "Sin partidos próximos en estadios del catálogo" else "${r.value.size} partidos en estadios del AMBA" +
                                if (sportsApiKey.isNullOrBlank()) " · clave de prueba: cobertura limitada" else "",
                            now, r.value.size,
                        )
                    }
                    is ApiResult.Fail -> statuses += SourceStatus("Fixtures de fútbol (TheSportsDB)", false, r.message + " · se conservan datos previos", now)
                }
            } else {
                statuses += SourceStatus("Fixtures de fútbol (TheSportsDB)", false, "Desactivado: cargá tu clave en Ajustes para usarlo en esta versión", now)
            }

            val year = Instant.ofEpochMilli(now).atZone(ZoneId.of(city.timeZone)).year
            var holidays = prev.holidays
            var holidayNames = prev.holidayNames
            when (val h = holidaysClient.year(year)) {
                is ApiResult.Ok -> {
                    holidays = h.value.map { it.fecha }.distinct()
                    holidayNames = h.value.associate { it.fecha to (it.nombre ?: "Feriado") }
                    statuses += SourceStatus("Feriados nacionales (ArgentinaDatos)", true, "${holidays.size} feriados $year", now, holidays.size)
                }
                is ApiResult.Fail -> statuses += SourceStatus(
                    "Feriados nacionales (ArgentinaDatos)", false,
                    h.message + if (holidays.isEmpty()) " · se usan feriados fijos" else " · se conservan datos previos", now,
                )
            }

            val next = prev.copy(
                remoteSignals = remote,
                holidays = holidays,
                holidayNames = holidayNames,
                sources = statuses,
                refreshedAt = now,
                manualSignals = prev.manualSignals.filter { DemandRadar.impactWindow(it).second > now },
                aiSignals = prev.aiSignals.filter { DemandRadar.impactWindow(it).second > now },
            )
            save(next)
            return next
        } finally {
            _refreshing.value = false
        }
    }

    suspend fun addManual(signal: DemandSignal): Boolean {
        if (!signal.isValid || signal.origin != SignalOrigin.CONDUCTOR) return false
        store.edit { p ->
            val c = decode(p[key])
            p[key] = encode(c.copy(manualSignals = (listOf(signal) + c.manualSignals.filterNot { it.id == signal.id }).take(MAX_MANUAL)))
        }
        return true
    }

    suspend fun removeManual(id: String) {
        store.edit { p ->
            val c = decode(p[key])
            p[key] = encode(c.copy(manualSignals = c.manualSignals.filterNot { it.id == id }))
        }
    }

    /** Reemplaza el informe IA anterior; solo acepta señales de origen [SignalOrigin.IA_EXTERNA]. */
    suspend fun importAi(signals: List<DemandSignal>, summary: String?): Int {
        val valid = signals.filter { it.isValid && it.origin == SignalOrigin.IA_EXTERNA }.take(AiReport.MAX_SIGNALS)
        val now = clock()
        store.edit { p ->
            val c = decode(p[key])
            p[key] = encode(c.copy(aiSignals = valid, aiImportedAt = now, aiSummary = summary?.take(280)))
        }
        return valid.size
    }

    suspend fun clearAi() {
        store.edit { p ->
            val c = decode(p[key])
            p[key] = encode(c.copy(aiSignals = emptyList(), aiImportedAt = null, aiSummary = null))
        }
    }

    suspend fun removeAi(id: String) {
        store.edit { p ->
            val c = decode(p[key])
            p[key] = encode(c.copy(aiSignals = c.aiSignals.filterNot { it.id == id }))
        }
    }

    private suspend fun save(c: IntelCache) { store.edit { it[key] = encode(c) } }

    companion object {
        const val MAX_MANUAL = 40
        private val ser = IntelCache.serializer()

        fun decode(raw: String?): IntelCache =
            if (raw.isNullOrBlank()) IntelCache() else runCatching { AppJson.decodeFromString(ser, raw) }.getOrDefault(IntelCache())

        fun encode(c: IntelCache): String = AppJson.encodeToString(ser, c)

        fun holidaySet(c: IntelCache, years: Iterable<Int>): Set<LocalDate> =
            c.holidays.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet() +
                years.flatMap { HolidaysClient.fixedDates(it) }

        /** Señal manual a partir de una plantilla rápida del conductor. */
        fun manualSignal(
            kind: SignalKind,
            title: String,
            zoneId: String?,
            startAt: Long,
            endAt: Long?,
            intensity: Double,
            now: Long,
            venue: Venue? = null,
        ): DemandSignal = DemandSignal(
            id = "manual-" + UUID.randomUUID().toString(),
            kind = kind,
            origin = SignalOrigin.CONDUCTOR,
            title = title.trim().take(80),
            zoneId = zoneId,
            startAt = startAt,
            endAt = endAt,
            intensity = intensity.coerceIn(0.0, 1.0),
            confidence = 0.6,
            sourceName = "Cargada por vos",
            fetchedAt = now,
            venueId = venue?.id,
            capacity = venue?.capacity?.takeIf { it > 0 },
        )

        /** Construye el patrón personal con las ofertas de los últimos [days] días. */
        fun personalPattern(entries: List<HistoryEntry>, now: Long, tz: ZoneId, days: Int = 60): PersonalPattern? {
            val since = now - days * 86_400_000L
            val usable = entries.filter { it.at in since..now && it.grossPerHour != null && it.grossPerHour.isFinite() && it.grossPerHour > 0 }
            if (usable.size < PersonalPattern.MIN_SAMPLES * 2) return null
            val overall = usable.map { it.grossPerHour!! }.average()
            val distinctDays = usable.map { Instant.ofEpochMilli(it.at).atZone(tz).toLocalDate() }.distinct().size.coerceAtLeast(1)
            val byHour = usable.groupBy { Instant.ofEpochMilli(it.at).atZone(tz).hour }.mapValues { (_, list) ->
                PersonalPattern.HourStat(list.size, list.map { it.grossPerHour!! }.average(), list.size.toDouble() / distinctDays)
            }
            return PersonalPattern(overall, byHour, usable.size)
        }

        fun allSignals(c: IntelCache, extra: List<DemandSignal> = emptyList()): List<DemandSignal> =
            c.remoteSignals + c.manualSignals + c.aiSignals + extra
    }
}
