package ar.driverai.bridge.intel

import java.text.Normalizer

/**
 * Perfil urbano que usa el radar para traducir señales en zonas y patrones horarios.
 * Es una heurística explícita (no un mapa de demanda medido): cada valor indica de dónde sale.
 */
enum class ZoneProfile {
    /** Oficinas y trámites: picos de lunes a viernes por la mañana y a la salida laboral. */
    CENTRO_LABORAL,
    /** Gastronomía y salidas: noches de jueves a sábado. */
    NOCTURNO,
    /** Barrios residenciales: salidas a la mañana y regresos a la tarde/noche. */
    RESIDENCIAL,
    /** Conurbano: viajes más largos, picos laborales marcados. */
    CONURBANO,
    /** Aeropuerto o terminal: depende de vuelos/arribos, no del reloj. */
    TERMINAL,
}

data class CityZone(
    val id: String,
    val name: String,
    val profile: ZoneProfile,
    /** Corredor aproximado para agrupar zonas cercanas (Norte, Centro, Oeste, Sur). */
    val corridor: String,
    /** Barrios o referencias que permiten reconocer la zona en el texto de una oferta. */
    val aliases: List<String> = emptyList(),
)

enum class VenueKind { ESTADIO_FUTBOL, ARENA, TEATRO, PREDIO, AEROPUERTO, PUERTO, ESTACION }

data class Venue(
    val id: String,
    val name: String,
    val zoneId: String,
    val kind: VenueKind,
    /** Capacidad aproximada de dominio público; se usa como escala relativa, no como asistencia real. */
    val capacity: Int,
    /** Nombres alternativos y equipos locales que se usan para asociar fixtures externos. */
    val aliases: List<String> = emptyList(),
    val homeTeams: List<String> = emptyList(),
)

/** Ciudad soportada. La arquitectura admite varias; el piloto completo es AMBA. */
data class CityPack(
    val id: String,
    val name: String,
    val countryCode: String,
    val currency: String,
    val timeZone: String,
    val zones: List<CityZone>,
    val venues: List<Venue>,
    /** Ligas de fútbol locales en TheSportsDB. */
    val sportsLeagueIds: List<String>,
) {
    fun zone(id: String?): CityZone? = zones.firstOrNull { it.id == id }
    fun venue(id: String?): Venue? = venues.firstOrNull { it.id == id }

    /** Busca una zona por nombre visible (compatibilidad con la zona elegida en Inicio). */
    fun zoneByName(name: String?): CityZone? {
        if (name.isNullOrBlank()) return null
        val key = fold(name)
        return zones.firstOrNull { fold(it.name) == key || it.aliases.any { a -> fold(a) == key } }
    }

    /** Asocia un fixture a un estadio por nombre de estadio o equipo local. */
    fun matchVenue(venueName: String?, homeTeam: String?): Venue? {
        val v = venueName?.let(::fold).orEmpty()
        val h = homeTeam?.let(::fold).orEmpty()
        if (v.isNotBlank()) {
            // Alias con límites de palabra (evita que "EZE" o "AEP" aparezcan dentro de otras palabras);
            // el nombre del feed puede ser más corto que el alias solo si es suficientemente específico.
            // Gana el alias más largo: "Estadio Único Diego Armando Maradona" no debe caer en el de Argentinos Juniors.
            venues.mapNotNull { venue ->
                (listOf(venue.name) + venue.aliases).map(::fold).filter { a ->
                    a.isNotBlank() && (Regex("(?<![\\p{L}\\d])" + Regex.escape(a) + "(?![\\p{L}\\d])").containsMatchIn(v) || (v.length >= 10 && a.contains(v)))
                }.maxOfOrNull { it.length }?.let { venue to it }
            }.maxByOrNull { it.second }?.first
                ?.let { return it }
        }
        if (h.isNotBlank()) {
            venues.firstOrNull { venue -> venue.homeTeams.any { fold(it) == h } }?.let { return it }
        }
        return null
    }

    companion object {
        fun fold(s: String): String =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("\\s+"), " ").trim()
    }
}

/** Catálogo del piloto: Ciudad de Buenos Aires y conurbano (AMBA). */
object Cities {

