package ar.driverai.bridge.engine

import kotlin.math.roundToLong

/**
 * Motor económico determinístico. No usa modelos de lenguaje: mismas entradas, mismo resultado.
 *
 * Principios:
 * - Un dato faltante o leído con baja confianza NO se inventa: se descarta y se avisa.
 * - Si falta la recogida, las métricas son optimistas (cotas superiores). Por eso un umbral
 *   que FALLA igual es concluyente, pero uno que PASA no alcanza para decir CONVIENE → REVISAR.
 * - Los umbrales se cumplen con igualdad exacta (>=).
 */
object OfferEvaluator {

    const val FUEL_PRICE_STALE_DAYS = 30L
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun evaluate(offer: OfferData, s: DriverSettings, nowMillis: Long = System.currentTimeMillis()): Evaluation {
        val reasons = mutableListOf<Reason>()
        var invalid = false
        var lowConfidence = false
        val minConf = s.tolerance.minConfidence

        fun usable(label: String, r: Reading, mustBePositive: Boolean): Double? {
            val v = r.value ?: return null
            if (v.isNaN() || v.isInfinite() || v < 0 || (mustBePositive && v == 0.0)) {
                invalid = true
                reasons += Reason(ReasonKind.AVISO, "Valor inválido en $label (${r.raw ?: Fmt.num(v)}).")
                return null
            }
            val automatic = r.source == DataSource.ACCESSIBILITY || r.source == DataSource.OCR
            if (automatic && r.confidence < minConf) {
                lowConfidence = true
                reasons += Reason(
                    ReasonKind.AVISO,
                    "No se pudo leer $label con suficiente confianza (${(r.confidence * 100).roundToLong()} %). Confirmalo a mano."
                )
                return null
            }
            return v
        }

        val fare = usable("la tarifa", offer.fare, mustBePositive = true)
        val tripKm = usable("la distancia del viaje", offer.tripKm, mustBePositive = true)
        val tripMin = usable("el tiempo del viaje", offer.tripMin, mustBePositive = true)
        var pickupKm = usable("la distancia a la recogida", offer.pickupKm, mustBePositive = false)
        var pickupMin = usable("el tiempo a la recogida", offer.pickupMin, mustBePositive = false)

        var pickupAssumed = false
        var pickupUnknown = false
        if (pickupKm == null || pickupMin == null) {
            if (s.defaultPickupKm != null && s.defaultPickupMin != null) {
                pickupKm = pickupKm ?: s.defaultPickupKm
                pickupMin = pickupMin ?: s.defaultPickupMin
                pickupAssumed = true
                reasons += Reason(ReasonKind.AVISO, "Recogida no leída: se usó tu estimación de ajustes (${Fmt.num(s.defaultPickupKm)} km, ${Fmt.num(s.defaultPickupMin)} min).")
            } else {
                pickupUnknown = true
                reasons += Reason(ReasonKind.AVISO, "Sin datos de recogida: los números son optimistas.")
            }
        }

        val frac = s.emptyReturnFraction.coerceIn(0.0, 1.0)
        val returnKm = tripKm?.let { it * frac }
        val returnMin = tripMin?.let { it * frac }
        val totalKm = tripKm?.let { (pickupKm ?: 0.0) + it + (returnKm ?: 0.0) }
        val totalMin = tripMin?.let { (pickupMin ?: 0.0) + it + (returnMin ?: 0.0) }
        val grossPerKm = if (fare != null && totalKm != null && totalKm > 0) fare / totalKm else null
        val grossPerHour = if (fare != null && totalMin != null && totalMin > 0) fare / (totalMin / 60.0) else null
        val costKm = s.effectiveCostPerKm
        val variableCost = if (costKm != null && totalKm != null) costKm * totalKm else null
        val profit = if (fare != null && variableCost != null) fare - variableCost else null
        val margin = if (profit != null && fare != null && fare > 0) profit / fare * 100.0 else null

        val metrics = Metrics(
            fare, pickupKm, tripKm, returnKm, totalKm, pickupMin, tripMin, returnMin, totalMin,
            grossPerKm, grossPerHour, variableCost, profit, margin, pickupAssumed,
        )

        var anyFail = false
        var anyUnknown = false
        fun check(label: String, actual: Double?, min: Double?, fmt: (Double) -> String, missingWhy: String) {
            if (min == null) return
            if (actual == null) {
                anyUnknown = true
                reasons += Reason(ReasonKind.AVISO, "$label: no se puede calcular ($missingWhy).")
            } else if (actual >= min) {
                reasons += Reason(ReasonKind.PASA, "$label ${fmt(actual)} ≥ mínimo ${fmt(min)}.")
            } else {
                anyFail = true
                reasons += Reason(ReasonKind.FALLA, "$label ${fmt(actual)} < mínimo ${fmt(min)}.")
            }
        }

        check("Tarifa", fare, s.minFare, Fmt::ars, "falta la tarifa")
        check("Bruto por km", grossPerKm, s.minGrossPerKm, { Fmt.ars(it) + "/km" }, "faltan tarifa o km")
        check("Bruto por hora", grossPerHour, s.minGrossPerHour, { Fmt.ars(it) + "/h" }, "faltan tarifa o minutos")
        if (s.minMarginPct != null && costKm == null) {
            anyUnknown = true
            reasons += Reason(ReasonKind.AVISO, "Margen: configurá costo por km o combustible para calcularlo.")
        } else {
            check("Margen", margin, s.minMarginPct, { Fmt.num(it) + " %" }, "faltan tarifa o km")
        }

        if (s.fuelPricePerLiter != null) {
            val updated = s.fuelPriceUpdatedAt
            if (updated == null) {
                reasons += Reason(ReasonKind.AVISO, "Precio de combustible sin fecha de actualización.")
            } else {
                val days = (nowMillis - updated) / DAY_MS
                if (days > FUEL_PRICE_STALE_DAYS) reasons += Reason(ReasonKind.AVISO, "Precio de combustible desactualizado (hace $days días).")
            }
        }
        if (costKm == null) {
            reasons += Reason(ReasonKind.INFO, "Ingreso BRUTO: no descuenta combustible, desgaste ni comisiones no incluidas.")
        } else if (profit != null) {
            reasons += Reason(ReasonKind.INFO, "Ganancia estimada antes de costos fijos e impuestos: ${Fmt.ars(profit)}.")
        }

        val verdict = when {
            !s.hasAnyThreshold -> {
                reasons.add(0, Reason(ReasonKind.AVISO, "Configurá al menos un umbral en Ajustes para recibir una recomendación."))
                Verdict.REVISAR
            }
            fare == null -> {
                if (!lowConfidence && !invalid) reasons.add(0, Reason(ReasonKind.AVISO, "No se pudo leer la tarifa."))
                Verdict.REVISAR
            }
            invalid -> Verdict.REVISAR
            anyFail -> Verdict.NO_CONVIENE
            anyUnknown || pickupUnknown || lowConfidence -> Verdict.REVISAR
            else -> Verdict.CONVIENE
        }

        val ordered = reasons.sortedBy {
            when (it.kind) { ReasonKind.FALLA -> 0; ReasonKind.AVISO -> 1; ReasonKind.PASA -> 2; ReasonKind.INFO -> 3 }
        }
        return Evaluation(verdict, metrics, ordered, offer)
    }
}

/** Formatos argentinos: miles con punto, decimales con coma. */
object Fmt {
    fun ars(v: Double): String {
        val rounded = v.roundToLong()
        val neg = rounded < 0
        val digits = kotlin.math.abs(rounded).toString().reversed().chunked(3).joinToString(".").reversed()
        return (if (neg) "-$" else "$") + digits
    }

    fun num(v: Double, decimals: Int = 1): String {
        val factor = Math.pow(10.0, decimals.toDouble())
        val r = Math.round(v * factor) / factor
        return if (r == Math.floor(r)) r.toLong().toString() else String.format(java.util.Locale.ROOT, "%.${decimals}f", r).replace('.', ',')
    }
}
