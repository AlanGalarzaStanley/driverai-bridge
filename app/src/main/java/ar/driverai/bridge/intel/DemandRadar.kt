package ar.driverai.bridge.intel

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Radar de demanda: convierte señales con fuente en un índice relativo 0–100 por zona y franja.
 *
 * Reglas:
 * - Determinístico y explicable: cada punto proviene de una [Contribution] con origen y confianza.
 * - El patrón general por horario es una hipótesis de partida (confianza baja) y se marca como tal.
 * - Las señales externas modelan cuándo se LIBERA público (salida del estadio, arribo + migraciones),
 *   no solo cuándo empieza el hecho.
 * - La confianza se calcula aparte del índice; un índice alto con confianza baja debe leerse con cautela.
 */
object DemandRadar {

    const val DEFAULT_SLOT_MINUTES = 30
    const val DEFAULT_HORIZON_HOURS = 6
    private const val MINUTE = 60_000L

    enum class DayType { LABORAL, VIERNES, SABADO, DOMINGO_O_FERIADO }

    fun forecast(
        city: CityPack,
        signals: List<DemandSignal>,
        now: Long,
        holidays: Set<LocalDate> = emptySet(),
        personal: PersonalPattern? = null,
        horizonHours: Int = DEFAULT_HORIZON_HOURS,
        slotMinutes: Int = DEFAULT_SLOT_MINUTES,
    ): RadarForecast {
        val tz = ZoneId.of(city.timeZone)
        val slotMs = slotMinutes * MINUTE
        val firstSlot = now - Math.floorMod(now, slotMs)
        val slots = (0 until (horizonHours * 60 / slotMinutes)).map { firstSlot + it * slotMs }
        val warnings = mutableListOf<String>()
        val valid = signals.filter { it.isValid }
        if (valid.size < signals.size) warnings += "Se descartaron ${signals.size - valid.size} señales con datos inválidos."
        val active = valid.filter { s -> impactWindow(s).let { (from, to) -> to >= firstSlot && from <= slots.last() + slotMs } }
        if (active.none { it.origin == SignalOrigin.API_PUBLICA || it.origin == SignalOrigin.CONDUCTOR }) {
            warnings += "Sin señales concretas en las próximas horas: el radar solo muestra el patrón general y tu historial."
        }

        val windows = slots.flatMap { slotStart ->
            val mid = slotStart + slotMs / 2
            val local = Instant.ofEpochMilli(mid).atZone(tz)
            val dayType = dayType(local.toLocalDate(), local.hour, holidays)
            val hour = local.hour + local.minute / 60.0
            city.zones.map { zone ->
                val contributions = mutableListOf<Contribution>()
                val base = baseline(zone.profile, dayType, hour)
                contributions += Contribution(
                    label = "Patrón general ${zoneProfileLabel(zone.profile)} · ${dayTypeLabel(dayType)}",
                    points = base,
                    confidence = BASELINE_CONFIDENCE,
                    origin = SignalOrigin.PATRON_GENERAL,
                )
                active.forEach { s -> signalPoints(s, zone, mid)?.let(contributions::add) }
                personal?.contribution(local.dayOfWeek, local.hour)?.let(contributions::add)
                val total = contributions.sumOf { it.points }
                val positive = contributions.filter { it.points > 0 }
                val weight = positive.sumOf { it.points }
                val confidence = if (weight > 0) positive.sumOf { it.points * it.confidence } / weight else BASELINE_CONFIDENCE
                ZoneWindow(
                    zoneId = zone.id,
                    zoneName = zone.name,
                    slotStart = slotStart,
                    slotEnd = slotStart + slotMs,
                    index = total.roundToInt().coerceIn(0, 100),
                    confidence = confidence.coerceIn(0.0, 1.0),
                    contributions = contributions.sortedByDescending { it.points },
                )
            }
        }
        return RadarForecast(city.id, now, slotMinutes, windows, active, warnings)
    }

    // ---------------- Patrón general ----------------

    const val BASELINE_CONFIDENCE = 0.3

    fun dayType(date: LocalDate, hour: Int, holidays: Set<LocalDate>): DayType {
        // La madrugada (0–5 h) pertenece a la noche del día anterior.
        val effective = if (hour < 6) date.minusDays(1) else date
        val isHoliday = effective in holidays
        return when {
            isHoliday && hour >= 6 -> DayType.DOMINGO_O_FERIADO
            effective.dayOfWeek == DayOfWeek.FRIDAY -> DayType.VIERNES
            effective.dayOfWeek == DayOfWeek.SATURDAY -> DayType.SABADO
            effective.dayOfWeek == DayOfWeek.SUNDAY || isHoliday -> DayType.DOMINGO_O_FERIADO
            else -> DayType.LABORAL
        }
    }

