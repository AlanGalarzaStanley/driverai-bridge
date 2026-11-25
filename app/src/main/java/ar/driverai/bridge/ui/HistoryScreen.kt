package ar.driverai.bridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
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
    val df = SimpleDateFormat("dd/MM · HH:mm", Locale("es", "AR"))
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
        ScreenTitle("REGISTRO", "Actividad de ofertas", "Lecturas y evaluaciones recientes")
        Section("Privacidad y alcance") {
            Text("Marcar una oferta como tomada no registra un viaje completado ni un ingreso cobrado. El historial guarda cifras, no nombres, direcciones ni capturas. Máximo 100.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        OutlinedButton(onClick = { scope.launch { g.historyRepo.clear() } }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text("Borrar historial") }
        if (items.isEmpty()) {
            Section("Todavía no hay actividad") {
                Text("Sin evaluaciones guardadas.", style = MaterialTheme.typography.bodyMedium)
                Text("Las ofertas aparecerán cuando se evalúen con la lectura o desde el modo manual.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                items(items, key = { it.at }) { e ->
                    val verdict = runCatching { Verdict.valueOf(e.verdict) }.getOrDefault(Verdict.REVISAR)
                    Section("${df.format(Date(e.at))}${if (e.taken) " · marcada tomada" else ""}", modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            StatusPill(verdict.label, verdictColor(verdict))
                            Text(e.source.name.lowercase().replaceFirstChar { it.titlecase(Locale("es", "AR")) }, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                        Text(e.fare?.let { Fmt.ars(it) } ?: "Tarifa no identificada", style = MaterialTheme.typography.headlineSmall)
                        Text("${e.totalKm?.let { Fmt.num(it) } ?: "—"} km · ${e.totalMin?.let { Fmt.num(it, 0) } ?: "—"} min", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                        Text(
                            "Estimación bruta: ${e.grossPerKm?.let { Fmt.ars(it) + "/km" } ?: "—"} · ${e.grossPerHour?.let { Fmt.ars(it) + "/h" } ?: "—"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = verdictColor(verdict),
                            fontWeight = FontWeight.Medium,
                        )
                        OutlinedButton(onClick = { scope.launch { g.historyRepo.markTaken(e.at, !e.taken) } }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (e.taken) "Desmarcar tomada" else "Marcar como tomada")
                        }
                    }
                }
            }
        }
    }
}
