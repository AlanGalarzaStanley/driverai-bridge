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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
} catch (_: PackageManager.NameNotFoundException) {
    null
}

@Composable
fun DiagnosticsScreen(activity: Activity) {
    val g = DriverAiApp.graph
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var backendMsg by remember { mutableStateOf<String?>(null) }
    val diag by g.pipeline.diagnosticsEnabled.collectAsState()
    val diagnostic by g.pipeline.lastDiagnostic.collectAsState()
    val capturing by ScreenCaptureService.running.collectAsState()
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }

    @Composable
    fun CheckRow(label: String, ok: Boolean?, detail: String) {
        val tone = when (ok) {
            true -> DriverColors.Go
            false -> DriverColors.Caution
            null -> DriverColors.InkFaint
        }
        val tag = when (ok) {
            true -> "OK"
            false -> "FALTA"
            null -> "—"
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StatusPill(tag, tone)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(label, color = DriverColors.Ink, style = MaterialTheme.typography.bodyMedium)
                Text(detail, color = DriverColors.InkDim, style = MaterialTheme.typography.bodySmall)
            }
        }
        VSpace(8.dp)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenScaffold(
            title = "Chequeo",
            subtitle = "Diagnóstico de permisos, módulos y backend",
        ) {
            refresh.let { }

            Section("Dispositivo y app", accent = DriverColors.Ice) {
                Text(
                    "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    color = DriverColors.Ink,
                    style = MaterialTheme.typography.bodyMedium,
                )
                VSpace(4.dp)
                Text(
                    "DriverAI ${BuildConfig.VERSION_NAME} · ${BuildConfig.GIT_SHA} · ${BuildConfig.BUILD_TYPE}",
                    color = DriverColors.InkDim,
                    style = MaterialTheme.typography.bodySmall,
                )
                VSpace(10.dp)
                val uv = uberVersion(activity)
                CheckRow("Uber Driver", uv != null, uv?.let { "versión $it" } ?: "no instalado o no visible")
            }

            Section("Permisos y módulos") {
                CheckRow(
                    "Servicio de accesibilidad",
                    DriverAccessibilityService.isEnabled(activity),
                    if (DriverAccessibilityService.instance != null) "conectado" else "no conectado",
                )
                CheckRow(
                    "Mostrar sobre otras apps",
                    Settings.canDrawOverlays(activity),
                    if (Settings.canDrawOverlays(activity)) "concedido" else "no concedido (opcional si accesibilidad activa)",
                )
                CheckRow(
                    "Notificaciones",
                    NotificationManagerCompat.from(activity).areNotificationsEnabled(),
                    "para captura y alternativa a tarjeta",
                )
                CheckRow(
                    "Captura de pantalla",
                    capturing,
                    if (capturing) "sesión activa" else "inactiva (se pide al activar)",
                )
                CheckRow("OCR local (ML Kit)", true, "modelo incluido en el APK")
                CheckRow(
                    "Voz (TTS)",
                    g.tts.ready,
                    g.tts.localeUsed?.toLanguageTag() ?: (g.tts.initError ?: "inicializando…"),
                )
                val rec = activity.packageManager
                    .queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0)
                    .isNotEmpty()
                CheckRow(
                    "Reconocimiento de voz",
                    rec || SpeechRecognizer.isRecognitionAvailable(activity),
                    if (rec) "disponible" else "no disponible: usar texto",
                )
                CheckRow(
                    "Tarjeta flotante",
                    g.overlay.canShowOverlay(),
                    if (g.overlay.isCardVisible) "visible" else "oculta",
                )
                Text(
                    "Escaneos: ${g.pipeline.scans} · Ofertas detectadas: ${g.pipeline.offersDetected}",
                    color = DriverColors.InkFaint,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Section("Backend Supabase", accent = DriverColors.Cyan) {
                PrimaryCta("Probar backend", {
                    backendMsg = "Probando…"
                    scope.launch {
                        val t0 = System.currentTimeMillis()
                        val r1 = g.api.reporte()
                        val t1 = System.currentTimeMillis()
                        val r2 = g.api.posicion("Microcentro")
                        val t2 = System.currentTimeMillis()
                        fun s(r: ApiResult<*>) = if (r is ApiResult.Ok) "OK" else (r as ApiResult.Fail).message
                        backendMsg =
                            "driverai-reporte: ${s(r1)} (${t1 - t0} ms)\ndriverai-posicion: ${s(r2)} (${t2 - t1} ms)"
                    }
                }, Modifier.testTag("btn_backend"))
                backendMsg?.let {
                    VSpace(8.dp)
                    Text(it, modifier = Modifier.testTag("backend_msg"), color = DriverColors.InkDim, style = MaterialTheme.typography.bodySmall)
                }
            }

            Section("Pruebas", accent = DriverColors.InkFaint) {
                GhostCta("Probar tarjeta flotante (demo)", {
                    val demo = OfferData(
                        Reading.manual(8000.0),
                        Reading.manual(1.5),
                        Reading.manual(5.0),
                        Reading.manual(8.5),
                        Reading.manual(20.0),
                    )
                    val s = g.settings.value.takeIf { it.hasAnyThreshold } ?: DriverSettings(minGrossPerKm = 1.0)
                    g.overlay.showEvaluation(
                        EvalRecord(OfferEvaluator.evaluate(demo, s), System.currentTimeMillis(), DataSource.MANUAL, listOf("DEMO")),
                        false,
                    )
                }, Modifier.testTag("btn_overlay_demo"))
                VSpace(8.dp)
                GhostCta("Ocultar tarjetas") { g.overlay.hideAll() }
                if (BuildConfig.SIMULATOR_WATCH) {
                    VSpace(8.dp)
                    GhostCta("Abrir simulador de oferta (no es Uber)", {
                        activity.startActivity(Intent(activity, SimulatorActivity::class.java))
                    }, Modifier.testTag("btn_simulator"))
                }
            }

            Section("Textos leídos (calibración)", accent = DriverColors.Caution) {
                Text(
                    "Registra temporalmente solo etiquetas permitidas, tarifa, tiempos, distancias y veredicto. No conserva nombres, direcciones ni capturas; no se persiste ni se envía. Al apagarlo, se borra.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DriverColors.InkDim,
                )
                VSpace(10.dp)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Registrar lectura reducida", Modifier.weight(1f), color = DriverColors.Ink)
                    Switch(
                        diag,
                        { g.pipeline.setDiagnosticsEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DriverColors.Void,
                            checkedTrackColor = DriverColors.Cyan,
                            uncheckedThumbColor = DriverColors.InkDim,
                            uncheckedTrackColor = DriverColors.Rail,
                        ),
                    )
                }
                if (diag && diagnostic == null) {
                    VSpace(6.dp)
                    Text("Esperando una lectura de oferta…", style = MaterialTheme.typography.bodySmall, color = DriverColors.InkFaint)
                }
                diagnostic?.let { record ->
                    VSpace(10.dp)
                    Text(
                        "Veredicto: ${record.verdict}",
                        style = MaterialTheme.typography.titleMedium,
                        color = DriverColors.Ink,
                        modifier = Modifier.testTag("diag_verdict"),
                    )
                    VSpace(4.dp)
                    Text(
                        "Tarifa: ${record.fare.displayText()}\nDistancia del viaje: ${record.tripKm.displayText()}\nDuración del viaje: ${record.tripMin.displayText()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = DriverColors.InkDim,
                    )
                    VSpace(8.dp)
                    GhostCta("Copiar registro reducido", {
                        val cm = activity.getSystemService(ClipboardManager::class.java)
                        cm.setPrimaryClip(ClipData.newPlainText("DriverAI Chequeo", record.clipboardText()))
                    }, Modifier.testTag("btn_copy_diagnostic"))
                    if (record.snippets.isEmpty()) {
                        VSpace(6.dp)
                        Text("No se encontraron fragmentos de tarifa, tiempo o distancia.", style = MaterialTheme.typography.bodySmall, color = DriverColors.InkFaint)
                    }
                    record.snippets.forEach {
                        Text("· $it", style = MaterialTheme.typography.bodySmall, color = DriverColors.InkDim)
                    }
                }
            }
        }
    }
}

// local helper — padding without importing foundation.layout.padding at call sites that already have it
private fun Modifier.padding(start: androidx.compose.ui.unit.Dp) =
    this.then(androidx.compose.foundation.layout.padding(start = start))
