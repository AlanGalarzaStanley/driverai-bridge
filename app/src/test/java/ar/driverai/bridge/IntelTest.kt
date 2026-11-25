package ar.driverai.bridge

import ar.driverai.bridge.backend.ApiResult
import ar.driverai.bridge.data.HistoryEntry
import ar.driverai.bridge.data.MoneyCategory
import ar.driverai.bridge.data.MoneyEntry
import ar.driverai.bridge.data.MoneyKind
import ar.driverai.bridge.data.TripStatus
import ar.driverai.bridge.engine.DataSource
import ar.driverai.bridge.engine.RidePlatform
import ar.driverai.bridge.insights.Insights
import ar.driverai.bridge.intel.Cities
import ar.driverai.bridge.intel.DemandRadar
import ar.driverai.bridge.intel.DemandSignal
import ar.driverai.bridge.intel.HolidaysClient
import ar.driverai.bridge.intel.IntelRepository
import ar.driverai.bridge.intel.SignalKind
import ar.driverai.bridge.intel.SignalOrigin
import ar.driverai.bridge.intel.SportsFixturesClient
import ar.driverai.bridge.intel.WeatherSignal
import ar.driverai.bridge.intel.ZoneMatcher
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class IntelTest {
    private val tz = ZoneId.of("America/Argentina/Buenos_Aires")
    private val city = Cities.AMBA
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = LocalDateTime.of(y, m, d, h, min).atZone(tz).toInstant().toEpochMilli()
    private val H = 3_600_000L

    private fun signal(
        kind: SignalKind,
        zoneId: String?,
        start: Long,
        end: Long? = null,
        intensity: Double = 0.8,
        confidence: Double = 0.75,
        id: String = "s-${kind.name}-$zoneId-$start",
    ) = DemandSignal(id, kind, SignalOrigin.API_PUBLICA, "Test ${kind.label}", zoneId, start, end, intensity, confidence, "test", null, start)

    // ---------- Patrón general ----------

    @Test fun patronGeneral_centroLaboralPicoEnHoraPicoDeDiaHabil() {
        // Miércoles 25/11/2026
        val f = DemandRadar.forecast(city, emptyList(), at(2026, 11, 25, 8, 10))
        val micro = f.now().first { it.zoneId == "microcentro" }
        val f3 = DemandRadar.forecast(city, emptyList(), at(2026, 11, 25, 3, 10))
        val micro3 = f3.now().first { it.zoneId == "microcentro" }
        assertTrue("8 h > 3 h en Microcentro (${micro.index} vs ${micro3.index})", micro.index > micro3.index)
        assertEquals(DemandRadar.BASELINE_CONFIDENCE, micro.confidence, 1e-9)
        assertTrue(f.warnings.any { it.contains("patrón general") })
    }

    @Test fun feriado_cambiaElTipoDeDia() {
        val d = LocalDate.of(2026, 11, 25)
        assertEquals(DemandRadar.DayType.LABORAL, DemandRadar.dayType(d, 10, emptySet()))
        assertEquals(DemandRadar.DayType.DOMINGO_O_FERIADO, DemandRadar.dayType(d, 10, setOf(d)))
        assertTrue(HolidaysClient.fixedDates(2026).contains(LocalDate.of(2026, 5, 25)))
    }

    // ---------- Eventos ----------

    @Test fun partido_sumaEnLaSalidaYNoAlInicio() {
        val kickoff = at(2026, 11, 25, 21, 0)
        val s = signal(SignalKind.EVENTO_DEPORTIVO, "la_boca", kickoff, intensity = 0.67)
        val (from, to) = DemandRadar.impactWindow(s)
        // Fin típico 110 min → 22:50; ventana 22:40–00:00
        assertEquals(kickoff + 100 * 60_000L, from)
        assertEquals(kickoff + 180 * 60_000L, to)

        val f = DemandRadar.forecast(city, listOf(s), at(2026, 11, 25, 20, 30))
        val atKickoff = f.at("la_boca", kickoff + 5 * 60_000L)!!
        val atEgress = f.at("la_boca", kickoff + 130 * 60_000L)!!
        assertTrue("salida (${atEgress.index}) > inicio (${atKickoff.index})", atEgress.index > atKickoff.index + 15)
        assertEquals(atEgress.contributions.toString(), s.id, atEgress.topReason?.signalId)
        assertTrue("conf ${atEgress.confidence}", atEgress.confidence > DemandRadar.BASELINE_CONFIDENCE)
        assertTrue("peaks ${f.upcomingPeaks().map { it.zoneId to it.index }} egress ${atEgress.index}", f.upcomingPeaks().any { it.zoneId == "la_boca" })
        // Otra zona no recibe el partido
        val palermo = f.at("palermo", kickoff + 130 * 60_000L)!!
        assertTrue(palermo.contributions.none { it.signalId == s.id })
    }

    @Test fun vuelosYCrucero_ventanasDeArribo() {
        val t = at(2026, 11, 25, 10)
        val (vf, vt) = DemandRadar.impactWindow(signal(SignalKind.VUELOS, "ezeiza", t))
        assertEquals(t + 25 * 60_000L, vf); assertEquals(t + 80 * 60_000L, vt)
        val (cf, ct) = DemandRadar.impactWindow(signal(SignalKind.CRUCERO, "retiro", t))
        assertEquals(t + 60 * 60_000L, cf); assertEquals(t + 300 * 60_000L, ct)
    }

    @Test fun paroDeColectivos_subeTodaLaCiudad() {
        val now = at(2026, 11, 25, 7, 0)
        val paro = signal(SignalKind.PARO_TRANSPORTE, null, now, now + 10 * H, intensity = 0.9, confidence = 0.6)
        val base = DemandRadar.forecast(city, emptyList(), now).now().associateBy { it.zoneId }
        val withParo = DemandRadar.forecast(city, listOf(paro), now).now()
        assertTrue(withParo.all { w -> w.index >= (base[w.zoneId]!!.index) })
        assertTrue(withParo.all { w -> w.contributions.any { it.signalId == paro.id && it.label.contains("toda la ciudad") } })
    }

    @Test fun senalesInvalidasSeDescartan() {
        val now = at(2026, 11, 25, 12)
        val bad = signal(SignalKind.OTRO, null, now, intensity = 3.0)
        val f = DemandRadar.forecast(city, listOf(bad), now)
        assertTrue(f.signalsUsed.isEmpty())
        assertTrue(f.warnings.any { it.contains("descartaron") })
    }

    @Test fun indiceSiempreEnRango() {
        val now = at(2026, 11, 28, 22)
        val many = (1..6).map { signal(SignalKind.EVENTO_SHOW, "palermo", now - 2 * H, intensity = 1.0, id = "x$it") } +
            signal(SignalKind.PARO_TRANSPORTE, null, now - H, now + 5 * H, 1.0)
        val f = DemandRadar.forecast(city, many, now)
        assertTrue(f.windows.all { it.index in 0..100 && it.confidence in 0.0..1.0 })
    }

    @Test fun climaSoloConProbabilidadAlta() {
        assertNull(WeatherSignal.fromRainProbability(30, 0, 0))
        assertNull(WeatherSignal.fromRainProbability(null, 0, 0))
        val s = WeatherSignal.fromRainProbability(90, 1_000_000L, 1_000_000L)!!
        assertNull(s.zoneId)
        assertTrue(s.isValid)
    }

    // ---------- Patrón personal ----------

    private fun entry(at: Long, perHour: Double, platform: RidePlatform? = RidePlatform.UBER, status: TripStatus = TripStatus.OBSERVED, verdict: String = "CONVIENE") =
        HistoryEntry(at, verdict, 5000.0, 10.0, 20.0, 500.0, perHour, DataSource.ACCESSIBILITY, id = "e$at", tripStatus = status, platform = platform?.name)

    @Test fun patronPersonal_requiereMuestrasYMarcaMejoresHoras() {
        val now = at(2026, 11, 25, 23)
        val few = (0 until 6).map { entry(now - it * H, 20000.0) }
        assertNull(IntelRepository.personalPattern(few, now, tz))

        val entries = (1..10).flatMap { d ->
            listOf(entry(at(2026, 11, 25 - d, 8, 15), 30000.0), entry(at(2026, 11, 25 - d, 15, 15), 15000.0))
        }
        val p = IntelRepository.personalPattern(entries, now, tz)!!
        val morning = p.contribution(java.time.DayOfWeek.MONDAY, 8)!!
        val afternoon = p.contribution(java.time.DayOfWeek.MONDAY, 15)!!
        assertTrue(morning.points > 0 && afternoon.points < 0)
        assertEquals(SignalOrigin.TU_HISTORIAL, morning.origin)

        val best = Insights.bestHours(entries, tz)
        assertEquals(8, best.first().first)
    }

    // ---------- Catálogo y fuentes ----------

    @Test fun catalogo_asociaEstadiosDelFeed() {
        assertEquals("bombonera", city.matchVenue("Estadio Alberto José Armando", "Boca Juniors")?.id)
        assertEquals("unico_lp", city.matchVenue("Estadio Único Diego Armando Maradona", null)?.id)
        assertEquals("maradona_ajs", city.matchVenue("Estadio Diego Armando Maradona", null)?.id)
        assertEquals("bombonera", city.matchVenue(null, "Boca Juniors")?.id)
        assertNull(city.matchVenue("Estadio Mario Alberto Kempes", "Talleres"))
        assertTrue(city.zones.map { it.id }.toSet().containsAll(city.venues.map { it.zoneId }))
    }

    @Test fun theSportsDb_parseaFixtureYDescartaEstadiosAjenos() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """{"events":[
                  {"idEvent":"1","strEvent":"Boca Juniors vs Independiente Rivadavia","strHomeTeam":"Boca Juniors","strAwayTeam":"Independiente Rivadavia","strVenue":"Estadio Alberto José Armando","strTimestamp":"2026-11-26T00:00:00","strStatus":"Not Started"},
                  {"idEvent":"2","strEvent":"Talleres vs Belgrano","strHomeTeam":"Talleres","strVenue":"Estadio Mario Alberto Kempes","strTimestamp":"2026-11-26T22:00:00"},
                  {"idEvent":"3","strEvent":"River vs Racing","strHomeTeam":"River Plate","strVenue":"Estadio Monumental","strTimestamp":"2026-11-20T22:00:00","strStatus":"FT"}
                ]}""",
            ),
        )
        server.start()
        try {
            val client = SportsFixturesClient(OkHttpClient(), server.url("/api/v1/json").toString().trimEnd('/'), clock = { 42L })
            val r = client.upcoming(city, "")
            assertTrue(r is ApiResult.Ok)
            val list = (r as ApiResult.Ok).value
            assertEquals(1, list.size)
            val s = list.first()
            assertEquals("la_boca", s.zoneId)
            assertEquals(at(2026, 11, 25, 21), s.startAt)
            assertEquals(SignalKind.EVENTO_DEPORTIVO, s.kind)
            assertTrue(s.isValid)
            val path = server.takeRequest().path!!
            assertTrue(path, path.contains("/${SportsFixturesClient.DEMO_KEY}/eventsnextleague.php?id=4406"))
        } finally { server.shutdown() }
    }

    @Test fun theSportsDb_claveInvalidaYErrores() = runBlocking {
        val bad = SportsFixturesClient(OkHttpClient(), "http://localhost:1").upcoming(city, "abc/../x")
        assertTrue(bad is ApiResult.Fail)
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(500))
        server.start()
        try {
            val r = SportsFixturesClient(OkHttpClient(), server.url("/j").toString()).upcoming(city, "123")
            assertEquals(ApiResult.Kind.HTTP, (r as ApiResult.Fail).kind)
        } finally { server.shutdown() }
        assertNull(SportsFixturesClient.parseUtc("no-fecha"))
        assertEquals(at(2026, 11, 25, 21), SportsFixturesClient.parseUtc("2026-11-26T00:00:00Z"))
    }

    @Test fun feriados_parseaYDescartaFechasInvalidas() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""[{"fecha":"2026-11-23","tipo":"trasladable","nombre":"Soberanía"},{"fecha":"x"}]"""))
        server.start()
        try {
            val r = HolidaysClient(OkHttpClient(), server.url("/feriados").toString()).year(2026)
            val list = (r as ApiResult.Ok).value
            assertEquals(listOf("2026-11-23"), list.map { it.fecha })
            assertTrue(server.takeRequest().path!!.endsWith("/feriados/2026"))
        } finally { server.shutdown() }
    }

    // ---------- Destino ----------

    @Test fun zonaDeDestino_ignoraNombresDeCallesYPrefiereElFinal() {
        val frags = listOf("UberX", "Av. Belgrano 1234, Monserrat", "Gorriti 4800, Palermo")
        val (zone, conf) = ZoneMatcher.destination(frags, city)!!
        assertEquals("palermo", zone.id)
        assertTrue(conf >= 0.7)
        val single = ZoneMatcher.destination(listOf("Av. Belgrano 500"), city)
        assertNull("una avenida no es un barrio", single)
        assertNull(ZoneMatcher.destination(listOf("Once minutos", "12 km"), city))
    }

    @Test fun zonaDeDestino_combinaConElRadar() {
        val now = at(2026, 11, 25, 22)
        val f = DemandRadar.forecast(city, emptyList(), now)
        val o = ZoneMatcher.outlook(listOf("Hacia Palermo"), city, f, now + 20 * 60_000L)!!
        assertEquals("palermo", o.zoneId)
        assertNotNull(o.index)
        assertTrue(o.shortLine { "22:20" }.startsWith("Destino Palermo"))
    }

    // ---------- Plataformas ----------

    @Test fun plataformas_porPaqueteYTexto() {
        assertEquals(RidePlatform.UBER, RidePlatform.fromPackage("com.ubercab.driver"))
        assertEquals(RidePlatform.INDRIVE, RidePlatform.fromPackage("sinet.startup.inDriver"))
        assertNull(RidePlatform.fromPackage("com.whatsapp"))
        assertEquals(RidePlatform.DIDI, RidePlatform.guessFromText(listOf("DiDi Express", "$ 5.000")))
        assertNull("ambiguo", RidePlatform.guessFromText(listOf("Uber", "DiDi")))
        assertNull(RidePlatform.guessFromText(listOf("Ofertas", "$ 5.000")))
        assertEquals(RidePlatform.OTRA, RidePlatform.fromName("desconocida"))
        assertEquals(5, RidePlatform.READABLE.size)
    }

    // ---------- Informes ----------

    @Test fun informes_porAppAceptacionYCsvSeguro() {
        val base = at(2026, 11, 24, 10)
        val entries = listOf(
            entry(base, 20000.0, RidePlatform.UBER, TripStatus.COMPLETED),
            entry(base + H, 10000.0, RidePlatform.UBER, verdict = "NO_CONVIENE"),
            entry(base + 2 * H, 12000.0, RidePlatform.DIDI, TripStatus.ACCEPTED),
            entry(base + 3 * H, 9000.0, null),
        )
        val byApp = Insights.byPlatform(entries).associateBy { it.key }
        assertEquals(2, byApp["Uber"]!!.offers)
        assertEquals(0.5, byApp["Uber"]!!.acceptanceRate!!, 1e-9)
        assertEquals(15000.0, byApp["Uber"]!!.avgGrossPerHour!!, 1e-9)
        assertEquals(0.5, byApp["Uber"]!!.convenientPct!!, 1e-9)
        assertEquals(1, byApp["Sin identificar"]!!.offers)

        val csv = Insights.historyCsv(entries.take(1).map { it.copy(destinationZoneId = "=HYPERLINK(\"x\")") }, tz)
        val row = csv.lines()[1]
        assertTrue(row, row.contains("\"'=HYPERLINK(\"\"x\"\")\""))
        assertTrue(row.startsWith("2026-11-24 10:00,Uber,CONVIENE,5000.00"))

        val money = listOf(
            MoneyEntry("1", at(2026, 11, 23, 12), MoneyKind.INCOME, 10000.0, MoneyCategory.TRIP_INCOME),
            MoneyEntry("2", at(2026, 11, 24, 12), MoneyKind.EXPENSE, 3000.0, MoneyCategory.FUEL),
            MoneyEntry("3", at(2026, 11, 20, 12), MoneyKind.INCOME, 7000.0, MoneyCategory.TRIP_INCOME),
        )
        val weeks = Insights.weekly(money, tz, weeks = 2, today = LocalDate.of(2026, 11, 25))
        assertEquals(LocalDate.of(2026, 11, 23), weeks[0].start)
        assertEquals(7000.0, weeks[0].net, 1e-9)
        assertEquals(7000.0, weeks[1].income, 1e-9)
        assertTrue(Insights.walletCsv(money, tz).lines().first().startsWith("fecha,tipo"))
    }

    @Test fun senalManual_seAcotaYEsValida() {
        val now = at(2026, 11, 25, 12)
        val s = IntelRepository.manualSignal(SignalKind.PARO_TRANSPORTE, "  Paro de colectivos  ", null, now, now + 6 * H, 2.0, now)
        assertEquals("Paro de colectivos", s.title)
        assertEquals(1.0, s.intensity, 1e-9)
        assertEquals(SignalOrigin.CONDUCTOR, s.origin)
        assertTrue(s.isValid)
        assertFalse(s.id.isBlank())
        val c = IntelRepository.decode(IntelRepository.encode(ar.driverai.bridge.intel.IntelCache(manualSignals = listOf(s))))
        assertEquals(s, c.manualSignals.single())
        assertEquals(ar.driverai.bridge.intel.IntelCache(), IntelRepository.decode("{roto"))
    }
}
