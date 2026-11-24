package ar.driverai.bridge.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import ar.driverai.bridge.DriverAiApp
import ar.driverai.bridge.capture.DriverAccessibilityService
import ar.driverai.bridge.capture.ScreenCaptureService
import ar.driverai.bridge.copilot.CopilotContext
import ar.driverai.bridge.copilot.CopilotEngine
import ar.driverai.bridge.copilot.Intent as CIntent
import ar.driverai.bridge.copilot.Zones
import ar.driverai.bridge.data.ShiftCalculator
import ar.driverai.bridge.engine.Fmt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val DISCLOSURE_ACCESSIBILITY = """DriverAI usa el servicio de accesibilidad de Android para LEER el texto visible de la app Uber Driver (solo ese paquete) mientras está abierta, con el único fin de detectar la tarifa, distancias y tiempos de una oferta y mostrarte una evaluación económica.

• Qué procesa: el texto visible en pantalla de Uber Driver, dentro de tu teléfono.
• Qué guarda: solo números de la evaluación (tarifa, km, minutos, veredicto) si el historial está activado. Nunca nombres, direcciones ni capturas.
• Qué transmite: nada del contenido de pantalla. Al servidor solo se envía la zona que elegís para el reporte estratégico.
• Qué NO hace: no toca la pantalla, no acepta ni rechaza viajes, no lee otras apps.

DriverAI no es una herramienta de accesibilidad para personas con discapacidad. Podés desactivarlo cuando quieras en Ajustes > Accesibilidad."""

const val DISCLOSURE_CAPTURE = """La lectura por captura usa la grabación de pantalla de Android SOLO cuando tocás el botón "Leer oferta": se toma una imagen, se reconoce el texto en el teléfono (OCR local) y la imagen se descarta en el acto.

• No se graba video ni se guardan imágenes.
• No se envía ninguna imagen a servidores.
• Android te pedirá confirmación y mostrará un aviso mientras esté activa. Se apaga sola a los 10 minutos sin uso o desde la notificación."""

fun openAccessibilitySettings(ctx: Context) = ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
fun openOverlaySettings(ctx: Context) = ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

