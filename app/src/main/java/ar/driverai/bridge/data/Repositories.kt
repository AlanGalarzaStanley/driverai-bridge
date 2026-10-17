package ar.driverai.bridge.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import ar.driverai.bridge.engine.DataSource
import ar.driverai.bridge.engine.DriverSettings
import ar.driverai.bridge.engine.Evaluation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true; coerceInputValues = true }

/** Configuración del conductor, persistida localmente (DataStore, almacenamiento privado de la app). */
class SettingsRepository(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("driver_settings_v1")

    val settings: Flow<DriverSettings> = store.data.map { p -> decode(p[key]) }

    suspend fun current(): DriverSettings = settings.first()

    suspend fun update(transform: (DriverSettings) -> DriverSettings) {
        store.edit { p -> p[key] = AppJson.encodeToString(DriverSettings.serializer(), transform(decode(p[key]))) }
    }

    companion object {
        fun decode(raw: String?): DriverSettings =
            if (raw.isNullOrBlank()) DriverSettings()
            else runCatching { AppJson.decodeFromString(DriverSettings.serializer(), raw) }.getOrDefault(DriverSettings())
    }
}

/**
 * Historial mínimo: solo números, veredicto, fuente y hora. NO guarda direcciones,
 * nombres de pasajeros, capturas ni ubicación.
 */
@Serializable
data class HistoryEntry(
    val at: Long,
    val verdict: String,
    val fare: Double?,
    val totalKm: Double?,
    val totalMin: Double?,
    val grossPerKm: Double?,
    val grossPerHour: Double?,
    val source: DataSource,
    val taken: Boolean = false,
)

class HistoryRepository(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("history_v1")
    private val shiftKey = longPreferencesKey("shift_start")
    val entries: Flow<List<HistoryEntry>> = store.data.map { decode(it[key]) }
    val shiftStart: Flow<Long?> = store.data.map { it[shiftKey] }

    suspend fun add(e: Evaluation, at: Long, source: DataSource) {
        val m = e.metrics
        val entry = HistoryEntry(at, e.verdict.name, m.fare, m.totalKm, m.totalMin, m.grossPerKm, m.grossPerHour, source)
        store.edit { p -> p[key] = encode((listOf(entry) + decode(p[key])).take(MAX)) }
    }

    suspend fun markTaken(at: Long, taken: Boolean = true) {
        store.edit { p -> p[key] = encode(decode(p[key]).map { if (it.at == at) it.copy(taken = taken) else it }) }
    }

    suspend fun clear() { store.edit { it.remove(key) } }
    suspend fun startShift(now: Long) { store.edit { it[shiftKey] = now } }
    suspend fun endShift() { store.edit { it.remove(shiftKey) } }

    companion object {
        const val MAX = 100
        private val ser = kotlinx.serialization.builtins.ListSerializer(HistoryEntry.serializer())
        fun decode(raw: String?): List<HistoryEntry> =
            if (raw.isNullOrBlank()) emptyList() else runCatching { AppJson.decodeFromString(ser, raw) }.getOrDefault(emptyList())
        fun encode(list: List<HistoryEntry>): String = AppJson.encodeToString(ser, list)
    }
}

/** Resumen del turno a partir de los viajes que el conductor marcó como tomados. */
data class ShiftStats(val trips: Int, val grossFare: Double, val hours: Double?, val grossPerHour: Double?)

object ShiftCalculator {
    fun compute(entries: List<HistoryEntry>, shiftStart: Long?, now: Long): ShiftStats {
        val taken = entries.filter { it.taken && (shiftStart == null || it.at >= shiftStart) }
        val gross = taken.sumOf { it.fare ?: 0.0 }
        val hours = shiftStart?.let { (now - it) / 3_600_000.0 }?.takeIf { it > 0 }
        return ShiftStats(taken.size, gross, hours, hours?.let { if (it >= 0.05) gross / it else null })
    }
}
