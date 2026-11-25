package ar.driverai.bridge.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ar.driverai.bridge.DriverAiApp
import ar.driverai.bridge.intel.Cities
import ar.driverai.bridge.intel.DemandLevel
import ar.driverai.bridge.intel.DemandRadar
import ar.driverai.bridge.intel.DemandSignal
import ar.driverai.bridge.intel.IntelRepository
import ar.driverai.bridge.intel.SignalKind
import ar.driverai.bridge.intel.SignalOrigin
import ar.driverai.bridge.intel.Venue
import ar.driverai.bridge.intel.VenueKind
import ar.driverai.bridge.intel.ZoneWindow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val hhmm = SimpleDateFormat("HH:mm", Locale("es", "AR"))
private val dayHhmm = SimpleDateFormat("EEE d/M HH:mm", Locale("es", "AR"))

internal fun levelColor(l: DemandLevel): Color = when (l) {
    DemandLevel.MUY_ALTA -> DriverPalette.mint
    DemandLevel.ALTA -> DriverPalette.success.copy(alpha = 0.85f)
    DemandLevel.MEDIA -> DriverPalette.warning
    DemandLevel.BAJA -> DriverPalette.textMuted
}

internal fun confidenceLabel(c: Double): String = when {
    c >= 0.65 -> "confianza alta"
    c >= 0.45 -> "confianza media"
    else -> "confianza baja"
}

