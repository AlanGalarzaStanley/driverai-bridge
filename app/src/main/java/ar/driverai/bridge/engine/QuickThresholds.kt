package ar.driverai.bridge.engine

import kotlin.math.ceil

/**
 * Calculadora de primeros pasos: a partir de los costos que carga el conductor sugiere un mínimo
 * por km. No trae precios precargados: todo valor sale de lo que escribe el conductor.
 *
 * Regla: el combustible suele ser un tercio del costo variable real (desgaste, aceite, cubiertas,
 * amortización), así que el piso sugerido por km es [FUEL_MULTIPLIER] veces el costo de combustible
 * por km. Es una sugerencia editable, no una tarifa.
 */
object QuickThresholds {
    const val FUEL_MULTIPLIER = 3.0

    data class Suggestion(val fuelCostPerKm: Double, val minGrossPerKm: Double, val minGrossPerHour: Double?)

    fun suggest(fuelPricePerUnit: Double?, unitsPer100Km: Double?, wantedPerHour: Double?): Suggestion? {
        if (fuelPricePerUnit == null || unitsPer100Km == null) return null
        if (!fuelPricePerUnit.isFinite() || !unitsPer100Km.isFinite() || fuelPricePerUnit <= 0 || unitsPer100Km <= 0 || unitsPer100Km > 60) return null
        val perKm = fuelPricePerUnit * unitsPer100Km / 100.0
        val min = ceil(perKm * FUEL_MULTIPLIER / 10.0) * 10.0
        return Suggestion(perKm, min, wantedPerHour?.takeIf { it.isFinite() && it > 0 })
    }
}
