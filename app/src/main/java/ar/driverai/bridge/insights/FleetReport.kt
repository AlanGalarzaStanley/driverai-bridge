package ar.driverai.bridge.insights

import ar.driverai.bridge.data.HistoryEntry
import ar.driverai.bridge.data.MaintenanceRecord
import ar.driverai.bridge.data.MoneyEntry
import ar.driverai.bridge.data.MoneyKind
import ar.driverai.bridge.data.OdometerEntry
import ar.driverai.bridge.engine.DataSource
import ar.driverai.bridge.engine.Fmt
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Resumen semanal para el encargado de una flota piloto. Lo genera el teléfono y el conductor lo
 * envía a mano (WhatsApp, mail). Sin sincronización automática: solo agregados, sin direcciones,
 * nombres de pasajeros, capturas ni ubicación.
 */
object FleetReport {

    data class Week(
        val weekStart: LocalDate,
        val fleet: String,
        val driver: String,
        val vehicle: String,
        val offers: Int,
        val accepted: Int,
        val convenient: Int,
        /** Aceptadas que DriverAI marcaba como CONVIENE: mide si la recomendación se sigue. */
        val acceptedConvenient: Int,
        /** Aceptadas que DriverAI marcaba como NO CONVIENE. */
        val acceptedAgainst: Int,
        val readAutomatic: Int,
        val readOcr: Int,
        val readManual: Int,
        val avgGrossPerHourAccepted: Double?,
        val avgGrossPerKmAccepted: Double?,
        val incomeArs: Double,
        val expenseArs: Double,
        val odometerKm: Double,
        val pendingMaintenance: List<String>,
    ) {
        val netArs: Double get() = incomeArs - expenseArs
        val adherencePct: Int? get() = if (accepted > 0) (acceptedConvenient * 100 / accepted) else null
    }

    fun week(
        history: List<HistoryEntry>,
        money: List<MoneyEntry>,
        odometer: List<OdometerEntry>,
        maintenance: List<MaintenanceRecord>,
        fleet: String?,
        driver: String?,
        vehicle: String?,
        tz: ZoneId,
        today: LocalDate = LocalDate.now(tz),
        weeksAgo: Int = 0,
    ): Week {
        val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(weeksAgo.toLong())
        val end = start.plusWeeks(1)
        fun inWeek(at: Long): Boolean = Instant.ofEpochMilli(at).atZone(tz).toLocalDate().let { !it.isBefore(start) && it.isBefore(end) }
        val h = history.filter { inWeek(it.at) }
        val acc = h.filter(Insights::isAccepted)
        val m = money.filter { inWeek(it.at) }
        val odo = odometer.filter { e -> runCatching { LocalDate.parse(e.localDate) }.getOrNull()?.let { !it.isBefore(start) && it.isBefore(end) } == true }
        fun conv(e: HistoryEntry) = e.verdict.equals("CONVIENE", true)
        fun against(e: HistoryEntry) = e.verdict.equals("NO_CONVIENE", true) || e.verdict.equals("NO CONVIENE", true)
        val lastKm = odometer.maxByOrNull { it.localDate }?.endKm
        return Week(
            weekStart = start,
            fleet = fleet?.trim().orEmpty().ifBlank { "Sin flota" },
            driver = driver?.trim().orEmpty().ifBlank { "Sin alias" },
            vehicle = vehicle?.trim().orEmpty().ifBlank { "Sin vehículo" },
            offers = h.size,
            accepted = acc.size,
            convenient = h.count(::conv),
            acceptedConvenient = acc.count(::conv),
            acceptedAgainst = acc.count(::against),
            readAutomatic = h.count { it.source == DataSource.ACCESSIBILITY },
            readOcr = h.count { it.source == DataSource.OCR },
            readManual = h.count { it.source == DataSource.MANUAL },
            avgGrossPerHourAccepted = acc.mapNotNull { it.grossPerHour?.takeIf { v -> v.isFinite() && v > 0 } }.takeIf { it.isNotEmpty() }?.average(),
            avgGrossPerKmAccepted = acc.mapNotNull { it.grossPerKm?.takeIf { v -> v.isFinite() && v > 0 } }.takeIf { it.isNotEmpty() }?.average(),
            incomeArs = m.filter { it.kind == MoneyKind.INCOME }.sumOf { it.amountArs },
            expenseArs = m.filter { it.kind == MoneyKind.EXPENSE }.sumOf { it.amountArs },
            odometerKm = odo.sumOf { it.drivenKm.coerceAtLeast(0.0) },
            pendingMaintenance = maintenance.filter { it.completedAt == null }
                .filter { r -> (r.dueOdometerKm != null && lastKm != null && r.dueOdometerKm - lastKm <= 500) || (r.dueDate != null && runCatching { !LocalDate.parse(r.dueDate).isAfter(today.plusDays(7)) }.getOrDefault(false)) }
                .map { it.title.take(40) },
        )
    }

