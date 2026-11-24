package ar.driverai.bridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ar.driverai.bridge.DriverAiApp
import ar.driverai.bridge.capture.EvalRecord
import ar.driverai.bridge.engine.DataSource
import ar.driverai.bridge.engine.Fmt
import ar.driverai.bridge.engine.OfferData
import ar.driverai.bridge.engine.OfferTextParser
import ar.driverai.bridge.engine.Reading
import ar.driverai.bridge.engine.ReasonKind

fun parseUserNumber(s: String): Double? {
    val t = s.trim()
    if (t.isEmpty()) return null
    OfferTextParser.parseArsAmount(t.removePrefix("$").trim())?.let { if (it.confidence >= 0.9) return it.value }
    return t.replace(',', '.').toDoubleOrNull()
}

private fun fmtField(v: Double?) = v?.let { Fmt.num(it, 2) } ?: ""

@Composable
fun EvaluateScreen(prefill: Boolean, onPrefillConsumed: () -> Unit) {
    val g = DriverAiApp.graph
    val last by g.pipeline.last.collectAsState()
    var fare by remember { mutableStateOf("") }
    var pkm by remember { mutableStateOf("") }
    var pmin by remember { mutableStateOf("") }
    var tkm by remember { mutableStateOf("") }
    var tmin by remember { mutableStateOf("") }
    var passengerRating by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<EvalRecord?>(null) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current

    fun load(o: OfferData) {
        fare = fmtField(o.fare.value); pkm = fmtField(o.pickupKm.value); pmin = fmtField(o.pickupMin.value)
        tkm = fmtField(o.tripKm.value); tmin = fmtField(o.tripMin.value)
        passengerRating = fmtField(o.passengerRating.value)
    }
    LaunchedEffect(prefill) { if (prefill) { last?.evaluation?.offer?.let(::load); onPrefillConsumed() } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp)) {
        ScreenTitle("CENTRO DE DECISIÓN", "Evaluar oferta", "Modo manual / demo · los datos los ingresás vos")

        last?.takeIf { it.source != DataSource.MANUAL }?.let { rec ->
            Section("Última lectura automática (${if (rec.source == DataSource.OCR) "OCR" else "accesibilidad"})") {
                val o = rec.evaluation.offer
                ConfRow("Tarifa", o.fare) { Fmt.ars(it) }
                ConfRow("Recogida km", o.pickupKm) { Fmt.num(it) }
                ConfRow("Recogida min", o.pickupMin) { Fmt.num(it, 0) }
                ConfRow("Viaje km", o.tripKm) { Fmt.num(it) }
                ConfRow("Viaje min", o.tripMin) { Fmt.num(it, 0) }
                ConfRow("Calificación del pasajero", o.passengerRating) { "${Fmt.num(it, 2)}/5" }
                rec.notes.forEach { Text("Nota: $it", style = MaterialTheme.typography.bodySmall) }
                OutlinedButton(onClick = { load(o) }) { Text("Corregir estos datos abajo") }
            }
        }

        @Composable
        fun field(label: String, v: String, tag: String, set: (String) -> Unit) = OutlinedTextField(
            v, set, label = { Text(label) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().testTag(tag),
        )
        Section("Datos de la oferta") {
            Text("La evaluación usa lo visible o lo que cargues acá. Si no conocés un valor, dejalo vacío: no se completa por inferencia.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            field("Tarifa ofrecida (\$)", fare, "in_fare") { fare = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { field("Km a recogida", pkm, "in_pkm") { pkm = it } }
                Column(Modifier.weight(1f)) { field("Min a recogida", pmin, "in_pmin") { pmin = it } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { field("Km del viaje", tkm, "in_tkm") { tkm = it } }
                Column(Modifier.weight(1f)) { field("Min del viaje", tmin, "in_tmin") { tmin = it } }
            }
            field("Calificación del pasajero (1–5, si está visible)", passengerRating, "in_passenger_rating") { passengerRating = it }
            Button(onClick = {
                val offer = OfferData(
                    fare = Reading.manual(parseUserNumber(fare)),
                    pickupKm = Reading.manual(parseUserNumber(pkm)),
                    pickupMin = Reading.manual(parseUserNumber(pmin)),
                    tripKm = Reading.manual(parseUserNumber(tkm)),
                    tripMin = Reading.manual(parseUserNumber(tmin)),
                    passengerRating = Reading.manual(parseUserNumber(passengerRating)),
                )
                result = g.pipeline.evaluateManual(offer)
                focus.clearFocus() // cierra el teclado para que se vea el resultado
            }, modifier = Modifier.fillMaxWidth().height(56.dp).testTag("btn_evaluate")) { Text("Evaluar oferta") }
        }

        Section("Práctica segura") {
            Text("Ejemplos de demostración (valores ficticios):", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
                OutlinedButton(onClick = { fare = "8000"; pkm = "1,5"; pmin = "5"; tkm = "8,5"; tmin = "20" }, Modifier.weight(1f), contentPadding = pad) { Text("Demo A", maxLines = 1) }
                OutlinedButton(onClick = { fare = "2500"; pkm = "3"; pmin = "10"; tkm = "6"; tmin = "18" }, Modifier.weight(1f), contentPadding = pad) { Text("Demo B", maxLines = 1) }
                OutlinedButton(onClick = { fare = "9000"; pkm = ""; pmin = ""; tkm = "12"; tmin = "" }, Modifier.weight(1f), contentPadding = pad) { Text("Parcial", maxLines = 1) }
            }
        }

        result?.let { rec ->
            val e = rec.evaluation
            Section("Resultado") {
                Text(e.verdict.label, color = verdictColor(e.verdict), style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.testTag("verdict"))
                MetricsSummary(e.metrics)
                e.reasons.forEach { r ->
                    val prefix = when (r.kind) { ReasonKind.PASA -> "✓"; ReasonKind.FALLA -> "✗"; ReasonKind.AVISO -> "!"; ReasonKind.INFO -> "i" }
                    Text("$prefix ${r.text}", color = when (r.kind) { ReasonKind.PASA -> Green; ReasonKind.FALLA -> Red; ReasonKind.AVISO -> Amber; else -> Color.LightGray })
                }
                e.fareAlternative?.let { alternative ->
                    val assumptions = listOfNotNull(
                        alternative.assumedPickupKm?.let { "${Fmt.num(it)} km de recogida estimados" },
                        alternative.assumedPickupMin?.let { "${Fmt.num(it, 0)} min a la recogida estimados" },
                        if (alternative.emptyReturnFraction > 0.0) "retorno vacío ${Fmt.num(alternative.emptyReturnFraction * 100.0, 0)} %" else null,
                    ).joinToString(" y ")
                    Text("Alternativa según tus umbrales", fontWeight = FontWeight.Bold)
                    Text("Con los datos disponibles para esta oferta, el piso para cumplir tus umbrales es ${Fmt.ars(alternative.minimumFare)}.")
                    if (assumptions.isNotEmpty()) Text("Supuestos: $assumptions.", color = Amber)
                    Text("No predice otra oferta ni incorpora tráfico o demanda.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                OutlinedButton(onClick = { g.overlay.showEvaluation(rec, false) }) { Text("Mostrar en tarjeta flotante (demo)") }
            }
        }
    }
}

@Composable
private fun ConfRow(label: String, r: Reading, fmt: (Double) -> String) {
    val v = r.value
    Text(if (v == null) "$label: no se pudo leer" else "$label: ${fmt(v)} (confianza ${(r.confidence * 100).toInt()} %)",
        color = if (v == null || r.confidence < 0.7) Amber else Color.Unspecified)
}
