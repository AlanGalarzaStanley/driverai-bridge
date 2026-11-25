package ar.driverai.bridge.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ar.driverai.bridge.DriverAiApp
import ar.driverai.bridge.backend.ApiResult
import ar.driverai.bridge.backend.LocalWeatherRepository
import ar.driverai.bridge.backend.StrategicRepository
import ar.driverai.bridge.copilot.Zones
import ar.driverai.bridge.engine.Fmt
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Clima modelado con controles, para los detalles del radar. */
@Composable
fun RadarWeatherCard() {
    val g = DriverAiApp.graph
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by g.weather.state.collectAsState()
    WeatherCard(
        state = state,
        onRefresh = { scope.launch { g.weather.refresh() } },
        onOpenSource = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open-meteo.com/en/docs")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
        onOpenLicense = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://creativecommons.org/licenses/by/4.0/")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
    )
}

/**
 * Reporte del servidor heredado (estimación, no feed de las apps). Se consulta solo cuando el
 * conductor lo pide; vive en los detalles del radar para no cargar la pantalla principal.
 */
@Composable
fun ServerContextCard(now: Long) {
    val g = DriverAiApp.graph
    val scope = rememberCoroutineScope()
    val settings by g.settings.collectAsState()
    val strategic by g.strategic.state.collectAsState()
    val briefing by g.shiftBriefing.snapshot.collectAsState(initial = null)
    var message by remember { mutableStateOf<String?>(null) }
    var zoneMenu by remember { mutableStateOf(false) }
    ShiftBriefingCard(briefing, now, message)
    Section("Reporte del servidor · estimación") {
        SectionCaption("Estimación del servicio DriverAI, no un feed de actividad de las apps. Solo se envía la zona que elijas.")
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
        }
        OutlinedButton(
            enabled = !strategic.loading,
            onClick = {
                scope.launch {
                    message = when (val r = g.strategic.refreshReporte()) {
                        is ApiResult.Ok -> { g.shiftBriefing.capture(r.value, settings.currentZone, System.currentTimeMillis()); "Respuesta recibida." }
                        is ApiResult.Fail -> "No se pudo consultar: ${r.message}. Se conserva la consulta anterior."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        ) { Text(if (strategic.loading) "Consultando…" else "Consultar reporte del servidor") }
    }
}

@Composable
internal fun WeatherCard(
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

internal fun formatHomeTime(epochMillis: Long): String = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("es-AR"))
    .format(java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault()))

