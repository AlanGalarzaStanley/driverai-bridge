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
import ar.driverai.bridge.data.JournalResult
import ar.driverai.bridge.data.MaintenanceRecord
import ar.driverai.bridge.data.MoneyCategory
import ar.driverai.bridge.data.MoneyAmountParser
import ar.driverai.bridge.data.MoneyKind
import ar.driverai.bridge.data.MoneyLedgerRepository
import ar.driverai.bridge.data.PendingTripEstimateSummary
import ar.driverai.bridge.data.OdometerEntry
import ar.driverai.bridge.data.OdometerSaveResult
import ar.driverai.bridge.data.SettingsRepository
import ar.driverai.bridge.data.ShiftCalculator
import ar.driverai.bridge.data.TripStatus
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
        val target = repo.entries.first().first { it.at == 104L }
        repo.markTaken(target.id)
        assertTrue(repo.entries.first().first { it.id == target.id }.taken)
        val json = HistoryRepository.encode(list)
        assertFalse(json.contains("Av.")); assertFalse(json.contains("raw"))
        repo.clear(); assertTrue(repo.entries.first().isEmpty())
    }

    @Test fun historial_aceptacionDevuelveResultadoYRechazaDobleConfirmacion() = runBlocking {
        val repo = HistoryRepository(store("h-acceptance-result"))
        val evaluation = OfferEvaluator.evaluate(
            OfferData(Reading.manual(8000.0), Reading.manual(1.0), Reading.manual(3.0), Reading.manual(8.0), Reading.manual(20.0)),
            DriverSettings(minFare = 1.0),
        )
        repo.add(evaluation, 1L, DataSource.MANUAL, id = "acceptance-result")

        assertEquals(JournalResult.UPDATED, repo.markTaken("acceptance-result", at = 2L))
        assertEquals(JournalResult.INVALID_TRANSITION, repo.markTaken("acceptance-result", at = 3L))
        assertEquals(JournalResult.NOT_FOUND, repo.markTaken("missing", at = 4L))
        assertEquals(TripStatus.ACCEPTED, repo.entries.first().single().tripStatus)
    }

    @Test fun historial_conservaViajesPendientesMasAntiguosAlSuperarElLimite() = runBlocking {
        val sharedStore = store("h-pending-retention")
        val repo = HistoryRepository(sharedStore)
        val wallet = MoneyLedgerRepository(sharedStore)
        val evaluation = OfferEvaluator.evaluate(
            OfferData(Reading.manual(8000.0), Reading.manual(1.0), Reading.manual(3.0), Reading.manual(8.0), Reading.manual(20.0)),
            DriverSettings(minFare = 1.0),
        )
        repo.add(evaluation, 1L, DataSource.MANUAL, id = "old-pending")
        assertEquals(JournalResult.UPDATED, repo.markTaken("old-pending", at = 2L))
        repeat(HistoryRepository.MAX + 5) { index ->
            repo.add(evaluation, 10L + index, DataSource.MANUAL, id = "new-observed-$index")
        }

        val entries = repo.entries.first()
        assertEquals(HistoryRepository.MAX, entries.size)
        assertTrue("una aceptación sin cobro no debe desaparecer", entries.any { it.id == "old-pending" })
        assertEquals(1, PendingTripEstimateSummary.from(entries).tripCount)
        assertEquals(8000.0, PendingTripEstimateSummary.from(entries).offeredFareArs, 0.0)
        assertTrue("una estimación no crea ingreso real", wallet.entries.first().isEmpty())
    }

    @Test fun historial_migraIdsUnicosAunqueCompartaTimestamp() {
        val legacy = """[
            {"at":1,"verdict":"CONVIENE","fare":1000.0,"totalKm":1.0,"totalMin":10.0,"grossPerKm":1000.0,"grossPerHour":6000.0,"source":"MANUAL","taken":true},
            {"at":1,"verdict":"REVISAR","fare":900.0,"totalKm":1.0,"totalMin":10.0,"grossPerKm":900.0,"grossPerHour":5400.0,"source":"MANUAL","taken":false}
        ]"""
        val migrated = HistoryRepository.decode(legacy)
        assertEquals(2, migrated.map { it.id }.toSet().size)
        assertEquals(TripStatus.ACCEPTED, migrated.first().tripStatus)
        assertEquals(TripStatus.OBSERVED, migrated.last().tripStatus)
        assertEquals(migrated, HistoryRepository.decode(HistoryRepository.encode(migrated)))
    }

    @Test fun diarioViaje_separaOfertaDecisionFinalizacionYCobroYActualizaBilleteraUnaSolaVez() = runBlocking {
        val sharedStore = store("trip-journal")
        val historyRepo = HistoryRepository(sharedStore)
        val walletRepo = MoneyLedgerRepository(sharedStore)
        val evaluation = OfferEvaluator.evaluate(
            OfferData(Reading.manual(8000.0), Reading.manual(2.0), Reading.manual(5.0), Reading.manual(8.0), Reading.manual(20.0)),
            DriverSettings(minFare = 1.0),
        )
        historyRepo.add(evaluation, 100L, DataSource.MANUAL, id = "offer-1")

        assertEquals(TripStatus.OBSERVED, historyRepo.entries.first().single().tripStatus)
        assertEquals(JournalResult.UPDATED, historyRepo.recordOfferDecision("offer-1", accepted = false, at = 110L))
        assertEquals(JournalResult.INVALID_TRANSITION, historyRepo.completeTrip("offer-1", null, null, at = 120L))
        assertEquals(JournalResult.UPDATED, historyRepo.restoreObserved("offer-1", at = 130L))
        assertEquals(JournalResult.UPDATED, historyRepo.recordOfferDecision("offer-1", accepted = true, at = 140L))
        assertEquals(JournalResult.UPDATED, historyRepo.restoreObserved("offer-1", at = 145L))
        assertEquals(TripStatus.OBSERVED, historyRepo.entries.first().single().tripStatus)
        assertEquals(JournalResult.UPDATED, historyRepo.recordOfferDecision("offer-1", accepted = true, at = 146L))
        assertTrue("aceptar no crea un ingreso real", walletRepo.entries.first().isEmpty())
        val acceptedEstimate = PendingTripEstimateSummary.from(historyRepo.entries.first())
        assertEquals(1, acceptedEstimate.tripCount)
        assertEquals(8000.0, acceptedEstimate.offeredFareArs, 0.0)
        assertEquals(JournalResult.UPDATED, historyRepo.completeTrip("offer-1", actualDistanceKm = 9.4, actualDurationMin = 31.0, at = 150L))
        assertEquals(1, PendingTripEstimateSummary.from(historyRepo.entries.first()).tripCount)
        assertEquals(JournalResult.INVALID_AMOUNT, historyRepo.recordTripPayment("offer-1", 0.001, at = 160L))
        assertEquals(JournalResult.UPDATED, historyRepo.recordTripPayment("offer-1", 7500.0, at = 170L))
        assertEquals(0, PendingTripEstimateSummary.from(historyRepo.entries.first()).tripCount)
        val firstPayment = walletRepo.entries.first().single()
        assertEquals("offer-1", firstPayment.tripHistoryId)
        assertEquals(MoneyCategory.TRIP_INCOME, firstPayment.category)
        assertFalse(walletRepo.delete(firstPayment.id))
        assertEquals(1, walletRepo.entries.first().size)
        assertEquals(TripStatus.PAID, historyRepo.entries.first().single().tripStatus)
        assertEquals(7500.0, historyRepo.entries.first().single().actualIncomeArs!!, 0.0)

        assertEquals(JournalResult.UPDATED, historyRepo.recordTripPayment("offer-1", 7600.0, at = 180L))
        assertEquals(1, walletRepo.entries.first().size)
        assertEquals(7600.0, walletRepo.entries.first().single().amountArs, 0.0)
        val shift = ShiftCalculator.compute(historyRepo.entries.first(), shiftStart = 0L, now = 3_600_000L)
        assertEquals(1, shift.trips)
        assertEquals(1, shift.paidTrips)
        assertEquals(8000.0, shift.grossFare, 0.0)
        assertEquals(7600.0, shift.actualIncome, 0.0)
        assertEquals(7600.0, shift.actualIncomePerHour!!, 0.0)

        assertEquals(JournalResult.UPDATED, historyRepo.removeTripPayment("offer-1", at = 190L))
        assertEquals(TripStatus.COMPLETED, historyRepo.entries.first().single().tripStatus)
        assertTrue(walletRepo.entries.first().isEmpty())

        assertEquals(JournalResult.UPDATED, historyRepo.recordTripPayment("offer-1", 7600.0, at = 200L))
        historyRepo.clear()
        assertTrue(historyRepo.entries.first().isEmpty())
        assertEquals(1, walletRepo.entries.first().size)
        assertNull(walletRepo.entries.first().single().tripHistoryId)
    }

    @Test fun diario_alExpulsarUnaOfertaPagadaConservaElIngresoYLoDesvincula() = runBlocking {
        val sharedStore = store("trip-journal-overflow")
        val historyRepo = HistoryRepository(sharedStore)
        val walletRepo = MoneyLedgerRepository(sharedStore)
        val evaluation = OfferEvaluator.evaluate(
            OfferData(Reading.manual(8000.0), Reading.manual(2.0), Reading.manual(5.0), Reading.manual(8.0), Reading.manual(20.0)),
            DriverSettings(minFare = 1.0),
        )
        historyRepo.add(evaluation, 1L, DataSource.MANUAL, id = "old-paid-offer")
        historyRepo.recordOfferDecision("old-paid-offer", accepted = true, at = 2L)
        historyRepo.completeTrip("old-paid-offer", actualDistanceKm = null, actualDurationMin = null, at = 3L)
        historyRepo.recordTripPayment("old-paid-offer", 7600.0, at = 4L)
        val paymentId = walletRepo.entries.first().single().id

        repeat(HistoryRepository.MAX) { index ->
            historyRepo.add(evaluation, 10L + index, DataSource.MANUAL, id = "new-offer-$index")
        }

        assertFalse(historyRepo.entries.first().any { it.id == "old-paid-offer" })
        val retainedPayment = walletRepo.entries.first().single { it.id == paymentId }
        assertEquals(7600.0, retainedPayment.amountArs, 0.0)
        assertNull(retainedPayment.tripHistoryId)
        assertTrue(walletRepo.delete(paymentId))
        assertTrue(walletRepo.entries.first().isEmpty())
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

    @Test fun copiloto_distingueCobrosRealesVinculadosDeTarifasYGananciaNeta() {
        val paid = HistoryEntry(
            3_600_000, "CONVIENE", 8000.0, 10.0, 25.0, 800.0, 19200.0,
            DataSource.ACCESSIBILITY,
            taken = true,
            id = "paid-trip",
            tripStatus = TripStatus.PAID,
            actualIncomeArs = 7600.0,
        )
        val shift = ShiftCalculator.compute(listOf(paid), shiftStart = 0L, now = 2 * 3_600_000L)
        val answer = CopilotEngine.answer("¿Cuánto hago por hora?", ctx().copy(shift = shift))
        assertTrue(answer, answer.contains("tarifas"))
        assertTrue(answer, answer.contains("Cobros vinculados: 1 viajes, \$7.600"))
        assertTrue(answer, answer.contains("No es ganancia neta"))
    }

    @Test fun billetera_persisteYResumePorDiaLocal() = runBlocking {
        val legacyMoney = """[{"id":"old-entry","at":1,"kind":"INCOME","amountArs":2.0,"category":"TRIP_INCOME"}]"""
        assertNull(MoneyLedgerRepository.decode(legacyMoney).single().tripHistoryId)
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
        assertEquals(Intent.PAROS, CopilotEngine.intentOf("¿Hay paro de colectivos?"))
        assertEquals(Intent.MEJOR_HORA, CopilotEngine.intentOf("¿Cuál es mi mejor hora?"))
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
        assertTrue(objective, objective.contains("No es una predicción"))
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

    @Test fun copiloto_usaElRadarLocalAntesQueElServidor() {
        val now = java.time.LocalDateTime.of(2026, 11, 25, 22, 40).atZone(java.time.ZoneId.of("America/Argentina/Buenos_Aires")).toInstant().toEpochMilli()
        val partido = ar.driverai.bridge.intel.DemandSignal(
            "p1", ar.driverai.bridge.intel.SignalKind.EVENTO_DEPORTIVO, ar.driverai.bridge.intel.SignalOrigin.API_PUBLICA,
            "Boca vs Independiente Rivadavia", "la_boca", now - 100 * 60_000L, null, 0.67, 0.75, "TheSportsDB", null, now,
        )
        val radar = ar.driverai.bridge.intel.DemandRadar.forecast(ar.driverai.bridge.intel.Cities.AMBA, listOf(partido), now)
        val c = ctx().copy(radar = radar, now = now)
        val d = CopilotEngine.answer("¿dónde espero?", c)
        assertTrue(d, d.startsWith("Radar local: La Boca"))
        val ev = CopilotEngine.answer("eventos esta noche", c)
        assertTrue(ev, ev.contains("Boca vs Independiente Rivadavia"))
        assertTrue(CopilotEngine.answer("¿hay paro?", c).contains("No tengo paros"))
        val mh = CopilotEngine.answer("¿cuál es mi mejor hora?", c.copy(bestHours = listOf(Triple(8, 25000.0, 12))))
        assertTrue(mh, mh.contains("08 h") && mh.contains("12 ofertas"))
    }
}
