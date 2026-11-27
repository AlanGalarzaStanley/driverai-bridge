package ar.driverai.bridge.plan

/**
 * Construye el plan a partir de eventos con fuente.
 * Determinístico: sin LLM. Prohibido inventar lugares u horarios.
 */
object PlanDiaBuilder {

    data class EventoFuente(
        val id: String,
        val tipo: BloqueTipo,
        val nombre: String,
        val lugar: PlanLugar?,
        val inicio: Long,
        val fin: Long?,
        val finOficial: Boolean,
        val fuentes: List<FuenteRef>,
        val puntosEspera: List<PuntoEspera> = emptyList(),
        val destinosLargos: List<String> = emptyList(),
        val confianza: Confianza = Confianza.media,
    )

    fun duracionTipicaMs(tipo: BloqueTipo): Long = when (tipo) {
        BloqueTipo.partido -> 2 * 60 * 60 * 1000L + 30 * 60 * 1000L
        BloqueTipo.recital -> 3 * 60 * 60 * 1000L
        BloqueTipo.aeropuerto -> 60 * 60 * 1000L
        BloqueTipo.terminal -> 45 * 60 * 1000L
        BloqueTipo.clima -> 3 * 60 * 60 * 1000L
        BloqueTipo.otro -> 2 * 60 * 60 * 1000L
    }

    fun build(
        eventos: List<EventoFuente>,
        zonaBase: String,
        generadoAt: Long,
        vigenciaHasta: Long = generadoAt + 12 * 60 * 60 * 1000L,
        minDesdeChoferMin: Int? = null,
    ): PlanDia {
        val bloques = eventos
            .filter { it.fuentes.isNotEmpty() }
            .sortedBy { it.inicio }
            .map { e ->
                val finFuente = if (e.fin != null && e.finOficial) FinFuente.oficial else FinFuente.estimado
                val fin = e.fin ?: (e.inicio + duracionTipicaMs(e.tipo))
                val picoDesde = fin
                val picoHasta = fin + 30 * 60 * 1000L
                val lugarNoConfirmado = e.lugar == null || !e.lugar.confirmado
                val aviso = buildAvisoTexto(e, fin, picoDesde, picoHasta, minDesdeChoferMin, lugarNoConfirmado)
                PlanBloque(
                    id = e.id,
                    tipo = e.tipo,
                    nombre = e.nombre,
                    lugar = e.lugar,
                    lugarNoConfirmado = lugarNoConfirmado,
                    inicio = e.inicio,
                    finEstimado = fin,
                    finFuente = finFuente,
                    ventanaPico = VentanaPico(picoDesde, picoHasta),
                    puntosEspera = e.puntosEspera,
                    destinosLargos = e.destinosLargos,
                    confianza = if (lugarNoConfirmado) Confianza.baja else e.confianza,
                    fuentes = e.fuentes,
                    avisoTexto = aviso,
                )
            }

        val huecos = mutableListOf<PlanHueco>()
        for (i in 0 until bloques.size - 1) {
            val a = bloques[i]
            val b = bloques[i + 1]
            val gapStart = a.ventanaPico.hasta
            val gapEnd = b.inicio
            if (gapEnd - gapStart >= 45 * 60 * 1000L) {
                huecos += PlanHueco(
                    desde = gapStart,
                    hasta = gapEnd,
                    sugerencia = "Hueco libre. Priorizá viajes cortos cerca de ${a.lugar?.nombre ?: "el último evento"} o reposicionte hacia ${b.lugar?.nombre ?: "el próximo"}.",
                )
            }
        }

        val supuestos = buildList {
            if (bloques.any { it.finFuente == FinFuente.estimado }) {
                add("Algunos horarios de fin son estimados por tipo de evento (no hay fuente oficial de cierre).")
            }
            if (bloques.any { it.lugarNoConfirmado }) {
                add("Hay lugares no confirmados; no te muevas solo por ese dato.")
            }
            add("Plan generado sin inventar eventos. Solo se listan ítems con al menos una fuente.")
        }

        return PlanDia(
            generadoAt = generadoAt,
            vigenciaHasta = vigenciaHasta,
            zonaBase = zonaBase,
            bloques = bloques,
            huecos = huecos,
            zonasPrecaucion = emptyList(),
            supuestos = supuestos,
        )
    }

    private fun buildAvisoTexto(
        e: EventoFuente,
        fin: Long,
        picoDesde: Long,
        picoHasta: Long,
        minDesde: Int?,
        lugarNoConfirmado: Boolean,
    ): String {
        val lugarNom = e.lugar?.nombre ?: "lugar no confirmado"
        val espera = e.puntosEspera.take(2).joinToString(" o ") { it.calle }.ifBlank { "cerca del lugar, evitando la salida principal" }
        val dest = e.destinosLargos.firstOrNull()?.let { " Salís hacia $it." } ?: ""
        val traslado = minDesde?.let { " Estás a ~$it min." } ?: ""
        val finH = formatHm(fin)
        val picoH = "${formatHm(picoDesde)}–${formatHm(picoHasta)}"
        val base = "${e.nombre} termina $finH en $lugarNom.$traslado Pico $picoH. Parate en $espera.$dest"
        return if (lugarNoConfirmado) "$base (lugar sin confirmar)" else base
    }

    private fun formatHm(epoch: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = epoch }
        return "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
    }
}
