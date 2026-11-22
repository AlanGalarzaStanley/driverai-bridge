package ar.driverai.bridge.backend

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Clima local verificable. Independiente del reporte del servidor. */
class LocalWeatherRepository(private val client: OpenMeteoClient = OpenMeteoClient()) {
    data class State(
        val weather: OpenMeteoClient.Weather? = null,
        val loading: Boolean = false,
        val error: String? = null,
        val fetchedAt: Long? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    suspend fun refresh(): ApiResult<OpenMeteoClient.Weather> {
        _state.value = _state.value.copy(loading = true)
        val r = client.fetchBuenosAires()
        _state.value = when (r) {
            is ApiResult.Ok -> State(weather = r.value, loading = false, error = null, fetchedAt = r.value.fetchedAtMs)
            is ApiResult.Fail -> _state.value.copy(loading = false, error = r.message)
        }
        return r
    }
}
