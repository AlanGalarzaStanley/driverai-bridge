package ar.driverai.bridge

import ar.driverai.bridge.engine.*
import org.junit.Assert.*
import org.junit.Test

class OfferTextParserTest {
    private fun p(vararg f: String, src: DataSource = DataSource.ACCESSIBILITY) = OfferTextParser.parse(f.toList(), src)

    @Test fun importesArgentinos() {
        assertEquals(8540.0, OfferTextParser.parseArsAmount("8.540")!!.value, 0.0)
        assertEquals(8540.5, OfferTextParser.parseArsAmount("8.540,50")!!.value, 1e-9)
        assertEquals(1234567.0, OfferTextParser.parseArsAmount("1.234.567")!!.value, 0.0)
        assertEquals(8540.0, OfferTextParser.parseArsAmount("8540")!!.value, 0.0)
        assertEquals(12.5, OfferTextParser.parseArsAmount("12,50")!!.value, 1e-9)
        assertEquals(8540.0, OfferTextParser.parseArsAmount("8,540")!!.value, 0.0)
        val amb = OfferTextParser.parseArsAmount("12.50")!!
        assertTrue("12.50 es ambiguo", amb.confidence < 0.7)
        assertNull(OfferTextParser.parseArsAmount("abc"))
        assertNull(OfferTextParser.parseArsAmount("8..5"))
    }

    @Test fun ofertaClara_espanol() {
        val r = p("UberX", "$ 8.540", "A 4 min (1,2 km)", "Viaje de 18 min (9,3 km)", "Av. Siempre Viva 742", "Toca para aceptar")
        assertTrue(r.isLikelyOffer)
        val o = r.offer
        assertEquals(8540.0, o.fare.value!!, 0.0)
        assertEquals(1.2, o.pickupKm.value!!, 1e-9); assertEquals(4.0, o.pickupMin.value!!, 0.0)
        assertEquals(9.3, o.tripKm.value!!, 1e-9); assertEquals(18.0, o.tripMin.value!!, 0.0)
        assertTrue(o.fare.confidence >= 0.95 && o.tripKm.confidence >= 0.9 && o.pickupKm.confidence >= 0.9)
        assertEquals("UberX", o.product)
    }

    @Test fun ofertaClara_ingles_millas() {
        val o = p("UberX", "$12", "5 mins (1.6 mi) away", "12 mins (5.1 mi) trip").offer
        assertEquals(1.6 * 1.609344, o.pickupKm.value!!, 1e-6)
        assertEquals(5.1 * 1.609344, o.tripKm.value!!, 1e-6)
        assertEquals(12.0, o.tripMin.value!!, 0.0)
    }

    @Test fun horasYMinutos() {
        val o = p("$ 25.000", "A 3 min (800 m)", "Viaje de 1 h 5 min (42,7 km)").offer
        assertEquals(65.0, o.tripMin.value!!, 0.0)
        assertEquals(0.8, o.pickupKm.value!!, 1e-9)
    }

    @Test fun sinEtiquetas_porOrden_confianzaMedia() {
        val o = p("$ 8.540", "4 min (1,2 km)", "18 min (9,3 km)").offer
        assertEquals(1.2, o.pickupKm.value!!, 1e-9)
        assertEquals(9.3, o.tripKm.value!!, 1e-9)
        assertTrue(o.tripKm.confidence in 0.6..0.8)
    }

    @Test fun unSoloPar_ambiguo_bajaConfianza() {
        val o = p("$ 8.540", "18 min (9,3 km)").offer
        assertTrue(o.tripKm.confidence < 0.5)
        val e = OfferEvaluator.evaluate(o, DriverSettings(minGrossPerKm = 100.0))
        assertEquals(Verdict.REVISAR, e.verdict)
    }

    @Test fun textoParcial_soloTarifa() {
        val r = p("Comfort", "$ 12.300", "Solicitud nueva", "Toca para aceptar")
        assertTrue(r.isLikelyOffer)
        assertNull(r.offer.tripKm.value)
        assertEquals(Verdict.REVISAR, OfferEvaluator.evaluate(r.offer, DriverSettings(minGrossPerKm = 100.0)).verdict)
    }

    @Test fun noEsOferta() {
        assertFalse(p("Estás en línea", "Buscando viajes").isLikelyOffer)
        assertFalse(p("Ganancias de hoy", "Ver detalles").isLikelyOffer)
    }

    @Test fun bonosYTarifasPorUnidadSeIgnoran() {
        val o = p("$ 8.540", "+$ 500 incluido por promoción", "$ 1.200/h", "A 4 min (1,2 km)", "Viaje de 18 min (9,3 km)").offer
        assertEquals(8540.0, o.fare.value!!, 0.0)
        assertTrue(o.fare.confidence >= 0.9)
    }

    @Test fun variosImportes_ambiguo() {
        val o = p("$ 8.540", "$ 9.100", "A 4 min (1,2 km)", "Viaje de 18 min (9,3 km)").offer
        assertTrue(o.fare.confidence < 0.7)
    }

    @Test fun ocr_correccionesYFactor() {
        val o = p("S 8.540", "A 4 rnin (1,2 krn)", "Viaje de 18 min (9,3 km)", src = DataSource.OCR).offer
        assertEquals(8540.0, o.fare.value!!, 0.0)
        assertEquals(1.2, o.pickupKm.value!!, 1e-9)
        assertEquals(DataSource.OCR, o.fare.source)
        assertTrue("OCR reduce confianza", o.fare.confidence < 1.0)
    }

    @Test fun ocr_lineasEnDistintoOrdenYFormatoDistanciaPrimero() {
        val o = p("Recogida", "1,2 km · 4 min", "$8.540", "Viaje", "9,3 km · 18 min", src = DataSource.OCR).offer
        assertEquals(1.2, o.pickupKm.value!!, 1e-9)
        assertEquals(9.3, o.tripKm.value!!, 1e-9)
        assertEquals(18.0, o.tripMin.value!!, 0.0)
    }

    @Test fun espaciosNoSeparables() {
        val o = p("$\u00A08.540", "A\u00A04\u00A0min (1,2\u00A0km)", "Viaje de 18 min (9,3 km)").offer
        assertEquals(8540.0, o.fare.value!!, 0.0)
        assertEquals(1.2, o.pickupKm.value!!, 1e-9)
    }
}
