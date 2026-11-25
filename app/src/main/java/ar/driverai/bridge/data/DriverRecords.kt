package ar.driverai.bridge.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToLong

@Serializable
enum class MoneyKind { INCOME, EXPENSE }

@Serializable
enum class MoneyCategory {
    TRIP_INCOME, BONUS, OTHER_INCOME,
    FUEL, TOLL, PARKING, MAINTENANCE, OTHER_EXPENSE,
}

/** Parseo estricto de pesos: miles con punto y decimales con coma; no adivina separadores ambiguos. */
object MoneyAmountParser {
    const val MAX_AMOUNT_ARS = 100_000_000.0
    private val integer = Regex("^\\d+$")
    private val thousands = Regex("^\\d{1,3}(\\.\\d{3})+(,\\d{1,2})?$")
    private val commaDecimal = Regex("^\\d+,\\d{1,2}$")
    private val dotDecimal = Regex("^\\d+\\.\\d{1,2}$")

    fun parse(raw: String): Double? {
        val value = raw.trim().removePrefix("$").trim().replace(" ", "")
        val parsed = when {
            integer.matches(value) -> value.toDoubleOrNull()
            thousands.matches(value) -> value.replace(".", "").replace(',', '.').toDoubleOrNull()
            commaDecimal.matches(value) -> value.replace(',', '.').toDoubleOrNull()
            dotDecimal.matches(value) -> value.toDoubleOrNull()
            else -> null
        } ?: return null
        return parsed.takeIf { it.isFinite() && it > 0.0 && it <= MAX_AMOUNT_ARS }
    }
}

/** Movimiento de caja confirmado por el conductor. No se deriva de una oferta detectada. */
@Serializable
data class MoneyEntry(
    val id: String,
    val at: Long,
    val kind: MoneyKind,
    val amountArs: Double,
    val category: MoneyCategory,
    /** Links a confirmed trip payment to its offer history record; null for manual entries. */
    val tripHistoryId: String? = null,
)

data class MoneyDaySummary(
    val incomeArs: Double,
    val expensesArs: Double,
    val cashBalanceArs: Double,
    val entries: List<MoneyEntry>,
)

