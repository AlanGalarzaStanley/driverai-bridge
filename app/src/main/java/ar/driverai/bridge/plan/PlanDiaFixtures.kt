package ar.driverai.bridge.plan

/** Fixture de aceptación: 4 eventos (partido 12:00, recitales 16/20/23). */
object PlanDiaFixtures {

    fun dayStartMs(year: Int = 2026, month: Int = 11, day: Int = 28): Long {
        val c = java.util.Calendar.getInstance()
        c.clear()
        c.set(year, month - 1, day, 0, 0, 0)
        c.set(java.util.Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun at(dayStart: Long, hour: Int, minute: Int = 0): Long =
        dayStart + hour * 3_600_000L + minute * 60_000L

    fun fourEventsExample(dayStart: Long = dayStartMs()): List<PlanDiaBuilder.EventoFuente> {
        val consultado = dayStart + 8 * 3_600_000L
        fun fuente(name: String, url: String? = null) = FuenteRef(name, url, consultado)

        return listOf(
            PlanDiaBuilder.EventoFuente(
                id = "partido-12",
                tipo = BloqueTipo.partido,
                nombre = "Partido Liga Profesional",
                lugar = PlanLugar("Estadio Ciudad de La Plata", "Av. 25 y 25, La Plata", -34.9137, -57.9892, true),
                inicio = at(dayStart, 12, 0),
                fin = at(dayStart, 14, 0),
                finOficial = true,
                fuentes = listOf(fuente("AFA fixture", "https://www.afa.com.ar")),
                puntosEspera = listOf(
                    PuntoEspera("Calle 31 y 25", "Evita la salida principal del estadio", 8),
                    PuntoEspera("Diag. 74 y 25", "Salida rápida a Autopista La Plata", 10),
                ),
                destinosLargos = listOf("CABA Centro", "Quilmes"),
                confianza = Confianza.alta,
            ),
            PlanDiaBuilder.EventoFuente(
                id = "recital-16",
                tipo = BloqueTipo.recital,
                nombre = "Recital tarde",
                lugar = PlanLugar("Movistar Arena", "Humboldt 450, Villa Crespo", -34.5975, -58.4470, true),
                inicio = at(dayStart, 14, 0),
                fin = at(dayStart, 16, 0),
                finOficial = true,
                fuentes = listOf(fuente("Ticketek", "https://www.ticketek.com.ar")),
                puntosEspera = listOf(
                    PuntoEspera("Honorio Pueyrredón y Córdoba", "Evita cuello de Botánico", 6),
                    PuntoEspera("Corrientes y Thames", "Salida a Av. Córdoba", 7),
                ),
                destinosLargos = listOf("Palermo", "Belgrano"),
                confianza = Confianza.alta,
            ),
            PlanDiaBuilder.EventoFuente(
                id = "recital-20",
                tipo = BloqueTipo.recital,
                nombre = "Recital noche temprana",
                lugar = PlanLugar("Luna Park", "Av. Eduardo Madero 420", -34.6018, -58.3684, true),
                inicio = at(dayStart, 18, 0),
                fin = at(dayStart, 20, 0),
                finOficial = true,
                fuentes = listOf(fuente("Luna Park cartelera")),
                puntosEspera = listOf(
                    PuntoEspera("Alem y Viamonte", "Evita salida Madero", 5),
                    PuntoEspera("Leandro N. Alem y Corrientes", "Acceso a 9 de Julio", 6),
                ),
                destinosLargos = listOf("Recoleta", "Norte CABA"),
                confianza = Confianza.media,
            ),
            PlanDiaBuilder.EventoFuente(
                id = "maria-becerra-23",
                tipo = BloqueTipo.recital,
                nombre = "María Becerra",
                lugar = PlanLugar("Estadio Vélez", "Av. Juan B. Justo 9200", -34.6353, -58.5206, true),
                inicio = at(dayStart, 21, 0),
                fin = at(dayStart, 23, 0),
                finOficial = true,
                fuentes = listOf(fuente("Ticketek", "https://www.ticketek.com.ar")),
                puntosEspera = listOf(
                    PuntoEspera("Av. Asbury y Juan B. Justo", "Evita salida principal del estadio", 8),
                    PuntoEspera("Av. Álvarez Jonte y Segurola", "Salida hacia Av. Rivadavia", 10),
                ),
                destinosLargos = listOf("Oeste GBA", "Centro"),
                confianza = Confianza.alta,
            ),
        )
    }

    fun expectedPlan(dayStart: Long = dayStartMs(), generadoAt: Long = dayStart + 8 * 3_600_000L): PlanDia =
        PlanDiaBuilder.build(
            eventos = fourEventsExample(dayStart),
            zonaBase = "Quilmes / GBA Sur",
            generadoAt = generadoAt,
            minDesdeChoferMin = 25,
        )
}
