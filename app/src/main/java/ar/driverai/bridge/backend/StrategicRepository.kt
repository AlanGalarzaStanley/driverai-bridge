package ar.driverai.bridge.backend

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Contexto estratégico (zonas, clima, eventos). Es complementario: si falla, el motor local de
 * ofertas sigue funcionando y la UI muestra que el contexto está desactualizado.
 */
class StrategicRepository(private val api: DriverAiApi, private val clock: () -> Long = System::currentTimeMillis) {
    data class State(
        val reporte: ReporteResponse? = null,
        val reporteAt: Long? = null,
        val posicion: PosicionResponse? = null,
        val posicionAt: Long? = null,
        val loading: Boolean = false,
        val lastError: String? = null,
    ) {
        fun isStale(now: Long): Boolean = reporteAt == null || now - reporteAt > STALE_MS || lastError != null
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    suspend fun refreshReporte(): ApiResult<ReporteResponse> {
        _state.value = _state.value.copy(loading = true)
        val r = api.reporte()
        _state.value = when (r) {
            is ApiResult.Ok -> _state.value.copy(reporte = r.value, reporteAt = clock(), loading = false, lastError = null)
            is ApiResult.Fail -> _state.value.copy(loading = false, lastError = r.message)
        }
        return r
    }

    suspend fun refreshPosicion(zona: String): ApiResult<PosicionResponse> {
        _state.value = _state.value.copy(loading = true)
        val r = api.posicion(zona)
        _state.value = when (r) {
            is ApiResult.Ok -> _state.value.copy(posicion = r.value, posicionAt = clock(), loading = false, lastError = null)
            is ApiResult.Fail -> _state.value.copy(loading = false, lastError = r.message)
        }
        return r
    }

    companion object { const val STALE_MS = 30 * 60 * 1000L }
}
