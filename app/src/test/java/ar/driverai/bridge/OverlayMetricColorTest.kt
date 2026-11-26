package ar.driverai.bridge

import ar.driverai.bridge.overlay.OverlayMetricColor
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayMetricColorTest {
    @Test fun ingresosMayoresAlMinimo_seMarcanVerdesYLosMenoresRojos() {
        assertEquals(OverlayMetricColor.GREEN, OverlayMetricColor.forThreshold(13_000.0, 12_000.0, higherIsBetter = true))
        assertEquals(OverlayMetricColor.RED, OverlayMetricColor.forThreshold(11_000.0, 12_000.0, higherIsBetter = true))
    }

    @Test fun recogidaMenorAlMaximoEsVerdeYLaMayorRoja() {
        assertEquals(OverlayMetricColor.GREEN, OverlayMetricColor.forThreshold(3.0, 5.0, higherIsBetter = false))
        assertEquals(OverlayMetricColor.RED, OverlayMetricColor.forThreshold(6.0, 5.0, higherIsBetter = false))
    }

    @Test fun faltaDeUmbralEsNeutralYFaltaDeDatoConUmbralEsRevision() {
        assertEquals(OverlayMetricColor.NEUTRAL, OverlayMetricColor.forThreshold(13_000.0, null, higherIsBetter = true))
        assertEquals(OverlayMetricColor.AMBER, OverlayMetricColor.forThreshold(null, 12_000.0, higherIsBetter = true))
        assertEquals(OverlayMetricColor.AMBER, OverlayMetricColor.forThreshold(Double.NaN, 12_000.0, higherIsBetter = true))
    }
}
