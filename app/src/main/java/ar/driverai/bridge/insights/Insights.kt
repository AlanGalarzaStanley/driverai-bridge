package ar.driverai.bridge.insights

import ar.driverai.bridge.data.HistoryEntry
import ar.driverai.bridge.data.MoneyEntry
import ar.driverai.bridge.data.MoneyKind
import ar.driverai.bridge.data.TripStatus
import ar.driverai.bridge.engine.RidePlatform
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Informes locales al estilo de los rastreadores de ingresos: por app, por hora, tasa de aceptación
 * e ingresos semanales. Todo sale del historial y la billetera del teléfono.
 */
object Insights {

    data class Bucket(
        val key: String,
        val offers: Int,
        val accepted: Int,
        val avgGrossPerKm: Double?,
        val avgGrossPerHour: Double?,
        val convenientPct: Double?,
    ) {
        val acceptanceRate: Double? get() = if (offers > 0) accepted.toDouble() / offers else null
    }

    data class Week(val start: LocalDate, val income: Double, val expense: Double) {
        val net: Double get() = income - expense
    }

    fun isAccepted(e: HistoryEntry): Boolean =
        e.taken || e.tripStatus in setOf(TripStatus.ACCEPTED, TripStatus.COMPLETED, TripStatus.PAID)

    private fun bucket(key: String, list: List<HistoryEntry>): Bucket {
        val perKm = list.mapNotNull { it.grossPerKm?.takeIf { v -> v.isFinite() && v > 0 } }
        val perHour = list.mapNotNull { it.grossPerHour?.takeIf { v -> v.isFinite() && v > 0 } }
        val judged = list.filter { it.verdict.isNotBlank() }
        return Bucket(
            key = key,
            offers = list.size,
            accepted = list.count(::isAccepted),
            avgGrossPerKm = perKm.takeIf { it.isNotEmpty() }?.average(),
            avgGrossPerHour = perHour.takeIf { it.isNotEmpty() }?.average(),
            convenientPct = judged.takeIf { it.isNotEmpty() }?.let { j -> j.count { it.verdict.equals("CONVIENE", true) }.toDouble() / j.size },
        )
    }

    fun byPlatform(entries: List<HistoryEntry>): List<Bucket> =
        entries.groupBy { e -> e.platform?.let { RidePlatform.fromName(it).label } ?: "Sin identificar" }
            .map { (k, v) -> bucket(k, v) }
            .sortedByDescending { it.offers }

    fun byHour(entries: List<HistoryEntry>, tz: ZoneId): List<Pair<Int, Bucket>> =
        entries.groupBy { Instant.ofEpochMilli(it.at).atZone(tz).hour }
            .map { (h, v) -> h to bucket("%02d h".format(h), v) }
            .sortedBy { it.first }

    /** Mejores horas por bruto/h con al menos [minSamples] ofertas. */
    fun bestHours(entries: List<HistoryEntry>, tz: ZoneId, minSamples: Int = 5, top: Int = 3): List<Pair<Int, Bucket>> =
        byHour(entries, tz).filter { it.second.offers >= minSamples && it.second.avgGrossPerHour != null }
            .sortedByDescending { it.second.avgGrossPerHour }
            .take(top)

    fun weekly(money: List<MoneyEntry>, tz: ZoneId, weeks: Int = 8, today: LocalDate = LocalDate.now(tz)): List<Week> {
        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (0 until weeks).map { i ->
            val start = thisMonday.minusWeeks(i.toLong())
            val end = start.plusWeeks(1)
            val inWeek = money.filter { m ->
                val d = Instant.ofEpochMilli(m.at).atZone(tz).toLocalDate()
                !d.isBefore(start) && d.isBefore(end)
            }
            Week(start, inWeek.filter { it.kind == MoneyKind.INCOME }.sumOf { it.amountArs }, inWeek.filter { it.kind == MoneyKind.EXPENSE }.sumOf { it.amountArs })
        }
    }

    private val tsFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

    internal fun csvCell(v: Any?): String {
        val s = when (v) {
            null -> ""
            is Double -> if (v.isFinite()) String.format(Locale.ROOT, "%.2f", v) else ""
            else -> v.toString()
        }
        // Evita inyección de fórmulas en planillas y escapa comillas.
        val safe = if (s.isNotEmpty() && s.first() in "=+-@" && s.toDoubleOrNull() == null) "'$s" else s
        return if (safe.any { it == ',' || it == '"' || it == '\n' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    fun historyCsv(entries: List<HistoryEntry>, tz: ZoneId): String = buildString {
        appendLine("fecha,app,veredicto,tarifa_ars,km_total,min_total,ars_km,ars_h,estado,ingreso_real_ars,zona_destino,fuente")
        entries.sortedBy { it.at }.forEach { e ->
            appendLine(
                listOf(
                    tsFmt.format(Instant.ofEpochMilli(e.at).atZone(tz)),
                    e.platform?.let { RidePlatform.fromName(it).label },
                    e.verdict, e.fare, e.totalKm, e.totalMin, e.grossPerKm, e.grossPerHour,
                    e.tripStatus.name, e.actualIncomeArs, e.destinationZoneId, e.source.name,
                ).joinToString(",") { csvCell(it) },
            )
        }
    }

    fun walletCsv(money: List<MoneyEntry>, tz: ZoneId): String = buildString {
        appendLine("fecha,tipo,categoria,monto_ars")
        money.sortedBy { it.at }.forEach { m ->
            appendLine(listOf(tsFmt.format(Instant.ofEpochMilli(m.at).atZone(tz)), m.kind.name, m.category.name, m.amountArs).joinToString(",") { csvCell(it) })
        }
    }
}
