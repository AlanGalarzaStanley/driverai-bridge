package ar.driverai.bridge.plan

import kotlinx.serialization.Serializable

/** Schema versionado del Plan del Día. Sin inventar: si falta fuente, no se muestra o se marca. */
object PlanDiaSchema {
    const val VERSION = 1
}

@Serializable
enum class BloqueTipo { partido, recital, aeropuerto, terminal, clima, otro }

@Serializable
enum class Confianza { alta, media, baja }

@Serializable
enum class FinFuente { oficial, estimado }

@Serializable
data class PlanLugar(
    val nombre: String,
    val direccion: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val confirmado: Boolean = true,
)

@Serializable
data class VentanaPico(val desde: Long, val hasta: Long)

@Serializable
data class PuntoEspera(
    val calle: String,
    val motivo: String,
    val minDesdeLugar: Int,
)

@Serializable
data class FuenteRef(
    val nombre: String,
    val url: String? = null,
    val consultadoAt: Long,
)

@Serializable
data class PlanBloque(
    val id: String,
    val tipo: BloqueTipo,
    val nombre: String,
    val lugar: PlanLugar?,
    val lugarNoConfirmado: Boolean = false,
    val inicio: Long,
    val finEstimado: Long,
    val finFuente: FinFuente,
    val ventanaPico: VentanaPico,
    val puntosEspera: List<PuntoEspera> = emptyList(),
    val destinosLargos: List<String> = emptyList(),
    val confianza: Confianza,
    val fuentes: List<FuenteRef> = emptyList(),
    val avisoTexto: String? = null,
)

@Serializable
data class PlanHueco(
    val desde: Long,
    val hasta: Long,
    val sugerencia: String,
)

@Serializable
data class ZonaPrecaucion(
    val tramo: String,
    val franjaDesde: Long,
    val franjaHasta: Long,
    val motivo: String,
    val confianza: Confianza,
    val fuente: String,
)

@Serializable
data class PlanDia(
    val schemaVersion: Int = PlanDiaSchema.VERSION,
    val generadoAt: Long,
    val vigenciaHasta: Long,
    val zonaBase: String,
    val bloques: List<PlanBloque> = emptyList(),
    val huecos: List<PlanHueco> = emptyList(),
    val zonasPrecaucion: List<ZonaPrecaucion> = emptyList(),
    val supuestos: List<String> = emptyList(),
) {
    fun isExpired(now: Long): Boolean = now > vigenciaHasta
}

@Serializable
enum class AvisoTipo { SALI_HACIA, PICO_EN_30, LEJOS_DEL_PUNTO }

@Serializable
data class PlanAviso(
    val bloqueId: String,
    val tipo: AvisoTipo,
    val texto: String,
    val disparaAt: Long,
)
