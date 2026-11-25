package ar.driverai.bridge.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ar.driverai.bridge.backend.ShiftBriefingSnapshot
import ar.driverai.bridge.engine.Fmt
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ShiftBriefingCard(snapshot: ShiftBriefingSnapshot?, now: Long, message: String?) {
    Section("Briefing del turno") {
        message?.let { Text(it) }
        if (snapshot == null) {
            Text("Todavía no hay una consulta guardada. Al iniciar el turno podés consultar el contexto disponible.")
            return@Section
        }

        val old = now - snapshot.retrievedAt > MAX_DISPLAY_AGE_MS
        StatusPill(if (old) "CONSULTA ANTERIOR" else "RESPUESTA RECIBIDA", if (old) Amber else AccentMint)
        Text(
            "Respuesta recibida ${formatBriefingTime(snapshot.retrievedAt)}. La hora de actualización de las fuentes no está disponible.",
            color = TextMuted,
        )
        Text("Zona inicial · ${snapshot.selectedZone ?: "no elegida"}", style = MaterialTheme.typography.labelLarge)
        Text("Demanda estimada por el servicio", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Text(snapshot.demandLevel?.replace('_', ' ') ?: "No informada", style = MaterialTheme.typography.titleLarge, color = AccentMint)
        Text("Zona sugerida ahora · ${snapshot.bestZoneNow ?: "no informada"}")
        Text("Zona sugerida en 2 h · ${snapshot.bestZoneIn2h ?: "no informada"}")

        val weather = buildList {
            snapshot.temperatureC?.let { add("${Fmt.num(it)} °C") }
            snapshot.rainNow?.let { add("lluvia ahora ${if (it) "sí" else "no"}") }
            snapshot.rainProbability3h?.let { add("probabilidad de lluvia 3 h ${Fmt.num(it, 0)} %") }
        }
        Text("Contexto meteorológico", style = MaterialTheme.typography.labelLarge)
        Text(if (weather.isEmpty()) "Sin dato informado." else "${weather.joinToString(" · ")} · fuente y vigencia por variable desconocidas.")
        Text("Eventos · ${snapshot.eventsListed?.let { "$it listados, no verificados" } ?: "sin lista informada"}.", color = TextMuted)

        if (old) Text("La consulta tiene más de 30 min; no la tomes como actual.", color = Amber)
        Text(
            "El endpoint actual no identifica la fuente ni su vigencia por dato, y no entrega confianza por señal. Esto no es un índice calibrado ni una predicción de viajes o ganancias.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.LightGray,
        )
    }
}

private const val MAX_DISPLAY_AGE_MS = 30 * 60 * 1000L
private val briefingTimeFormatter = DateTimeFormatter.ofPattern("HH:mm, dd/MM", Locale.forLanguageTag("es-AR"))

private fun formatBriefingTime(epochMillis: Long): String =
    briefingTimeFormatter.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMillis))
