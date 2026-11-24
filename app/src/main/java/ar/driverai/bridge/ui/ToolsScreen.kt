package ar.driverai.bridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun ToolsScreen(onNavigate: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScreenTitle("DriverAI Bridge", "Herramientas", "Configuración, actividad y privacidad")
        Section("Tu espacio de trabajo") {
            Text("Los ingresos, gastos, odómetro y servicios se guardan localmente en este teléfono. No hay sincronización de billetera activa.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        OutlinedButton(onClick = { onNavigate("copiloto") }, modifier = Modifier.fillMaxWidth().testTag("tools_copilot")) { Text("Copiloto de jornada") }
        OutlinedButton(onClick = { onNavigate("historial") }, modifier = Modifier.fillMaxWidth().testTag("tab_historial")) { Text("Actividad de ofertas") }
        OutlinedButton(onClick = { onNavigate("ajustes") }, modifier = Modifier.fillMaxWidth().testTag("tab_ajustes")) { Text("Ajustes y umbrales") }
        OutlinedButton(onClick = { onNavigate("diag") }, modifier = Modifier.fillMaxWidth().testTag("tab_diag")) { Text("Chequeo y permisos") }
        Section("Uso seguro") {
            Text("DriverAI evalúa y orienta. La aceptación o rechazo del viaje siempre la decidís vos desde la app de viajes.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}
