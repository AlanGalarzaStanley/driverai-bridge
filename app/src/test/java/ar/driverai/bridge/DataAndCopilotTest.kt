package ar.driverai.bridge

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import ar.driverai.bridge.backend.PosicionResponse
import ar.driverai.bridge.backend.Reporte
import ar.driverai.bridge.backend.ReporteResponse
import ar.driverai.bridge.backend.StrategicRepository
import ar.driverai.bridge.backend.ZonaRank
import ar.driverai.bridge.copilot.CopilotContext
import ar.driverai.bridge.copilot.CopilotEngine
import ar.driverai.bridge.copilot.ExternalBriefingPrompt
import ar.driverai.bridge.copilot.Intent
import ar.driverai.bridge.data.HistoryEntry
import ar.driverai.bridge.data.HistoryRepository
import ar.driverai.bridge.data.MaintenanceRecord
import ar.driverai.bridge.data.MoneyCategory
import ar.driverai.bridge.data.MoneyAmountParser
import ar.driverai.bridge.data.MoneyKind
import ar.driverai.bridge.data.MoneyLedgerRepository
import ar.driverai.bridge.data.OdometerEntry
import ar.driverai.bridge.data.OdometerSaveResult
import ar.driverai.bridge.data.SettingsRepository
import ar.driverai.bridge.data.ShiftCalculator
import ar.driverai.bridge.data.VehicleRecordsRepository
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
import java.time.LocalDate
import java.time.ZoneId

class DataAndCopilotTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store(name: String) = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { java.io.File(tmp.root, "$name.preferences_pb") })

    @Test fun settings_persistenYRecargan() = runBlocking {
        val repo = SettingsRepository(store("s1"))
        assertEquals(DriverSettings(), repo.current())
        repo.update { it.copy(minGrossPerKm = 700.0, tolerance = UncertaintyTolerance.ESTRICTA, homeZone = "Bernal", dailyTargetArs = 15000.0) }
        val again = repo.current()
        assertEquals(700.0, again.minGrossPerKm!!, 0.0)
        assertEquals(UncertaintyTolerance.ESTRICTA, again.tolerance)
        assertEquals("Bernal", again.homeZone)
        assertEquals(15000.0, again.dailyTargetArs!!, 0.0)
    }

    @Test fun settings_jsonCorrupto_vuelveADefaults() {
        assertEquals(DriverSettings(), SettingsRepository.decode("{no es json"))
        assertEquals(DriverSettings(), SettingsRepository.decode(null))
        assertNull(SettingsRepository.decode("""{"minGrossPerKm":500.0}""").dailyTargetArs)
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

    @Test fun copiloto_porHoraAclaraQueSonTarifasEstimadas() {
        val taken = HistoryEntry(3_600_000, "CONVIENE", 8000.0, 10.0, 25.0, 800.0, 19200.0, DataSource.ACCESSIBILITY, taken = true)
        val shift = ShiftCalculator.compute(listOf(taken), shiftStart = 0L, now = 2 * 3_600_000L)
        val answer = CopilotEngine.answer("¿Cuánto hago por hora?", ctx().copy(shift = shift))
        assertTrue(answer, answer.contains("tarifas leídas"))
        assertTrue(answer, answer.contains("estimado"))
        assertTrue(answer, answer.contains("No confirma cobros"))
    }

    @Test fun billetera_persisteYResumePorDiaLocal() = runBlocking {
        val repo = MoneyLedgerRepository(store("money"))
        val zone = ZoneId.of("America/Argentina/Buenos_Aires")
        val day = LocalDate.of(2026, 11, 21)
        val incomeAt = day.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val expenseAt = day.atTime(13, 0).atZone(zone).toInstant().toEpochMilli()
        repo.add(MoneyKind.INCOME, 12000.0, MoneyCategory.TRIP_INCOME, incomeAt)
        repo.add(MoneyKind.EXPENSE, 2500.0, MoneyCategory.FUEL, expenseAt)
        val entries = repo.entries.first()
        val summary = MoneyLedgerRepository.summarize(entries, day, zone)
        assertEquals(12000.0, summary.incomeArs, 0.0)
        assertEquals(2500.0, summary.expensesArs, 0.0)
        assertEquals(9500.0, summary.cashBalanceArs, 0.0)
        assertEquals(entries, MoneyLedgerRepository.decode(MoneyLedgerRepository.encode(entries)))
        assertTrue(runCatching { repo.add(MoneyKind.INCOME, 0.0, MoneyCategory.TRIP_INCOME) }.isFailure)
        assertTrue(runCatching { repo.add(MoneyKind.INCOME, 0.001, MoneyCategory.TRIP_INCOME) }.isFailure)
        assertTrue(runCatching { repo.add(MoneyKind.INCOME, MoneyAmountParser.MAX_AMOUNT_ARS + 1, MoneyCategory.TRIP_INCOME) }.isFailure)
        assertEquals(1234.0, MoneyAmountParser.parse("1.234")!!, 0.0)
        assertEquals(12000.0, MoneyAmountParser.parse("12.000")!!, 0.0)
        assertEquals(1.23, MoneyAmountParser.parse("1,23")!!, 1e-9)
        assertEquals(1234.56, MoneyAmountParser.parse("1.234,56")!!, 1e-9)
        assertNull(MoneyAmountParser.parse("1,234"))
        assertNull(MoneyAmountParser.parse("100.000.000,01"))
        repo.clear()
        assertTrue(repo.entries.first().isEmpty())
    }

    @Test fun odometro_validaMonotoniaYActualizaUnaLecturaPorDia() = runBlocking {
        val repo = VehicleRecordsRepository(store("vehicle"))
        fun entry(day: LocalDate, start: Double, end: Double) = OdometerEntry(day.toString(), start, end, 1L)
        val firstDay = LocalDate.of(2026, 11, 20)
        val secondDay = firstDay.plusDays(1)
        assertTrue(repo.saveOdometer(entry(firstDay, 100.0, 99.0)) is OdometerSaveResult.Invalid)
        assertEquals(OdometerSaveResult.Saved, repo.saveOdometer(entry(firstDay, 100.0, 120.0)))
        assertEquals(OdometerSaveResult.Saved, repo.saveOdometer(entry(secondDay, 120.0, 135.0)))
        assertTrue(repo.saveOdometer(entry(firstDay, 100.0, 121.0)) is OdometerSaveResult.Invalid)
        assertEquals(OdometerSaveResult.Saved, repo.saveOdometer(entry(firstDay, 100.0, 118.0)))
        assertTrue(repo.saveOdometer(entry(secondDay, 117.0, 140.0)) is OdometerSaveResult.Invalid)
        val entries = repo.odometerEntries.first()
        assertEquals(2, entries.size)
        assertEquals(18.0, entries.first().drivenKm, 0.0)
        assertEquals(entries, VehicleRecordsRepository.decodeOdometer(VehicleRecordsRepository.encodeOdometer(entries)))
    }

    @Test fun mantenimiento_validaYDetectaVencimientosPorKmOFecha() = runBlocking {
        val repo = VehicleRecordsRepository(store("service"))
        val today = LocalDate.of(2026, 11, 21)
        assertNotNull(repo.addMaintenance("  ", null, null, now = 1L))
        assertNotNull(repo.addMaintenance("VTV", null, null, now = 1L))
        assertNull(repo.addMaintenance("VTV", 24000.0, null, now = 1L))
        val record = repo.maintenanceRecords.first().single()
        assertFalse(VehicleRecordsRepository.maintenanceIsOverdue(record, 23999.0, today))
        assertTrue(VehicleRecordsRepository.maintenanceIsOverdue(record, 24000.0, today))
        val dueByDate = MaintenanceRecord("date", "Seguro", dueDate = today.minusDays(1).toString(), createdAt = 1L)
        assertTrue(VehicleRecordsRepository.maintenanceIsOverdue(dueByDate, null, today))
        repo.markMaintenanceDone(record.id, 24000.0, now = 2L)
        val completed = repo.maintenanceRecords.first().single()
        assertFalse(VehicleRecordsRepository.maintenanceIsOverdue(completed, 25000.0, today))
        assertEquals(listOf(completed), VehicleRecordsRepository.decodeMaintenance(VehicleRecordsRepository.encodeMaintenance(listOf(completed))))
    }

    private fun ctx(eval: Evaluation? = null, posicion: PosicionResponse? = null, reporte: ReporteResponse? = null, settings: DriverSettings = DriverSettings()) =
        CopilotContext(eval, null, StrategicRepository.State(reporte = reporte, reporteAt = reporte?.let { 0L }, posicion = posicion), settings, now = 1000L)

    @Test fun copiloto_intenciones() {
        assertEquals(Intent.CONVIENE, CopilotEngine.intentOf("¿Me conviene este viaje?"))
        assertEquals(Intent.POR_QUE, CopilotEngine.intentOf("¿Por qué no conviene?"))
        assertEquals(Intent.RESUMEN_JORNADA, CopilotEngine.intentOf("¿Cómo va mi jornada hoy?"))
        assertEquals(Intent.OBJETIVO, CopilotEngine.intentOf("¿Cómo va mi objetivo diario?"))
        assertEquals(Intent.POR_HORA, CopilotEngine.intentOf("¿Cuánto estoy haciendo por hora?"))
        assertEquals(Intent.DONDE_ESPERAR, CopilotEngine.intentOf("¿Dónde me conviene esperar ahora?"))
        assertEquals(Intent.EVENTOS, CopilotEngine.intentOf("¿Qué eventos pueden generar demanda esta noche?"))
        assertEquals(Intent.CERCA_CASA, CopilotEngine.intentOf("¿Qué zona me queda mejor para terminar cerca de casa?"))
        assertEquals(Intent.DESCONOCIDA, CopilotEngine.intentOf("hola"))
    }

    @Test fun copiloto_resumenCruzaRegistrosDeHoyConMetaYAclaraLimites() {
        val day = ar.driverai.bridge.data.MoneyDaySummary(10000.0, 2500.0, 7500.0, emptyList())
        val copilotContext = ctx().copy(
            settings = DriverSettings(dailyTargetArs = 15000.0),
            moneyToday = day,
            odometerTodayKm = 12.5,
            shift = ShiftCalculator.compute(
                listOf(HistoryEntry(3_600_000, "CONVIENE", 8000.0, 10.0, 25.0, 800.0, 19200.0, DataSource.ACCESSIBILITY, taken = true)),
                shiftStart = 0L,
                now = 2 * 3_600_000L,
            ),
        )
        val summary = CopilotEngine.answer("¿Cómo va mi jornada hoy?", copilotContext)
        assertTrue(summary, summary.contains("10.000"))
        assertTrue(summary, summary.contains("no ganancia neta"))
        assertTrue(summary, summary.contains("12,5 km"))
        assertTrue(summary, summary.contains("no confirma cobros"))
        assertTrue(summary, summary.contains("67 %"))
        val objective = CopilotEngine.answer("¿Cómo va mi objetivo diario?", copilotContext)
        assertTrue(objective, objective.contains("Faltan"))
        assertTrue(objective, objective.contains("no es una predicción"))
    }

    @Test fun informeExterno_contextualizaSinAfirmarBusquedaNiGps() {
        val prompt = ExternalBriefingPrompt.build(
            now = 1_800_000_000_000L,
            zoneId = ZoneId.of("America/Argentina/Buenos_Aires"),
            selectedWorkZone = "Palermo",
            dailyTargetArs = 15000.0,
            shiftStart = null,
            lastEvaluation = null,
        )
        assertTrue(prompt.contains("Palermo"))
        assertTrue(prompt.contains("No es ubicación GPS"))
        assertTrue(prompt.contains("Objetivo de ingresos registrado"))
        assertTrue(prompt.contains("DriverAI no realizó esta búsqueda"))
        assertTrue(prompt.contains("HECHOS CONFIRMADOS, ESTIMACIONES/PATRONES e HIPÓTESIS"))
        assertTrue(prompt.contains("No hay una evaluación de oferta disponible"))
    }

    @Test fun copiloto_respuestasBrevesYConFuente() {
        val e = OfferEvaluator.evaluate(OfferData(Reading.manual(2500.0), Reading.manual(3.0), Reading.manual(10.0), Reading.manual(6.0), Reading.manual(18.0)), DriverSettings(minGrossPerKm = 700.0))
        val a = CopilotEngine.answer("¿me conviene?", ctx(e))
        assertTrue(a, a.startsWith("No conviene"))
        assertTrue(a.length < 220)
        assertTrue(a, a.contains("\$6.300"))
        assertTrue(CopilotEngine.answer("¿por qué?", ctx(e)).contains("mínimo"))
        assertEquals("Todavía no evalué ninguna oferta.", CopilotEngine.answer("¿me conviene?", ctx()))
        val p = PosicionResponse(ok = true, zona_actual = "Microcentro", score_actual = 6.0, decision = "moverte", zona_recomendada = "Recoleta", score_recomendado = 10.0)
        val d = CopilotEngine.answer("¿dónde espero?", ctx(posicion = p))
        assertTrue(d, d.startsWith("Estimación del servidor") && d.contains("Recoleta"))
    val r = ReporteResponse(ok = true, reporte = Reporte(zonas_rankeadas = listOf(ZonaRank("Bernal", 8.0), ZonaRank("Palermo", 9.0), ZonaRank("Quilmes", 6.0))))
    val c = CopilotEngine.answer("cerca de casa", ctx(reporte = r, settings = DriverSettings(homeZone = "Quilmes")))
    assertTrue(c, c.contains("Bernal"))
    assertTrue(CopilotEngine.answer("cerca de casa", ctx()).contains("Configurá"))
    val missingEvents = CopilotEngine.answer("eventos esta noche", ctx(reporte = r))
    assertTrue(missingEvents, missingEvents.contains("no informó una lista de eventos"))
    val emptyEvents = ReporteResponse(ok = true, eventos = emptyList())
    val noEventsListed = CopilotEngine.answer("eventos esta noche", ctx(reporte = emptyEvents))
        assertTrue(noEventsListed, noEventsListed.contains("no listó eventos"))
    }

    @Test fun copiloto_porQue_priorizaFallaEconomicaSobreRatingAprobado() {
        val offer = OfferData(
            fare = Reading.manual(2000.0),
            tripKm = Reading.manual(5.0),
            tripMin = Reading.manual(20.0),
            passengerRating = Reading.manual(4.9),
        )
        val evaluation = OfferEvaluator.evaluate(
            offer,
            DriverSettings(minGrossPerKm = 800.0, minPassengerRating = 4.5),
        )

        val answer = CopilotEngine.answer("¿por qué?", ctx(evaluation))
        assertTrue(answer, answer.contains("Bruto por km"))
        assertFalse(answer, answer.contains("4,9/5"))
    }
}
