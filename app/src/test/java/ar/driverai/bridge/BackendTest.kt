package ar.driverai.bridge

import ar.driverai.bridge.backend.ApiResult
import ar.driverai.bridge.backend.DriverAiApi
import ar.driverai.bridge.backend.StrategicRepository
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class BackendTest {
    private lateinit var server: MockWebServer
    private lateinit var api: DriverAiApi

    // Respuestas reales capturadas del backend el 2026-10-17 (recortadas)
    private val reporteJson = """{"ok":true,"reporte":{"resumen":"La noche está fresca","precio_minimo_sugerido":150,"mejor_zona_ahora":"Lomas","mejor_zona_en_2h":"Microcentro","zonas_rankeadas":[{"zona":"Lomas","score":10,"razon":"Demanda máxima","mejor_horario":"20-23hs"},{"zona":"Bernal","score":8,"razon":"Buen flujo","mejor_horario":"21-00hs"}],"estrategia_viajes":"x","alertas":[],"factor_lluvia":false,"nivel_demanda_general":"muy_alto"},"groq_status":200,"groq_raw_len":1305,"demanda_por_zona":[{"zona":"Lomas","score":10}],"clima":{"lluvia_mm":0,"viento_kmh":12.1,"temperatura":9.3,"lluvia_ahora":false,"prob_lluvia_3h":0,"lluvia_proxima_3h":false},"eventos":[],"hora":5,"dia":"sábado","score_turno":8,"multiplicador":1.62,"hay_partido":false,"post_explosion":false,"quincena":false}"""
    private val posicionJson = """{"ok":true,"zona_actual":"Microcentro","score_actual":6,"decision":"moverte","zona_recomendada":"Recoleta","score_recomendado":10,"alternativas":[{"zona":"Recoleta","score":10},{"zona":"Puerto Madero","score":10}],"espera_estimada_min":8,"tip":"Aprovechá","clima":{"temperatura":9.3},"hay_partido":false}"""

    @Before fun setUp() {
        server = MockWebServer(); server.start()
        val client = OkHttpClient.Builder().readTimeout(1, TimeUnit.SECONDS).callTimeout(3, TimeUnit.SECONDS).build()
        api = DriverAiApi(server.url("/functions/v1/").toString(), client)
    }
    @After fun tearDown() { server.shutdown() }

    @Test fun reporte_contratoReal() = runBlocking {
        server.enqueue(MockResponse().setBody(reporteJson))
        val r = api.reporte()
        assertTrue(r is ApiResult.Ok)
        val v = (r as ApiResult.Ok).value
        assertEquals("Lomas", v.reporte!!.mejor_zona_ahora)
        assertEquals(2, v.reporte!!.zonas_rankeadas.size)
        assertEquals(9.3, v.clima!!.temperatura!!, 1e-9)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/functions/v1/driverai-reporte", req.path)
        assertEquals("{}", req.body.readUtf8())
        assertNull("no se envían claves", req.getHeader("Authorization"))
    }

    @Test fun posicion_contratoReal() = runBlocking {
        server.enqueue(MockResponse().setBody(posicionJson))
        val r = api.posicion("Microcentro") as ApiResult.Ok
        assertEquals("moverte", r.value.decision)
        assertEquals("Recoleta", r.value.zona_recomendada)
        val req = server.takeRequest()
        assertEquals("/functions/v1/driverai-posicion", req.path)
        assertEquals("""{"zona_actual":"Microcentro"}""", req.body.readUtf8())
    }

    @Test fun camposFaltantesONuevos_noRompen() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"ok":true,"campo_nuevo":123,"reporte":{"zonas_rankeadas":[{"zona":"Flores"}]}}"""))
        val r = api.reporte() as ApiResult.Ok
        assertEquals("Flores", r.value.reporte!!.zonas_rankeadas[0].zona)
        assertNull(r.value.clima)
        assertNull(r.value.eventos)
    }

    @Test fun eventosListaVacia_noSeConfundeConCampoAusente() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"ok":true,"eventos":[]}"""))
        val r = api.reporte() as ApiResult.Ok
        assertNotNull(r.value.eventos)
        assertTrue(r.value.eventos!!.isEmpty())
    }

    @Test fun http500() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val r = api.reporte() as ApiResult.Fail
        assertEquals(ApiResult.Kind.HTTP, r.kind)
    }

    @Test fun okFalse() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"ok":false,"error":"x"}"""))
        assertEquals(ApiResult.Kind.BACKEND, (api.reporte() as ApiResult.Fail).kind)
    }

    @Test fun jsonInvalido() = runBlocking {
        server.enqueue(MockResponse().setBody("<html>"))
        assertEquals(ApiResult.Kind.PARSE, (api.reporte() as ApiResult.Fail).kind)
    }

    @Test fun timeout() = runBlocking {
        server.enqueue(MockResponse().setBody(reporteJson).setBodyDelay(5, TimeUnit.SECONDS))
        val r = api.reporte() as ApiResult.Fail
        assertTrue(r.kind == ApiResult.Kind.TIMEOUT || r.kind == ApiResult.Kind.NETWORK)
    }

    @Test fun sinConexion() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertTrue(api.reporte() is ApiResult.Fail)
    }

    @Test fun repositorio_marcaDesactualizadoYConservaUltimoDato() = runBlocking {
        var now = 1_000_000L
        val repo = StrategicRepository(api) { now }
        server.enqueue(MockResponse().setBody(reporteJson))
        repo.refreshReporte()
        assertFalse(repo.state.value.isStale(now))
        server.enqueue(MockResponse().setResponseCode(503))
        repo.refreshReporte()
        assertNotNull("se conserva el último reporte", repo.state.value.reporte)
        assertTrue(repo.state.value.isStale(now))
        assertNotNull(repo.state.value.lastError)
        now += StrategicRepository.STALE_MS + 1
        server.enqueue(MockResponse().setBody(reporteJson)); repo.refreshReporte()
        now += StrategicRepository.STALE_MS + 1
        assertTrue(repo.state.value.isStale(now))
    }
}

/** Prueba de contrato contra el backend REAL. Se ejecuta solo con LIVE_BACKEND=1 (CI). */
class LiveBackendContractTest {
    @Test fun endpointsReales() = runBlocking {
        Assume.assumeTrue(System.getenv("LIVE_BACKEND") == "1")
        val api = DriverAiApi(BuildConfig.BACKEND_BASE_URL)
        val r = api.reporte()
        assertTrue("reporte: $r", r is ApiResult.Ok)
        val p = api.posicion("Microcentro")
        assertTrue("posicion: $p", p is ApiResult.Ok)
        val pv = (p as ApiResult.Ok).value
        assertNotNull(pv.decision)
        assertNotNull(pv.zona_recomendada)
    }
}