class MoneyLedgerRepository(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("money_entries_v1")
    val entries: Flow<List<MoneyEntry>> = store.data.map { decode(it[key]) }

    suspend fun add(kind: MoneyKind, amountArs: Double, category: MoneyCategory, at: Long = System.currentTimeMillis()) {
        val cents = amountArs * 100.0
        require(amountArs.isFinite() && amountArs > 0.0 && amountArs <= MoneyAmountParser.MAX_AMOUNT_ARS && abs(cents - round(cents)) < 1e-6) {
            "El importe debe ser positivo, no superar ${MoneyAmountParser.MAX_AMOUNT_ARS.toLong()} ARS y tener hasta dos decimales."
        }
        val entry = MoneyEntry(UUID.randomUUID().toString(), at, kind, amountArs, category)
        store.edit { prefs -> prefs[key] = encode((decode(prefs[key]) + entry).takeLast(MAX_ENTRIES)) }
    }

    suspend fun delete(id: String): Boolean {
        var deleted = false
        store.edit { prefs ->
            val entries = decode(prefs[key])
            if (entries.any { it.id == id && it.tripHistoryId != null }) return@edit
            deleted = entries.any { it.id == id }
            if (deleted) prefs[key] = encode(entries.filterNot { it.id == id })
        }
        return deleted
    }

    suspend fun clear() { store.edit { it.remove(key) } }

    companion object {
        const val MAX_ENTRIES = 3000
        private val serializer = kotlinx.serialization.builtins.ListSerializer(MoneyEntry.serializer())

        fun decode(raw: String?): List<MoneyEntry> =
            if (raw.isNullOrBlank()) emptyList() else runCatching { AppJson.decodeFromString(serializer, raw) }.getOrDefault(emptyList())

        fun encode(entries: List<MoneyEntry>): String = AppJson.encodeToString(serializer, entries)

        fun summarize(entries: List<MoneyEntry>, day: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): MoneyDaySummary {
            val selected = entries.filter { localDay(it.at, zone) == day }.sortedByDescending { it.at }
            val income = selected.filter { it.kind == MoneyKind.INCOME }.sumOf { (it.amountArs * 100.0).roundToLong() } / 100.0
            val expenses = selected.filter { it.kind == MoneyKind.EXPENSE }.sumOf { (it.amountArs * 100.0).roundToLong() } / 100.0
            return MoneyDaySummary(income, expenses, income - expenses, selected)
        }

        fun localDay(at: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
            Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    }
}

@Serializable
data class OdometerEntry(
    /** Fecha local del registro en ISO-8601 (yyyy-MM-dd); una lectura diaria se edita, no se duplica. */
    val localDate: String,
    val startKm: Double,
    val endKm: Double,
    val savedAt: Long,
) {
    val drivenKm: Double get() = endKm - startKm
}

@Serializable
data class MaintenanceRecord(
    val id: String,
    val title: String,
    val dueOdometerKm: Double? = null,
    /** Fecha local ISO-8601 (yyyy-MM-dd), opcional si hay vencimiento por kilometraje. */
    val dueDate: String? = null,
    val createdAt: Long,
    val completedAt: Long? = null,
    val completedAtKm: Double? = null,
)

sealed class OdometerSaveResult {
    data object Saved : OdometerSaveResult()
    data class Invalid(val message: String) : OdometerSaveResult()
}

class VehicleRecordsRepository(private val store: DataStore<Preferences>) {
    private val odometerKey = stringPreferencesKey("odometer_entries_v1")
    private val maintenanceKey = stringPreferencesKey("maintenance_records_v1")

    val odometerEntries: Flow<List<OdometerEntry>> = store.data.map { decodeOdometer(it[odometerKey]) }
    val maintenanceRecords: Flow<List<MaintenanceRecord>> = store.data.map { decodeMaintenance(it[maintenanceKey]) }

    suspend fun saveOdometer(entry: OdometerEntry): OdometerSaveResult {
        val parsedDay = runCatching { LocalDate.parse(entry.localDate) }.getOrNull()
            ?: return OdometerSaveResult.Invalid("La fecha del odómetro no es válida.")
        if (!entry.startKm.isFinite() || !entry.endKm.isFinite() || entry.startKm < 0 || entry.endKm < entry.startKm) {
            return OdometerSaveResult.Invalid("Revisá el odómetro: el final no puede ser menor al inicio.")
        }
        val existing = odometerEntries.first()
        val otherDays = existing.filterNot { it.localDate == parsedDay.toString() }
        val prior = otherDays.filter { it.localDate < parsedDay.toString() }.maxByOrNull { it.localDate }
        val next = otherDays.filter { it.localDate > parsedDay.toString() }.minByOrNull { it.localDate }
        if (prior != null && entry.startKm < prior.endKm) {
            return OdometerSaveResult.Invalid("El odómetro inicial no puede ser menor a la última lectura anterior (${formatKm(prior.endKm)} km).")
        }
        if (next != null && entry.endKm > next.startKm) {
            return OdometerSaveResult.Invalid("El odómetro final supera la lectura inicial del día siguiente (${formatKm(next.startKm)} km).")
        }
        val normalized = entry.copy(localDate = parsedDay.toString())
        store.edit { prefs ->
            val updated = (decodeOdometer(prefs[odometerKey]).filterNot { it.localDate == normalized.localDate } + normalized)
                .sortedBy { it.localDate }
                .takeLast(MAX_ODOMETER_DAYS)
            prefs[odometerKey] = encodeOdometer(updated)
        }
        return OdometerSaveResult.Saved
    }

    suspend fun addMaintenance(title: String, dueOdometerKm: Double?, dueDate: String?, now: Long = System.currentTimeMillis()): String? {
        val cleanTitle = title.trim().take(40)
        if (cleanTitle.isBlank()) return "Elegí una tarea de mantenimiento."
        if (dueOdometerKm != null && (!dueOdometerKm.isFinite() || dueOdometerKm < 0)) return "El kilometraje de vencimiento no es válido."
        val cleanDate = dueDate?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanDate != null && runCatching { LocalDate.parse(cleanDate) }.isFailure) return "Usá la fecha AAAA-MM-DD."
        if (dueOdometerKm == null && cleanDate == null) return "Agregá un vencimiento por fecha o por kilometraje."
        val record = MaintenanceRecord(UUID.randomUUID().toString(), cleanTitle, dueOdometerKm, cleanDate, now)
        store.edit { prefs -> prefs[maintenanceKey] = encodeMaintenance((decodeMaintenance(prefs[maintenanceKey]) + record).takeLast(MAX_MAINTENANCE_RECORDS)) }
        return null
    }

    suspend fun markMaintenanceDone(id: String, currentOdometerKm: Double?, now: Long = System.currentTimeMillis()) {
        store.edit { prefs ->
            prefs[maintenanceKey] = encodeMaintenance(decodeMaintenance(prefs[maintenanceKey]).map {
                if (it.id == id) it.copy(completedAt = now, completedAtKm = currentOdometerKm) else it
            })
        }
    }

    suspend fun deleteMaintenance(id: String) {
        store.edit { prefs -> prefs[maintenanceKey] = encodeMaintenance(decodeMaintenance(prefs[maintenanceKey]).filterNot { it.id == id }) }
    }

    suspend fun clearAll() {
        store.edit { prefs ->
            prefs.remove(odometerKey)
            prefs.remove(maintenanceKey)
        }
    }

    companion object {
        const val MAX_ODOMETER_DAYS = 1500
        const val MAX_MAINTENANCE_RECORDS = 300
        private val odoSerializer = kotlinx.serialization.builtins.ListSerializer(OdometerEntry.serializer())
        private val maintenanceSerializer = kotlinx.serialization.builtins.ListSerializer(MaintenanceRecord.serializer())

        fun decodeOdometer(raw: String?): List<OdometerEntry> =
            if (raw.isNullOrBlank()) emptyList() else runCatching { AppJson.decodeFromString(odoSerializer, raw) }.getOrDefault(emptyList())

        fun encodeOdometer(entries: List<OdometerEntry>): String = AppJson.encodeToString(odoSerializer, entries)

        fun decodeMaintenance(raw: String?): List<MaintenanceRecord> =
            if (raw.isNullOrBlank()) emptyList() else runCatching { AppJson.decodeFromString(maintenanceSerializer, raw) }.getOrDefault(emptyList())

        fun encodeMaintenance(records: List<MaintenanceRecord>): String = AppJson.encodeToString(maintenanceSerializer, records)

        fun latestOdometerKm(entries: List<OdometerEntry>): Double? = entries.maxByOrNull { it.localDate }?.endKm

        fun entryForDay(entries: List<OdometerEntry>, day: LocalDate = LocalDate.now()): OdometerEntry? =
            entries.firstOrNull { it.localDate == day.toString() }

        fun maintenanceIsOverdue(record: MaintenanceRecord, currentOdometerKm: Double?, today: LocalDate = LocalDate.now()): Boolean {
            if (record.completedAt != null) return false
            val kmOverdue = record.dueOdometerKm?.let { currentOdometerKm != null && currentOdometerKm >= it } == true
            val dateOverdue = record.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull()?.let { due -> !due.isAfter(today) } } == true
            return kmOverdue || dateOverdue
        }
    }
}

private fun formatKm(km: Double): String = if (km % 1.0 == 0.0) km.toLong().toString() else String.format(java.util.Locale.US, "%.1f", km)
