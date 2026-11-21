package ar.driverai.bridge

import ar.driverai.bridge.backend.Clima
import ar.driverai.bridge.backend.Reporte
import ar.driverai.bridge.backend.ReporteResponse
import ar.driverai.bridge.backend.ShiftBriefingSnapshot
import ar.driverai.bridge.data.AppJson
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShiftBriefingTest {
    @Test fun snapshotConservaSoloResumenNecesarioYMarcaHoraDeRecepcion() {
        val response = ReporteResponse(
            ok = true,
            reporte = Reporte(
                mejor_zona_ahora = "Lomas",
                mejor_zona_en_2h = "Quilmes",
                nivel_demanda_general = "media",
            ),
            clima = Clima(temperatura = 18.5, lluvia_ahora = false, prob_lluvia_3h = 20.0),
            eventos = listOf(JsonPrimitive("evento")),
        )

        val snapshot = ShiftBriefingSnapshot.from(response, "Bernal", 1234L)

        assertEquals(1234L, snapshot.retrievedAt)
        assertEquals("Bernal", snapshot.selectedZone)
        assertEquals("media", snapshot.demandLevel)
        assertEquals("Lomas", snapshot.bestZoneNow)
        assertEquals("Quilmes", snapshot.bestZoneIn2h)
        assertEquals(18.5, snapshot.temperatureC!!, 0.0)
        assertEquals(false, snapshot.rainNow)
        assertEquals(20.0, snapshot.rainProbability3h!!, 0.0)
        assertEquals(1, snapshot.eventsListed)
    }

    @Test fun distingueEventosAusentesDeUnaListaVacia() {
        val absent = ShiftBriefingSnapshot.from(ReporteResponse(ok = true), null, 1L)
        val empty = ShiftBriefingSnapshot.from(ReporteResponse(ok = true, eventos = emptyList()), null, 1L)

        assertNull(absent.eventsListed)
        assertEquals(0, empty.eventsListed)
    }

    @Test fun snapshotSeSerializaParaPersistenciaLocal() {
        val original = ShiftBriefingSnapshot.from(ReporteResponse(ok = true, eventos = emptyList()), "Lomas", 123L)
        val encoded = AppJson.encodeToString(ShiftBriefingSnapshot.serializer(), original)
        val restored = AppJson.decodeFromString(ShiftBriefingSnapshot.serializer(), encoded)

        assertEquals(original, restored)
    }
}
