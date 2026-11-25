package ar.driverai.bridge.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import ar.driverai.bridge.DriverAiApp
import ar.driverai.bridge.R
import ar.driverai.bridge.capture.DriverAccessibilityService
import ar.driverai.bridge.capture.EvalRecord
import ar.driverai.bridge.capture.ScreenCaptureService
import ar.driverai.bridge.data.MoneyLedgerRepository
import ar.driverai.bridge.data.ShiftCalculator
import ar.driverai.bridge.engine.Fmt
import ar.driverai.bridge.intel.AiReport
import ar.driverai.bridge.intel.SignalOrigin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

const val DISCLOSURE_ACCESSIBILITY = """DriverAI usa el servicio de accesibilidad de Android para LEER el texto visible de las apps de conductor que habilites (Uber Driver, DiDi Conductor, Cabify Driver, inDrive y Bolt Driver; solo esos paquetes) mientras están abiertas, con el único fin de detectar tarifa, distancias, tiempos y una calificación del pasajero si está explícitamente etiquetada, y mostrarte una evaluación económica.

• Qué procesa: el texto visible de esas apps, incluida la calificación del pasajero solo si está etiquetada explícitamente, dentro de tu teléfono.
• Qué guarda: solo tarifa, km, minutos y veredicto en el historial si lo activás. La calificación no se añade al historial; el Chequeo temporal puede mostrarla reducida. Del destino se guarda solo la zona aproximada (ej. "Palermo"), nunca la dirección. Nunca nombres ni capturas.
• Qué transmite: nada del contenido de pantalla. Al servidor solo se envía la zona que elegís para el reporte estratégico.
• Qué NO hace: no toca la pantalla, no acepta ni rechaza viajes, no lee otras apps fuera de esa lista.

DriverAI no es una herramienta de accesibilidad para personas con discapacidad. Podés desactivarlo cuando quieras en Ajustes > Accesibilidad."""

const val DISCLOSURE_CAPTURE = """La lectura por captura usa la grabación de pantalla de Android SOLO cuando tocás el botón "Leer oferta": se toma una imagen, se reconoce el texto en el teléfono (OCR local) y la imagen se descarta en el acto.

• No se graba video ni se guardan imágenes.
• No se envía ninguna imagen a servidores.
• Android te pedirá confirmación y mostrará un aviso mientras esté activa. Se apaga sola a los 10 minutos sin uso o desde la notificación."""

fun openAccessibilitySettings(ctx: Context) = ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
fun openOverlaySettings(ctx: Context) = ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

/**
 * Inicio = tres preguntas del conductor, en este orden:
 * 1. ¿Dónde conviene estar ahora? (radar)
 * 2. ¿Está leyendo mis ofertas? / ¿qué tal la última? (lector + veredicto)
 * 3. ¿Cómo voy hoy? (ingresos anotados vs meta)
 * Todo lo demás vive en Radar, Billetera o Más.
 */