    fun text(w: Week): String = buildString {
        appendLine("DriverAI · resumen semanal")
        appendLine("Flota: ${w.fleet} · Conductor: ${w.driver} · Vehículo: ${w.vehicle}")
        appendLine("Semana del ${w.weekStart}")
        appendLine("Ingresos anotados: ${Fmt.ars(w.incomeArs)} · Gastos: ${Fmt.ars(w.expenseArs)} · Neto: ${Fmt.ars(w.netArs)}")
        appendLine("Km odómetro: ${Fmt.num(w.odometerKm, 0)}")
        appendLine("Ofertas evaluadas: ${w.offers} · Aceptadas: ${w.accepted} · Convenían: ${w.convenient}")
        w.adherencePct?.let { appendLine("Aceptadas que DriverAI recomendaba: $it % · aceptadas que no convenían: ${w.acceptedAgainst}") }
        w.avgGrossPerHourAccepted?.let { appendLine("Bruto medio de lo aceptado: ${Fmt.ars(it)}/h" + (w.avgGrossPerKmAccepted?.let { k -> " · ${Fmt.ars(k)}/km" } ?: "")) }
        appendLine("Lectura: ${w.readAutomatic} automáticas · ${w.readOcr} por captura · ${w.readManual} a mano")
        if (w.pendingMaintenance.isNotEmpty()) appendLine("Service próximo/vencido: ${w.pendingMaintenance.joinToString(", ")}")
        appendLine()
        appendLine("Fila para planilla:")
        appendLine(CSV_HEADER)
        append(csvRow(w))
    }

    const val CSV_HEADER = "semana,flota,conductor,vehiculo,ingresos,gastos,neto,km,ofertas,aceptadas,convenian,adhesion_pct,aceptadas_no_convenian,bruto_h,bruto_km,lectura_auto,lectura_ocr,lectura_manual,service_pendiente"

    fun csvRow(w: Week): String = listOf(
        w.weekStart, w.fleet, w.driver, w.vehicle,
        "%.0f".format(java.util.Locale.ROOT, w.incomeArs), "%.0f".format(java.util.Locale.ROOT, w.expenseArs), "%.0f".format(java.util.Locale.ROOT, w.netArs),
        "%.0f".format(java.util.Locale.ROOT, w.odometerKm), w.offers, w.accepted, w.convenient, w.adherencePct ?: "", w.acceptedAgainst,
        w.avgGrossPerHourAccepted?.let { "%.0f".format(java.util.Locale.ROOT, it) } ?: "", w.avgGrossPerKmAccepted?.let { "%.0f".format(java.util.Locale.ROOT, it) } ?: "",
        w.readAutomatic, w.readOcr, w.readManual, w.pendingMaintenance.joinToString(" / "),
    ).joinToString(",") { Insights.csvCell(it) }
}
