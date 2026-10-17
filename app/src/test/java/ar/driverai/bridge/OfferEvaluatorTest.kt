package ar.driverai.bridge

import ar.driverai.bridge.engine.*
import org.junit.Assert.*
import org.junit.Test

class OfferEvaluatorTest {
    private fun offer(fare: Double?, pkm: Double?, pmin: Double?, tkm: Double?, tmin: Double?) =
        OfferData(Reading.manual(fare), Reading.manual(pkm), Reading.manual(pmin), Reading.manual(tkm), Reading.manual(tmin))

    @Test fun ejemploDelEnunciado_brutoPorKmYHora() {
        // $8.000, 10 km totales, 25 min totales → $800/km y $19.200/h
        val e = OfferEvaluator.evaluate(offer(8000.0, 2.0, 5.0, 8.0, 20.0), DriverSettings(minGrossPerKm = 700.0, minGrossPerHour = 15000.0))
        assertEquals(10.0, e.metrics.totalKm!!, 1e-9)
        assertEquals(25.0, e.metrics.totalMin!!, 1e-9)
        assertEquals(800.0, e.metrics.grossPerKm!!, 1e-9)
        assertEquals(19200.0, e.metrics.grossPerHour!!, 1e-9)
        assertEquals(Verdict.CONVIENE, e.verdict)
        assertNull("sin costos no hay ganancia estimada", e.metrics.estimatedProfitBeforeFixed)
        assertTrue(e.reasons.any { it.kind == ReasonKind.INFO && it.text.contains("BRUTO") })
    }

    @Test fun desfavorable_porKm() {
        val e = OfferEvaluator.evaluate(offer(2500.0, 3.0, 10.0, 6.0, 18.0), DriverSettings(minGrossPerKm = 700.0))
        assertEquals(Verdict.NO_CONVIENE, e.verdict)
        assertTrue(e.reasons.first().kind == ReasonKind.FALLA)
    }

    @Test fun limiteExacto_pasa() {
        val e = OfferEvaluator.evaluate(offer(7000.0, 0.0, 0.0, 10.0, 30.0), DriverSettings(minFare = 7000.0, minGrossPerKm = 700.0, minGrossPerHour = 14000.0))
        assertEquals(Verdict.CONVIENE, e.verdict)
    }

    @Test fun limiteJustoDebajo_falla() {
        val e = OfferEvaluator.evaluate(offer(6999.99, 0.0, 0.0, 10.0, 30.0), DriverSettings(minFare = 7000.0))
        assertEquals(Verdict.NO_CONVIENE, e.verdict)
    }

    @Test fun sinUmbrales_revisar() {
        val e = OfferEvaluator.evaluate(offer(8000.0, 1.0, 3.0, 8.0, 20.0), DriverSettings())
        assertEquals(Verdict.REVISAR, e.verdict)
        assertTrue(e.reasons.first().text.contains("umbral"))
    }

    @Test fun sinRecogida_paseNoAlcanza_revisar() {
        val e = OfferEvaluator.evaluate(offer(8000.0, null, null, 8.0, 20.0), DriverSettings(minGrossPerKm = 500.0))
        assertEquals(Verdict.REVISAR, e.verdict)
    }

    @Test fun sinRecogida_fallaEsConcluyente() {
        val e = OfferEvaluator.evaluate(offer(2000.0, null, null, 8.0, 20.0), DriverSettings(minGrossPerKm = 500.0))
        assertEquals(Verdict.NO_CONVIENE, e.verdict)
    }

    @Test fun recogidaPorDefecto_seUsaYSeAvisa() {
        val e = OfferEvaluator.evaluate(offer(8000.0, null, null, 8.0, 20.0), DriverSettings(minGrossPerKm = 500.0, defaultPickupKm = 2.0, defaultPickupMin = 5.0))
        assertEquals(Verdict.CONVIENE, e.verdict)
        assertTrue(e.metrics.pickupAssumed)
        assertEquals(10.0, e.metrics.totalKm!!, 1e-9)
    }

    @Test fun sinTarifa_revisar() {
        val e = OfferEvaluator.evaluate(offer(null, 1.0, 3.0, 8.0, 20.0), DriverSettings(minGrossPerKm = 500.0))
        assertEquals(Verdict.REVISAR, e.verdict)
    }