    private data class Amplitudes(val morning: Double, val evening: Double, val midday: Double, val night: Double)

    private fun amplitudes(profile: ZoneProfile): Amplitudes = when (profile) {
        ZoneProfile.CENTRO_LABORAL -> Amplitudes(morning = 26.0, evening = 28.0, midday = 18.0, night = 8.0)
        ZoneProfile.NOCTURNO -> Amplitudes(morning = 12.0, evening = 18.0, midday = 14.0, night = 30.0)
        ZoneProfile.RESIDENCIAL -> Amplitudes(morning = 20.0, evening = 16.0, midday = 10.0, night = 12.0)
        ZoneProfile.CONURBANO -> Amplitudes(morning = 18.0, evening = 18.0, midday = 8.0, night = 8.0)
        ZoneProfile.TERMINAL -> Amplitudes(morning = 6.0, evening = 6.0, midday = 6.0, night = 4.0)
    }

    private fun gauss(x: Double, mu: Double, sigma: Double) = exp(-(x - mu).pow(2) / (2 * sigma * sigma))

    /** Puntos del patrón general (0–~40). Hipótesis inicial: se recalibra con tu historial. */
    fun baseline(profile: ZoneProfile, dayType: DayType, hour: Double): Double {
        val a = amplitudes(profile)
        val workday = dayType == DayType.LABORAL || dayType == DayType.VIERNES
        val nightBoost = when (dayType) {
            DayType.VIERNES, DayType.SABADO -> 1.0
            DayType.LABORAL -> 0.35
            DayType.DOMINGO_O_FERIADO -> 0.3
        }
        // Hora "nocturna" continua: 0–5 h se tratan como 24–29 h de la noche previa.
        val nightHour = if (hour < 6) hour + 24 else hour
        val morning = if (workday) a.morning * gauss(hour, 8.0, 1.3) else a.morning * 0.25 * gauss(hour, 10.5, 2.0)
        val evening = if (workday) a.evening * gauss(hour, 18.5, 1.6) else a.evening * 0.5 * gauss(hour, 19.5, 2.2)
        val midday = a.midday * gauss(hour, 13.5, 3.0) * if (workday) 1.0 else 0.7
        val night = a.night * nightBoost * gauss(nightHour, 25.0, 2.2)
        return 4.0 + morning + evening + midday + night
    }

    private fun zoneProfileLabel(p: ZoneProfile) = when (p) {
        ZoneProfile.CENTRO_LABORAL -> "zona laboral"
        ZoneProfile.NOCTURNO -> "zona de salidas"
        ZoneProfile.RESIDENCIAL -> "zona residencial"
        ZoneProfile.CONURBANO -> "conurbano"
        ZoneProfile.TERMINAL -> "terminal (depende de arribos)"
    }

    fun dayTypeLabel(d: DayType) = when (d) {
        DayType.LABORAL -> "día laboral"
        DayType.VIERNES -> "viernes"
        DayType.SABADO -> "sábado"
        DayType.DOMINGO_O_FERIADO -> "domingo/feriado"
    }

    // ---------------- Señales ----------------

    /** Duración típica cuando la señal no trae fin explícito. */
    fun typicalDurationMin(kind: SignalKind): Long = when (kind) {
        SignalKind.EVENTO_DEPORTIVO -> 110
        SignalKind.EVENTO_SHOW -> 150
        SignalKind.VUELOS -> 0
        SignalKind.CRUCERO -> 0
        SignalKind.PARO_TRANSPORTE -> 8 * 60
        SignalKind.CLIMA -> 3 * 60
        SignalKind.FERIADO -> 24 * 60
        SignalKind.OTRO -> 120
    }

    /** Ventana [desde, hasta] en la que la señal suma demanda de viajes. */
    fun impactWindow(s: DemandSignal): Pair<Long, Long> {
        val end = s.endAt ?: (s.startAt + typicalDurationMin(s.kind) * MINUTE)
        return when (s.kind) {
            // Salida del público: desde 10 min antes del final hasta ~70 min después.
            SignalKind.EVENTO_DEPORTIVO, SignalKind.EVENTO_SHOW -> (end - 10 * MINUTE) to (end + 70 * MINUTE)
            // Pasajeros salen 25–80 min después del aterrizaje (equipaje y migraciones).
            SignalKind.VUELOS -> (s.startAt + 25 * MINUTE) to (max(end, s.startAt) + 80 * MINUTE)
            // Desembarco escalonado durante varias horas.
            SignalKind.CRUCERO -> (s.startAt + 60 * MINUTE) to (max(end, s.startAt) + 300 * MINUTE)
            else -> s.startAt to end
        }
    }

