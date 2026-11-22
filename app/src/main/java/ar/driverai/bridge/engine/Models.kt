package ar.driverai.bridge.engine

import kotlinx.serialization.Serializable

/** Origen de un dato de la oferta. */
@Serializable
enum class DataSource { ACCESSIBILITY, OCR, MANUAL, DEFAULT_SETTING }

/**
 * Un valor leído con su nivel de confianza (0.0 a 1.0).
 * value == null significa "no se pudo leer"; nunca se rellena con valores inventados.
 */
@Serializable
data class Reading(
    val value: Double? = null,
    val confidence: Double = 0.0,
    val source: DataSource = DataSource.MANUAL,
    val raw: String? = null,
) {
    val isPresent: Boolean get() = value != null

    companion object {
        val MISSING = Reading()
        fun manual(v: Double?) = if (v == null) MISSING else Reading(v, 1.0, DataSource.MANUAL)
    }
}

/** Datos de una oferta de viaje. Distancias en km, tiempos en minutos, importes en ARS. */
@Serializable
data class OfferData(
    val fare: Reading = Reading.MISSING,
    val pickupKm: Reading = Reading.MISSING,
    val pickupMin: Reading = Reading.MISSING,
    val tripKm: Reading = Reading.MISSING,
    val tripMin: Reading = Reading.MISSING,
    val product: String? = null,
)

/** Exigencia de confianza mínima para aceptar datos leídos automáticamente. */
@Serializable
enum class UncertaintyTolerance(val minConfidence: Double, val label: String) {
    ESTRICTA(0.85, "Estricta"),
    NORMAL(0.70, "Normal"),
    FLEXIBLE(0.50, "Flexible"),
}

/**
 * Umbrales configurados por el conductor. Todos los importes son opcionales:
 * la app no trae tarifas ni costos "inventados"; si el conductor no los carga, no se usan.
 */
@Serializable
data class DriverSettings(
    val minFare: Double? = null,
    val minGrossPerKm: Double? = null,
    val minGrossPerHour: Double? = null,
    val costPerKm: Double? = null,
    val fuelLitersPer100Km: Double? = null,
    val fuelPricePerLiter: Double? = null,
    val fuelPriceUpdatedAt: Long? = null,
    val defaultPickupKm: Double? = null,
    val defaultPickupMin: Double? = null,
    /** Fracción del viaje que se estima volver vacío (0.0 = no se cuenta retorno, 1.0 = vuelta completa). */
    val emptyReturnFraction: Double = 0.0,
    /** Margen mínimo (0..100 %) = (tarifa - costo variable) / tarifa. */
    val minMarginPct: Double? = null,
    val tolerance: UncertaintyTolerance = UncertaintyTolerance.NORMAL,
    // Preferencias de la app (no afectan el cálculo)
    val voiceEnabled: Boolean = true,
    val voiceVolume: Float = 0.8f,
    val speakVerdicts: Boolean = false,
    val historyEnabled: Boolean = true,
    val homeZone: String? = null,
    val currentZone: String? = null,
    val overlayCompact: Boolean = false,
) {
    val hasAnyThreshold: Boolean
        get() = minFare != null || minGrossPerKm != null || minGrossPerHour != null || minMarginPct != null

    /** Costo variable por km combinando costo propio + combustible, si están configurados. */
    val effectiveCostPerKm: Double?
        get() {
            val fuel = if (fuelLitersPer100Km != null && fuelPricePerLiter != null)
                fuelLitersPer100Km * fuelPricePerLiter / 100.0 else null
            return when {
                costPerKm == null && fuel == null -> null
                else -> (costPerKm ?: 0.0) + (fuel ?: 0.0)
            }
        }
}

enum class Verdict(val label: String) { CONVIENE("CONVIENE"), NO_CONVIENE("NO CONVIENE"), REVISAR("REVISAR") }

enum class ReasonKind { PASA, FALLA, AVISO, INFO }

data class Reason(val kind: ReasonKind, val text: String)

data class Metrics(
    val fare: Double?,
    val pickupKm: Double?,
    val tripKm: Double?,
    val returnKm: Double?,
    val totalKm: Double?,
    val pickupMin: Double?,
    val tripMin: Double?,
    val returnMin: Double?,
    val totalMin: Double?,
    val grossPerKm: Double?,
    val grossPerHour: Double?,
    val variableCost: Double?,
    /** Ganancia estimada ANTES de costos fijos e impuestos. No es ganancia neta real. */
    val estimatedProfitBeforeFixed: Double?,
    val marginPct: Double?,
    val pickupAssumed: Boolean,
)

data class Evaluation(
    val verdict: Verdict,
    val metrics: Metrics,
    val reasons: List<Reason>,
    val offer: OfferData,
    val fareAlternative: FareAlternative? = null,
) {
    val headline: String get() = reasons.firstOrNull { it.kind == ReasonKind.FALLA }?.text
        ?: reasons.firstOrNull { it.kind == ReasonKind.AVISO }?.text
        ?: reasons.firstOrNull()?.text ?: ""
}

/** Tarifa piso calculada para alcanzar los umbrales personales con el mismo recorrido. */
data class FareAlternative(
    val minimumFare: Double,
    val pickupAssumed: Boolean,
    val emptyReturnFraction: Double,
    val assumedPickupKm: Double? = null,
    val assumedPickupMin: Double? = null,
)
