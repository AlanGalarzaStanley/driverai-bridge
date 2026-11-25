package ar.driverai.bridge.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ar.driverai.bridge.DriverAiApp
import ar.driverai.bridge.engine.Fmt
import ar.driverai.bridge.insights.Insights
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun InsightsScreen() {
    val g = DriverAiApp.graph
    val ctx = LocalContext.current
    val history by g.historyRepo.entries.collectAsState(initial = emptyList())
    val money by g.walletRepo.entries.collectAsState(initial = emptyList())
    val tz = remember { ZoneId.systemDefault() }
    val platforms = remember(history) { Insights.byPlatform(history) }
    val hours = remember(history) { Insights.byHour(history, tz) }
    val best = remember(history) { Insights.bestHours(history, tz) }
    val weeks = remember(money) { Insights.weekly(money, tz, weeks = 6) }
    val weekFmt = remember { DateTimeFormatter.ofPattern("d/M") }

    fun share(subject: String, body: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        ctx.startActivity(Intent.createChooser(send, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        ScreenTitle("INFORMES", "Tu rendimiento", "Calculado con tu historial y tu billetera, solo en este teléfono")

        Section("Por app", Modifier.testTag("insights_platforms")) {
            if (platforms.isEmpty()) SectionCaption("Todavía no hay ofertas registradas.")
            platforms.forEach { b ->
                Column {
                    Text(b.key, style = MaterialTheme.typography.titleMedium)
                    SectionCaption(
                        listOfNotNull(
                            "${b.offers} ofertas",
                            b.acceptanceRate?.let { "aceptaste ${(it * 100).toInt()} %" },
                            b.avgGrossPerKm?.let { "${Fmt.ars(it)}/km" },
                            b.avgGrossPerHour?.let { "${Fmt.ars(it)}/h" },
                            b.convenientPct?.let { "${(it * 100).toInt()} % convenían" },
                        ).joinToString(" · "),
                    )
                }
            }
        }

        Section("Tus mejores horas") {
            if (best.isEmpty()) SectionCaption("Se necesitan al menos 5 ofertas por hora para comparar.")
            best.forEach { (h, b) -> Text("%02d–%02d h · %s/h · %d ofertas".format(h, (h + 1) % 24, Fmt.ars(b.avgGrossPerHour ?: 0.0), b.offers), style = MaterialTheme.typography.bodyMedium) }
        }

        Section("Por hora del día") {
            if (hours.isEmpty()) SectionCaption("Sin datos.")
            hours.forEach { (_, b) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(b.key, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(52.dp))
                    SectionCaption(listOfNotNull("${b.offers} of.", b.avgGrossPerHour?.let { "${Fmt.ars(it)}/h" }, b.avgGrossPerKm?.let { "${Fmt.ars(it)}/km" }).joinToString(" · "))
                }
            }
        }

        Section("Billetera por semana") {
            weeks.forEach { w ->
                Row {
                    Text("Sem. ${w.start.format(weekFmt)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(90.dp))
                    SectionCaption("ingresos ${Fmt.ars(w.income)} · gastos ${Fmt.ars(w.expense)} · neto ${Fmt.ars(w.net)}")
                }
            }
        }

        Section("Exportar") {
            SectionCaption("CSV para planilla o contador (monotributo). Incluye montos, km, minutos, app y zona aproximada; nunca direcciones ni nombres.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { share("DriverAI historial.csv", Insights.historyCsv(history, tz)) }, modifier = Modifier.testTag("export_history")) { Text("Historial") }
                OutlinedButton(onClick = { share("DriverAI billetera.csv", Insights.walletCsv(money, tz)) }, modifier = Modifier.testTag("export_wallet")) { Text("Billetera") }
            }
        }
    }
}
