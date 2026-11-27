package ar.driverai.bridge.plan

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import ar.driverai.bridge.data.AppJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Cache local del plan. Sin backend o sin internet: null → UI "plan no disponible".
 * Nunca rellena con datos inventados.
 */
class PlanDiaRepository(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("plan_dia_v1")

    val plan: Flow<PlanDia?> = store.data.map { p ->
        p[key]?.let { raw ->
            runCatching { AppJson.decodeFromString(PlanDia.serializer(), raw) }.getOrNull()
        }
    }

    suspend fun save(plan: PlanDia) {
        store.edit { it[key] = AppJson.encodeToString(PlanDia.serializer(), plan) }
    }

    suspend fun clear() {
        store.edit { it.remove(key) }
    }

    /**
     * Carga de demostración / offline controlado: solo el fixture de aceptación.
     * En producción se reemplaza por llamada a Edge Function plan-dia.
     */
    suspend fun loadFixtureForDemo(now: Long = System.currentTimeMillis()): PlanDia {
        val dayStart = PlanDiaFixtures.dayStartMs()
        val plan = PlanDiaFixtures.expectedPlan(dayStart, generadoAt = now)
        save(plan)
        return plan
    }
}
