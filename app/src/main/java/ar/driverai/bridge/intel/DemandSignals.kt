package ar.driverai.bridge.intel

import kotlinx.serialization.Serializable

/** Tipo de señal que puede mover la demanda de viajes. */
@Serializable
enum class SignalKind(val label: String) {
    EVENTO_DEPORTIVO("Partido"),
    EVENTO_SHOW("Show / evento"),
    VUELOS("Vuelos"),
    CRUCERO("Crucero"),
    PARO_TRANSPORTE("Paro / corte de transporte"),
    CLIMA("Clima"),
    FERIADO("Feriado"),
    OTRO("Otra señal"),
}

/** De dónde viene una señal; define cómo se presenta y cuánto pesa su confianza. */
@Serializable
enum class SignalOrigin(val label: String) {
    API_PUBLICA("Fuente pública"),
    CONDUCTOR("Cargada por vos"),
    PATRON_GENERAL("Patrón general"),
    TU_HISTORIAL("Tu historial"),
    /** Importada desde la respuesta de una IA externa que eligió el conductor. No verificada por la app. */
    IA_EXTERNA("Informe IA (verificar)"),
}

/**
 * Señal con fuente, vigencia y confianza. Nunca se presenta como demanda medida:
 * es un indicio que el radar convierte en puntos explicables.
 *
 * @param zoneId null = afecta a toda la ciudad (paro, lluvia).
 * @param startAt hora de inicio del hecho (comienzo del partido, arribo del vuelo, inicio del paro).
 * @param endAt fin del hecho si se conoce; si es null el radar usa la duración típica del tipo.
 * @param intensity 0..1, escala relativa (capacidad del estadio, volumen de vuelos, alcance del paro).
 * @param confidence 0..1, qué tan seguros estamos de que el hecho ocurre en ese horario.
 */
@Serializable
data class DemandSignal(
    val id: String,
    val kind: SignalKind,
    val origin: SignalOrigin,
    val title: String,
    val zoneId: String?,
    val startAt: Long,
    val endAt: Long? = null,
    val intensity: Double,
    val confidence: Double,
    val sourceName: String,
    val sourceUrl: String? = null,
    val fetchedAt: Long,
    val venueId: String? = null,
    val capacity: Int? = null,
) {
    val isValid: Boolean
        get() = id.isNotBlank() && title.isNotBlank() && startAt > 0 &&
            intensity.isFinite() && intensity in 0.0..1.0 &&
            confidence.isFinite() && confidence in 0.0..1.0 &&
            (endAt == null || endAt >= startAt)
}

/** Aporte de una señal concreta al índice de una zona/franja. */
data class Contribution(
    val label: String,
    val points: Double,
    val confidence: Double,
    val origin: SignalOrigin,
    val signalId: String? = null,
    val sourceName: String? = null,
)

enum class DemandLevel(val label: String) { BAJA("Baja"), MEDIA("Media"), ALTA("Alta"), MUY_ALTA("Muy alta") }

/** Índice relativo 0–100 de una zona en una franja. No es probabilidad de viaje ni promesa de ganancia. */
data class ZoneWindow(
    val zoneId: String,
    val zoneName: String,
    val slotStart: Long,
    val slotEnd: Long,
    val index: Int,
    val confidence: Double,
    val contributions: List<Contribution>,
) {
    val level: DemandLevel
        get() = when {
            index >= 80 -> DemandLevel.MUY_ALTA
            index >= 60 -> DemandLevel.ALTA
            index >= 35 -> DemandLevel.MEDIA
            else -> DemandLevel.BAJA
        }

    /** Motivo principal, excluyendo el patrón general si hay algo más concreto. */
    val topReason: Contribution?
        get() = contributions.filter { it.points > 0 }.sortedByDescending { it.points }
            .let { list -> list.firstOrNull { it.origin != SignalOrigin.PATRON_GENERAL } ?: list.firstOrNull() }
}

data class RadarForecast(
    val cityId: String,
    val generatedAt: Long,
    val slotMinutes: Int,
    val windows: List<ZoneWindow>,
    val signalsUsed: List<DemandSignal>,
    val warnings: List<String>,
) {
    fun slotStarts(): List<Long> = windows.map { it.slotStart }.distinct().sorted()

    fun ranking(slotStart: Long): List<ZoneWindow> =
        windows.filter { it.slotStart == slotStart }.sortedWith(compareByDescending<ZoneWindow> { it.index }.thenByDescending { it.confidence })

    fun now(): List<ZoneWindow> = slotStarts().firstOrNull()?.let(::ranking).orEmpty()

    fun at(zoneId: String, time: Long): ZoneWindow? =
        windows.firstOrNull { it.zoneId == zoneId && time >= it.slotStart && time < it.slotEnd }

    /** Mejor zona por franja, para la línea de tiempo de las próximas horas. */
    fun bestPerSlot(): List<ZoneWindow> = slotStarts().mapNotNull { ranking(it).firstOrNull() }

    /** Picos concretos (no patrón general) ordenados por hora. */
    fun upcomingPeaks(minIndex: Int = 45): List<ZoneWindow> =
        windows.filter { w -> w.index >= minIndex && w.contributions.any { it.origin != SignalOrigin.PATRON_GENERAL && it.points >= 10 } }
            .groupBy { it.zoneId to (it.topReason?.signalId ?: it.topReason?.label) }
            .values.mapNotNull { group -> group.maxByOrNull { it.index } }
            .sortedBy { it.slotStart }
}