    @Test fun valoresInvalidos_revisar() {
        for (bad in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, 0.0)) {
            val e = OfferEvaluator.evaluate(offer(8000.0, 1.0, 3.0, bad, 20.0), DriverSettings(minGrossPerKm = 1.0))
            assertEquals("km=$bad", Verdict.REVISAR, e.verdict)
            assertTrue(e.reasons.any { it.text.contains("inválido") })
        }
        val e2 = OfferEvaluator.evaluate(offer(-5.0, 1.0, 3.0, 8.0, 20.0), DriverSettings(minFare = 1.0))
        assertEquals(Verdict.REVISAR, e2.verdict)
    }

    @Test fun bajaConfianza_noSeUsaYRevisar() {
        val o = OfferData(Reading(8000.0, 0.5, DataSource.OCR), Reading(1.0, 0.95, DataSource.OCR), Reading(3.0, 0.95, DataSource.OCR),
            Reading(8.0, 0.95, DataSource.OCR), Reading(20.0, 0.95, DataSource.OCR))
        val e = OfferEvaluator.evaluate(o, DriverSettings(minGrossPerKm = 100.0))
        assertEquals(Verdict.REVISAR, e.verdict)
        assertNull(e.metrics.fare)
        assertTrue(e.reasons.any { it.text.contains("suficiente confianza") })
        // Con tolerancia flexible (50 %) sí se acepta
        assertEquals(Verdict.CONVIENE, OfferEvaluator.evaluate(o, DriverSettings(minGrossPerKm = 100.0, tolerance = UncertaintyTolerance.FLEXIBLE)).verdict)
    }

    @Test fun manualNoSeFiltraPorConfianza() {
        val e = OfferEvaluator.evaluate(offer(8000.0, 1.0, 3.0, 8.0, 20.0), DriverSettings(minGrossPerKm = 100.0, tolerance = UncertaintyTolerance.ESTRICTA))
        assertEquals(Verdict.CONVIENE, e.verdict)
    }

    @Test fun costosYMargen() {
        // 10 km totales, costo 100/km + combustible 8 l/100km * 1500 = 120/km → 220/km → 2200
        val s = DriverSettings(costPerKm = 100.0, fuelLitersPer100Km = 8.0, fuelPricePerLiter = 1500.0, fuelPriceUpdatedAt = 0L, minMarginPct = 70.0)
        val e = OfferEvaluator.evaluate(offer(8000.0, 2.0, 5.0, 8.0, 20.0), s, nowMillis = 1000L)
        assertEquals(2200.0, e.metrics.variableCost!!, 1e-9)
        assertEquals(5800.0, e.metrics.estimatedProfitBeforeFixed!!, 1e-9)
        assertEquals(72.5, e.metrics.marginPct!!, 1e-9)
        assertEquals(Verdict.CONVIENE, e.verdict)
        val e2 = OfferEvaluator.evaluate(offer(8000.0, 2.0, 5.0, 8.0, 20.0), s.copy(minMarginPct = 75.0), nowMillis = 1000L)
        assertEquals(Verdict.NO_CONVIENE, e2.verdict)
    }

    @Test fun margenSinCostos_revisar() {
        val e = OfferEvaluator.evaluate(offer(8000.0, 2.0, 5.0, 8.0, 20.0), DriverSettings(minMarginPct = 50.0))
        assertEquals(Verdict.REVISAR, e.verdict)
    }

    @Test fun combustibleDesactualizado_avisa() {
        val day = 86_400_000L
        val s = DriverSettings(minGrossPerKm = 1.0, fuelLitersPer100Km = 8.0, fuelPricePerLiter = 1500.0, fuelPriceUpdatedAt = 0L)
        val e = OfferEvaluator.evaluate(offer(8000.0, 2.0, 5.0, 8.0, 20.0), s, nowMillis = 45 * day)
        assertTrue(e.reasons.any { it.text.contains("desactualizado") })
        val e2 = OfferEvaluator.evaluate(offer(8000.0, 2.0, 5.0, 8.0, 20.0), s, nowMillis = 5 * day)
        assertFalse(e2.reasons.any { it.text.contains("desactualizado") })
    }

    @Test fun retornoVacio() {
        val e = OfferEvaluator.evaluate(offer(8000.0, 2.0, 5.0, 8.0, 20.0), DriverSettings(minGrossPerKm = 1.0, emptyReturnFraction = 0.5))
        assertEquals(4.0, e.metrics.returnKm!!, 1e-9)
        assertEquals(14.0, e.metrics.totalKm!!, 1e-9)
        assertEquals(35.0, e.metrics.totalMin!!, 1e-9)
    }

    @Test fun sinMinutos_porHoraDesconocido_revisar() {
        val e = OfferEvaluator.evaluate(offer(8000.0, 2.0, 5.0, 8.0, null), DriverSettings(minGrossPerHour = 10000.0))
        assertEquals(Verdict.REVISAR, e.verdict)
        assertNull(e.metrics.grossPerHour)
    }

    @Test fun formatos() {
        assertEquals("$8.000", Fmt.ars(8000.0))
        assertEquals("$19.200", Fmt.ars(19200.0))
        assertEquals("$1.234.568", Fmt.ars(1234567.6))
        assertEquals("-$500", Fmt.ars(-500.0))
        assertEquals("9,3", Fmt.num(9.3))
        assertEquals("10", Fmt.num(10.0))
    }
}
