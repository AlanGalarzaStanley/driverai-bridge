package ar.driverai.bridge.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ToolsScreen(onNavigate: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        ScreenTitle("TU ESPACIO", "Más herramientas", "Configuración, actividad y privacidad")
        Section("Tus datos") {
            Text("Ingresos, gastos, odómetro y servicios se guardan localmente en este teléfono. No hay sincronización de billetera activa.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        ToolActionCard(
            title = "Copiloto de jornada",
            detail = "Preguntas sobre ofertas, caja y contexto disponible",
            tag = "tools_copilot",
            onClick = { onNavigate("copiloto") },
        )
        ToolActionCard(
            title = "Informes y rendimiento",
            detail = "Por app y por hora, ingresos semanales y exportación CSV",
            tag = "tools_informes",
            onClick = { onNavigate("informes") },
        )
        ToolActionCard(
            title = "Vehículo",
            detail = "Odómetro, mantenimiento y costos del auto",
            tag = "tab_vehiculo",
            onClick = { onNavigate("vehiculo") },
        )
        ToolActionCard(
            title = "Actividad de ofertas",
            detail = "Revisá lecturas y evaluaciones recientes",
            tag = "tab_historial",
            onClick = { onNavigate("historial") },
        )
        ToolActionCard(
            title = "Ajustes y umbrales",
            detail = "Tus mínimos, costos y preferencias de pasajero",
            tag = "tab_ajustes",
            onClick = { onNavigate("ajustes") },
        )
        ToolActionCard(
            title = "Chequeo y permisos",
            detail = "Estado local de captura, lectura y conexiones",
            tag = "tab_diag",
            onClick = { onNavigate("diag") },
        )
        Section("Control del conductor") {
            Text("DriverAI evalúa y orienta. La aceptación o el rechazo del viaje siempre los decidís vos desde la app de viajes.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}

@Composable
internal fun ToolActionCard(title: String, detail: String, tag: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(tag),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = AccentMint, fontWeight = FontWeight.Light)
        }
    }
}
