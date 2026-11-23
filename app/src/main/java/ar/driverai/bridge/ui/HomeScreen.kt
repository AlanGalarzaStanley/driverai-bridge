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
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import ar.driverai.bridge.DriverAiApp
import ar.driverai.bridge.backend.ApiResult
import ar.driverai.bridge.backend.LocalWeatherRepository
import ar.driverai.bridge.backend.StrategicRepository
import ar.driverai.bridge.capture.DriverAccessibilityService
import ar.driverai.bridge.capture.ScreenCaptureService
import ar.driverai.bridge.copilot.CopilotContext
import ar.driverai.bridge.copilot.CopilotEngine
import ar.driverai.bridge.copilot.Intent as CIntent
import ar.driverai.bridge.copilot.Zones
import ar.driverai.bridge.data.MoneyLedgerRepository
import ar.driverai.bridge.data.ShiftCalculator
import ar.driverai.bridge.data.VehicleRecordsRepository
import ar.driverai.bridge.engine.Fmt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

const val DISCLOSURE_ACCESSIBILITY = """DriverAI usa el servicio de accesibilidad de Android para LEER el texto visible de la app Uber Driver (solo ese paquete) mientras está abierta, con el único fin de detectar tarifa, distancias, tiempos y una calificación del pasajero si está explícitamente etiquetada, y mostrarte una evaluación económica.

• Qué procesa: el texto visible de Uber Driver, incluida la calificación del pasajero solo si está etiquetada explícitamente, dentro de tu teléfono.
• Qué guarda: solo tarifa, km, minutos y veredicto en el historial si lo activás. La calificación no se añade al historial; el Chequeo temporal puede mostrarla reducida. Nunca nombres, direcciones ni capturas.
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
fun HomeScreen(activity: Activity, onNavigate: (String) -> Unit = {}) {
    val g = DriverAiApp.graph
    val scope = rememberCoroutineScope()
    val settings by g.settings.collectAsState()
    val strategic by g.strategic.state.collectAsState()
    val status by g.pipeline.status.collectAsState()
    val weatherState by g.weather.state.collectAsState()
    val last by g.pipeline.last.collectAsState()
    val history by g.historyRepo.entries.collectAsState(initial = emptyList())
    val shiftStart by g.historyRepo.shiftStart.collectAsState(initial = null)
    val briefing by g.shiftBriefing.snapshot.collectAsState(initial = null)
    val moneyEntries by g.walletRepo.entries.collectAsState(initial = emptyList())
    val odometerEntries by g.vehicleRepo.odometerEntries.collectAsState(initial = emptyList())
    val capturing by ScreenCaptureService.running.collectAsState()
    var a11yOn by remember { mutableStateOf(false) }
    var overlayOk by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<String?>(null) }
    var briefingMessage by remember { mutableStateOf<String?>(null) }
    var zoneMenu by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val moneyToday = remember(moneyEntries, today) { MoneyLedgerRepository.summarize(moneyEntries, today) }
    val todayOdometer = remember(odometerEntries, today) { VehicleRecordsRepository.entryForDay(odometerEntries, today) }

    LifecycleResumeEffect(Unit) {
        a11yOn = DriverAccessibilityService.isEnabled(activity)
        overlayOk = Settings.canDrawOverlays(activity)
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(30_000) } }
    LaunchedEffect(Unit) { if (g.weather.state.value.enabled) g.weather.refresh() }

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
            val shiftEstimate = ShiftCalculator.compute(history, shiftStart, t)
            val ctx = CopilotContext(g.pipeline.last.value?.evaluation, shiftEstimate, g.strategic.state.value, settings, t)
            val a = CopilotEngine.answer(q, ctx)
            answer = a
            if (settings.voiceEnabled) g.tts.speak(a, settings.voiceVolume)
        }
    }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val text = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) { question = text; ask(text) }
    }

    suspend fun refreshBriefing() {
        when (val result = g.strategic.refreshReporte()) {
            is ApiResult.Ok -> {
                g.shiftBriefing.capture(result.value, settings.currentZone, System.currentTimeMillis())
                briefingMessage = "Respuesta recibida. Mirá abajo qué datos informa y cuáles faltan."
            }
            is ApiResult.Fail -> briefingMessage = "No se pudo actualizar el contexto: ${result.message}. Se conserva la consulta anterior, si existe."
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp)) {
        ScreenTitle("DriverAI · AMBA", "Tu jornada", today.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-AR"))))

        ShiftHeroCard(
            shiftStart = shiftStart,
            status = status.label,
            readingActive = a11yOn || capturing,
            loading = strategic.loading,
            onStart = {
                scope.launch {
                    g.historyRepo.startShift(System.currentTimeMillis())
                    briefingMessage = "Turno iniciado. Consultando el contexto disponible…"
                    launch { g.weather.refresh() }
                    refreshBriefing()
                }
            },
            onRefresh = { scope.launch { refreshBriefing(); g.weather.refresh() } },
            onEnd = { scope.launch { g.historyRepo.endShift() } },
        )

        WeatherCard(
            state = weatherState,
            onRefresh = { scope.launch { g.weather.refresh() } },
            onOpenSource = { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open-meteo.com/en/docs"))) },
            onOpenLicense = { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://creativecommons.org/licenses/by/4.0/"))) },
        )

        Text("CAJA REGISTRADA HOY", style = MaterialTheme.typography.labelSmall, color = AccentMint, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard("Ingresos", Fmt.ars(moneyToday.incomeArs), "Cargados por vos", Modifier.weight(1f), Green)
            MetricCard("Gastos", Fmt.ars(moneyToday.expensesArs), "Cargados por vos", Modifier.weight(1f), Amber)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            MetricCard("Diferencia de caja", Fmt.ars(moneyToday.cashBalanceArs), "No es ganancia neta", Modifier.weight(1f), if (moneyToday.cashBalanceArs < 0) Red else AccentMint)
            MetricCard("Km de hoy", todayOdometer?.let { "${Fmt.num(it.drivenKm, 1)} km" } ?: "—", if (todayOdometer == null) "Pendiente de cargar" else "Odómetro manual", Modifier.weight(1f), AccentBlue)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            OutlinedButton(onClick = { onNavigate("billetera") }, modifier = Modifier.weight(1f).testTag("home_wallet")) { Text("Abrir billetera") }
            OutlinedButton(onClick = { onNavigate("vehiculo") }, modifier = Modifier.weight(1f).testTag("home_vehicle")) { Text("Vehículo y service") }
        }

        ShiftBriefingCard(briefing, now, briefingMessage)
        Section("Contexto del día · cobertura de datos") {
            Text("No mostramos demanda inventada. El reporte heredado es una estimación del servidor, no un feed de actividad de las apps.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            ContextSignalRow(
                "Clima meteorológico",
                when {
                    weatherState.weather != null && weatherState.error != null -> "Open-Meteo · dato anterior; error al actualizar"
                    weatherState.weather != null -> "Open-Meteo · estimación modelada"
                    weatherState.enabled -> "Open-Meteo · sin lectura todavía"
                    else -> "Requiere fuente/plan autorizado"
                },
                if (weatherState.weather != null && weatherState.error == null) Green else Amber,
            )
            ContextSignalRow("Eventos deportivos y música", "Sin feed verificado", Amber)
            ContextSignalRow("Vuelos y cruceros", "Sin fuente integrada", Amber)
            ContextSignalRow("Patrones por día y hora", "Sin datos propios calibrados", Amber)
            OutlinedButton(onClick = { zoneMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Zona de trabajo: ${settings.currentZone ?: "Elegir zona"}") }
            DropdownMenu(expanded = zoneMenu, onDismissRequest = { zoneMenu = false }) {
                Zones.ALL.forEach { zone ->
                    DropdownMenuItem(text = { Text(zone) }, onClick = {
                        zoneMenu = false
                        scope.launch { g.settingsRepo.update { it.copy(currentZone = zone) }; g.strategic.refreshPosicion(zone) }
                    })
                }
            }
            strategic.posicion?.let { p ->
                val positionStale = strategic.posicionAt?.let { now - it > StrategicRepository.STALE_MS } != false || strategic.lastError != null
                val label = if (positionStale) "Última referencia (puede estar desactualizada)" else "Estimación del servidor"
                Text(if (p.decision == "moverte") "$label: ${p.zona_recomendada ?: "sin zona informada"} (${Fmt.num(p.score_recomendado ?: 0.0)} vs ${Fmt.num(p.score_actual ?: 0.0)})" else "$label: quedate en ${p.zona_actual ?: "tu zona"}", fontWeight = FontWeight.SemiBold)
                strategic.posicionAt?.let { Text("Consultado ${formatHomeTime(it)}", style = MaterialTheme.typography.bodySmall, color = TextMuted) }
            }
        }

        Section("Lectura de ofertas") {
            Text(if (a11yOn) "Accesibilidad activa" else "Accesibilidad no activa", color = if (a11yOn) Green else Amber)
            Text(if (capturing) "OCR por captura activo" else "OCR por captura disponible al pedirlo", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Text(if (overlayOk || a11yOn) "Tarjeta flotante disponible" else "Falta permiso de superposición; se usará notificación", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { dialog = if (a11yOn) "a11y_off" else "a11y" }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text(if (a11yOn) "Configurar accesibilidad" else "Activar lectura automática")
            }
            OutlinedButton(onClick = { if (capturing) ScreenCaptureService.stop(activity) else dialog = "capture" }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text(if (capturing) "Detener OCR por captura" else "Activar OCR por captura")
            }
            if (!overlayOk) OutlinedButton(onClick = { openOverlaySettings(activity) }, modifier = Modifier.fillMaxWidth()) { Text("Permitir tarjeta sobre otras apps") }
            last?.let { rec ->
                Spacer(Modifier.height(8.dp))
                Text("Última lectura: ${rec.evaluation.verdict.label}", color = verdictColor(rec.evaluation.verdict), style = MaterialTheme.typography.titleMedium)
                Text(rec.evaluation.headline, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }

        Section("Copiloto") {
            OutlinedTextField(question, { question = it }, label = { Text("Preguntá algo") }, modifier = Modifier.fillMaxWidth().testTag("copilot_input"), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { if (question.isNotBlank()) ask(question) }, modifier = Modifier.testTag("copilot_ask")) { Text("Preguntar") }
                OutlinedButton(onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "¿Qué querés saber?")
                    try { speechLauncher.launch(intent) } catch (e: ActivityNotFoundException) { answer = "Reconocimiento de voz no disponible. Escribí la pregunta." }
                }) { Text("Hablar") }
                TextButton(onClick = { g.tts.stop() }) { Text("Callar") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf("¿Me conviene?", "¿Por qué?", "¿Dónde espero?").forEach { q -> TextButton(onClick = { question = q; ask(q) }) { Text(q) } }
            }
            answer?.let { Text(it, modifier = Modifier.testTag("copilot_answer"), fontWeight = FontWeight.SemiBold) }
        }
    }

    when (dialog) {
        "a11y" -> AlertDialog(onDismissRequest = { dialog = null },
            title = { Text("Lectura automática de ofertas") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(DISCLOSURE_ACCESSIBILITY) } },
            confirmButton = { Button(onClick = { dialog = null; openAccessibilitySettings(activity) }) { Text("Acepto, ir a Accesibilidad") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("No, gracias") } })
        "a11y_off" -> AlertDialog(onDismissRequest = { dialog = null },
            title = { Text("Desactivar lectura") },
            text = { Text("En la pantalla que se abre, elegí DriverAI y desactivalo.") },
            confirmButton = { Button(onClick = { dialog = null; openAccessibilitySettings(activity) }) { Text("Abrir Accesibilidad") } })
        "capture" -> AlertDialog(onDismissRequest = { dialog = null },
            title = { Text("Lectura por captura") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(DISCLOSURE_CAPTURE) } },
            confirmButton = { Button(onClick = {
                dialog = null
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else projectionLauncher.launch(activity.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
            }) { Text("Acepto") } },
        dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancelar") } })
    }
}

@Composable
private fun ShiftHeroCard(
    shiftStart: Long?,
    status: String,
    readingActive: Boolean,
    loading: Boolean,
    onStart: () -> Unit,
    onRefresh: () -> Unit,
    onEnd: () -> Unit,
) {
    val active = shiftStart != null
    Box(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF123C3A), Color(0xFF17253C), Color(0xFF20243B))))
            .padding(20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("DRIVERAI  /  COCKPIT", style = MaterialTheme.typography.labelSmall, color = AccentMint, fontWeight = FontWeight.Bold)
                    Text(if (active) "Turno en marcha" else "Preparado para salir", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(if (active) "Iniciado ${formatHomeTime(shiftStart!!)}" else "Ingresos, gastos, kilómetros y contexto en un solo lugar", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                Text(if (readingActive) "LECTOR ACTIVO" else "LECTOR INACTIVO", style = MaterialTheme.typography.labelSmall, color = if (readingActive) Green else Amber, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("Lectura de ofertas: $status", modifier = Modifier.testTag("status"), style = MaterialTheme.typography.bodySmall, color = if (readingActive) Green else TextMuted)
            Spacer(Modifier.height(10.dp))
            if (!active) {
                Button(enabled = !loading, onClick = onStart, modifier = Modifier.fillMaxWidth().height(50.dp).testTag("start_shift")) {
                    Text(if (loading) "Consultando contexto…" else "Iniciar turno y consultar")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(enabled = !loading, onClick = onRefresh, modifier = Modifier.weight(1f)) { Text("Actualizar contexto") }
                    TextButton(onClick = onEnd, modifier = Modifier.testTag("end_shift")) { Text("Terminar") }
                }
            }
        }
    }
}

@Composable
private fun ContextSignalRow(label: String, state: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.9f))
        Text(
            state,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.1f).padding(start = 8.dp),
        )
    }
}

@Composable
private fun WeatherCard(
    state: LocalWeatherRepository.State,
    onRefresh: () -> Unit,
    onOpenSource: () -> Unit,
    onOpenLicense: () -> Unit,
) {
    Section("Modelo meteorológico · CABA Centro (punto fijo)") {
        val weather = state.weather
        if (weather != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("TEMPERATURA", style = MaterialTheme.typography.labelSmall, color = AccentMint)
                    Text(weather.temperatureC?.let { "${Fmt.num(it)} °C" } ?: "— °C", style = MaterialTheme.typography.headlineMedium, color = AccentMint)
                    Text("Viento ${weather.windKmh?.let { "${Fmt.num(it)} km/h" } ?: "sin dato"}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                Column(Modifier.weight(1f)) {
                    Text("PRECIPITACIÓN MODELADA", style = MaterialTheme.typography.labelSmall, color = AccentBlue)
                    Text(weather.precipitationMm?.let { "${Fmt.num(it, 1)} mm" } ?: "Sin dato", style = MaterialTheme.typography.titleMedium)
                    Text(weather.precipitationIntervalSeconds?.let { seconds ->
                        val minutes = seconds / 60
                        "Acumulado reportado · intervalo ${if (seconds % 60 == 0) "$minutes min" else "$seconds s"}"
                    } ?: "Acumulado/intervalo: sin dato", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    Text(weather.precipProbNextHours?.let { "Máx. en hasta 3 datos horarios: $it %" } ?: "Probabilidad: sin dato", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
        Text("Estimación modelada por Open-Meteo · última consulta ${formatHomeTime(weather.fetchedAtMs)} · sin GPS · no representa necesariamente tu zona de trabajo", style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(top = 8.dp))
            if (state.error != null) Text("Actualización fallida; se conserva el dato anterior. ${state.error}", style = MaterialTheme.typography.bodySmall, color = Amber)
        } else {
            Text(
                when {
                    state.loading -> "Consultando Open-Meteo…"
                    !state.enabled -> "Clima externo desactivado en esta compilación. Una distribución comercial requiere una fuente/plan autorizado."
                    else -> state.error ?: "Todavía no hay una lectura meteorológica disponible."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (state.enabled) TextMuted else Amber,
            )
        }
        Text("Weather data by Open-Meteo.com · CC BY 4.0. La probabilidad muestra el máximo de hasta 3 datos horarios. Es referencia modelada, no observación de estación, demanda ni alerta oficial del SMN.", style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(enabled = state.enabled && !state.loading, onClick = onRefresh, modifier = Modifier.weight(1f)) { Text("Actualizar") }
            TextButton(onClick = onOpenSource) { Text("Fuente") }
            TextButton(onClick = onOpenLicense) { Text("Licencia") }
        }
    }
}

private fun formatHomeTime(epochMillis: Long): String = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("es-AR"))
    .format(java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault()))

@Composable
fun MetricsSummary(m: ar.driverai.bridge.engine.Metrics) {
    fun d(v: Double?, f: (Double) -> String) = v?.let(f) ?: "—"
    Text("Tarifa: ${d(m.fare, Fmt::ars)}")
    Text("Km recogida: ${d(m.pickupKm) { Fmt.num(it) }} · Km con pasajero: ${d(m.tripKm) { Fmt.num(it) }} · Retorno vacío: ${d(m.returnKm) { Fmt.num(it) }}")
    Text("Km totales: ${d(m.totalKm) { Fmt.num(it) }} · Min con pasajero: ${d(m.tripMin) { Fmt.num(it, 0) }} · Min totales: ${d(m.totalMin) { Fmt.num(it, 0) }}")
    Text("Bruto/km: ${d(m.grossPerKm, Fmt::ars)} · Bruto/h: ${d(m.grossPerHour, Fmt::ars)}", fontWeight = FontWeight.Bold, modifier = Modifier.testTag("metrics_gross"))
    if (m.variableCost != null) Text("Costo variable est.: ${Fmt.ars(m.variableCost)} · Ganancia est. antes de fijos e impuestos: ${d(m.estimatedProfitBeforeFixed, Fmt::ars)} · Margen: ${d(m.marginPct) { Fmt.num(it) + " %" }}")
}