    private fun signalPoints(s: DemandSignal, zone: CityZone, at: Long): Contribution? {
        if (s.kind == SignalKind.FERIADO) return null // el feriado cambia el patrón, no suma puntos directos
        val (from, to) = impactWindow(s)
        if (at < from || at > to) return null
        val citywide = s.zoneId == null
        if (!citywide && s.zoneId != zone.id) return null
        val i = s.intensity.coerceIn(0.0, 1.0)
        val peakShape = when (s.kind) {
            SignalKind.EVENTO_DEPORTIVO, SignalKind.EVENTO_SHOW, SignalKind.VUELOS -> triangular(at, from, to, peakFraction = 0.3)
            SignalKind.CRUCERO -> triangular(at, from, to, peakFraction = 0.25)
            else -> 1.0
        }
        val maxPoints = when (s.kind) {
            SignalKind.EVENTO_DEPORTIVO -> 20 + 55 * i
            SignalKind.EVENTO_SHOW -> 15 + 45 * i
            SignalKind.VUELOS -> 10 + 35 * i
            SignalKind.CRUCERO -> 12 + 28 * i
            SignalKind.PARO_TRANSPORTE -> 15 + 25 * i
            SignalKind.CLIMA -> 5 + 15 * i
            SignalKind.OTRO -> 8 + 30 * i
            SignalKind.FERIADO -> 0.0
        }
        val points = maxPoints * peakShape
        if (points < 0.5) return null
        val what = when (s.kind) {
            SignalKind.EVENTO_DEPORTIVO, SignalKind.EVENTO_SHOW -> "salida de ${s.title}"
            SignalKind.VUELOS -> "arribos: ${s.title}"
            SignalKind.CRUCERO -> "desembarco: ${s.title}"
            else -> s.title
        }
        return Contribution(
            label = what + if (citywide) " (toda la ciudad)" else "",
            points = points,
            confidence = s.confidence,
            origin = s.origin,
            signalId = s.id,
            sourceName = s.sourceName,
        )
    }

    /** Forma de pico: sube hasta peakFraction del intervalo y luego decae linealmente. */
    private fun triangular(at: Long, from: Long, to: Long, peakFraction: Double): Double {
        if (to <= from) return 1.0
        val x = (at - from).toDouble() / (to - from)
        return if (x <= peakFraction) 0.45 + 0.55 * (x / peakFraction) else max(0.0, 1.0 - (x - peakFraction) / (1.0 - peakFraction))
    }

    /** Intensidad 0..1 a partir de la capacidad del recinto (85.000 ≈ 1). */
    fun intensityForCapacity(capacity: Int?): Double = min(1.0, max(0.1, (capacity ?: 10_000) / 85_000.0))
}

/**
 * Patrón personal derivado de TU historial local de ofertas (no de otros conductores).
 * Compara el bruto/hora de las ofertas observadas a cada hora con tu promedio general.
 */
data class PersonalPattern(
    val overallGrossPerHour: Double,
    /** Clave = hora del día (0–23). */
    val byHour: Map<Int, HourStat>,
    val totalSamples: Int,
) {
    data class HourStat(val samples: Int, val meanGrossPerHour: Double, val offersPerDay: Double)

    fun contribution(@Suppress("UNUSED_PARAMETER") day: DayOfWeek, hour: Int): Contribution? {
        val stat = byHour[hour] ?: return null
        if (stat.samples < MIN_SAMPLES || overallGrossPerHour <= 0) return null
        val ratio = stat.meanGrossPerHour / overallGrossPerHour - 1.0
        val points = (ratio * 30.0).coerceIn(-10.0, 15.0)
        if (kotlin.math.abs(points) < 1.0) return null
        val pct = (ratio * 100).roundToInt()
        return Contribution(
            label = "Tu historial a las $hour h: bruto/h ${if (pct >= 0) "+" else ""}$pct % vs tu promedio (${stat.samples} ofertas)",
            points = points,
            confidence = min(0.75, 0.35 + stat.samples / 100.0),
            origin = SignalOrigin.TU_HISTORIAL,
        )
    }

    companion object {
        const val MIN_SAMPLES = 5
    }
}
