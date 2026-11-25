package ar.driverai.bridge.backend

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import ar.driverai.bridge.data.AppJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/**
 * Resumen local y minimizado de lo que devolvió el endpoint estratégico existente.
 * retrievedAt es la hora de consulta al servidor, no la hora de actualización de sus fuentes.
 */
@Serializable
data class ShiftBriefingSnapshot(
    val retrievedAt: Long,
    val selectedZone: String?,
    val demandLevel: String?,
    val bestZoneNow: String?,
    val bestZoneIn2h: String?,
    val temperatureC: Double?,
    val rainNow: Boolean?,
    val rainProbability3h: Double?,
    val eventsListed: Int?,
) {
    companion object {
        fun from(response: ReporteResponse, selectedZone: String?, retrievedAt: Long) = ShiftBriefingSnapshot(
            retrievedAt = retrievedAt,
            selectedZone = selectedZone,
            demandLevel = response.reporte?.nivel_demanda_general,
            bestZoneNow = response.reporte?.mejor_zona_ahora,
            bestZoneIn2h = response.reporte?.mejor_zona_en_2h,
            temperatureC = response.clima?.temperatura,
            rainNow = response.clima?.lluvia_ahora,
            rainProbability3h = response.clima?.prob_lluvia_3h,
            eventsListed = response.eventos?.size,
        )
    }
}

/** Persists only a compact, non-identifying snapshot so the driver can inspect the shift briefing later. */
class ShiftBriefingRepository(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("shift_briefing_v1")

    val snapshot: Flow<ShiftBriefingSnapshot?> = store.data.map { preferences ->
        preferences[key]?.let { raw ->
            runCatching { AppJson.decodeFromString(ShiftBriefingSnapshot.serializer(), raw) }.getOrNull()
        }
    }

    suspend fun save(snapshot: ShiftBriefingSnapshot) {
        store.edit { it[key] = AppJson.encodeToString(ShiftBriefingSnapshot.serializer(), snapshot) }
    }

    suspend fun capture(response: ReporteResponse, selectedZone: String?, retrievedAt: Long) {
        save(ShiftBriefingSnapshot.from(response, selectedZone, retrievedAt))
    }
}
