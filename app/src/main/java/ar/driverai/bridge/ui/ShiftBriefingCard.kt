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
        Text(
            "Respuesta recibida ${formatBriefingTime(snapshot.retrievedAt)}. La hora de actualización de las fuentes no está disponible.",
            color = if (old) Amber else Color.LightGray,
        )
        Text("Zona inicial elegida: ${snapshot.selectedZone ?: "no elegida"}")
        Text("Demanda estimada por el servicio: ${snapshot.demandLevel?.replace('_', ' ') ?: "no informada"}")
        Text("Zona sugerida por el servicio ahora (sin feed verificado): ${snapshot.bestZoneNow ?: "no informada"}")
        Text("Zona sugerida por el servicio en 2 h (sin feed verificado): ${snapshot.bestZoneIn2h ?: "no informada"}")

        val weather = buildList {
            snapshot.temperatureC?.let { add("${Fmt.num(it)} °C") }
            snapshot.rainNow?.let { add("lluvia ahora ${if (it) "sí" else "no"}") }
            snapshot.rainProbability3h?.let { add("probabilidad de lluvia 3 h ${Fmt.num(it, 0)} %") }
        }
        Text(if (weather.isEmpty()) "Clima estimado por el servicio: sin dato informado." else "Clima estimado por el servicio: ${weather.joinToString(", ")} (fuente/vigencia por variable desconocidas).")
        Text("Eventos del servicio: ${snapshot.eventsListed?.let { "$it listados; no verificados" } ?: "lista no informada"}.")

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
