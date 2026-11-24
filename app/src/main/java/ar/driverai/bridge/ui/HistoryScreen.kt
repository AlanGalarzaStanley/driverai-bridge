package ar.driverai.bridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ar.driverai.bridge.DriverAiApp
import ar.driverai.bridge.engine.Fmt
import ar.driverai.bridge.engine.Verdict
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen() {
    val g = DriverAiApp.graph
    val scope = rememberCoroutineScope()
    val items by g.historyRepo.entries.collectAsState(initial = emptyList())
    val df = SimpleDateFormat("dd/MM HH:mm", Locale("es", "AR"))

    Column(Modifier.fillMaxSize()) {
        ScreenScaffold(
            title = "Historial",
            subtitle = "Solo números y veredicto · máx. 100",
        ) {
            GhostCta("Borrar historial") { scope.launch { g.historyRepo.clear() } }
            VSpace(12.dp)
            if (items.isEmpty()) {
                Section("Sin datos", accent = DriverColors.TextTertiary) {
                    Text("Todavía no hay evaluaciones guardadas.", color = DriverColors.TextSecondary)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(items, key = { it.at }) { e ->
                        val v = runCatching { Verdict.valueOf(e.verdict) }.getOrDefault(Verdict.REVISAR)
                        Section(
                            "${df.format(Date(e.at))}${if (e.taken) " · TOMADO" else ""}",
                            accent = verdictColor(v),
                        ) {
                            Text(
                                v.label,
                                color = verdictColor(v),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            VSpace(8.dp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MetricTile("Tarifa", e.fare?.let { Fmt.ars(it) } ?: "—", Modifier.weight(1f))
                                MetricTile("Km", e.totalKm?.let { Fmt.num(it) } ?: "—", Modifier.weight(1f))
                                MetricTile("Min", e.totalMin?.let { Fmt.num(it, 0) } ?: "—", Modifier.weight(1f))
                            }
                            VSpace(10.dp)
                            Text(
                                "${e.grossPerKm?.let { Fmt.ars(it) + "/km" } ?: "—"} · ${e.grossPerHour?.let { Fmt.ars(it) + "/h" } ?: "—"} · ${e.source.name.lowercase()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = DriverColors.TextSecondary,
                            )
                            VSpace(10.dp)
                            GhostCta(if (e.taken) "Desmarcar tomado" else "Marcar tomado") {
                                scope.launch { g.historyRepo.markTaken(e.at, !e.taken) }
                            }
                        }
                    }
                }
            }
        }
    }
}
