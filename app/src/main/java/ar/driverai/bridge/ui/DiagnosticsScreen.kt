package ar.driverai.bridge.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import ar.driverai.bridge.BuildConfig
import ar.driverai.bridge.DriverAiApp
import ar.driverai.bridge.backend.ApiResult
import ar.driverai.bridge.capture.DriverAccessibilityService
import ar.driverai.bridge.capture.EvalRecord
import ar.driverai.bridge.capture.ScreenCaptureService
import ar.driverai.bridge.engine.DataSource
import ar.driverai.bridge.engine.DriverSettings
import ar.driverai.bridge.engine.OfferData
import ar.driverai.bridge.engine.OfferEvaluator
import ar.driverai.bridge.engine.Reading
import kotlinx.coroutines.launch

fun uberVersion(ctx: Context): String? = try {
    ctx.packageManager.getPackageInfo(DriverAccessibilityService.UBER_DRIVER_PACKAGE, 0).versionName ?: "instalado"
} catch (e: PackageManager.NameNotFoundException) { null }

@Composable
fun DiagnosticsScreen(activity: Activity) {
    val g = DriverAiApp.graph
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var backendMsg by remember { mutableStateOf<String?>(null) }
    val diag by g.pipeline.diagnosticsEnabled.collectAsState()
    val diagnostic by g.pipeline.lastDiagnostic.collectAsState()
    val capturing by ScreenCaptureService.running.collectAsState()
    LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose { } }

    @Composable fun row(label: String, ok: Boolean?, detail: String) {
        Text("${when (ok) { true -> "OK"; false -> "FALTA"; null -> "—" }}  $label: $detail",
            color = when (ok) { true -> Green; false -> Amber; null -> Color.LightGray })
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text("Diagnóstico", style = MaterialTheme.typography.titleLarge)
        refresh.let { }
        Section("Dispositivo y app") {
            Text("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            Text("DriverAI ${BuildConfig.VERSION_NAME} · commit ${BuildConfig.GIT_SHA} · ${BuildConfig.BUILD_TYPE}")
            val uv = uberVersion(activity)
            row("Uber Driver", uv != null, uv?.let { "versión $it" } ?: "no instalado o no visible")
        }
        Section("Permisos y módulos") {
            row("Servicio de accesibilidad", DriverAccessibilityService.isEnabled(activity), if (DriverAccessibilityService.instance != null) "conectado" else "no conectado")
            row("Mostrar sobre otras apps", Settings.canDrawOverlays(activity), if (Settings.canDrawOverlays(activity)) "concedido" else "no concedido (opcional si accesibilidad está activa)")
            row("Notificaciones", NotificationManagerCompat.from(activity).areNotificationsEnabled(), "para captura y alternativa a tarjeta")
            row("Captura de pantalla", capturing, if (capturing) "sesión activa" else "inactiva (se pide al activar)")
            row("OCR local (ML Kit)", true, "modelo incluido en el APK")
            row("Voz (TTS)", g.tts.ready, g.tts.localeUsed?.toLanguageTag() ?: (g.tts.initError ?: "inicializando…"))
            val rec = activity.packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty()
            row("Reconocimiento de voz", rec || SpeechRecognizer.isRecognitionAvailable(activity), if (rec) "disponible" else "no disponible: usar texto")
            row("Tarjeta flotante", g.overlay.canShowOverlay(), if (g.overlay.isCardVisible) "visible" else "oculta")
            Text("Escaneos: ${g.pipeline.scans} · Ofertas detectadas: ${g.pipeline.offersDetected}")
        }
        Section("Backend Supabase") {
            Button(onClick = {
                backendMsg = "Probando…"
                scope.launch {
                    val t0 = System.currentTimeMillis()
                    val r1 = g.api.reporte(); val t1 = System.currentTimeMillis()
                    val r2 = g.api.posicion("Microcentro"); val t2 = System.currentTimeMillis()
                    fun s(r: ApiResult<*>) = if (r is ApiResult.Ok) "OK" else (r as ApiResult.Fail).message
                    backendMsg = "driverai-reporte: ${s(r1)} (${t1 - t0} ms)\ndriverai-posicion: ${s(r2)} (${t2 - t1} ms)"
                }
            }, modifier = Modifier.testTag("btn_backend")) { Text("Probar backend") }
            backendMsg?.let { Text(it, modifier = Modifier.testTag("backend_msg")) }
        }
        Section("Pruebas") {
            OutlinedButton(onClick = {
                val demo = OfferData(Reading.manual(8000.0), Reading.manual(1.5), Reading.manual(5.0), Reading.manual(8.5), Reading.manual(20.0))
                val s = g.settings.value.takeIf { it.hasAnyThreshold } ?: DriverSettings(minGrossPerKm = 1.0)
                g.overlay.showEvaluation(EvalRecord(OfferEvaluator.evaluate(demo, s), System.currentTimeMillis(), DataSource.MANUAL, listOf("DEMO")), false)
            }, modifier = Modifier.fillMaxWidth().testTag("btn_overlay_demo")) { Text("Probar tarjeta flotante (demo)") }
            OutlinedButton(onClick = { g.overlay.hideAll() }, modifier = Modifier.fillMaxWidth()) { Text("Ocultar tarjetas") }
            if (BuildConfig.SIMULATOR_WATCH) OutlinedButton(onClick = { activity.startActivity(Intent(activity, SimulatorActivity::class.java)) }, modifier = Modifier.fillMaxWidth().testTag("btn_simulator")) {
                Text("Abrir simulador de oferta (no es Uber)")
            }
        }
        Section("Textos leídos (calibración)") {
            Text("Registra temporalmente solo etiquetas permitidas, tarifa, tiempos, distancias, rating explícito y veredicto. No conserva nombres, direcciones ni capturas; no se persiste ni se envía. Al apagarlo, se borra.", style = MaterialTheme.typography.bodySmall)
            androidx.compose.foundation.layout.Row { Text("Registrar lectura reducida", Modifier.weight(1f)); Switch(diag, { g.pipeline.setDiagnosticsEnabled(it) }) }
            if (diag && diagnostic == null) Text("Esperando una lectura de oferta…", style = MaterialTheme.typography.bodySmall)
            diagnostic?.let { record ->
                Text("Veredicto: ${record.verdict}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("diag_verdict"))
                Text("Tarifa: ${record.fare.displayText()}\nDistancia del viaje: ${record.tripKm.displayText()}\nDuración del viaje: ${record.tripMin.displayText()}\nCalificación del pasajero: ${record.passengerRating.displayText()}", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    val cm = activity.getSystemService(ClipboardManager::class.java)
                    cm.setPrimaryClip(ClipData.newPlainText("DriverAI Chequeo", record.clipboardText()))
                }, modifier = Modifier.testTag("btn_copy_diagnostic")) { Text("Copiar registro reducido") }
                if (record.snippets.isEmpty()) Text("No se encontraron fragmentos de tarifa, tiempo o distancia.", style = MaterialTheme.typography.bodySmall)
                record.snippets.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
