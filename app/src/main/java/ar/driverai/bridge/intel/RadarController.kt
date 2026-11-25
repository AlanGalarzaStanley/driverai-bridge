package ar.driverai.bridge.intel

import ar.driverai.bridge.backend.LocalWeatherRepository
import ar.driverai.bridge.data.HistoryEntry
import ar.driverai.bridge.engine.DriverSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Mantiene el pronóstico del radar recalculado cada [tickMs] con las señales en caché, el clima
 * (si está habilitado), los feriados y el patrón personal. Todo se calcula en el teléfono.
 */
class RadarController(
    private val scope: CoroutineScope,
    val repo: IntelRepository,
    settings: StateFlow<DriverSettings>,
    history: Flow<List<HistoryEntry>>,
    weather: StateFlow<LocalWeatherRepository.State>,
    private val sportsAllowed: (DriverSettings) -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    tickMs: Long = 5 * 60_000L,
) {
    private val settingsFlow = settings
    private val ticker: Flow<Long> = flow {
        while (true) {
            emit(clock())
            delay(tickMs)
        }
    }

    val cache: StateFlow<IntelCache> = repo.cache.stateIn(scope, SharingStarted.Eagerly, IntelCache())

    val forecast: StateFlow<RadarForecast?> = combine(repo.cache, history, ticker, weather, settings) { c, entries, _, w, s ->
        val now = clock()
        val city = Cities.byId(s.cityId)
        val tz = ZoneId.of(city.timeZone)
        val year = Instant.ofEpochMilli(now).atZone(tz).year
        val rain = w.weather?.let { WeatherSignal.fromRainProbability(it.precipProbNextHours, now, it.fetchedAtMs) }
        DemandRadar.forecast(
            city = city,
            signals = IntelRepository.allSignals(c, listOfNotNull(rain)),
            now = now,
            holidays = IntelRepository.holidaySet(c, listOf(year, year + 1)),
            personal = IntelRepository.personalPattern(entries, now, tz),
        )
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, null)

    val refreshing: StateFlow<Boolean> get() = repo.refreshing

    fun refresh() {
        val s = settingsFlow.value
        scope.launch { repo.refresh(Cities.byId(s.cityId), s.sportsApiKey, sportsAllowed(s)) }
    }

    /** Refresca solo si el caché tiene más de [maxAgeMs]; usado al abrir la app. */
    fun refreshIfStale(maxAgeMs: Long = 6 * 3_600_000L) {
        scope.launch {
            val last = repo.current().refreshedAt
            if (last == null || clock() - last > maxAgeMs) {
                val s = settingsFlow.value
                repo.refresh(Cities.byId(s.cityId), s.sportsApiKey, sportsAllowed(s))
            }
        }
    }

    fun addManual(signal: DemandSignal) { scope.launch { repo.addManual(signal) } }
    fun removeManual(id: String) { scope.launch { repo.removeManual(id) } }
    fun importAi(result: AiReport.Result) { scope.launch { repo.importAi(result.signals, result.summary) } }
    fun clearAi() { scope.launch { repo.clearAi() } }
    fun removeAi(id: String) { scope.launch { repo.removeAi(id) } }
}