@Composable
fun HomeScreen(activity: Activity, onNavigate: (String) -> Unit = {}) {
    val g = DriverAiApp.graph
    val scope = rememberCoroutineScope()
    val settings by g.settings.collectAsState()
    val status by g.pipeline.status.collectAsState()
    val last by g.pipeline.last.collectAsState()
    val shiftStart by g.historyRepo.shiftStart.collectAsState(initial = null)
    val history by g.historyRepo.entries.collectAsState(initial = emptyList())
    val moneyEntries by g.walletRepo.entries.collectAsState(initial = emptyList())
    val capturing by ScreenCaptureService.running.collectAsState()
    val cache by g.radar.cache.collectAsState()
    var a11yOn by remember { mutableStateOf(false) }
    var overlayOk by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var dialog by remember { mutableStateOf<String?>(null) }

    val today = LocalDate.now()
    val moneyToday = remember(moneyEntries, today) { MoneyLedgerRepository.summarize(moneyEntries, today) }
    val reading = a11yOn || capturing

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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Column(Modifier.weight(1f)) {
                Text("DriverAI", style = MaterialTheme.typography.titleLarge, color = AccentMint, fontWeight = FontWeight.Bold)
                Text(today.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-AR"))), style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            StatusPill(if (reading) "LECTOR ACTIVO" else "LECTOR APAGADO", if (reading) Green else Amber)
        }

        val stepsDone = listOf(settings.hasEconomicThreshold, reading, cache.aiImportedAt != null)
        if (!settings.onboardingDismissed && stepsDone.any { !it }) {
            FirstStepsCard(
                done = stepsDone,
                onThresholds = { dialog = "quick" },
                onReading = { dialog = "a11y" },
                onAi = { onNavigate("ia") },
                onHide = { scope.launch { g.settingsRepo.update { it.copy(onboardingDismissed = true) } } },
            )
        }

        NowCard(
            aiImportedAt = cache.aiImportedAt,
            now = now,
            onRadar = { onNavigate("radar") },
            onAi = { onNavigate("ia") },
        )

        ShiftCard(
            shiftStart = shiftStart,
            now = now,
            status = status.label,
            reading = reading,
            stats = remember(history, shiftStart, now) { ShiftCalculator.compute(history, shiftStart, now) },
            onStart = {
                scope.launch {
                    g.historyRepo.startShift(System.currentTimeMillis())
                    g.radar.refreshIfStale(maxAgeMs = 30 * 60_000L)
                    launch { g.weather.refresh() }
                }
            },
            onEnd = { scope.launch { g.historyRepo.endShift() } },
            onActivate = { dialog = "a11y" },
            onOptions = { dialog = "reading" },
        )

        LatestOfferCard(last, onEvaluate = { onNavigate("evaluar") })

        TodayCard(
            incomeArs = moneyToday.incomeArs,
            expensesArs = moneyToday.expensesArs,
            dailyTargetArs = settings.dailyTargetArs,
            onWallet = { onNavigate("billetera") },
        )

        OutlinedButton(onClick = { onNavigate("copiloto") }, modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 4.dp).testTag("home_open_copilot")) {
            Text("Preguntale al copiloto")
        }
        Spacer(Modifier.height(12.dp))
    }

    when (dialog) {
        "quick" -> QuickSetupDialog(onDismiss = { dialog = null })
        "reading" -> AlertDialog(onDismissRequest = { dialog = null },
            title = { Text("Opciones de lectura") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (a11yOn) "Lectura automática activa." else "Lectura automática apagada.", color = if (a11yOn) Green else Amber)
                    Text("Si tu app no se deja leer, usá la captura: tocás «Leer oferta» y se reconoce el texto en el teléfono.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    OutlinedButton(onClick = { dialog = if (a11yOn) "a11y_off" else "a11y" }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (a11yOn) "Configurar accesibilidad" else "Activar lectura automática")
                    }
                    OutlinedButton(onClick = { if (capturing) { ScreenCaptureService.stop(activity); dialog = null } else dialog = "capture" }, modifier = Modifier.fillMaxWidth().testTag("home_capture")) {
                        Text(if (capturing) "Detener lectura por captura" else "Leer por captura (OCR)")
                    }
                    if (!overlayOk && !a11yOn) OutlinedButton(onClick = { openOverlaySettings(activity) }, modifier = Modifier.fillMaxWidth()) { Text("Permitir tarjeta sobre otras apps") }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Cerrar") } })
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
private fun FirstStepsCard(done: List<Boolean>, onThresholds: () -> Unit, onReading: () -> Unit, onAi: () -> Unit, onHide: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 7.dp)
            .clip(RoundedCornerShape(DriverRadius.hero))
            .background(Brush.linearGradient(listOf(Color(0xFF12291C), Color(0xFF0D1A13))))
            .border(1.dp, AccentMint.copy(alpha = 0.25f), RoundedCornerShape(DriverRadius.hero))
            .testTag("home_first_steps"),
    ) {
        Image(
            painter = painterResource(R.drawable.driverai_hero_car),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alpha = 0.35f,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp).width(130.dp).height(70.dp),
        )
        Column(Modifier.padding(18.dp)) {
            Text("PRIMEROS PASOS", style = MaterialTheme.typography.labelSmall, color = AccentMint, fontWeight = FontWeight.Bold)
            Text("Listo en 3 minutos", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            StepRow(1, "Cargá tus mínimos", "Con tu costo de combustible calculamos cuánto pedir por km.", done[0], onThresholds, "step_thresholds")
            StepRow(2, "Activá la lectura", "DriverAI lee la oferta y te muestra el veredicto. No toca nada.", done[1], onReading, "step_reading")
            StepRow(3, "Actualizá el radar con IA", "Tu IA busca partidos, shows y paros de hoy.", done[2], onAi, "step_ai")
            TextButton(onClick = onHide, modifier = Modifier.align(Alignment.End).testTag("first_steps_hide")) { Text("Ocultar") }
        }
    }
}

