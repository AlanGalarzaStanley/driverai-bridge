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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text("DriverAI", style = MaterialTheme.typography.titleLarge)
        Text("Copiloto de ofertas · BA", color = Muted)
        Text("Estado: ${status.label}", modifier = Modifier.testTag("status"))

        Section("Clima (verificado)") {
            when {
                weatherState.loading -> Text("Actualizando Open-Meteo…")
                weatherState.weather != null -> {
                    val w = weatherState.weather!!
                    Text(
                        buildString {
                            append(w.temperatureC?.let { "${Fmt.num(it)} °C" } ?: "— °C")
                            append(" · viento ")
                            append(w.windKmh?.let { "${Fmt.num(it)} km/h" } ?: "—")
                            append(" · lluvia ")
                            append(if (w.rainNow) "sí" else "no")
                            w.precipProbNextHours?.let { append(" · prob. ~3 h $it %") }
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("Fuente: ${w.source}. No es demanda de apps.", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                else -> Text(weatherState.error ?: "Sin clima todavía.", color = Amber)
            }
            OutlinedButton(onClick = { scope.launch { g.weather.refresh() } }, modifier = Modifier.fillMaxWidth()) {
                Text("Actualizar clima")
            }
        }

        Section("Lectura de ofertas") {
            Text(if (a11yOn) "Lectura automática (accesibilidad): ACTIVADA" else "Lectura automática: desactivada", color = if (a11yOn) Green else Amber)
            Text(if (capturing) "Lectura por captura: ACTIVA (botón \"Leer oferta\")" else "Lectura por captura: inactiva")
            Text(if (overlayOk || a11yOn) "Tarjeta flotante: disponible" else "Tarjeta flotante: falta permiso (se usará notificación)")
            Spacer(Modifier.height(6.dp))
            Button(onClick = { dialog = if (a11yOn) "a11y_off" else "a11y" }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (a11yOn) "Desactivar lectura automática" else "Activar lectura automática")
            }
            OutlinedButton(onClick = { if (capturing) ScreenCaptureService.stop(activity) else dialog = "capture" }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (capturing) "Detener lectura por captura" else "Activar lectura por captura (OCR)")
            }
            if (!overlayOk) OutlinedButton(onClick = { openOverlaySettings(activity) }, modifier = Modifier.fillMaxWidth()) { Text("Permitir mostrar sobre otras apps") }
        }

        last?.let { rec ->
            Section("Última oferta") {
                Text(rec.evaluation.verdict.label, color = verdictColor(rec.evaluation.verdict), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                MetricsSummary(rec.evaluation.metrics)
                Text(rec.evaluation.headline)
            }
        }

        Section("Turno") {
            val stats = ShiftCalculator.compute(history, shiftStart, now)
            if (shiftStart == null) {
                Text("Iniciá el turno para medir tu ingreso bruto por hora (viajes marcados como \"La tomé\").")
                Button(onClick = { scope.launch { g.historyRepo.startShift(System.currentTimeMillis()) } }, modifier = Modifier.fillMaxWidth()) { Text("Iniciar turno") }
            } else {
                Text("Viajes tomados: ${stats.trips} · Bruto: ${Fmt.ars(stats.grossFare)}")
                Text("Horas: ${Fmt.num(stats.hours ?: 0.0)} · Bruto/h: ${stats.grossPerHour?.let { Fmt.ars(it) } ?: "—"}")
                OutlinedButton(onClick = { scope.launch { g.historyRepo.endShift() } }) { Text("Terminar turno") }
            }
        }

        Section("Copiloto") {
            OutlinedTextField(question, { question = it }, label = { Text("Preguntá algo") }, modifier = Modifier.fillMaxWidth().testTag("copilot_input"), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { if (question.isNotBlank()) ask(question) }, modifier = Modifier.testTag("copilot_ask")) { Text("Preguntar") }
                OutlinedButton(onClick = {
                    val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "¿Qué querés saber?")
                    try { speechLauncher.launch(i) } catch (e: ActivityNotFoundException) { answer = "Reconocimiento de voz no disponible en este teléfono. Escribí la pregunta." }
                }) { Text("Hablar") }
                TextButton(onClick = { g.tts.stop() }) { Text("Callar") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("¿Me conviene?", "¿Por qué?", "¿Dónde espero?").forEach { q ->
                    TextButton(onClick = { question = q; ask(q) }) { Text(q) }
                }
            }
            answer?.let { Text(it, modifier = Modifier.testTag("copilot_answer"), fontWeight = FontWeight.SemiBold) }
        }

        Section("Contexto del servidor") {
            val stale = strategic.isStale(now)
            Text(
                when {
                    strategic.loading -> "Actualizando…"
                    strategic.lastError != null -> "No disponible: ${strategic.lastError}. Las ofertas siguen con tus reglas."
                    stale -> "Desactualizado."
                    else -> "Respuesta recibida."
                },
                color = if (stale) Amber else Green,
            )
            Text(
                "Los puntajes de zona y la demanda del servidor NO están verificados. No son un mapa real de Uber.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { scope.launch { g.strategic.refreshReporte() } }) { Text("Actualizar") }
                Column {
                    OutlinedButton(onClick = { zoneMenu = true }) { Text(settings.currentZone ?: "Mi zona") }
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
                Text(
                    if (p.decision == "moverte")
                        "Estimación no verificada: moverte a ${p.zona_recomendada}"
                    else
                        "Estimación no verificada: quedarte en ${p.zona_actual}",
                    fontWeight = FontWeight.SemiBold,
                )
            }
            strategic.reporte?.let { r ->
                val ev = r.eventos.size
                Text(
                    when {
                        ev == 0 && r.hay_partido != true -> "Eventos verificados: ninguno en el servidor."
                        r.hay_partido == true && ev == 0 -> "El servidor marca partido sin detalle ni fuente."
                        else -> "Eventos en respuesta del servidor: $ev · partido flag: ${r.hay_partido == true}"
                    },
                    color = if (ev == 0) Amber else Green,
                )
                r.reporte?.resumen?.let {
                    Text("Resumen (puede ser generado): $it", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                r.reporte?.zonas_rankeadas?.take(3)?.forEach { z ->
                    Text("• ${z.zona}: score ${z.score?.let { Fmt.num(it) } ?: "—"} (no verificado)", color = Muted)
                }
                r.reporte?.precio_minimo_sugerido?.let {
                    Text("Precio mín. servidor ${Fmt.ars(it)} — tus umbrales mandan", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }
    }

    when (dialog) {
        "a11y" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Lectura automática de ofertas") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(DISCLOSURE_ACCESSIBILITY) } },
            confirmButton = { Button(onClick = { dialog = null; openAccessibilitySettings(activity) }) { Text("Acepto, ir a Accesibilidad") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("No, gracias") } },
        )
        "a11y_off" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Desactivar lectura") },
            text = { Text("En la pantalla que se abre, elegí DriverAI y desactivalo.") },
            confirmButton = { Button(onClick = { dialog = null; openAccessibilitySettings(activity) }) { Text("Abrir Accesibilidad") } },
        )
        "capture" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Lectura por captura") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(DISCLOSURE_CAPTURE) } },
            confirmButton = {
                Button(onClick = {
                    dialog = null
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else projectionLauncher.launch(activity.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
                }) { Text("Acepto") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun MetricsSummary(m: ar.driverai.bridge.engine.Metrics) {
    fun d(v: Double?, f: (Double) -> String) = v?.let(f) ?: "—"
    Text("Tarifa: ${d(m.fare, Fmt::ars)}")
    Text("Km recogida: ${d(m.pickupKm) { Fmt.num(it) }} · Km con pasajero: ${d(m.tripKm) { Fmt.num(it) }} · Retorno vacío: ${d(m.returnKm) { Fmt.num(it) }}")
    Text("Km totales: ${d(m.totalKm) { Fmt.num(it) }} · Min con pasajero: ${d(m.tripMin) { Fmt.num(it, 0) }} · Min totales: ${d(m.totalMin) { Fmt.num(it, 0) }}")
    Text("Bruto/km: ${d(m.grossPerKm, Fmt::ars)} · Bruto/h: ${d(m.grossPerHour, Fmt::ars)}", fontWeight = FontWeight.Bold, modifier = Modifier.testTag("metrics_gross"))
    if (m.variableCost != null) {
        Text("Costo variable est.: ${Fmt.ars(m.variableCost)} · Ganancia est. antes de fijos e impuestos: ${d(m.estimatedProfitBeforeFixed, Fmt::ars)} · Margen: ${d(m.marginPct) { Fmt.num(it) + " %" }}")
    }
}
