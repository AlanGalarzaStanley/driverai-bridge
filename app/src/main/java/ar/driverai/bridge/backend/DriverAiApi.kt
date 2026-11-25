package ar.driverai.bridge.backend

import ar.driverai.bridge.data.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

// Contratos verificados contra el backend real el 2026-10-17 (ver docs/INITIAL_AUDIT.md).
// Todos los campos son opcionales: el backend genera parte del contenido con un LLM.

@Serializable data class Clima(
    val lluvia_mm: Double? = null, val viento_kmh: Double? = null, val temperatura: Double? = null,
    val lluvia_ahora: Boolean? = null, val prob_lluvia_3h: Double? = null, val lluvia_proxima_3h: Boolean? = null,
)
@Serializable data class ZonaRank(val zona: String, val score: Double? = null, val razon: String? = null, val mejor_horario: String? = null)
@Serializable data class ZonaScore(val zona: String, val score: Double? = null)
@Serializable data class Reporte(
    val resumen: String? = null,
    val precio_minimo_sugerido: Double? = null,
    val mejor_zona_ahora: String? = null,
    val mejor_zona_en_2h: String? = null,
    val zonas_rankeadas: List<ZonaRank> = emptyList(),
    val estrategia_viajes: String? = null,
    val alertas: List<JsonElement> = emptyList(),
    val factor_lluvia: Boolean? = null,
    val nivel_demanda_general: String? = null,
)
@Serializable data class ReporteResponse(
    val ok: Boolean = false,
    val reporte: Reporte? = null,
    val demanda_por_zona: List<ZonaScore> = emptyList(),
    val clima: Clima? = null,
    val eventos: List<JsonElement>? = null,
    val hora: Int? = null,
    val dia: String? = null,
    val score_turno: Double? = null,
    val multiplicador: Double? = null,
    val hay_partido: Boolean? = null,
    val quincena: Boolean? = null,
    val error: String? = null,
)
@Serializable data class PosicionResponse(
    val ok: Boolean = false,
    val zona_actual: String? = null,
    val score_actual: Double? = null,
    val decision: String? = null,
    val zona_recomendada: String? = null,
    val score_recomendado: Double? = null,
    val alternativas: List<ZonaScore> = emptyList(),
    val espera_estimada_min: Double? = null,
    val tip: String? = null,
    val clima: Clima? = null,
    val hay_partido: Boolean? = null,
    val error: String? = null,
)
@Serializable private data class PosicionRequest(val zona_actual: String)

sealed class ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>()
    data class Fail(val kind: Kind, val message: String) : ApiResult<Nothing>()
    enum class Kind { TIMEOUT, NETWORK, HTTP, PARSE, BACKEND }
}

/**
 * Cliente de las funciones Edge existentes. No incluye claves: las funciones están desplegadas
 * con acceso público (verificado: responden 200 sin Authorization). No se envían capturas ni
 * datos de pasajeros; solo el nombre de la zona elegida por el conductor.
 */
class DriverAiApi(
    private val baseUrl: String,
    private val client: OkHttpClient = defaultClient(),
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun reporte(): ApiResult<ReporteResponse> = post("driverai-reporte", "{}") {
        AppJson.decodeFromString(ReporteResponse.serializer(), it)
    }

    suspend fun posicion(zona: String): ApiResult<PosicionResponse> =
        post("driverai-posicion", AppJson.encodeToString(PosicionRequest.serializer(), PosicionRequest(zona))) {
            AppJson.decodeFromString(PosicionResponse.serializer(), it)
        }

    private suspend fun <T> post(fn: String, body: String, parse: (String) -> T): ApiResult<T> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(baseUrl.trimEnd('/') + "/" + fn)
            .post(body.toRequestBody(jsonType))
            .header("Accept", "application/json")
            .build()
        try {
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext ApiResult.Fail(ApiResult.Kind.HTTP, "HTTP ${resp.code}")
                val parsed = try { parse(text) } catch (e: Exception) {
                    return@withContext ApiResult.Fail(ApiResult.Kind.PARSE, "Respuesta inesperada del servidor")
                }
                val ok = when (parsed) { is ReporteResponse -> parsed.ok; is PosicionResponse -> parsed.ok; else -> true }
                if (!ok) ApiResult.Fail(ApiResult.Kind.BACKEND, "El servidor respondió ok=false") else ApiResult.Ok(parsed)
            }
        } catch (e: SocketTimeoutException) {
            ApiResult.Fail(ApiResult.Kind.TIMEOUT, "El servidor tardó demasiado")
        } catch (e: IOException) {
            ApiResult.Fail(ApiResult.Kind.NETWORK, "Sin conexión con el servidor")
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
