package ar.driverai.bridge.engine

import kotlin.math.ceil
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
        val thresholdValues = listOf(s.minFare, s.minGrossPerKm, s.minGrossPerHour, s.minMarginPct)
        val invalidThresholds = thresholdValues.any { it != null && (!it.isFinite() || it < 0.0) } || (s.minMarginPct ?: 0.0) > 100.0
        if (invalidThresholds) {
            invalid = true
            reasons += Reason(ReasonKind.AVISO, "Hay un umbral personal inválido; corregilo en Ajustes.")
        }

        fun usable(label: String, r: Reading, mustBePositive: Boolean): Double? {
            val v = r.value ?: return null
            if (v.isNaN() || v.isInfinite() || v < 0 || (mustBePositive && v == 0.0)) {
                invalid = true
                reasons += Reason(ReasonKind.AVISO, "Valor inválido en $label (${r.raw ?: Fmt.num(v)}).")
                return null
            }
            val automatic = r.source == DataSource.ACCESSIBILITY || r.source == DataSource.OCR
            if (automatic && (!r.confidence.isFinite() || r.confidence !in 0.0..1.0)) {
                lowConfidence = true
                reasons += Reason(ReasonKind.AVISO, "No se pudo validar la confianza de lectura de $label. Confirmalo a mano.")
                return null
            }
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

        var pickupKmAssumed = false
        var pickupMinAssumed = false
        var pickupAssumed = false
        var pickupUnknown = false
        if (pickupKm == null || pickupMin == null) {
            if (pickupKm == null && s.defaultPickupKm != null) {
                if (!s.defaultPickupKm.isFinite() || s.defaultPickupKm < 0.0) {
                    invalid = true
                    reasons += Reason(ReasonKind.AVISO, "Tu estimación de km a la recogida es inválida; corregila en Ajustes.")
                } else {
                    pickupKm = s.defaultPickupKm
                    pickupKmAssumed = true
                }
            }
            if (pickupMin == null && s.defaultPickupMin != null) {
                if (!s.defaultPickupMin.isFinite() || s.defaultPickupMin < 0.0) {
                    invalid = true
                    reasons += Reason(ReasonKind.AVISO, "Tu estimación de minutos a la recogida es inválida; corregila en Ajustes.")
                } else {
                    pickupMin = s.defaultPickupMin
                    pickupMinAssumed = true
                }
            }
            pickupAssumed = pickupKmAssumed || pickupMinAssumed
            if (pickupAssumed) {
                val used = listOfNotNull(
                    if (pickupKmAssumed) "${Fmt.num(s.defaultPickupKm!!)} km" else null,
                    if (pickupMinAssumed) "${Fmt.num(s.defaultPickupMin!!)} min" else null,
                ).joinToString(" y ")
                reasons += Reason(ReasonKind.AVISO, "Recogida incompleta: se completó con tu estimación de Ajustes ($used).")
            }
            if (pickupKm == null || pickupMin == null) {
                pickupUnknown = true
                reasons += Reason(ReasonKind.AVISO, "Sin datos de recogida: los números son optimistas.")
            }
        }

        val returnFractionValid = s.emptyReturnFraction.isFinite() && s.emptyReturnFraction in 0.0..1.0
        if (!returnFractionValid) {
            invalid = true
            reasons += Reason(ReasonKind.AVISO, "La fracción de retorno vacío no es válida; corregila en Ajustes.")
        }
        val frac = if (s.emptyReturnFraction.isFinite()) s.emptyReturnFraction.coerceIn(0.0, 1.0) else 0.0
        fun finiteDerived(label: String, value: Double?): Double? {
            if (value == null) return null
            if (!value.isFinite()) {
                invalid = true
                reasons += Reason(ReasonKind.AVISO, "Resultado fuera de rango al calcular $label; revisá los datos y ajustes.")
                return null
            }
            return value
        }
        val returnKm = finiteDerived("km de retorno", tripKm?.let { it * frac })
        val returnMin = finiteDerived("minutos de retorno", tripMin?.let { it * frac })
        val totalKm = finiteDerived("km totales", tripKm?.let { (pickupKm ?: 0.0) + it + (returnKm ?: 0.0) })
        val totalMin = finiteDerived("minutos totales", tripMin?.let { (pickupMin ?: 0.0) + it + (returnMin ?: 0.0) })
        val grossPerKm = finiteDerived("bruto por km", if (fare != null && totalKm != null && totalKm > 0) fare / totalKm else null)
        val grossPerHour = finiteDerived("bruto por hora", if (fare != null && totalMin != null && totalMin > 0) fare / (totalMin / 60.0) else null)
        val costSettingsInvalid = listOf(s.costPerKm, s.fuelLitersPer100Km, s.fuelPricePerLiter)
            .any { it != null && (!it.isFinite() || it < 0.0) }
        if (costSettingsInvalid) reasons += Reason(ReasonKind.AVISO, "Hay un costo configurado inválido; corregilo en Ajustes.")
        if (costSettingsInvalid) invalid = true
        val costKm = if (costSettingsInvalid) null else s.effectiveCostPerKm
        val variableCost = finiteDerived("costo variable", if (costKm != null && totalKm != null) costKm * totalKm else null)
        val profit = finiteDerived("ganancia estimada", if (fare != null && variableCost != null) fare - variableCost else null)
        val margin = finiteDerived("margen", if (profit != null && fare != null && fare > 0) profit / fare * 100.0 else null)

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
                val ageMillis = runCatching { Math.subtractExact(nowMillis, updated) }.getOrNull()
                if (ageMillis == null || ageMillis < 0L) {
                    reasons += Reason(ReasonKind.AVISO, "Fecha del precio de combustible futura o inválida.")
                } else {
                    val days = ageMillis / DAY_MS
                    if (days > FUEL_PRICE_STALE_DAYS) reasons += Reason(ReasonKind.AVISO, "Precio de combustible desactualizado (hace $days días).")
                }
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

        val fareAlternative = calculateFareAlternative(
            settings = s,
            currentFare = fare,
            totalKm = totalKm,
            totalMin = totalMin,
            variableCost = variableCost,
            verdict = verdict,
            invalid = invalid,
            lowConfidence = lowConfidence,
            anyUnknown = anyUnknown,
            pickupUnknown = pickupUnknown,
            pickupAssumed = pickupAssumed,
            pickupKmAssumed = pickupKmAssumed,
            pickupMinAssumed = pickupMinAssumed,
            nowMillis = nowMillis,
        )

        val ordered = reasons.sortedBy {
            when (it.kind) { ReasonKind.FALLA -> 0; ReasonKind.AVISO -> 1; ReasonKind.PASA -> 2; ReasonKind.INFO -> 3 }
        }
        return Evaluation(verdict, metrics, ordered, offer, fareAlternative)
    }

    private fun calculateFareAlternative(
        settings: DriverSettings,
        currentFare: Double?,
        totalKm: Double?,
        totalMin: Double?,
        variableCost: Double?,
        verdict: Verdict,
        invalid: Boolean,
        lowConfidence: Boolean,
        anyUnknown: Boolean,
        pickupUnknown: Boolean,
        pickupAssumed: Boolean,
        pickupKmAssumed: Boolean,
        pickupMinAssumed: Boolean,
        nowMillis: Long,
    ): FareAlternative? {
        if (verdict != Verdict.NO_CONVIENE || invalid || lowConfidence || anyUnknown || currentFare == null) return null

        val routeThresholdConfigured = settings.minGrossPerKm != null ||
            settings.minGrossPerHour != null || settings.minMarginPct != null
        if (pickupUnknown && routeThresholdConfigured) return null
        if (!settings.emptyReturnFraction.isFinite()) return null

        val requiredFares = mutableListOf<Double>()
        settings.minFare?.let { requiredFares += it }
        settings.minGrossPerKm?.let { threshold ->
            requiredFares += threshold * (totalKm ?: return null)
        }
        settings.minGrossPerHour?.let { threshold ->
            requiredFares += threshold * (totalMin ?: return null) / 60.0
        }
        settings.minMarginPct?.let { threshold ->
            val cost = variableCost ?: return null
            if (!cost.isFinite() || cost < 0.0 || !threshold.isFinite() || threshold < 0.0 || threshold > 100.0) return null
            if (fuelPriceIsStale(settings, nowMillis)) return null
            if (threshold == 100.0) {
                if (cost > 0.0) return null // No finite fare can produce a 100% margin with positive costs.
            } else {
                requiredFares += cost / (1.0 - threshold / 100.0)
            }
        }

        val unroundedMinimum = requiredFares.maxOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
        val minimumFare = ceil(unroundedMinimum)
        if (!minimumFare.isFinite() || minimumFare >= Long.MAX_VALUE.toDouble() || minimumFare <= currentFare) return null
        return FareAlternative(
            minimumFare = minimumFare,
            pickupAssumed = pickupAssumed,
            emptyReturnFraction = settings.emptyReturnFraction.coerceIn(0.0, 1.0),
            assumedPickupKm = if (pickupKmAssumed) settings.defaultPickupKm else null,
            assumedPickupMin = if (pickupMinAssumed) settings.defaultPickupMin else null,
        )
    }

    private fun fuelPriceIsStale(settings: DriverSettings, nowMillis: Long): Boolean {
        if (settings.fuelLitersPer100Km == null || settings.fuelPricePerLiter == null) return false
        val updated = settings.fuelPriceUpdatedAt ?: return true
        val ageMillis = runCatching { Math.subtractExact(nowMillis, updated) }.getOrNull() ?: return true
        return ageMillis < 0L || ageMillis / DAY_MS > FUEL_PRICE_STALE_DAYS
    }
}

/** Formatos argentinos: miles con punto, decimales con coma. */
object Fmt {
    fun ars(v: Double): String {
        val maxFormatted = "9.223.372.036.854.775.807"
        if (v.isNaN()) return "—"
        if (v >= Long.MAX_VALUE.toDouble()) return ">" + '$' + maxFormatted
        if (v <= Long.MIN_VALUE.toDouble()) return "<-" + '$' + maxFormatted
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
