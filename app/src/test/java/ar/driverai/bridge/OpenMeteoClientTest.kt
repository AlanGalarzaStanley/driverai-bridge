package ar.driverai.bridge

import ar.driverai.bridge.backend.ApiResult
import ar.driverai.bridge.backend.LocalWeatherRepository
import ar.driverai.bridge.backend.OpenMeteoClient
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoClientTest {
    @Test fun datoDePrecipitacionAusenteNoSeConvierteEnCero() = runTest {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setBody("""{"current":{"temperature_2m":18.7,"wind_speed_10m":14.2,"interval":900},"hourly":{"precipitation_probability":[10,45,20]}}"""))
            start()
        }
        try {
            val client = OpenMeteoClient(OkHttpClient(), clock = { 1234L }, endpoint = server.url("v1/forecast").toString().trimEnd('/'))
            val result = client.fetchBuenosAires()
            assertTrue(result is ApiResult.Ok)
            val weather = (result as ApiResult.Ok).value
            assertEquals(18.7, weather.temperatureC!!, 1e-9)
            assertEquals(14.2, weather.windKmh!!, 1e-9)
            assertNull(weather.precipitationMm)
            assertEquals(900, weather.precipitationIntervalSeconds)
            assertEquals(45, weather.precipProbNextHours)
            assertEquals(1234L, weather.fetchedAtMs)
            val request = server.takeRequest()
            assertTrue(request.path!!.contains("timezone=America%2FArgentina%2FBuenos_Aires"))
            assertTrue(request.path!!.contains("latitude=-34.6037"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun ceroExplicitoSeConservaComoCantidadReportada() = runTest {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setBody("""{"current":{"precipitation":0.0}}"""))
            start()
        }
        try {
            val client = OpenMeteoClient(OkHttpClient(), endpoint = server.url("v1/forecast").toString().trimEnd('/'))
            val result = client.fetchBuenosAires() as ApiResult.Ok
            assertEquals(0.0, result.value.precipitationMm!!, 0.0)
            assertNull(result.value.precipitationIntervalSeconds)
        } finally {
            server.shutdown()
        }
    }

    @Test fun versionDeshabilitadaNoConsultaFuenteExterna() = runTest {
        val repository = LocalWeatherRepository(enabled = { false })
        val result = repository.refresh()
        assertTrue(result is ApiResult.Fail)
        assertFalse(repository.state.value.enabled)
        assertTrue(repository.state.value.error.orEmpty().contains("plan autorizado"))
        assertNull(repository.state.value.weather)
    }
}
