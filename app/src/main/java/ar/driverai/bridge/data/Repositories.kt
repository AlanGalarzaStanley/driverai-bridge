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
import java.util.UUID
import kotlin.math.abs
import kotlin.math.round

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

@Serializable
enum class TripStatus { OBSERVED, ACCEPTED, REJECTED, CANCELLED, COMPLETED, PAID }

enum class JournalResult { UPDATED, NOT_FOUND, INVALID_TRANSITION, INVALID_AMOUNT }

/**
 * Historial mínimo: números, veredicto, fuente, hora y estado confirmado por el conductor.
 * NO guarda direcciones, nombres de pasajeros, capturas ni ubicación.
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
    /** Compatibilidad con la versión anterior: significaba que el conductor dijo "la tomé". */
    val taken: Boolean = false,
    /** Identidad estable; queda antes de los campos nuevos para mantener constructores antiguos. */
    val id: String = "",
    val tripStatus: TripStatus = TripStatus.OBSERVED,
    val tripStatusUpdatedAt: Long? = null,
    val actualDistanceKm: Double? = null,
    val actualDurationMin: Double? = null,
    val actualIncomeArs: Double? = null,
    /** App de origen (nombre de [ar.driverai.bridge.engine.RidePlatform]); null en registros antiguos. */
    val platform: String? = null,
    /** Zona aproximada de destino detectada; nunca la dirección. */
    val destinationZoneId: String? = null,
)