@Composable
private fun StepRow(n: Int, title: String, caption: String, done: Boolean, onClick: () -> Unit, tag: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = !done, onClick = onClick).padding(vertical = 7.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(if (done) Green.copy(alpha = 0.22f) else AccentMint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Text(if (done) "✓" else "$n", color = if (done) Green else AccentMint, fontWeight = FontWeight.Bold) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (done) TextMuted else MaterialTheme.colorScheme.onSurface)
            if (!done) Text(caption, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        if (!done) Text("›", style = MaterialTheme.typography.titleLarge, color = AccentMint)
    }
}

/** Tarjeta principal: la mejor zona ahora y el próximo pico, con el acceso a datos en vivo. */
@Composable
private fun NowCard(aiImportedAt: Long?, now: Long, onRadar: () -> Unit, onAi: () -> Unit) {
    val forecast by DriverAiApp.graph.radar.forecast.collectAsState()
    val top = forecast?.now()?.firstOrNull()
    val peak = forecast?.upcomingPeaks()?.firstOrNull { it.slotStart > now }
    val aiFresh = aiImportedAt != null && now - aiImportedAt < AiReport.STALE_AFTER_MS
    Section("Dónde conviene estar ahora", Modifier.fillMaxWidth().testTag("home_radar")) {
        if (top == null) {
            SectionCaption("Calculando zonas…")
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(top.zoneName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).testTag("home_top_zone"))
                StatusPill(top.level.label.uppercase(), levelColor(top.level))
            }
            val reason = top.topReason
            Text(
                (reason?.label ?: "Patrón general") + if (reason?.origin == SignalOrigin.IA_EXTERNA) " · informe IA" else "",
                style = MaterialTheme.typography.bodyMedium, color = TextMuted, modifier = Modifier.padding(top = 2.dp),
            )
            peak?.let {
                Text(
                    "Próximo pico ${formatHomeTime(it.slotStart)} · ${it.zoneName}" + (it.topReason?.label?.let { r -> " · $r" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = AccentMint, modifier = Modifier.padding(top = 6.dp).testTag("home_next_peak"),
                )
            }
        }
        Text(
            when {
                aiFresh -> "Datos en vivo: informe IA de las ${formatHomeTime(aiImportedAt!!)}."
                aiImportedAt != null -> "El informe IA tiene más de 6 h. Actualizalo para hoy."
                else -> "Sin datos en vivo de hoy: el radar usa patrones y fuentes públicas."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (aiFresh) TextMuted else Amber,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Button(onClick = onAi, modifier = Modifier.weight(1f).height(48.dp).testTag("home_ai")) { Text(if (aiFresh) "Actualizar IA" else "Actualizar con IA") }
            OutlinedButton(onClick = onRadar, modifier = Modifier.weight(1f).height(48.dp).testTag("home_open_radar")) { Text("Ver radar") }
        }
    }
}

@Composable
private fun ShiftCard(
    shiftStart: Long?,
    now: Long,
    status: String,
    reading: Boolean,
    stats: ar.driverai.bridge.data.ShiftStats,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onActivate: () -> Unit,
    onOptions: () -> Unit,
) {
    val active = shiftStart != null
    Section(if (active) "Turno en marcha" else "Turno") {
        if (active) {
            Text("Desde ${formatHomeTime(shiftStart!!)} · ${stats.trips} viajes tomados" + (stats.grossPerHour?.let { " · ${Fmt.ars(it)}/h bruto" } ?: ""), style = MaterialTheme.typography.bodyMedium)
            val previousDay = java.time.Instant.ofEpochMilli(shiftStart).atZone(java.time.ZoneId.systemDefault()).toLocalDate() <
                java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            if (previousDay) SectionCaption("Empezó un día anterior: las horas no descuentan pausas.")
        }
        Text(status, modifier = Modifier.padding(top = 4.dp).testTag("status"), style = MaterialTheme.typography.bodySmall, color = if (reading) Green else TextMuted)
        if (!reading) {
            Button(onClick = onActivate, modifier = Modifier.fillMaxWidth().height(50.dp).padding(top = 6.dp).testTag("home_activate_reading")) { Text("Activar lectura de ofertas") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            if (!active) {
                OutlinedButton(onClick = onStart, modifier = Modifier.weight(1f).height(48.dp).testTag("start_shift")) { Text("Iniciar turno") }
            } else {
                OutlinedButton(onClick = onEnd, modifier = Modifier.weight(1f).height(48.dp).testTag("end_shift")) { Text("Terminar turno") }
            }
            TextButton(onClick = onOptions, modifier = Modifier.testTag("home_reading_options")) { Text("Lectura") }
        }
    }
}

@Composable
private fun LatestOfferCard(record: EvalRecord?, onEvaluate: () -> Unit) {
    Section("Última oferta") {
        if (record == null) {
            SectionCaption("Cuando llegue una oferta vas a ver acá si conviene. También podés cargarla a mano.")
        } else {
            val evaluation = record.evaluation
            val m = evaluation.metrics
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    StatusPill(evaluation.verdict.label, verdictColor(evaluation.verdict))
                    Text(m.fare?.let(Fmt::ars) ?: "Falta tarifa", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 6.dp))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(m.grossPerHour?.let { "${Fmt.ars(it)}/h" } ?: "— /h", style = MaterialTheme.typography.titleMedium, color = AccentMint)
                    Text(m.grossPerKm?.let { "${Fmt.ars(it)}/km" } ?: "— /km", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
            if (evaluation.headline.isNotBlank()) SectionCaption(evaluation.headline)
        }
        OutlinedButton(onClick = onEvaluate, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("home_open_evaluate")) {
            Text(if (record == null) "Evaluar una oferta a mano" else "Revisar o corregir")
        }
    }
}

@Composable
private fun TodayCard(incomeArs: Double, expensesArs: Double, dailyTargetArs: Double?, onWallet: () -> Unit) {
    Section("Hoy") {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("INGRESOS ANOTADOS", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Text(Fmt.ars(incomeArs), style = MaterialTheme.typography.headlineSmall, color = Green, modifier = Modifier.testTag("home_daily_income"))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("GASTOS", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Text(Fmt.ars(expensesArs), style = MaterialTheme.typography.titleMedium, color = Amber)
            }
        }
        dailyTargetArs?.let { target ->
            val progress = (incomeArs / target).coerceIn(0.0, 1.0).toFloat()
            Text("Meta ${Fmt.ars(target)} · ${(progress * 100).toInt()} %", style = MaterialTheme.typography.labelLarge, color = AccentMint, modifier = Modifier.padding(top = 6.dp))
            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth().padding(top = 2.dp), color = AccentMint)
        }
        OutlinedButton(onClick = onWallet, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("home_wallet")) { Text("Anotar en billetera") }
    }
}

@Composable
fun MetricsSummary(m: ar.driverai.bridge.engine.Metrics) {
    fun d(v: Double?, f: (Double) -> String) = v?.let(f) ?: "—"
    Text("Tarifa: ${d(m.fare, Fmt::ars)}")
    Text("Km recogida: ${d(m.pickupKm) { Fmt.num(it) }} · Km con pasajero: ${d(m.tripKm) { Fmt.num(it) }} · Retorno vacío: ${d(m.returnKm) { Fmt.num(it) }}")
    Text("Km totales: ${d(m.totalKm) { Fmt.num(it) }} · Min con pasajero: ${d(m.tripMin) { Fmt.num(it, 0) }} · Min totales: ${d(m.totalMin) { Fmt.num(it, 0) }}")
    Text("Bruto/km: ${d(m.grossPerKm, Fmt::ars)} · Bruto/h: ${d(m.grossPerHour, Fmt::ars)}", fontWeight = FontWeight.Bold, modifier = Modifier.testTag("metrics_gross"))
    if (m.variableCost != null) Text("Costo variable est.: ${Fmt.ars(m.variableCost)} · Ganancia est. antes de fijos e impuestos: ${d(m.estimatedProfitBeforeFixed, Fmt::ars)} · Margen: ${d(m.marginPct) { Fmt.num(it) + " %" }}")
}


