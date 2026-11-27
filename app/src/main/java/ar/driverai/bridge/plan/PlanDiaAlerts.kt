package ar.driverai.bridge.plan

/**
 * Avisos determinísticos con turno activo.
 * Máximo 1 aviso por bloque + 1 de pico.
 * Tests usan reloj simulado (nowMs).
 */
object PlanDiaAlerts {

    fun schedule(plan: PlanDia, travelMinByBloque: Map<String, Int> = emptyMap()): List<PlanAviso> {
        val out = mutableListOf<PlanAviso>()
        for (b in plan.bloques) {
            val travel = travelMinByBloque[b.id] ?: 25
            val saliAt = b.finEstimado - travel * 60_000L - 5 * 60_000L
            val lugar = b.lugar?.nombre ?: "el evento"
            out += PlanAviso(
                bloqueId = b.id,
                tipo = AvisoTipo.SALI_HACIA,
                texto = "Salí hacia $lugar. ${b.avisoTexto ?: ""}".trim(),
                disparaAt = saliAt,
            )
            out += PlanAviso(
                bloqueId = b.id,
                tipo = AvisoTipo.PICO_EN_30,
                texto = "Pico en 30 min: ${b.nombre}. ${b.puntosEspera.firstOrNull()?.calle ?: "Revisá punto de espera"}.",
                disparaAt = b.ventanaPico.desde - 30 * 60_000L,
            )
        }
        return out.sortedBy { it.disparaAt }
    }

    fun activeAt(
        avisos: List<PlanAviso>,
        now: Long,
        silenced: Set<String> = emptySet(),
        windowMs: Long = 10 * 60_000L,
    ): List<PlanAviso> {
        val due = avisos.filter {
            it.disparaAt <= now && now - it.disparaAt <= windowMs &&
                "${it.bloqueId}:${it.tipo}" !in silenced
        }
        return due.groupBy { it.bloqueId }.values.map { group ->
            group.minByOrNull { if (it.tipo == AvisoTipo.PICO_EN_30) 0 else 1 }!!
        }.sortedBy { it.disparaAt }
    }
}