class HistoryRepository(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("history_v1")
    private val shiftKey = longPreferencesKey("shift_start")
    private val moneyKey = stringPreferencesKey("money_entries_v1")
    val entries: Flow<List<HistoryEntry>> = store.data.map { decode(it[key]) }
    val shiftStart: Flow<Long?> = store.data.map { it[shiftKey] }

    suspend fun add(
        e: Evaluation,
        at: Long,
        source: DataSource,
        id: String = UUID.randomUUID().toString(),
        platform: ar.driverai.bridge.engine.RidePlatform? = null,
        destinationZoneId: String? = null,
    ) {
        val m = e.metrics
        val entry = HistoryEntry(
            at = at,
            verdict = e.verdict.name,
            fare = m.fare,
            totalKm = m.totalKm,
            totalMin = m.totalMin,
            grossPerKm = m.grossPerKm,
            grossPerHour = m.grossPerHour,
            source = source,
            id = id,
            platform = platform?.name,
            destinationZoneId = destinationZoneId,
        )
        store.edit { p ->
            val combined = listOf(entry) + decode(p[key])
            val retained = combined.take(MAX)
            val evictedIds = combined.drop(MAX).mapTo(mutableSetOf()) { it.id }
            if (evictedIds.isNotEmpty()) {
                val money = MoneyLedgerRepository.decode(p[moneyKey])
                if (money.any { it.tripHistoryId != null && it.tripHistoryId in evictedIds }) {
                    p[moneyKey] = MoneyLedgerRepository.encode(
                        money.map { movement ->
                            if (movement.tripHistoryId != null && movement.tripHistoryId in evictedIds) {
                                movement.copy(tripHistoryId = null)
                            } else movement
                        },
                    )
                }
            }
            p[key] = encode(retained)
        }
    }

    /** Called by the floating card; this records acceptance, never completion or payment. */
    suspend fun markTaken(id: String, taken: Boolean = true, at: Long = System.currentTimeMillis()) {
        if (taken) {
            recordOfferDecision(id, accepted = true, at = at)
        } else {
            store.edit { p ->
                val list = decode(p[key])
                val index = list.indexOfFirst { it.id == id }
                if (index >= 0 && list[index].tripStatus == TripStatus.ACCEPTED) {
                    p[key] = encode(list.toMutableList().also { items ->
                        items[index] = items[index].copy(taken = false, tripStatus = TripStatus.OBSERVED, tripStatusUpdatedAt = at)
                    })
                }
            }
        }
    }

    suspend fun recordOfferDecision(id: String, accepted: Boolean, at: Long = System.currentTimeMillis()): JournalResult =
        updateEntry(id) { current ->
            if (current.tripStatus != TripStatus.OBSERVED) return@updateEntry JournalResult.INVALID_TRANSITION to current
            val next = if (accepted) TripStatus.ACCEPTED else TripStatus.REJECTED
            JournalResult.UPDATED to current.copy(
                taken = accepted,
                tripStatus = next,
                tripStatusUpdatedAt = at,
            )
        }

    suspend fun completeTrip(
        id: String,
        actualDistanceKm: Double?,
        actualDurationMin: Double?,
        at: Long = System.currentTimeMillis(),
    ): JournalResult {
        if (!validOptionalMetric(actualDistanceKm, 2_000.0) || !validOptionalMetric(actualDurationMin, 48 * 60.0)) {
            return JournalResult.INVALID_AMOUNT
        }
        return updateEntry(id) { current ->
            if (current.tripStatus != TripStatus.ACCEPTED) return@updateEntry JournalResult.INVALID_TRANSITION to current
            JournalResult.UPDATED to current.copy(
                taken = true,
                tripStatus = TripStatus.COMPLETED,
                tripStatusUpdatedAt = at,
                actualDistanceKm = actualDistanceKm,
                actualDurationMin = actualDurationMin,
            )
        }
    }

    suspend fun cancelTrip(id: String, at: Long = System.currentTimeMillis()): JournalResult =
        updateEntry(id) { current ->
            if (current.tripStatus != TripStatus.ACCEPTED) return@updateEntry JournalResult.INVALID_TRANSITION to current
            JournalResult.UPDATED to current.copy(taken = false, tripStatus = TripStatus.CANCELLED, tripStatusUpdatedAt = at)
        }

    /** Return a recorded decision to observed when the driver wants to correct it. */
    suspend fun restoreObserved(id: String, at: Long = System.currentTimeMillis()): JournalResult =
        updateEntry(id) { current ->
            if (current.tripStatus !in setOf(TripStatus.ACCEPTED, TripStatus.REJECTED, TripStatus.CANCELLED)) {
                return@updateEntry JournalResult.INVALID_TRANSITION to current
            }
            JournalResult.UPDATED to current.copy(taken = false, tripStatus = TripStatus.OBSERVED, tripStatusUpdatedAt = at)
        }

    /** Saves a confirmed payment and its linked wallet income in one DataStore transaction. */
    suspend fun recordTripPayment(id: String, amountArs: Double, at: Long = System.currentTimeMillis()): JournalResult {
        if (!validMoneyAmount(amountArs)) return JournalResult.INVALID_AMOUNT
        var result = JournalResult.NOT_FOUND
        store.edit { p ->
            val list = decode(p[key])
            val index = list.indexOfFirst { it.id == id }
            if (index < 0) return@edit
            val current = list[index]
            if (current.tripStatus !in setOf(TripStatus.COMPLETED, TripStatus.PAID)) {
                result = JournalResult.INVALID_TRANSITION
                return@edit
            }

            val money = MoneyLedgerRepository.decode(p[moneyKey])
            val linked = money.firstOrNull { it.tripHistoryId == id }
            val payment = MoneyEntry(
                id = linked?.id ?: UUID.randomUUID().toString(),
                at = at,
                kind = MoneyKind.INCOME,
                amountArs = amountArs,
                category = MoneyCategory.TRIP_INCOME,
                tripHistoryId = id,
            )
            p[moneyKey] = MoneyLedgerRepository.encode((money.filterNot { it.tripHistoryId == id } + payment).takeLast(MoneyLedgerRepository.MAX_ENTRIES))
            p[key] = encode(list.toMutableList().also { items ->
                items[index] = current.copy(
                    taken = true,
                    tripStatus = TripStatus.PAID,
                    tripStatusUpdatedAt = at,
                    actualIncomeArs = amountArs,
                )
            })
            result = JournalResult.UPDATED
        }
        return result
    }

    /** Reverses a linked payment without deleting the completed-trip record or other wallet entries. */
    suspend fun removeTripPayment(id: String, at: Long = System.currentTimeMillis()): JournalResult {
        var result = JournalResult.NOT_FOUND
        store.edit { p ->
            val list = decode(p[key])
            val index = list.indexOfFirst { it.id == id }
            if (index < 0) return@edit
            val current = list[index]
            if (current.tripStatus != TripStatus.PAID) {
                result = JournalResult.INVALID_TRANSITION
                return@edit
            }
            p[moneyKey] = MoneyLedgerRepository.encode(MoneyLedgerRepository.decode(p[moneyKey]).filterNot { it.tripHistoryId == id })
            p[key] = encode(list.toMutableList().also { items ->
                items[index] = current.copy(
                    taken = true,
                    tripStatus = TripStatus.COMPLETED,
                    tripStatusUpdatedAt = at,
                    actualIncomeArs = null,
                )
            })
            result = JournalResult.UPDATED
        }
        return result
    }

    private suspend fun updateEntry(
        id: String,
        transform: (HistoryEntry) -> Pair<JournalResult, HistoryEntry>,
    ): JournalResult {
        var result = JournalResult.NOT_FOUND
        store.edit { p ->
            val list = decode(p[key])
            val index = list.indexOfFirst { it.id == id }
            if (index < 0) return@edit
            val (updateResult, updated) = transform(list[index])
            result = updateResult
            if (updateResult == JournalResult.UPDATED) {
                p[key] = encode(list.toMutableList().also { it[index] = updated })
            }
        }
        return result
    }

    suspend fun clear() {
        // Real cash entries survive clearing offer history; detach them instead of deleting them.
        store.edit { p ->
            p.remove(key)
            val money = MoneyLedgerRepository.decode(p[moneyKey])
            if (money.any { it.tripHistoryId != null }) {
                p[moneyKey] = MoneyLedgerRepository.encode(money.map { it.copy(tripHistoryId = null) })
            }
        }
    }

    suspend fun startShift(now: Long) { store.edit { it[shiftKey] = now } }
    suspend fun endShift() { store.edit { it.remove(shiftKey) } }

    private fun validMoneyAmount(amount: Double): Boolean {
        val cents = amount * 100.0
        return amount.isFinite() && amount > 0.0 && amount <= MoneyAmountParser.MAX_AMOUNT_ARS && abs(cents - round(cents)) < 1e-6
    }

    private fun validOptionalMetric(value: Double?, max: Double): Boolean =
        value == null || (value.isFinite() && value > 0.0 && value <= max)

    companion object {
        const val MAX = 100
        private val ser = kotlinx.serialization.builtins.ListSerializer(HistoryEntry.serializer())
        fun decode(raw: String?): List<HistoryEntry> {
            if (raw.isNullOrBlank()) return emptyList()
            val decoded = runCatching { AppJson.decodeFromString(ser, raw) }.getOrDefault(emptyList())
            // Older app versions only persisted `taken`; migrate it to the explicit accepted state.
            return decoded.mapIndexed { index, entry ->
                val id = if (entry.id.isBlank()) "legacy-${entry.at}-$index" else entry.id
                val status = if (entry.tripStatus == TripStatus.OBSERVED && entry.taken) TripStatus.ACCEPTED else entry.tripStatus
                entry.copy(
                    id = id,
                    tripStatus = status,
                    taken = status in setOf(TripStatus.ACCEPTED, TripStatus.COMPLETED, TripStatus.PAID),
                )
            }
        }
        fun encode(list: List<HistoryEntry>): String = AppJson.encodeToString(ser, list)
    }
}

