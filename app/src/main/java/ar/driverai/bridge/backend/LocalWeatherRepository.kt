package ar.driverai.bridge.backend

import ar.driverai.bridge.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Clima de referencia no persistido; el módulo Open-Meteo solo está habilitado en debug privado. */
class LocalWeatherRepository(
    private val client: OpenMeteoClient = OpenMeteoClient(),
    private val enabled: () -> Boolean = { BuildConfig.DEBUG },
) {
    data class State(
        val weather: OpenMeteoClient.Weather? = null,
        val loading: Boolean = false,
        val error: String? = null,
        val fetchedAt: Long? = null,
        val enabled: Boolean = BuildConfig.DEBUG,
    )

    private val _state = MutableStateFlow(State(enabled = enabled()))
    val state: StateFlow<State> = _state.asStateFlow()

    suspend fun refresh(): ApiResult<OpenMeteoClient.Weather> {
        if (!enabled()) {
            val message = "Clima externo desactivado: la versión comercial requiere una fuente/plan autorizado."
            _state.value = _state.value.copy(loading = false, error = message, enabled = false)
            return ApiResult.Fail(ApiResult.Kind.BACKEND, message)
        }
        _state.value = _state.value.copy(loading = true, error = null, enabled = true)
        return when (val result = client.fetchBuenosAires()) {
            is ApiResult.Ok -> {
                _state.value = State(weather = result.value, fetchedAt = result.value.fetchedAtMs, enabled = true)
                result
            }
            is ApiResult.Fail -> {
                _state.value = _state.value.copy(loading = false, error = result.message, enabled = true)
                result
            }
        }
    }
}
