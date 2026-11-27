package ar.driverai.bridge.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanDiaTest {

    @Test
    fun fixtureGeneraCuatroBloquesConLugarYFin() {
        val plan = PlanDiaFixtures.expectedPlan()
        assertEquals(4, plan.bloques.size)
        plan.bloques.forEach { b ->
            assertFalse(b.lugar == null && !b.lugarNoConfirmado)
            assertTrue(b.finEstimado > b.inicio)
            assertTrue(b.fuentes.isNotEmpty())
            assertTrue(b.ventanaPico.hasta > b.ventanaPico.desde)
        }
        val maria = plan.bloques.first { it.id == "maria-becerra-23" }
        assertEquals(FinFuente.oficial, maria.finFuente)
        assertTrue(maria.puntosEspera.size >= 2)
    }

    @Test
    fun sinFuenteNoEntraAlPlan() {
        val day = PlanDiaFixtures.dayStartMs()
        val eventos = PlanDiaFixtures.fourEventsExample(day) + PlanDiaBuilder.EventoFuente(
            id = "fantasma",
            tipo = BloqueTipo.otro,
            nombre = "Sin fuente",
            lugar = PlanLugar("X"),
            inicio = PlanDiaFixtures.at(day, 10),
            fin = PlanDiaFixtures.at(day, 11),
            finOficial = true,
            fuentes = emptyList(),
        )
        val plan = PlanDiaBuilder.build(eventos, "Quilmes", day + 8 * 3_600_000L)
        assertEquals(4, plan.bloques.size)
        assertTrue(plan.bloques.none { it.id == "fantasma" })
    }

    @Test
    fun finEstimadoCuandoNoHayCierreOficial() {
        val day = PlanDiaFixtures.dayStartMs()
        val e = PlanDiaBuilder.EventoFuente(
            id = "parcial",
            tipo = BloqueTipo.partido,
            nombre = "Partido sin fin",
            lugar = PlanLugar("Estadio X", confirmado = true),
            inicio = PlanDiaFixtures.at(day, 12),
            fin = null,
            finOficial = false,
            fuentes = listOf(FuenteRef("AFA", null, day)),
        )
        val plan = PlanDiaBuilder.build(listOf(e), "Quilmes", day)
        assertEquals(1, plan.bloques.size)
        assertEquals(FinFuente.estimado, plan.bloques[0].finFuente)
        assertEquals(PlanDiaBuilder.duracionTipicaMs(BloqueTipo.partido), plan.bloques[0].finEstimado - e.inicio)
    }

    @Test
    fun avisosConRelojSimulado() {
        val day = PlanDiaFixtures.dayStartMs()
        val plan = PlanDiaFixtures.expectedPlan(day)
        val avisos = PlanDiaAlerts.schedule(plan, mapOf("maria-becerra-23" to 25))
        assertTrue(avisos.isNotEmpty())
        val maria = plan.bloques.first { it.id == "maria-becerra-23" }
        val sali = avisos.first { it.bloqueId == "maria-becerra-23" && it.tipo == AvisoTipo.SALI_HACIA }
        assertEquals(maria.finEstimado - 30 * 60_000L, sali.disparaAt)
        val pico = avisos.first { it.bloqueId == "maria-becerra-23" && it.tipo == AvisoTipo.PICO_EN_30 }
        assertEquals(maria.ventanaPico.desde - 30 * 60_000L, pico.disparaAt)
        val active = PlanDiaAlerts.activeAt(avisos, sali.disparaAt + 60_000L)
        assertTrue(active.any { it.bloqueId == "maria-becerra-23" })
    }

    @Test
    fun planExpiradoSeDetecta() {
        val plan = PlanDiaFixtures.expectedPlan().copy(vigenciaHasta = 1000L)
        assertTrue(plan.isExpired(2000L))
        assertFalse(plan.isExpired(500L))
    }
}