/** Offer fares waiting for a driver-confirmed payment; never a wallet movement. */
data class PendingTripEstimateSummary(
    val pendingTrips: List<HistoryEntry>,
    val offeredFareArs: Double,
) {
    val tripCount: Int get() = pendingTrips.size

    companion object {
        fun from(entries: List<HistoryEntry>): PendingTripEstimateSummary {
            val pending = entries.filter {
                it.tripStatus == TripStatus.ACCEPTED || it.tripStatus == TripStatus.COMPLETED
            }
            val estimatedFare = pending.sumOf { entry ->
                entry.fare?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
            }
            return PendingTripEstimateSummary(pending, estimatedFare)
        }
    }
}

/** Shift figures keep offer estimates separate from confirmed money received. */
data class ShiftStats(
    val trips: Int,
    val grossFare: Double,
    val hours: Double?,
    val grossPerHour: Double?,
    val paidTrips: Int = 0,
    val actualIncome: Double = 0.0,
    val actualIncomePerHour: Double? = null,
)

object ShiftCalculator {
    fun compute(entries: List<HistoryEntry>, shiftStart: Long?, now: Long): ShiftStats {
        val shiftEntries = entries.filter { it.at >= (shiftStart ?: Long.MIN_VALUE) }
        val accepted = shiftEntries.filter {
            it.taken || it.tripStatus in setOf(TripStatus.ACCEPTED, TripStatus.COMPLETED, TripStatus.PAID)
        }
        val gross = accepted.sumOf { it.fare ?: 0.0 }
        val paid = accepted.filter { it.tripStatus == TripStatus.PAID && it.actualIncomeArs != null }
        val actualIncome = paid.sumOf { it.actualIncomeArs ?: 0.0 }
        val hours = shiftStart?.let { (now - it) / 3_600_000.0 }?.takeIf { it > 0 }
        return ShiftStats(
            trips = accepted.size,
            grossFare = gross,
            hours = hours,
            grossPerHour = hours?.takeIf { it >= 0.05 }?.let { gross / it },
            paidTrips = paid.size,
            actualIncome = actualIncome,
            actualIncomePerHour = hours?.takeIf { it >= 0.05 && actualIncome > 0.0 }?.let { actualIncome / it },
        )
    }
}
