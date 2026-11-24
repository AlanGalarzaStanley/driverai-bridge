package ar.driverai.bridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    var result by remember { mutableStateOf<EvalRecord?>(null) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current

    fun load(o: OfferData) {
        fare = fmtField(o.fare.value); pkm = fmtField(o.pickupKm.value); pmin = fmtField(o.pickupMin.value)
        tkm = fmtField(o.tripKm.value); tmin = fmtField(o.tripMin.value)
    }
    LaunchedEffect(prefill) { if (prefill) { last?.evaluation?.offer?.let(::load); onPrefillConsumed() } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenScaffold(
            title = "Evaluar",
            subtitle = "Manual o corrección de lectura",
        ) {
            last?.takeIf { it.source != DataSource.MANUAL }?.let { rec ->
                Section("Última lectura", accent = DriverColors.Info) {
                    val o = rec.evaluation.offer
                    ConfRow("Tarifa", o.fare) { Fmt.ars(it) }
                    ConfRow("Recogida km", o.pickupKm) { Fmt.num(it) }
                    ConfRow("Recogida min", o.pickupMin) { Fmt.num(it, 0) }
                    ConfRow("Viaje km", o.tripKm) { Fmt.num(it) }
                    ConfRow("Viaje min", o.tripMin) { Fmt.num(it, 0) }
                    rec.notes.forEach { Text("Nota: $it", style = MaterialTheme.typography.bodySmall, color = DriverColors.TextSecondary) }
                    VSpace(10.dp)
                    GhostCta("Corregir estos datos abajo") { load(o) }
                }
            }

            Section("Datos de la oferta") {
                CockpitField(fare, { fare = it }, "Tarifa ofrecida ($)", Modifier.testTag("in_fare"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                VSpace(10.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CockpitField(pkm, { pkm = it }, "Km recogida", Modifier.weight(1f).testTag("in_pkm"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    CockpitField(pmin, { pmin = it }, "Min recogida", Modifier.weight(1f).testTag("in_pmin"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                VSpace(10.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CockpitField(tkm, { tkm = it }, "Km viaje", Modifier.weight(1f).testTag("in_tkm"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    CockpitField(tmin, { tmin = it }, "Min viaje", Modifier.weight(1f).testTag("in_tmin"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                VSpace(14.dp)
                PrimaryCta("Evaluar", {
                    val offer = OfferData(
                        Reading.manual(parseUserNumber(fare)),
                        Reading.manual(parseUserNumber(pkm)),
                        Reading.manual(parseUserNumber(pmin)),
                        Reading.manual(parseUserNumber(tkm)),
                        Reading.manual(parseUserNumber(tmin)),
                    )
                    result = g.pipeline.evaluateManual(offer)
                    focus.clearFocus()
                }, Modifier.testTag("btn_evaluate"))
            }

            Section("Demos", accent = DriverColors.TextTertiary) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostCta("Demo A", { fare = "8000"; pkm = "1,5"; pmin = "5"; tkm = "8,5"; tmin = "20" }, Modifier.weight(1f))
                    GhostCta("Demo B", { fare = "2500"; pkm = "3"; pmin = "10"; tkm = "6"; tmin = "18" }, Modifier.weight(1f))
                    GhostCta("Parcial", { fare = "9000"; pkm = ""; pmin = ""; tkm = "12"; tmin = "" }, Modifier.weight(1f))
                }
            }

            result?.let { rec ->
                val e = rec.evaluation
                Section("Resultado", accent = verdictColor(e.verdict)) {
                    VerdictBanner(e.verdict.label, verdictColor(e.verdict), e.headline)
                    VSpace(12.dp)
                    MetricsSummary(e.metrics)
                    VSpace(10.dp)
                    e.reasons.forEach { r ->
                        val c = when (r.kind) {
                            ReasonKind.PASA -> DriverColors.Positive
                            ReasonKind.FALLA -> DriverColors.Negative
                            ReasonKind.AVISO -> DriverColors.Warning
                            ReasonKind.INFO -> DriverColors.TextSecondary
                        }
                        val prefix = when (r.kind) {
                            ReasonKind.PASA -> "✓"
                            ReasonKind.FALLA -> "✗"
                            ReasonKind.AVISO -> "!"
                            ReasonKind.INFO -> "i"
                        }
                        Text("$prefix ${r.text}", color = c, style = MaterialTheme.typography.bodyMedium)
                    }
                    VSpace(10.dp)
                    GhostCta("Mostrar en tarjeta flotante") { g.overlay.showEvaluation(rec, false) }
                }
            }
        }
    }
}

@Composable
private fun ConfRow(label: String, r: Reading, fmt: (Double) -> String) {
    val v = r.value
    Text(
        if (v == null) "$label: no se pudo leer" else "$label: ${fmt(v)} (${(r.confidence * 100).toInt()}%)",
        color = if (v == null || r.confidence < 0.7) DriverColors.Warning else DriverColors.TextPrimary,
        style = MaterialTheme.typography.bodyMedium,
    )
}
