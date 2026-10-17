package ar.driverai.bridge

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import ar.driverai.bridge.backend.PosicionResponse
import ar.driverai.bridge.backend.Reporte
import ar.driverai.bridge.backend.ReporteResponse
import ar.driverai.bridge.backend.StrategicRepository
import ar.driverai.bridge.backend.ZonaRank
import ar.driverai.bridge.copilot.CopilotContext
import ar.driverai.bridge.copilot.CopilotEngine
import ar.driverai.bridge.copilot.Intent
import ar.driverai.bridge.data.HistoryEntry
import ar.driverai.bridge.data.HistoryRepository
import ar.driverai.bridge.data.SettingsRepository
import ar.driverai.bridge.data.ShiftCalculator
import ar.driverai.bridge.engine.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataAndCopilotTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store(name: String) = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { java.io.File(tmp.root, "$name.preferences_pb") })

    @Test fun settings_persistenYRecargan() = runBlocking {
        val repo = SettingsRepository(store("s1"))
        assertEquals(DriverSettings(), repo.current())
        repo.update { it.copy(minGrossPerKm = 700.0, tolerance = UncertaintyTolerance.ESTRICTA, homeZone = "Bernal") }
        val again = repo.current()
        assertEquals(700.0, again.minGrossPerKm!!, 0.0)
        assertEquals(UncertaintyTolerance.ESTRICTA, again.tolerance)
        assertEquals("Bernal", again.homeZone)
    }

    @Test fun settings_jsonCorrupto_vuelveADefaults() {
        assertEquals(DriverSettings(), SettingsRepository.decode("{no es json"))
        assertEquals(DriverSettings(), SettingsRepository.decode(null))
    }

    @Test fun historial_limiteYNoGuardaTexto() = runBlocking {
        val repo = HistoryRepository(store("h1"))
        val e = OfferEvaluator.evaluate(OfferData(Reading.manual(8000.0), Reading.manual(1.0), Reading.manual(3.0), Reading.manual(8.0), Reading.manual(20.0)), DriverSettings(minFare = 1.0))
        repeat(HistoryRepository.MAX + 5) { repo.add(e, it.toLong(), DataSource.MANUAL) }
        val list = repo.entries.first()
        assertEquals(HistoryRepository.MAX, list.size)
        repo.markTaken(104L)
        assertTrue(repo.entries.first().first { it.at == 104L }.taken)
        val json = HistoryRepository.encode(list)
        assertFalse(json.contains("Av.")); assertFalse(json.contains("raw"))
        repo.clear(); assertTrue(repo.entries.first().isEmpty())
    }

    @Test fun turno_brutoPorHora() {
        val entries = listOf(
            HistoryEntry(3_600_000, "CONVIENE", 8000.0, 10.0, 25.0, 800.0, 19200.0, DataSource.ACCESSIBILITY, taken = true),
            HistoryEntry(3_700_000, "CONVIENE", 6000.0, 10.0, 25.0, 600.0, 14400.0, DataSource.ACCESSIBILITY, taken = false),
        )
        val s = ShiftCalculator.compute(entries, shiftStart = 0L, now = 2 * 3_600_000L)
        assertEquals(1, s.trips); assertEquals(8000.0, s.grossFare, 0.0); assertEquals(4000.0, s.grossPerHour!!, 1e-9)
    }

    private fun ctx(eval: Evaluation? = null, posicion: PosicionResponse? = null, reporte: ReporteResponse? = null, settings: DriverSettings = DriverSettings()) =
        CopilotContext(eval, null, StrategicRepository.State(reporte = reporte, reporteAt = reporte?.let { 0L }, posicion = posicion), settings, now = 1000L)

    @Test fun copiloto_intenciones() {
        assertEquals(Intent.CONVIENE, CopilotEngine.intentOf("¿Me conviene este viaje?"))
        assertEquals(Intent.POR_QUE, CopilotEngine.intentOf("¿Por qué no conviene?"))
        assertEquals(Intent.POR_HORA, CopilotEngine.intentOf("¿Cuánto estoy haciendo por hora?"))
        assertEquals(Intent.DONDE_ESPERAR, CopilotEngine.intentOf("¿Dónde me conviene esperar ahora?"))
        assertEquals(Intent.EVENTOS, CopilotEngine.intentOf("¿Qué eventos pueden generar demanda esta noche?"))
        assertEquals(Intent.CERCA_CASA, CopilotEngine.intentOf("¿Qué zona me queda mejor para terminar cerca de casa?"))
        assertEquals(Intent.DESCONOCIDA, CopilotEngine.intentOf("hola"))
    }

    @Test fun copiloto_respuestasBrevesYConFuente() {
        val e = OfferEvaluator.evaluate(OfferData(Reading.manual(2500.0), Reading.manual(3.0), Reading.manual(10.0), Reading.manual(6.0), Reading.manual(18.0)), DriverSettings(minGrossPerKm = 700.0))
        val a = CopilotEngine.answer("¿me conviene?", ctx(e))
        assertTrue(a, a.startsWith("No conviene"))
        assertTrue(a.length < 220)
        assertTrue(CopilotEngine.answer("¿por qué?", ctx(e)).contains("mínimo"))
        assertEquals("Todavía no evalué ninguna oferta.", CopilotEngine.answer("¿me conviene?", ctx()))
        val p = PosicionResponse(ok = true, zona_actual = "Microcentro", score_actual = 6.0, decision = "moverte", zona_recomendada = "Recoleta", score_recomendado = 10.0)
        val d = CopilotEngine.answer("¿dónde espero?", ctx(posicion = p))
        assertTrue(d, d.startsWith("Estimación del servidor") && d.contains("Recoleta"))
        val r = ReporteResponse(ok = true, reporte = Reporte(zonas_rankeadas = listOf(ZonaRank("Bernal", 8.0), ZonaRank("Palermo", 9.0), ZonaRank("Quilmes", 6.0))))
        val c = CopilotEngine.answer("cerca de casa", ctx(reporte = r, settings = DriverSettings(homeZone = "Quilmes")))
        assertTrue(c, c.contains("Bernal"))
        assertTrue(CopilotEngine.answer("cerca de casa", ctx()).contains("Configurá"))
        assertTrue(CopilotEngine.answer("eventos esta noche", ctx(reporte = r)).contains("no informa eventos"))
    }
}
