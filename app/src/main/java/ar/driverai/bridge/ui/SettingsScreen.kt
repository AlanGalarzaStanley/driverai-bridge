package ar.driverai.bridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ar.driverai.bridge.DriverAiApp
import ar.driverai.bridge.copilot.Zones
import ar.driverai.bridge.engine.DriverSettings
import ar.driverai.bridge.engine.Fmt
import ar.driverai.bridge.engine.UncertaintyTolerance
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private fun f(v: Double?) = v?.let { Fmt.num(it, 2) } ?: ""

@Composable
fun SettingsScreen() {
    val g = DriverAiApp.graph
    val scope = rememberCoroutineScope()
    val saved by g.settings.collectAsState()
    var loaded by remember { mutableStateOf(false) }
    var minFare by remember { mutableStateOf("") }
    var minKm by remember { mutableStateOf("") }
    var minHour by remember { mutableStateOf("") }
    var costKm by remember { mutableStateOf("") }
    var fuelL by remember { mutableStateOf("") }
    var fuelP by remember { mutableStateOf("") }
    var defPkm by remember { mutableStateOf("") }
    var defPmin by remember { mutableStateOf("") }
    var ret by remember { mutableStateOf("0") }
    var margin by remember { mutableStateOf("") }
    var tol by remember { mutableStateOf(UncertaintyTolerance.NORMAL) }
    var msg by remember { mutableStateOf<String?>(null) }
    var homeMenu by remember { mutableStateOf(false) }

    LaunchedEffect(saved) {
        if (!loaded) {
            val s = g.settingsRepo.current()
            minFare = f(s.minFare); minKm = f(s.minGrossPerKm); minHour = f(s.minGrossPerHour); costKm = f(s.costPerKm)
            fuelL = f(s.fuelLitersPer100Km); fuelP = f(s.fuelPricePerLiter); defPkm = f(s.defaultPickupKm); defPmin = f(s.defaultPickupMin)
            ret = Fmt.num(s.emptyReturnFraction * 100, 0); margin = f(s.minMarginPct); tol = s.tolerance
            loaded = true
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenScaffold(
            title = "Ajustes",
            subtitle = "Umbrales, costos y privacidad · sin valores precargados",
        ) {
            Section("Umbrales (ingreso bruto)") {
                CockpitField(minFare, { minFare = it }, "Tarifa mínima ($)", Modifier.testTag("set_minfare"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                VSpace(10.dp)
                CockpitField(minKm, { minKm = it }, "Mínimo $ por km", Modifier.testTag("set_minkm"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                VSpace(10.dp)
                CockpitField(minHour, { minHour = it }, "Mínimo $ por hora", Modifier.testTag("set_minhour"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                VSpace(10.dp)
                CockpitField(margin, { margin = it }, "Margen mínimo (%)", Modifier.testTag("set_margin"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }

            Section("Costos variables", accent = DriverColors.Warning) {
                CockpitField(costKm, { costKm = it }, "Costo propio por km ($)", Modifier.testTag("set_costkm"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                VSpace(10.dp)
                CockpitField(fuelL, { fuelL = it }, "Consumo (L / 100 km)", Modifier.testTag("set_fuell"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                VSpace(10.dp)
                CockpitField(fuelP, { fuelP = it }, "Precio combustible ($/L)", Modifier.testTag("set_fuelp"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                saved.fuelPriceUpdatedAt?.let {
                    VSpace(8.dp)
                    Text("Precio cargado el ${DateFormat.getDateInstance().format(Date(it))}", style = MaterialTheme.typography.bodySmall, color = DriverColors.TextTertiary)
                }
            }

            Section("Supuestos", accent = DriverColors.Info) {
                CockpitField(defPkm, { defPkm = it }, "Km a recogida si no se lee", Modifier.testTag("set_defpkm"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                VSpace(10.dp)
                CockpitField(defPmin, { defPmin = it }, "Min a recogida si no se lee", Modifier.testTag("set_defpmin"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                VSpace(10.dp)
                CockpitField(ret, { ret = it }, "Retorno vacío (% del viaje, 0–100)", Modifier.testTag("set_return"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                VSpace(12.dp)
                Text("Tolerancia a la incertidumbre de lectura", style = MaterialTheme.typography.labelMedium, color = DriverColors.TextSecondary)
                VSpace(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UncertaintyTolerance.entries.forEach { t ->
                        FilterChip(
                            selected = tol == t,
                            onClick = { tol = t },
                            label = { Text("${t.label} (${(t.minConfidence * 100).toInt()}%)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DriverColors.Accent.copy(alpha = 0.16f),
                                selectedLabelColor = DriverColors.Accent,
                                containerColor = DriverColors.Surface,
                                labelColor = DriverColors.TextSecondary,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = DriverColors.Border,
                                selectedBorderColor = DriverColors.Accent.copy(alpha = 0.5f),
                                enabled = true,
                                selected = tol == t,
                            ),
                        )
                    }
                }
            }

            Section("Voz y privacidad", accent = DriverColors.Positive) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Voz activada", Modifier.weight(1f), color = DriverColors.TextPrimary)
                    Switch(
                        saved.voiceEnabled,
                        { v -> scope.launch { g.settingsRepo.update { it.copy(voiceEnabled = v) } } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DriverColors.Void,
                            checkedTrackColor = DriverColors.Accent,
                            uncheckedThumbColor = DriverColors.TextSecondary,
                            uncheckedTrackColor = DriverColors.Border,
                        ),
                    )
                }
                VSpace(8.dp)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Decir el veredicto de cada oferta", Modifier.weight(1f), color = DriverColors.TextPrimary)
                    Switch(
                        saved.speakVerdicts,
                        { v -> scope.launch { g.settingsRepo.update { it.copy(speakVerdicts = v) } } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DriverColors.Void,
                            checkedTrackColor = DriverColors.Accent,
                            uncheckedThumbColor = DriverColors.TextSecondary,
                            uncheckedTrackColor = DriverColors.Border,
                        ),
                    )
                }
                VSpace(10.dp)
                Text("Volumen de voz: ${(saved.voiceVolume * 100).toInt()} %", color = DriverColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                Slider(
                    saved.voiceVolume,
                    { v -> scope.launch { g.settingsRepo.update { it.copy(voiceVolume = v) } } },
                    colors = SliderDefaults.colors(
                        thumbColor = DriverColors.Accent,
                        activeTrackColor = DriverColors.Accent,
                        inactiveTrackColor = DriverColors.Border,
                    ),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Guardar historial (solo números)", Modifier.weight(1f), color = DriverColors.TextPrimary)
                    Switch(
                        saved.historyEnabled,
                        { v -> scope.launch { g.settingsRepo.update { it.copy(historyEnabled = v) } } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DriverColors.Void,
                            checkedTrackColor = DriverColors.Accent,
                            uncheckedThumbColor = DriverColors.TextSecondary,
                            uncheckedTrackColor = DriverColors.Border,
                        ),
                    )
                }
                VSpace(12.dp)
                Column {
                    GhostCta("Zona de casa: ${saved.homeZone ?: "sin definir"}") { homeMenu = true }
                    DropdownMenu(homeMenu, { homeMenu = false }) {
                        Zones.ALL.forEach { z ->
                            DropdownMenuItem(
                                text = { Text(z) },
                                onClick = {
                                    homeMenu = false
                                    scope.launch { g.settingsRepo.update { it.copy(homeZone = z) } }
                                },
                            )
                        }
                    }
                }
            }

            VSpace(10.dp)
            PrimaryCta("Guardar ajustes", {
                val errors = mutableListOf<String>()
                fun p(label: String, s: String, max: Double = 1e9): Double? {
                    if (s.isBlank()) return null
                    val v = parseUserNumber(s)
                    if (v == null || v < 0 || v > max) {
                        errors += label
                        return null
                    }
                    return v
                }
                val retPct = p("Retorno vacío", ret, 100.0) ?: 0.0
                val newFuelP = p("Precio combustible", fuelP)
                val upd: (DriverSettings) -> DriverSettings = { s ->
                    s.copy(
                        minFare = p("Tarifa mínima", minFare),
                        minGrossPerKm = p("Mínimo por km", minKm),
                        minGrossPerHour = p("Mínimo por hora", minHour),
                        costPerKm = p("Costo por km", costKm),
                        fuelLitersPer100Km = p("Consumo", fuelL, 100.0),
                        fuelPricePerLiter = newFuelP,
                        fuelPriceUpdatedAt = if (newFuelP == null) null
                        else if (newFuelP != s.fuelPricePerLiter || s.fuelPriceUpdatedAt == null) System.currentTimeMillis()
                        else s.fuelPriceUpdatedAt,
                        defaultPickupKm = p("Km recogida", defPkm),
                        defaultPickupMin = p("Min recogida", defPmin),
                        emptyReturnFraction = retPct / 100.0,
                        minMarginPct = p("Margen", margin, 100.0),
                        tolerance = tol,
                    )
                }
                upd(saved)
                if (errors.isNotEmpty()) msg = "Valores inválidos: ${errors.distinct().joinToString()}"
                else {
                    scope.launch { g.settingsRepo.update(upd) }
                    msg = "Guardado."
                }
            }, Modifier.testTag("btn_save"))
            msg?.let {
                VSpace(10.dp)
                StatusPill(
                    it,
                    if (it.startsWith("Guardado")) DriverColors.Positive else DriverColors.Warning,
                    Modifier.testTag("settings_msg"),
                )
            }
        }
    }
}