@Composable
fun RadarScreen(onOpenAi: () -> Unit = {}) {
    val g = DriverAiApp.graph
    val forecast by g.radar.forecast.collectAsState()
    val cache by g.radar.cache.collectAsState()
    val refreshing by g.radar.refreshing.collectAsState()
    val settings by g.settings.collectAsState()
    val city = Cities.byId(settings.cityId)
    var showAdd by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { g.radar.refreshIfStale() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        ScreenTitle("RADAR", "Dónde y cuándo conviene estar", city.name)

        val f = forecast
        if (f == null) {
            Section("Calculando", Modifier.testTag("radar_loading")) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        } else {
            val now = f.now()
            val top = now.firstOrNull()
            Section("Ahora", Modifier.fillMaxWidth().testTag("radar_now")) {
                if (top == null) {
                    SectionCaption("Sin zonas en el catálogo.")
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(top.zoneName, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        StatusPill("${top.level.label} · ${top.index}", levelColor(top.level))
                    }
                    top.topReason?.let { SectionCaption(it.label) }
                    SectionCaption("${confidenceLabel(top.confidence)} · franja ${hhmm.format(Date(top.slotStart))}–${hhmm.format(Date(top.slotEnd))}")
                }
                f.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = DriverPalette.warning) }
                val aiAt = cache.aiImportedAt
                SectionCaption(
                    if (aiAt != null) "Informe IA de las ${hhmm.format(Date(aiAt))} · ${cache.aiSignals.size} señales" else "Sin informe IA de hoy",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Button(onClick = onOpenAi, modifier = Modifier.weight(1f).testTag("radar_ai")) { Text("Actualizar con IA") }
                    OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.weight(1f).testTag("radar_add_signal")) { Text("Cargar señal") }
                }
            }

            Section("Top zonas en esta franja") {
                now.take(3).forEachIndexed { i, w -> ZoneRow(i + 1, w) }
            }

            Section("Próximas 6 horas") {
                SectionCaption("Mejor zona prevista por franja de ${f.slotMinutes} min")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    f.bestPerSlot().forEach { w ->
                        Column(Modifier.width(92.dp)) {
                            Text(hhmm.format(Date(w.slotStart)), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Text(w.zoneName, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            Text("${w.index}", style = MaterialTheme.typography.titleMedium, color = levelColor(w.level), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            val peaks = f.upcomingPeaks()
            Section("Picos por eventos y señales") {
                if (peaks.isEmpty()) SectionCaption("No hay picos concretos detectados en las próximas horas.")
                peaks.take(6).forEach { w ->
                    Text("${hhmm.format(Date(w.slotStart))} · ${w.zoneName} · ${w.level.label} (${w.index})", style = MaterialTheme.typography.bodyMedium)
                    w.topReason?.let { SectionCaption(it.label) }
                }
            }
        }

        TextButton(onClick = { details = !details }, modifier = Modifier.fillMaxWidth().testTag("radar_details")) {
            Text(if (details) "Ocultar detalles" else "Ver señales, fuentes y clima")
        }
        if (details) {
            val signals = IntelRepository.allSignals(cache).sortedBy { it.startAt }
            Section("Señales en uso") {
                if (signals.isEmpty()) SectionCaption("Todavía no hay señales. Actualizá con IA, actualizá fuentes o cargá un paro, partido, vuelo o crucero.")
                signals.take(25).forEach { s ->
                    SignalRow(s, onDelete = when (s.origin) {
                        SignalOrigin.CONDUCTOR -> ({ g.radar.removeManual(s.id) })
                        SignalOrigin.IA_EXTERNA -> ({ g.radar.removeAi(s.id) })
                        else -> null
                    })
                }
                OutlinedButton(onClick = { g.radar.refresh() }, enabled = !refreshing, modifier = Modifier.testTag("radar_refresh")) {
                    Text(if (refreshing) "Actualizando…" else "Actualizar fuentes públicas")
                }
            }

            Section("Estado de fuentes") {
                if (cache.sources.isEmpty()) SectionCaption("Sin consultas todavía. Funciona igual con patrón general, feriados fijos y tu historial.")
                cache.sources.forEach { st ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(if (st.ok) "OK" else "Sin datos", if (st.ok) DriverPalette.success else DriverPalette.warning)
                        Column(Modifier.weight(1f)) {
                            Text(st.name, style = MaterialTheme.typography.bodyMedium)
                            SectionCaption("${st.message} · ${hhmm.format(Date(st.checkedAt))}")
                        }
                    }
                }
            }

            RadarWeatherCard()
            ServerContextCard(System.currentTimeMillis())

            Section("Cómo leer el radar") {
                SectionCaption(
                    "El índice 0–100 se calcula en tu teléfono y combina el patrón típico de cada zona por hora y tipo de día, señales concretas (partidos, shows, vuelos, cruceros, paros, lluvia, informe IA) " +
                        "con su horario de salida estimado y tu propio historial de ofertas. No es demanda medida ni promesa de ganancia: es una guía explicable. Vos decidís.",
                )
            }
        }
    }

    if (showAdd) AddSignalDialog(cityVenues = city.venues, onDismiss = { showAdd = false }) { sig ->
        g.radar.addManual(sig)
        showAdd = false
    }
}

@Composable
private fun ZoneRow(rank: Int, w: ZoneWindow) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("$rank", style = MaterialTheme.typography.titleMedium, color = TextMuted, modifier = Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(w.zoneName, style = MaterialTheme.typography.bodyLarge)
            SectionCaption((w.topReason?.label ?: "Patrón general") + " · " + confidenceLabel(w.confidence))
        }
        Text("${w.index}", style = MaterialTheme.typography.titleMedium, color = levelColor(w.level), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SignalRow(s: DemandSignal, onDelete: (() -> Unit)?) {
    val (from, to) = DemandRadar.impactWindow(s)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${s.kind.label}: ${s.title}", style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            SectionCaption("${dayHhmm.format(Date(s.startAt))} · impacto ${hhmm.format(Date(from))}–${hhmm.format(Date(to))} · ${s.sourceName} · ${confidenceLabel(s.confidence)}")
        }
        onDelete?.let { TextButton(onClick = it) { Text("Quitar") } }
    }
}

private enum class Template(val label: String, val kind: SignalKind, val citywide: Boolean, val venueKinds: Set<VenueKind>) {
    PARO_COLECTIVOS("Paro de colectivos", SignalKind.PARO_TRANSPORTE, true, emptySet()),
    PARO_SUBTE("Paro de subte", SignalKind.PARO_TRANSPORTE, true, emptySet()),
    PARO_TRENES("Paro de trenes", SignalKind.PARO_TRANSPORTE, true, emptySet()),
    PARTIDO("Partido", SignalKind.EVENTO_DEPORTIVO, false, setOf(VenueKind.ESTADIO_FUTBOL)),
    RECITAL("Recital / show", SignalKind.EVENTO_SHOW, false, setOf(VenueKind.ESTADIO_FUTBOL, VenueKind.ARENA, VenueKind.TEATRO, VenueKind.PREDIO)),
    VUELOS("Pico de vuelos", SignalKind.VUELOS, false, setOf(VenueKind.AEROPUERTO)),
    CRUCERO("Llega un crucero", SignalKind.CRUCERO, false, setOf(VenueKind.PUERTO)),
    LLUVIA("Lluvia fuerte", SignalKind.CLIMA, true, emptySet()),
    OTRO("Otra", SignalKind.OTRO, true, emptySet()),
}

@Composable
private fun AddSignalDialog(cityVenues: List<Venue>, onDismiss: () -> Unit, onAdd: (DemandSignal) -> Unit) {
    var template by remember { mutableStateOf(Template.PARO_COLECTIVOS) }
    var venue by remember { mutableStateOf<Venue?>(null) }
    var startOffsetH by remember { mutableStateOf(0) }
    var durationH by remember { mutableStateOf(3) }
    var note by remember { mutableStateOf("") }
    val venues = cityVenues.filter { it.kind in template.venueKinds }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cargar señal") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionCaption("Se guarda solo en tu teléfono y se usa para el radar.")
                ChipRow(Template.entries.toList(), { it.label }, template) { template = it; venue = null }
                if (!template.citywide) {
                    Text("Lugar", style = MaterialTheme.typography.labelLarge)
                    ChipRow(venues, { it.name }, venue) { venue = it }
                }
                Text("Empieza", style = MaterialTheme.typography.labelLarge)
                ChipRow(listOf(0, 1, 2, 3, 4, 6), { if (it == 0) "Ahora" else "en $it h" }, startOffsetH) { startOffsetH = it }
                Text("Dura", style = MaterialTheme.typography.labelLarge)
                ChipRow(listOf(2, 3, 6, 12, 24), { "$it h" }, durationH) { durationH = it }
                OutlinedTextField(value = note, onValueChange = { note = it.take(60) }, label = { Text("Detalle (opcional, sin datos personales)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                enabled = template.citywide || venue != null,
                onClick = {
                    val now = System.currentTimeMillis()
                    val start = now + startOffsetH * 3_600_000L
                    val title = listOfNotNull(template.label, venue?.name, note.takeIf { it.isNotBlank() }).joinToString(" · ")
                    val intensity = when (template) {
                        Template.PARO_COLECTIVOS, Template.PARO_TRENES -> 0.9
                        Template.PARO_SUBTE -> 0.6
                        Template.LLUVIA -> 0.7
                        Template.PARTIDO, Template.RECITAL -> DemandRadar.intensityForCapacity(venue?.capacity)
                        else -> 0.5
                    }
                    // Para partidos y shows el hecho dura lo típico; para el resto se usa la duración elegida.
                    val end = if (template.kind == SignalKind.EVENTO_DEPORTIVO || template.kind == SignalKind.EVENTO_SHOW) null else start + durationH * 3_600_000L
                    onAdd(IntelRepository.manualSignal(template.kind, title, venue?.zoneId, start, end, intensity, now, venue))
                },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun <T> ChipRow(items: List<T>, label: (T) -> String, selected: T?, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item -> FilterChip(selected = item == selected, onClick = { onSelect(item) }, label = { Text(label(item), maxLines = 1) }) }
    }
}
