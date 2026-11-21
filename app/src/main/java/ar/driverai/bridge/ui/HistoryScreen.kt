package ar.driverai.bridge.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
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
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Actividad de ofertas", style = MaterialTheme.typography.titleLarge)
        Text("Son lecturas y evaluaciones; marcar una oferta como tomada no registra un viaje completado ni un ingreso cobrado. Sin direcciones, nombres ni capturas. Máximo 100.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { scope.launch { g.historyRepo.clear() } }) { Text("Borrar historial") }
        if (items.isEmpty()) Text("Sin evaluaciones guardadas.")
        LazyColumn {
            items(items, key = { it.at }) { e ->
                val v = runCatching { Verdict.valueOf(e.verdict) }.getOrDefault(Verdict.REVISAR)
                Section("${df.format(Date(e.at))} · ${v.label}${if (e.taken) " · marcada tomada" else ""}") {
                    Text("${e.fare?.let { Fmt.ars(it) } ?: "—"} · ${e.totalKm?.let { Fmt.num(it) } ?: "—"} km · ${e.totalMin?.let { Fmt.num(it, 0) } ?: "—"} min")
                    Text("Estimación de la oferta: ${e.grossPerKm?.let { Fmt.ars(it) + "/km" } ?: "—"} · ${e.grossPerHour?.let { Fmt.ars(it) + "/h" } ?: "—"} · fuente ${e.source.name.lowercase()}", color = verdictColor(v))
                    OutlinedButton(onClick = { scope.launch { g.historyRepo.markTaken(e.at, !e.taken) } }) { Text(if (e.taken) "Desmarcar tomada" else "Marcar como tomada") }
                }
            }
        }
    }
}
