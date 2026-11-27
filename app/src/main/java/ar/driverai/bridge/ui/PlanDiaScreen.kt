package ar.driverai.bridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.driverai.bridge.plan.Confianza
import ar.driverai.bridge.plan.FinFuente
import ar.driverai.bridge.plan.PlanAviso
import ar.driverai.bridge.plan.PlanBloque
import ar.driverai.bridge.plan.PlanDia
import ar.driverai.bridge.plan.PlanDiaAlerts
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PlanDiaCard(
    plan: PlanDia?,
    now: Long,
    avisoActivo: PlanAviso?,
    onCargarDemo: () -> Unit,
    onSilenciar: (() -> Unit)? = null,
) {
    Section("Plan del día") {
        if (plan == null) {
            Text(
                "Plan no disponible. Sin internet o sin fuentes vigentes no inventamos eventos.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("plan_no_disponible"),
            )
            Spacer(Modifier.height(8.dp))
            Text("Podés seguir evaluando ofertas y registrando cobros con normalidad.", color = TextMuted)
            TextButton(onClick = onCargarDemo, modifier = Modifier.testTag("plan_cargar_demo")) {
                Text("Cargar plan de ejemplo (demo)")
            }
            return@Section
        }
        if (plan.isExpired(now)) {
            StatusPill("PLAN VENCIDO", Amber)
            Text("Venció ${hm(plan.vigenciaHasta)}. Pedí uno nuevo al arrancar turno.", color = Amber)
        } else {
            StatusPill("VIGENTE HASTA ${hm(plan.vigenciaHasta)}", AccentMint)
        }
        Text("Zona base \u00b7 ${plan.zonaBase}", style = MaterialTheme.typography.labelLarge)
        Text("Generado ${hm(plan.generadoAt)}. Solo eventos con fuente.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
        avisoActivo?.let { av ->
            Spacer(Modifier.height(8.dp))
            StatusPill("AVISO", AccentMint)
            Text(av.texto, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            onSilenciar?.let { TextButton(onClick = it) { Text("Silenciar este aviso") } }
        }
        val ordered = plan.bloques.sortedBy { it.inicio }
        val nextIdx = ordered.indexOfFirst { it.ventanaPico.hasta >= now }.let { if (it < 0) ordered.lastIndex else it }
        val visible = ordered.drop(nextIdx.coerceAtLeast(0)).take(3)
        if (visible.isEmpty()) {
            Text("No hay bloques próximos en este plan.", color = TextMuted)
        } else {
            visible.forEachIndexed { i, b ->
                Spacer(Modifier.height(10.dp))
                BloqueCard(b, destacado = i == 0 && nextIdx >= 0, now = now)
            }
        }
        if (plan.huecos.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Huecos", style = MaterialTheme.typography.labelLarge)
            plan.huecos.take(2).forEach { h ->
                Text("${hm(h.desde)}–${hm(h.hasta)} \u00b7 ${h.sugerencia}", color = TextMuted)
            }
        }
        plan.supuestos.take(2).forEach {
            Text("\u00b7 $it", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
        }
    }
}

@Composable
private fun BloqueCard(b: PlanBloque, destacado: Boolean, now: Long) {
    val border = if (destacado) AccentMint else TextMuted
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("plan_bloque_${b.id}")) {
        Row(Modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (destacado) "AHORA / SIGUIENTE" else b.tipo.name.uppercase(), color = border, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(confianzaLabel(b.confianza), color = confianzaColor(b.confianza))
        }
        Text(b.nombre, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        val donde = when {
            b.lugarNoConfirmado || b.lugar == null -> "Dónde \u00b7 no confirmado"
            else -> "Dónde \u00b7 ${b.lugar.nombre}${b.lugar.direccion?.let { " \u00b7 $it" } ?: ""}"
        }
        Text(donde, style = MaterialTheme.typography.bodyLarge)
        val finTag = if (b.finFuente == FinFuente.estimado) "estimado" else "oficial"
        Text("Cuándo \u00b7 fin ${hm(b.finEstimado)} ($finTag) \u00b7 pico ${hm(b.ventanaPico.desde)}–${hm(b.ventanaPico.hasta)}", style = MaterialTheme.typography.bodyLarge)
        val espera = b.puntosEspera.take(2).joinToString(" \u00b7 ") { it.calle }.ifBlank { "sin punto de espera cargado" }
        Text("Qué hacer \u00b7 parate en $espera", style = MaterialTheme.typography.bodyLarge)
        if (b.destinosLargos.isNotEmpty()) Text("Destinos largos probables \u00b7 ${b.destinosLargos.joinToString(", ")}", color = TextMuted)
        b.avisoTexto?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = AccentMint) }
        Text("Fuentes \u00b7 ${b.fuentes.joinToString { it.nombre }.ifBlank { "sin fuente" }}", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
    }
}

@Composable
fun HomePlanDiaSection(now: Long) {
    val g = ar.driverai.bridge.DriverAiApp.graph
    val scope = rememberCoroutineScope()
    val plan by g.planDia.plan.collectAsState(initial = null)
    val avisos = remember(plan) { plan?.let { PlanDiaAlerts.schedule(it) }.orEmpty() }
    var silenced by remember { mutableStateOf(setOf<String>()) }
    val avisoActivo = remember(avisos, now, silenced) { PlanDiaAlerts.activeAt(avisos, now, silenced).firstOrNull() }
    PlanDiaCard(
        plan = plan,
        now = now,
        avisoActivo = avisoActivo,
        onCargarDemo = { scope.launch { g.planDia.loadFixtureForDemo(System.currentTimeMillis()) } },
        onSilenciar = { avisoActivo?.let { silenced = silenced + "${it.bloqueId}:${it.tipo}" } },
    )
}

private fun confianzaLabel(c: Confianza) = when (c) {
    Confianza.alta -> "confianza alta"
    Confianza.media -> "confianza media"
    Confianza.baja -> "confianza baja"
}

private fun confianzaColor(c: Confianza) = when (c) {
    Confianza.alta -> AccentMint
    Confianza.media -> Amber
    Confianza.baja -> Color(0xFFFF8A80)
}

private val hmFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("es-AR"))
private fun hm(epoch: Long): String =
    hmFmt.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epoch))
