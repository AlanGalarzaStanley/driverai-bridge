package ar.driverai.bridge

import android.content.Intent
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import ar.driverai.bridge.capture.DriverAccessibilityService
import ar.driverai.bridge.engine.DataSource
import ar.driverai.bridge.engine.DriverSettings
import ar.driverai.bridge.engine.OfferData
import ar.driverai.bridge.engine.OfferEvaluator
import ar.driverai.bridge.engine.Reading
import ar.driverai.bridge.engine.Verdict
import ar.driverai.bridge.ui.MainActivity
import ar.driverai.bridge.ui.SimulatorActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppInstrumentedTest {
    companion object {
        init {
            // Por defecto UiAutomation SUSPENDE los demás servicios de accesibilidad durante la prueba.
            androidx.test.uiautomator.Configurator.getInstance().uiAutomationFlags =
                android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        }
    }

    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun setUp(): Unit = runBlocking {
        DriverAiApp.graph.settingsRepo.update { DriverSettings(minGrossPerKm = 500.0, historyEnabled = true, speakVerdicts = false, dailyTargetArs = null, onboardingDismissed = true, peakAlerts = false) }
        DriverAiApp.graph.overlay.hideAll()
        DriverAiApp.graph.historyRepo.clear()
        DriverAiApp.graph.walletRepo.clear()
        DriverAiApp.graph.vehicleRepo.clearAll()
    }

    private fun shot(name: String) {
        device.takeScreenshot(java.io.File(ctx.filesDir, "$name.png"))
    }

    private fun waitFor(timeoutMs: Long, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) { if (cond()) return true; Thread.sleep(200) }
        return cond()
    }

    @Test fun inicioAbre_simpleYEnfocado() {
        compose.onNodeWithTag("status").assertExists()
        compose.onNodeWithTag("tab_billetera").assertExists()
        compose.onNodeWithTag("tab_radar").assertExists()
        assertTrue("4 pestañas, sin Oferta", compose.onAllNodesWithTag("tab_evaluar").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("home_radar").assertExists()
        compose.onNodeWithTag("home_ai").assertExists()
        compose.onNodeWithTag("home_wallet").performScrollTo().assertExists()
        compose.onNodeWithTag("home_open_evaluate").performScrollTo().assertExists()
        // El contexto heredado del servidor ya no está en Inicio: vive en los detalles del Radar.
        assertTrue(compose.onAllNodesWithText("Briefing del turno").fetchSemanticsNodes().isEmpty())
        shot("inicio")
    }

    @Test fun primerosPasos_seMuestranYSeOcultan() {
        runBlocking { DriverAiApp.graph.settingsRepo.update { DriverSettings(onboardingDismissed = false, peakAlerts = false) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("home_first_steps").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("step_thresholds").performClick()
        compose.onNodeWithTag("qs_price").performTextInput("1500")
        compose.onNodeWithTag("qs_consumption").performTextInput("8")
        compose.onNodeWithTag("qs_perhour").performTextInput("12000")
        compose.onNodeWithTag("qs_save").performClick()
        assertTrue(waitFor(3000) { DriverAiApp.graph.settings.value.minGrossPerKm == 360.0 })
        assertEquals(12000.0, DriverAiApp.graph.settings.value.minGrossPerHour!!, 0.0)
        compose.onNodeWithTag("first_steps_hide").performScrollTo().performClick()
        assertTrue(waitFor(3000) { DriverAiApp.graph.settings.value.onboardingDismissed })
    }

    @Test fun evaluacionManual_conviene() {
        compose.onNodeWithTag("home_open_evaluate").performScrollTo().performClick()
        compose.onNodeWithTag("in_fare").performTextReplacement("8000")
        compose.onNodeWithTag("in_pkm").performTextReplacement("2")
        compose.onNodeWithTag("in_pmin").performTextReplacement("5")
        compose.onNodeWithTag("in_tkm").performTextReplacement("8")
        compose.onNodeWithTag("in_tmin").performTextReplacement("20")
        compose.onNodeWithTag("btn_evaluate").performScrollTo().performClick()
        compose.onNodeWithTag("verdict").performScrollTo().assertTextContains("CONVIENE")
        compose.onNodeWithTag("metrics_gross").assertTextContains("$800", substring = true)
        shot("evaluacion-manual")
        assertEquals(DataSource.MANUAL, DriverAiApp.graph.pipeline.last.value!!.source)
    }

    @Test fun ajustes_guardan() {
        compose.onNodeWithTag("tab_mas").performClick()
        compose.onNodeWithTag("tab_ajustes").performClick()
        compose.onNodeWithTag("set_minhour").performTextInput("15000")
        compose.onNodeWithTag("btn_save").performScrollTo().performClick()
        compose.onNodeWithTag("settings_msg").assertTextContains("Guardado.")
        assertTrue(waitFor(3000) { DriverAiApp.graph.settings.value.minGrossPerHour == 15000.0 })
    }

    @Test fun objetivoDiario_yCopilotoAbreDatosConIA() {
        compose.onNodeWithTag("tab_mas").performClick()
        compose.onNodeWithTag("tab_ajustes").performClick()
        compose.onNodeWithTag("set_daily_target").performScrollTo().performTextInput("15000")
        compose.onNodeWithTag("btn_save").performScrollTo().performClick()
        compose.onNodeWithTag("settings_msg").assertTextContains("Guardado.")
        assertEquals(15000.0, DriverAiApp.graph.settings.value.dailyTargetArs!!, 0.0)

        compose.onNodeWithTag("tab_inicio").performClick()
        compose.onNodeWithTag("home_daily_income").assertExists()
        compose.onNodeWithText("Meta \$15.000 · 0 %").performScrollTo().assertExists()
        compose.onNodeWithTag("home_open_copilot").performScrollTo().performClick()
        compose.onNodeWithText("Respuestas locales por reglas").assertExists()
        compose.onNodeWithTag("external_report").performClick()
        compose.onNodeWithTag("ai_copy").assertExists()
        compose.onNodeWithTag("ai_back").performClick()
        compose.onNodeWithTag("status").assertExists()
    }

    @Test fun ajustes_rechazanUmbralRatingNoFinito() {
        compose.onNodeWithTag("tab_mas").performClick()
        compose.onNodeWithTag("tab_ajustes").performClick()
        compose.onNodeWithTag("settings_advanced").performScrollTo().performClick()
        compose.onNodeWithTag("set_min_passenger_rating").performScrollTo().performTextInput("NaN")
        compose.onNodeWithTag("btn_save").performScrollTo().performClick()

        compose.onNodeWithTag("settings_msg").assertTextContains("Valores inválidos", substring = true)
        assertNull(DriverAiApp.graph.settings.value.minPassengerRating)
    }

    @Test fun tarjetaFlotante_apareceYSeCierra() {
        compose.onNodeWithTag("tab_mas").performClick()
        compose.onNodeWithTag("tab_diag").performClick()
        compose.onNodeWithTag("btn_overlay_demo").performScrollTo().performClick()
        assertTrue("tarjeta visible", waitFor(3000) { DriverAiApp.graph.overlay.isCardVisible })
        val close = device.wait(Until.findObject(By.desc("Cerrar tarjeta")), 3000)
        assertNotNull("botón cerrar en pantalla", close)
        shot("tarjeta-demo")
        close.click()
        assertTrue("tarjeta cerrada", waitFor(3000) { !DriverAiApp.graph.overlay.isCardVisible })
    }

    @Test fun tarjetaCompacta_confirmaAceptacionYActualizaEstimacionSinCrearIngreso() = runBlocking {
        val tripId = "compact-overlay-acceptance"
        val at = System.currentTimeMillis()
        val evaluation = OfferEvaluator.evaluate(
            OfferData(Reading.manual(8000.0), Reading.manual(2.0), Reading.manual(5.0), Reading.manual(8.0), Reading.manual(20.0)),
            DriverSettings(minFare = 1.0),
        )
        DriverAiApp.graph.historyRepo.add(evaluation, at, DataSource.MANUAL, id = tripId)
        val record = ar.driverai.bridge.capture.EvalRecord(evaluation, at, DataSource.MANUAL, emptyList(), historyId = tripId)
        DriverAiApp.graph.overlay.showEvaluation(record, compact = true)
        assertTrue("tarjeta compacta visible", waitFor(3000) { DriverAiApp.graph.overlay.isCardVisible })

        val accept = device.wait(
            Until.findObject(By.desc("Confirmar que acepté este viaje. Se agregará como estimación pendiente en Billetera, no como ingreso real.")),
            5000,
        )
        assertNotNull("CTA de aceptación accesible en modo compacto", accept)
        accept.click()

        val accepted = kotlinx.coroutines.withTimeout(5_000L) {
            DriverAiApp.graph.historyRepo.entries.first { entries ->
                entries.any { it.id == tripId && it.tripStatus == ar.driverai.bridge.data.TripStatus.ACCEPTED }
            }.single { it.id == tripId }
        }
        assertEquals(ar.driverai.bridge.data.TripStatus.ACCEPTED, accepted.tripStatus)
        val pending = ar.driverai.bridge.data.PendingTripEstimateSummary.from(DriverAiApp.graph.historyRepo.entries.first())
        assertEquals(1, pending.tripCount)
        assertEquals(8000.0, pending.offeredFareArs, 0.0)
        assertTrue("aceptar no crea movimiento de ingreso", DriverAiApp.graph.walletRepo.entries.first().isEmpty())
        assertNotNull("la tarjeta confirma el resultado guardado", device.wait(Until.findObject(By.textContains("Aceptación guardada.")), 3000))

        compose.onNodeWithTag("tab_billetera").performClick()
        compose.onNodeWithTag("wallet_pending_trip_estimate").assertExists()
        compose.onNodeWithTag("wallet_pending_trip_amount").assertExists()
        compose.onNodeWithText("Aceptado · falta completar el viaje").assertExists()
        assertTrue("estimación separada del balance", DriverAiApp.graph.walletRepo.entries.first().isEmpty())
    }

    @Test fun accesibilidad_simulador_extremoAExtremo() {
        Assume.assumeTrue("servicio de accesibilidad habilitado por el script de CI", DriverAccessibilityService.isEnabled(ctx))
        if (!waitFor(8_000) { DriverAccessibilityService.instance != null }) {
            // El proceso fue reiniciado por la instrumentación: se re-habilita el servicio para forzar el enlace
            val ua = InstrumentationRegistry.getInstrumentation().getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            val comp = "${ctx.packageName}/${DriverAccessibilityService::class.java.name}"
            ua.executeShellCommand("settings put secure enabled_accessibility_services null").close()
            Thread.sleep(1500)
            ua.executeShellCommand("settings put secure enabled_accessibility_services $comp").close()
        }
        assertTrue("servicio conectado", waitFor(15_000) { DriverAccessibilityService.instance != null })
        ctx.startActivity(Intent(ctx, SimulatorActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(SimulatorActivity.EXTRA_SCENARIO, 0))
        assertTrue("oferta leída por accesibilidad", waitFor(10_000) {
            DriverAiApp.graph.pipeline.last.value?.let { it.source == DataSource.ACCESSIBILITY && it.evaluation.metrics.fare == 8540.0 } == true
        })
        val rec = DriverAiApp.graph.pipeline.last.value!!
        assertEquals(9.3, rec.evaluation.metrics.tripKm!!, 1e-9)
        assertEquals(1.2, rec.evaluation.metrics.pickupKm!!, 1e-9)
        assertEquals(Verdict.CONVIENE, rec.evaluation.verdict) // 8540 / 10,5 km = 813/km ≥ 500
        assertTrue("tarjeta visible sobre la app", waitFor(5000) { DriverAiApp.graph.overlay.isCardVisible })
        assertNotNull(device.wait(Until.findObject(By.desc("Tarjeta DriverAI")), 3000))
        val cardSummary = DriverAiApp.graph.overlay.lastShownText.orEmpty()
        assertTrue("veredicto destacado en resumen", cardSummary.contains("CONVIENE"))
        assertTrue("métrica económica visible", cardSummary.contains("BRUTO / KM"))
        assertTrue("distancia de recogida visible", cardSummary.contains("HASTA PASAJERO"))
        shot("simulador-tarjeta")
        // Rotación con la tarjeta visible: no debe fallar
        device.setOrientationLeft(); Thread.sleep(1500); device.setOrientationNatural(); Thread.sleep(1000)
        // Sin oferta en pantalla → la tarjeta se oculta sola
        device.findObject(By.text("Siguiente escenario"))?.click(); Thread.sleep(800) // escenario 1
        device.findObject(By.text("Siguiente escenario"))?.click(); Thread.sleep(800) // 2
        device.findObject(By.text("Siguiente escenario"))?.click()                    // 3: sin oferta
        assertTrue("tarjeta oculta al desaparecer la oferta", waitFor(20_000) { !DriverAiApp.graph.overlay.isCardVisible })
        // El simulador sigue en primer plano: DriverAI no tocó nada
        assertNotNull(device.findObject(By.text("SIMULADOR DriverAI — no es Uber")))
    }

    @Test fun billetera_registraIngresoYGastoSeparadosDeOfertas() = runBlocking {
        compose.onNodeWithTag("tab_billetera").performClick()
        compose.onNodeWithTag("wallet_add_income").performClick()
        compose.onNodeWithTag("wallet_amount").performTextReplacement("12000")
        compose.onNodeWithTag("wallet_save").performClick()
        compose.onNodeWithTag("wallet_add_expense").performClick()
        compose.onNodeWithTag("wallet_amount").performTextReplacement("2500")
        compose.onNodeWithTag("wallet_save").performClick()
        compose.onNodeWithText("Movimientos de hoy").assertExists()
        compose.onNodeWithText("Ingreso confirmado").assertExists()
        compose.onNodeWithText("Combustible").assertExists()
        val summary = ar.driverai.bridge.data.MoneyLedgerRepository.summarize(DriverAiApp.graph.walletRepo.entries.first())
        assertEquals(12000.0, summary.incomeArs, 0.0)
        assertEquals(2500.0, summary.expensesArs, 0.0)
    }

    @Test fun historial_completaViajeYRegistraCobroVinculadoEnBilletera() = runBlocking {
        val tripId = "instrumented-trip-lifecycle"
        val evaluation = OfferEvaluator.evaluate(
            OfferData(Reading.manual(8000.0), Reading.manual(2.0), Reading.manual(5.0), Reading.manual(8.0), Reading.manual(20.0)),
            DriverSettings(minFare = 1.0),
        )
        DriverAiApp.graph.historyRepo.add(evaluation, System.currentTimeMillis(), DataSource.MANUAL, id = tripId)

        compose.onNodeWithTag("tab_mas").performClick()
        compose.onNodeWithTag("tab_historial").performClick()
        compose.onNodeWithText("La acepté").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Viaje completado").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Corregir estado").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("La acepté").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("La acepté").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Viaje completado").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("tab_billetera").performClick()
        compose.onNodeWithTag("wallet_pending_trip_estimate").assertExists()
        compose.onNodeWithTag("wallet_pending_trip_amount").assertExists()
        assertTrue("aceptar no crea un movimiento de dinero", DriverAiApp.graph.walletRepo.entries.first().isEmpty())
        compose.onNodeWithTag("tab_mas").performClick()
        compose.onNodeWithTag("tab_historial").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Viaje completado").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Viaje completado").performScrollTo().performClick()
        compose.onNodeWithTag("trip_actual_km").performTextInput("9,4")
        compose.onNodeWithTag("trip_actual_minutes").performTextInput("31")
        compose.onNodeWithTag("trip_save_completed").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Registrar cobro real").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Registrar cobro real").performScrollTo().performClick()
        compose.onNodeWithTag("trip_actual_income").performTextInput("7.600,50")
        compose.onNodeWithTag("trip_save_income").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Ingreso cobrado y vinculado:", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        val recordedTrip = DriverAiApp.graph.historyRepo.entries.first().single { it.id == tripId }
        val walletEntry = DriverAiApp.graph.walletRepo.entries.first().single()
        assertEquals(ar.driverai.bridge.data.TripStatus.PAID, recordedTrip.tripStatus)
        assertEquals(9.4, recordedTrip.actualDistanceKm!!, 1e-9)
        assertEquals(31.0, recordedTrip.actualDurationMin!!, 0.0)
        assertEquals(7600.5, recordedTrip.actualIncomeArs!!, 1e-9)
        assertEquals(tripId, walletEntry.tripHistoryId)
        assertEquals(7600.5, walletEntry.amountArs, 1e-9)

        compose.onNodeWithTag("tab_billetera").performClick()
        compose.onNodeWithText("Vinculado al diario. Corregilo desde Actividad de ofertas.").performScrollTo().assertExists()
        assertTrue("un cobro vinculado no se borra desde Billetera", compose.onAllNodesWithText("Borrar").fetchSemanticsNodes().isEmpty())
    }

    @Test fun radar_muestraPronosticoYFuentes() {
        compose.onNodeWithTag("tab_radar").performClick()
        assertTrue(waitFor(5_000) { compose.onAllNodesWithTag("radar_now").fetchSemanticsNodes().isNotEmpty() })
        compose.onNodeWithTag("radar_add_signal").performScrollTo().assertExists()
        compose.onNodeWithTag("radar_details").performScrollTo().performClick()
        compose.onNodeWithTag("radar_refresh").performScrollTo().assertExists()
        compose.onNodeWithText("Briefing del turno").performScrollTo().assertExists()
    }

    @Test fun informeIA_pegarRevisarYCargarAlRadar() = runBlocking {
        DriverAiApp.graph.intelRepo.clearAi()
        val start = java.time.ZonedDateTime.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires")).plusHours(2).withSecond(0).withNano(0)
        val answer = "```json\n{\"ciudad\":\"amba\",\"senales\":[{\"tipo\":\"partido\",\"titulo\":\"River vs Boca\",\"lugar\":\"Monumental\",\"zona\":\"nunez\",\"inicio\":\"$start\",\"intensidad\":\"alta\",\"confianza\":\"alta\",\"fuente\":\"AFA\",\"url\":\"https://www.afa.com.ar\"}],\"resumen\":\"Superclásico\"}\n```"
        compose.onNodeWithTag("home_ai").performScrollTo().performClick()
        compose.onNodeWithTag("ai_copy").performClick()
        compose.onNodeWithText("Copiado").assertExists()
        compose.onNodeWithTag("ai_answer").performScrollTo().performTextInput(answer)
        compose.onNodeWithTag("ai_check").performScrollTo().performClick()
        compose.onNodeWithTag("ai_import").performScrollTo().performClick()
        assertTrue(waitFor(3000) { DriverAiApp.graph.radar.cache.value.aiSignals.size == 1 })
        assertEquals(ar.driverai.bridge.intel.SignalOrigin.IA_EXTERNA, DriverAiApp.graph.radar.cache.value.aiSignals.single().origin)
        shot("informe-ia")
        DriverAiApp.graph.intelRepo.clearAi()
    }

    @Test fun compartirTextoDesdeOtraApp_abreInformeIA() {
        compose.activityRule.scenario.onActivity { a ->
            a.startActivity(Intent(Intent.ACTION_SEND).setClass(a, MainActivity::class.java).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "sin json"))
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("ai_error").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("ai_back").performClick()
    }

    @Test fun vehiculo_guardaKilometrosYRecordatorio() {
        compose.onNodeWithTag("tab_mas").performClick()
        compose.onNodeWithTag("tab_vehiculo").performScrollTo().performClick()
        compose.onNodeWithTag("odo_start").performTextReplacement("1000")
        compose.onNodeWithTag("odo_end").performTextReplacement("1012")
        compose.onNodeWithTag("odo_save").performScrollTo().performClick()
        compose.onNodeWithText("Guardado en este teléfono.").assertExists()
        compose.onNodeWithTag("maintenance_add").performScrollTo().performClick()
        compose.onNodeWithTag("maintenance_title").performTextReplacement("VTV")
        compose.onNodeWithTag("maintenance_due_km").performTextReplacement("1500")
        compose.onNodeWithTag("maintenance_save").performClick()
        compose.onNodeWithText("VTV").assertExists()
        compose.onNodeWithText("Pendiente").assertExists()
    }

    @Test fun deduplicacion_mismaOfertaNoSeRepite() {
        val p = DriverAiApp.graph.pipeline
        val frags = listOf("Toca para aceptar", "$ 7.777", "A 3 min (1 km)", "Viaje de 10 min (5 km)")
        val before = p.offersDetected
        assertTrue(p.onScreenText(frags, DataSource.ACCESSIBILITY))
        assertTrue(p.onScreenText(frags, DataSource.ACCESSIBILITY))
        assertTrue(p.onScreenText(frags, DataSource.ACCESSIBILITY))
        assertEquals(before + 1, p.offersDetected)
    }
}
