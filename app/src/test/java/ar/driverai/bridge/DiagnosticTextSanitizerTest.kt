package ar.driverai.bridge

import ar.driverai.bridge.capture.DiagnosticSnapshot
import ar.driverai.bridge.capture.DiagnosticTextSanitizer
import ar.driverai.bridge.engine.DataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticTextSanitizerTest {
    @Test fun conservaSoloMetricasYEtiquetasPermitidas() {
        val safe = DiagnosticTextSanitizer.sanitize(
            listOf(
                "$ 8.540 · María García",
                "A 4 min (1,2 km) · Av. Corrientes 1234",
                "Viaje de 18 min (9,3 km) · destino: Calle Falsa 123",
                "María García",
                "Av. Corrientes 1234",
            )
        )

        assertEquals(listOf("$ 8.540", "4 min · 1,2 km", "viaje · 18 min · 9,3 km"), safe)
        assertFalse(safe.any { it.contains("María", ignoreCase = true) })
        assertFalse(safe.any { it.contains("Corrientes", ignoreCase = true) })
        assertFalse(safe.any { it.contains("Falsa", ignoreCase = true) })
        assertFalse(safe.any { it.contains("1234") || it.contains("123") })
    }

    @Test fun noConservaTextoLibreSinMetricas() {
        assertTrue(DiagnosticTextSanitizer.sanitize(listOf("Estás en línea", "Juan Pérez", "Destino desconocido")).isEmpty())
    }

    @Test fun limitaElNumeroDeFragmentosYNormalizaEspacios() {
        val safe = DiagnosticTextSanitizer.sanitize(
            listOf("Tarifa\u00A0$\u202F8.540", "UberX") + List(50) { "${it + 1} min ${it + 2} km" }
        )
        assertEquals(40, safe.size)
        assertTrue(safe.first().contains("tarifa"))
        assertTrue(safe.first().contains("$ 8.540"))
        assertTrue(safe.contains("uberx"))
    }

    @Test fun registroCopiadoSoloIncluyeTextoReducidoYVeredicto() {
        val record = DiagnosticSnapshot(
            source = DataSource.ACCESSIBILITY,
            snippets = listOf("$ 8.540", "viaje · 18 min · 9,3 km"),
            fare = "$8.540",
            tripKm = "9,3 km",
            tripMin = "18 min",
            verdict = "REVISAR",
        )
        val clipboard = record.clipboardText()

        assertTrue(clipboard.contains("Veredicto: REVISAR"))
        assertTrue(clipboard.contains("9,3 km"))
        assertFalse(clipboard.contains("María"))
        assertFalse(clipboard.contains("Dirección"))
    }
}