@Composable
fun HomeScreen(activity: Activity) {
    val g = DriverAiApp.graph
    val scope = rememberCoroutineScope()
    val settings by g.settings.collectAsState()
    val strategic by g.strategic.state.collectAsState()
    val weatherState by g.weather.state.collectAsState()
    val status by g.pipeline.status.collectAsState()
    val last by g.pipeline.last.collectAsState()
    val history by g.historyRepo.entries.collectAsState(initial = emptyList())
    val shiftStart by g.historyRepo.shiftStart.collectAsState(initial = null)
    val capturing by ScreenCaptureService.running.collectAsState()
    var a11yOn by remember { mutableStateOf(false) }
    var overlayOk by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<String?>(null) }
    var zoneMenu by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        a11yOn = DriverAccessibilityService.isEnabled(activity)
        overlayOk = Settings.canDrawOverlays(activity)
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(30_000) } }
    LaunchedEffect(Unit) {
        if (weatherState.weather == null) g.weather.refresh()
        if (strategic.reporte == null) g.strategic.refreshReporte()
    }

    val projectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK && r.data != null) ScreenCaptureService.start(activity, r.resultCode, r.data!!)
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        val mpm = activity.getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    fun ask(q: String) {
        scope.launch {
            val intent = CopilotEngine.intentOf(q)
            if (intent == CIntent.DONDE_ESPERAR && settings.currentZone != null) g.strategic.refreshPosicion(settings.currentZone!!)
            if ((intent == CIntent.EVENTOS || intent == CIntent.CERCA_CASA) && g.strategic.state.value.isStale(System.currentTimeMillis())) g.strategic.refreshReporte()
            val t = System.currentTimeMillis()
            val ctx = CopilotContext(g.pipeline.last.value?.evaluation, ShiftCalculator.compute(history, shiftStart, t), g.strategic.state.value, settings, t)
            val a = CopilotEngine.answer(q, ctx)
            answer = a
            if (settings.voiceEnabled) g.tts.speak(a, settings.voiceVolume)
        }
    }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val text = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) { question = text; ask(text) }
    }

    val statusTone = when {
        a11yOn || capturing -> DriverColors.Positive
        else -> DriverColors.Warning
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenScaffold(
            title = "Inicio",
            subtitle = "Copiloto de ofertas · Buenos Aires",
            trailing = { StatusPill(status.label, statusTone, Modifier.testTag("status")) },
        ) {
            Section("Clima", accent = DriverColors.Info) {
                when {
                    weatherState.loading -> Text("Actualizando Open-Meteo…", color = DriverColors.TextSecondary)
                    weatherState.weather != null -> {
                        val w = weatherState.weather!!
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricTile("Temp", w.temperatureC?.let { "${Fmt.num(it)}°" } ?: "—", Modifier.weight(1f), emphasis = true)
                            MetricTile("Viento", w.windKmh?.let { "${Fmt.num(it)}" } ?: "—", Modifier.weight(1f))
                            MetricTile("Lluvia", if (w.rainNow) "Sí" else "No", Modifier.weight(1f))
                        }
                        VSpace(8.dp)
                        Text("Fuente: ${w.source}", style = MaterialTheme.typography.bodySmall, color = DriverColors.TextTertiary)
                    }
                    else -> Text(weatherState.error ?: "Sin clima todavía.", color = DriverColors.Warning)
                }
                VSpace(10.dp)
                GhostCta("Actualizar clima") { scope.launch { g.weather.refresh() } }
            }

            Section("Lectura", accent = if (a11yOn) DriverColors.Positive else DriverColors.Accent) {
                StatusPill(if (a11yOn) "Automática ON" else "Automática OFF", if (a11yOn) DriverColors.Positive else DriverColors.Warning)
                VSpace(8.dp)
                Text(if (capturing) "OCR por captura: activo" else "OCR por captura: inactivo", color = DriverColors.TextSecondary)
                Text(if (overlayOk || a11yOn) "Tarjeta flotante disponible" else "Falta permiso de overlay", color = DriverColors.TextSecondary)
                VSpace(12.dp)
                PrimaryCta(if (a11yOn) "Desactivar lectura automática" else "Activar lectura automática") {
                    dialog = if (a11yOn) "a11y_off" else "a11y"
                }
                VSpace(8.dp)
                GhostCta(if (capturing) "Detener captura OCR" else "Activar captura OCR") {
                    if (capturing) ScreenCaptureService.stop(activity) else dialog = "capture"
                }
                if (!overlayOk) {
                    VSpace(8.dp)
                    GhostCta("Permitir sobre otras apps") { openOverlaySettings(activity) }
                }
            }

            last?.let { rec ->
                Section("Última oferta", accent = verdictColor(rec.evaluation.verdict)) {
                    VerdictBanner(rec.evaluation.verdict.label, verdictColor(rec.evaluation.verdict), rec.evaluation.headline)
                    VSpace(12.dp)
                    MetricsSummary(rec.evaluation.metrics)
                }
            }

            Section("Turno") {
                val stats = ShiftCalculator.compute(history, shiftStart, now)
                if (shiftStart == null) {
                    Text("Iniciá el turno para medir bruto por hora.", color = DriverColors.TextSecondary)
                    VSpace(10.dp)
                    PrimaryCta("Iniciar turno") { scope.launch { g.historyRepo.startShift(System.currentTimeMillis()) } }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricTile("Viajes", "${stats.trips}", Modifier.weight(1f))
                        MetricTile("Bruto", Fmt.ars(stats.grossFare), Modifier.weight(1f), emphasis = true)
                        MetricTile("Bruto/h", stats.grossPerHour?.let { Fmt.ars(it) } ?: "—", Modifier.weight(1f))
                    }
                    VSpace(10.dp)
                    GhostCta("Terminar turno") { scope.launch { g.historyRepo.endShift() } }
                }
            }

            Section("Copiloto") {
                CockpitField(question, { question = it }, "Preguntá algo", Modifier.testTag("copilot_input"))
                VSpace(10.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryCta("Preguntar", { if (question.isNotBlank()) ask(question) }, Modifier.weight(1f).testTag("copilot_ask"))
                    GhostCta("Hablar", {
                        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
                            .putExtra(RecognizerIntent.EXTRA_PROMPT, "¿Qué querés saber?")
                        try { speechLauncher.launch(i) } catch (_: ActivityNotFoundException) {
                            answer = "Reconocimiento de voz no disponible. Escribí la pregunta."
                        }
                    }, Modifier.weight(1f))
                }
                VSpace(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("¿Me conviene?", "¿Por qué?", "¿Dónde espero?").forEach { q ->
                        TextButton(onClick = { question = q; ask(q) }) {
                            Text(q, color = DriverColors.Accent, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                answer?.let {
                    VSpace(10.dp)
                    Text(it, modifier = Modifier.testTag("copilot_answer"), fontWeight = FontWeight.SemiBold, color = DriverColors.TextPrimary)
                }
            }

            Section("Servidor", accent = DriverColors.TextTertiary) {
                val stale = strategic.isStale(now)
                StatusPill(
                    when {
                        strategic.loading -> "Actualizando"
                        strategic.lastError != null -> "Error"
                        stale -> "Desactualizado"
                        else -> "Online"
                    },
                    when {
                        strategic.loading -> DriverColors.Info
                        strategic.lastError != null || stale -> DriverColors.Warning
                        else -> DriverColors.Positive
                    },
                )
                VSpace(8.dp)
                Text("Puntajes de zona no verificados. No son demanda real de Uber.", style = MaterialTheme.typography.bodySmall, color = DriverColors.TextTertiary)
                VSpace(10.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostCta("Actualizar", { scope.launch { g.strategic.refreshReporte() } }, Modifier.weight(1f))
                    Column(Modifier.weight(1f)) {
                        GhostCta(settings.currentZone ?: "Mi zona") { zoneMenu = true }
                        DropdownMenu(zoneMenu, { zoneMenu = false }) {
                            Zones.ALL.forEach { z ->
                                DropdownMenuItem(text = { Text(z) }, onClick = {
                                    zoneMenu = false
                                    scope.launch {
                                        g.settingsRepo.update { it.copy(currentZone = z) }
                                        g.strategic.refreshPosicion(z)
                                    }
                                })
                            }
                        }
                    }
                }
                strategic.posicion?.let { p ->
                    VSpace(10.dp)
                    Text(
                        if (p.decision == "moverte") "Estimación: moverte a ${p.zona_recomendada}"
                        else "Estimación: quedarte en ${p.zona_actual}",
                        fontWeight = FontWeight.SemiBold,
                        color = DriverColors.TextSecondary,
                    )
                }
                strategic.reporte?.let { r ->
                    val ev = r.eventos.size
                    VSpace(8.dp)
                    Text(
                        when {
                            ev == 0 && r.hay_partido != true -> "Eventos verificados: ninguno"
                            r.hay_partido == true && ev == 0 -> "Partido marcado sin fuente"
                            else -> "Eventos en servidor: $ev"
                        },
                        color = if (ev == 0) DriverColors.Warning else DriverColors.Positive,
                    )
                }
            }
        }
    }

    when (dialog) {
        "a11y" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Lectura automática") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(DISCLOSURE_ACCESSIBILITY) } },
            confirmButton = { TextButton(onClick = { dialog = null; openAccessibilitySettings(activity) }) { Text("Acepto", color = DriverColors.Accent) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("No") } },
        )
        "a11y_off" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Desactivar lectura") },
            text = { Text("En la pantalla que se abre, elegí DriverAI y desactivalo.") },
            confirmButton = { TextButton(onClick = { dialog = null; openAccessibilitySettings(activity) }) { Text("Abrir") } },
        )
        "capture" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Lectura por captura") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(DISCLOSURE_CAPTURE) } },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else projectionLauncher.launch(activity.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
                }) { Text("Acepto", color = DriverColors.Accent) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun MetricsSummary(m: ar.driverai.bridge.engine.Metrics) {
    fun d(v: Double?, f: (Double) -> String) = v?.let(f) ?: "—"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricTile("Tarifa", d(m.fare, Fmt::ars), Modifier.weight(1f), emphasis = true)
        MetricTile("$/km", d(m.grossPerKm, Fmt::ars), Modifier.weight(1f))
        MetricTile("$/h", d(m.grossPerHour, Fmt::ars), Modifier.weight(1f))
    }
    VSpace(10.dp)
    Text(
        "Km total ${d(m.totalKm) { Fmt.num(it) }} · Min total ${d(m.totalMin) { Fmt.num(it, 0) }} · Recogida ${d(m.pickupKm) { Fmt.num(it) }} km",
        style = MaterialTheme.typography.bodySmall,
        color = DriverColors.TextSecondary,
        modifier = Modifier.testTag("metrics_gross"),
    )
    if (m.variableCost != null) {
        Text(
            "Costo var. ${Fmt.ars(m.variableCost)} · Margen ${d(m.marginPct) { Fmt.num(it) + "%" }}",
            style = MaterialTheme.typography.bodySmall,
            color = DriverColors.TextTertiary,
        )
    }
}
