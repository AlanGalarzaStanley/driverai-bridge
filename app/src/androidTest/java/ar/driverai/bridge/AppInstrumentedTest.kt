package ar.driverai.bridge

import android.content.Intent
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import ar.driverai.bridge.capture.DriverAccessibilityService
import ar.driverai.bridge.engine.DataSource
import ar.driverai.bridge.engine.DriverSettings
import ar.driverai.bridge.engine.Verdict
import ar.driverai.bridge.ui.MainActivity
import ar.driverai.bridge.ui.SimulatorActivity
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
        DriverAiApp.graph.settingsRepo.update { DriverSettings(minGrossPerKm = 500.0, historyEnabled = true, speakVerdicts = false) }
        DriverAiApp.graph.overlay.hideAll()
    }

    private fun shot(name: String) {
        val dir = ctx.getExternalFilesDir(null) ?: return
        device.takeScreenshot(java.io.File(dir, "$name.png"))
    }

    private fun waitFor(timeoutMs: Long, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) { if (cond()) return true; Thread.sleep(200) }
        return cond()
    }

    @Test fun inicioAbre() {
        compose.onNodeWithTag("status").assertExists()
        compose.onNodeWithTag("tab_evaluar").assertExists()
    }

    @Test fun evaluacionManual_conviene() {
        compose.onNodeWithTag("tab_evaluar").performClick()
        compose.onNodeWithTag("in_fare").performTextInput("8000")
        compose.onNodeWithTag("in_pkm").performTextInput("2")
        compose.onNodeWithTag("in_pmin").performTextInput("5")
        compose.onNodeWithTag("in_tkm").performTextInput("8")
        compose.onNodeWithTag("in_tmin").performTextInput("20")
        compose.onNodeWithTag("btn_evaluate").performScrollTo().performClick()
        compose.onNodeWithTag("verdict").performScrollTo().assertTextContains("CONVIENE")
        compose.onNodeWithTag("metrics_gross").assertTextContains("$800", substring = true)
        shot("evaluacion-manual")
        assertEquals(DataSource.MANUAL, DriverAiApp.graph.pipeline.last.value!!.source)
    }

    @Test fun ajustes_guardan() {
        compose.onNodeWithTag("tab_ajustes").performClick()
        compose.onNodeWithTag("set_minhour").performTextInput("15000")
        compose.onNodeWithTag("btn_save").performScrollTo().performClick()
        compose.onNodeWithTag("settings_msg").assertTextContains("Guardado.")
        assertTrue(waitFor(3000) { DriverAiApp.graph.settings.value.minGrossPerHour == 15000.0 })
    }

    @Test fun tarjetaFlotante_apareceYSeCierra() {
        compose.onNodeWithTag("tab_diag").performClick()
        compose.onNodeWithTag("btn_overlay_demo").performScrollTo().performClick()
        assertTrue("tarjeta visible", waitFor(3000) { DriverAiApp.graph.overlay.isCardVisible })
        val close = device.wait(Until.findObject(By.desc("Cerrar tarjeta")), 3000)
        assertNotNull("botón cerrar en pantalla", close)
        shot("tarjeta-demo")
        close.click()
        assertTrue("tarjeta cerrada", waitFor(3000) { !DriverAiApp.graph.overlay.isCardVisible })
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

    @Test fun deduplicacion_mismaOfertaNoSeRepite() {
        val p = DriverAiApp.graph.pipeline
        val frags = listOf("$ 7.777", "A 3 min (1 km)", "Viaje de 10 min (5 km)")
        val before = p.offersDetected
        assertTrue(p.onScreenText(frags, DataSource.ACCESSIBILITY))
        assertTrue(p.onScreenText(frags, DataSource.ACCESSIBILITY))
        assertTrue(p.onScreenText(frags, DataSource.ACCESSIBILITY))
        assertEquals(before + 1, p.offersDetected)
    }
}