    val AMBA = CityPack(
        id = "amba",
        name = "Buenos Aires (AMBA)",
        countryCode = "AR",
        currency = "ARS",
        timeZone = "America/Argentina/Buenos_Aires",
        zones = listOf(
            CityZone("microcentro", "Microcentro", ZoneProfile.CENTRO_LABORAL, "Centro", listOf("San Nicolás", "Monserrat", "Plaza de Mayo", "Obelisco", "Tribunales", "Congreso")),
            CityZone("puerto_madero", "Puerto Madero", ZoneProfile.CENTRO_LABORAL, "Centro", listOf("Costanera Sur", "Dique")),
            CityZone("retiro", "Retiro", ZoneProfile.CENTRO_LABORAL, "Centro", listOf("Terminal de Ómnibus", "Estación Retiro", "Terminal Quinquela Martín", "Dársena Norte", "Plaza San Martín")),
            CityZone("recoleta", "Recoleta", ZoneProfile.NOCTURNO, "Norte", listOf("Barrio Norte", "Plaza Francia")),
            CityZone("palermo", "Palermo", ZoneProfile.NOCTURNO, "Norte", listOf("Palermo Soho", "Palermo Hollywood", "Las Cañitas", "Palermo Chico", "La Rural", "Hipódromo de Palermo", "Campo de Polo")),
            CityZone("aeroparque", "Aeroparque", ZoneProfile.TERMINAL, "Norte", listOf("Aeroparque Jorge Newbery", "Aeroparque", "AEP", "Costanera Norte", "Costa Salguero")),
            CityZone("belgrano", "Belgrano", ZoneProfile.RESIDENCIAL, "Norte", listOf("Barrio Chino", "Belgrano R", "Colegiales")),
            CityZone("nunez", "Núñez", ZoneProfile.RESIDENCIAL, "Norte", listOf("Nuñez", "Saavedra", "Monumental", "Ciudad Universitaria")),
            CityZone("villa_urquiza", "Villa Urquiza", ZoneProfile.RESIDENCIAL, "Norte", listOf("Coghlan", "Villa Pueyrredón", "Parque Chas")),
            CityZone("villa_crespo", "Villa Crespo", ZoneProfile.NOCTURNO, "Centro", listOf("Movistar Arena", "Chacarita", "Paternal", "La Paternal")),
            CityZone("almagro", "Almagro", ZoneProfile.RESIDENCIAL, "Centro", listOf("Balvanera", "Plaza Miserere", "Abasto", "Boedo")),
            CityZone("caballito", "Caballito", ZoneProfile.RESIDENCIAL, "Oeste", listOf("Parque Centenario", "Primera Junta")),
            CityZone("flores", "Flores", ZoneProfile.RESIDENCIAL, "Oeste", listOf("Floresta", "Bajo Flores", "Parque Chacabuco", "Villa Soldati")),
            CityZone("liniers", "Liniers", ZoneProfile.RESIDENCIAL, "Oeste", listOf("Mataderos", "Villa Luro", "Versalles", "Vélez")),
            CityZone("devoto", "Villa Devoto", ZoneProfile.RESIDENCIAL, "Oeste", listOf("Devoto", "Villa del Parque", "Agronomía")),
            CityZone("san_telmo", "San Telmo", ZoneProfile.NOCTURNO, "Centro", listOf("Constitución", "Estación Constitución")),
            CityZone("la_boca", "La Boca", ZoneProfile.RESIDENCIAL, "Sur", listOf("Bombonera", "Barracas", "Caminito", "Parque Patricios", "Pompeya", "Nueva Pompeya")),
            CityZone("avellaneda", "Avellaneda", ZoneProfile.CONURBANO, "Sur", listOf("Sarandí", "Dock Sud", "Wilde")),
            CityZone("lanus", "Lanús", ZoneProfile.CONURBANO, "Sur", listOf("Lanus", "Remedios de Escalada", "Valentín Alsina")),
            CityZone("lomas", "Lomas de Zamora", ZoneProfile.CONURBANO, "Sur", listOf("Lomas", "Banfield", "Temperley", "Adrogué")),
            CityZone("quilmes", "Quilmes", ZoneProfile.CONURBANO, "Sur", listOf("Bernal", "Ezpeleta", "Berazategui", "Florencio Varela")),
            CityZone("vicente_lopez", "Vicente López", ZoneProfile.CONURBANO, "Norte", listOf("Olivos", "Munro", "Villa Martelli", "Tecnópolis")),
            CityZone("san_isidro", "San Isidro", ZoneProfile.CONURBANO, "Norte", listOf("Martínez", "Acassuso", "Béccar", "Boulogne", "Tigre", "Nordelta")),
            CityZone("moron", "Morón", ZoneProfile.CONURBANO, "Oeste", listOf("Haedo", "Ramos Mejía", "San Justo", "Castelar", "Ituzaingó")),
            CityZone("ezeiza", "Ezeiza", ZoneProfile.TERMINAL, "Sur", listOf("Aeropuerto de Ezeiza", "Aeropuerto Internacional Ministro Pistarini", "EZE", "Aeropuerto Internacional")),
            CityZone("la_plata", "La Plata", ZoneProfile.CONURBANO, "Sur", listOf("Estadio Único", "City Bell", "Gonnet")),
        ),
        venues = listOf(
            Venue("monumental", "Estadio Mas Monumental", "nunez", VenueKind.ESTADIO_FUTBOL, 85_000, listOf("Monumental", "Antonio Vespucio Liberti", "Más Monumental"), listOf("River Plate")),
            Venue("bombonera", "La Bombonera", "la_boca", VenueKind.ESTADIO_FUTBOL, 57_000, listOf("Alberto José Armando", "Alberto J. Armando", "Bombonera"), listOf("Boca Juniors")),
            Venue("cilindro", "Estadio Presidente Perón", "avellaneda", VenueKind.ESTADIO_FUTBOL, 51_000, listOf("Juan Domingo Perón", "El Cilindro", "Presidente Peron"), listOf("Racing Club")),
            Venue("libertadores", "Estadio Libertadores de América", "avellaneda", VenueKind.ESTADIO_FUTBOL, 48_000, listOf("Libertadores de América", "Ricardo Enrique Bochini"), listOf("Independiente")),
            Venue("gasometro", "Estadio Pedro Bidegain", "flores", VenueKind.ESTADIO_FUTBOL, 47_000, listOf("Nuevo Gasómetro", "Pedro Bidegain"), listOf("San Lorenzo", "San Lorenzo de Almagro")),
            Venue("amalfitani", "Estadio José Amalfitani", "liniers", VenueKind.ESTADIO_FUTBOL, 45_000, listOf("José Amalfitani", "Jose Amalfitani"), listOf("Vélez Sarsfield", "Velez Sarsfield")),
            Venue("duco", "Estadio Tomás A. Ducó", "la_boca", VenueKind.ESTADIO_FUTBOL, 48_000, listOf("Tomás Adolfo Ducó", "Tomas A. Duco", "Palacio Ducó"), listOf("Huracán", "Huracan")),
            Venue("lanus_stadium", "Estadio Ciudad de Lanús", "lanus", VenueKind.ESTADIO_FUTBOL, 47_000, listOf("Ciudad de Lanús", "Néstor Díaz Pérez"), listOf("Lanús", "Lanus")),
            Venue("florencio_sola", "Estadio Florencio Sola", "lomas", VenueKind.ESTADIO_FUTBOL, 34_000, listOf("Florencio Sola"), listOf("Banfield")),
            Venue("maradona_ajs", "Estadio Diego Armando Maradona", "villa_crespo", VenueKind.ESTADIO_FUTBOL, 25_000, listOf("Diego Armando Maradona", "Diego A. Maradona"), listOf("Argentinos Juniors")),
            Venue("vicente_lopez_stadium", "Estadio Ciudad de Vicente López", "vicente_lopez", VenueKind.ESTADIO_FUTBOL, 28_000, listOf("Ciudad de Vicente López"), listOf("Platense")),
            Venue("tigre_stadium", "Estadio José Dellagiovanna", "san_isidro", VenueKind.ESTADIO_FUTBOL, 26_000, listOf("José Dellagiovanna", "Jose Dellagiovanna"), listOf("Tigre")),
            Venue("unico_lp", "Estadio Único Diego Armando Maradona", "la_plata", VenueKind.ESTADIO_FUTBOL, 53_000, listOf("Estadio Único", "Ciudad de La Plata"), emptyList()),
            Venue("uno_lp", "Estadio Jorge Luis Hirschi", "la_plata", VenueKind.ESTADIO_FUTBOL, 30_000, listOf("Jorge Luis Hirschi", "UNO"), listOf("Estudiantes", "Estudiantes de La Plata", "Estudiantes L.P.")),
            Venue("bosque_lp", "Estadio Juan Carmelo Zerillo", "la_plata", VenueKind.ESTADIO_FUTBOL, 24_000, listOf("Juan Carmelo Zerillo", "El Bosque"), listOf("Gimnasia La Plata", "Gimnasia L.P.", "Gimnasia y Esgrima La Plata")),
            Venue("defensa_stadium", "Estadio Norberto Tomaghello", "quilmes", VenueKind.ESTADIO_FUTBOL, 20_000, listOf("Norberto Tomaghello"), listOf("Defensa y Justicia")),
            Venue("quilmes_stadium", "Estadio Centenario", "quilmes", VenueKind.ESTADIO_FUTBOL, 30_000, listOf("Centenario Dr. José Luis Meiszner"), listOf("Quilmes")),
            Venue("barracas_stadium", "Estadio Claudio Chiqui Tapia", "la_boca", VenueKind.ESTADIO_FUTBOL, 4_500, listOf("Claudio Chiqui Tapia"), listOf("Barracas Central")),
            Venue("movistar_arena", "Movistar Arena", "villa_crespo", VenueKind.ARENA, 15_000, listOf("Movistar Arena")),
            Venue("luna_park", "Luna Park", "microcentro", VenueKind.ARENA, 8_400, listOf("Luna Park")),
            Venue("obras", "Estadio Obras", "nunez", VenueKind.ARENA, 4_700, listOf("Obras Sanitarias", "Estadio Obras")),
            Venue("colon", "Teatro Colón", "microcentro", VenueKind.TEATRO, 2_500, listOf("Teatro Colón")),
            Venue("hipodromo_palermo", "Hipódromo de Palermo", "palermo", VenueKind.PREDIO, 30_000, listOf("Hipódromo Argentino", "Hipódromo de Palermo")),
            Venue("campo_polo", "Campo Argentino de Polo", "palermo", VenueKind.PREDIO, 30_000, listOf("Campo Argentino de Polo", "Campo de Polo")),
            Venue("la_rural", "La Rural", "palermo", VenueKind.PREDIO, 20_000, listOf("La Rural", "Predio Ferial de Palermo")),
            Venue("tecnopolis", "Tecnópolis", "vicente_lopez", VenueKind.PREDIO, 40_000, listOf("Tecnópolis")),
            Venue("costa_salguero", "Costa Salguero", "aeroparque", VenueKind.PREDIO, 15_000, listOf("Costa Salguero", "Centro Costa Salguero")),
            Venue("aep", "Aeroparque Jorge Newbery", "aeroparque", VenueKind.AEROPUERTO, 0, listOf("Aeroparque", "AEP", "SABE")),
            Venue("eze", "Aeropuerto Internacional de Ezeiza", "ezeiza", VenueKind.AEROPUERTO, 0, listOf("Ezeiza", "EZE", "SAEZ", "Ministro Pistarini")),
            Venue("puerto_cruceros", "Terminal de Cruceros Benito Quinquela Martín", "retiro", VenueKind.PUERTO, 0, listOf("Quinquela Martín", "Terminal de Cruceros", "Puerto de Buenos Aires")),
            Venue("retiro_terminal", "Terminal de Ómnibus de Retiro", "retiro", VenueKind.ESTACION, 0, listOf("Terminal de Ómnibus", "Estación Retiro")),
            Venue("constitucion", "Estación Constitución", "san_telmo", VenueKind.ESTACION, 0, listOf("Constitución")),
            Venue("once", "Estación Once", "almagro", VenueKind.ESTACION, 0, listOf("Estación Once", "Plaza Miserere")),
        ),
        sportsLeagueIds = listOf("4406"),
    )

    val ALL = listOf(AMBA)

    fun byId(id: String?): CityPack = ALL.firstOrNull { it.id == id } ?: AMBA
}
