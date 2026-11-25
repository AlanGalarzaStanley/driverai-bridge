package ar.driverai.bridge.backend

import ar.driverai.bridge.data.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Consulta meteorológica privada para builds debug. La fuente gratuita de Open-Meteo no se usa
 * en builds release: una distribución comercial requiere un plan comercial autorizado/proxy.
 * Coordenadas fijas de referencia en CABA Centro; no se consulta ni transmite GPS del conductor.
 */
class OpenMeteoClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val endpoint: String = "https://api.open-meteo.com/v1/forecast",
) {
    data class Weather(
        val temperatureC: Double?,
        val windKmh: Double?,
        val precipitationMm: Double?,
        val precipitationIntervalSeconds: Int?,
        val precipProbNextHours: Int?,
        val source: String = "Open-Meteo",
        val fetchedAtMs: Long,
    )

    @Serializable
    private data class Current(
        @SerialName("temperature_2m") val temperature2m: Double? = null,
        val precipitation: Double? = null,
        @SerialName("wind_speed_10m") val windSpeed10m: Double? = null,
        val interval: Int? = null,
    )

    @Serializable
    private data class Hourly(
        @SerialName("precipitation_probability") val precipitationProbability: List<Int?> = emptyList(),
    )

    @Serializable
    private data class Response(
        val current: Current? = null,
        val hourly: Hourly? = null,
    )

    suspend fun fetchBuenosAires(): ApiResult<Weather> = withContext(Dispatchers.IO) {
        val url = buildString {
            append(endpoint)
            append("?latitude=-34.6037&longitude=-58.3816")
            append("&current=temperature_2m,precipitation,wind_speed_10m")
            append("&hourly=precipitation_probability")
            append("&forecast_hours=3")
            append("&timezone=America%2FArgentina%2FBuenos_Aires")
            append("&wind_speed_unit=kmh")
        }
        val request = Request.Builder().url(url).get().header("Accept", "application/json").build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@withContext ApiResult.Fail(ApiResult.Kind.HTTP, "HTTP ${response.code}")
                val parsed = try {
                    AppJson.decodeFromString(Response.serializer(), body)
                } catch (_: Exception) {
                    return@withContext ApiResult.Fail(ApiResult.Kind.PARSE, "Respuesta de clima inesperada")
                }
                val current = parsed.current
                val probabilities = parsed.hourly?.precipitationProbability.orEmpty().take(3).filterNotNull()
                if (current?.temperature2m == null && current?.windSpeed10m == null && current?.precipitation == null && probabilities.isEmpty()) {
                    return@withContext ApiResult.Fail(ApiResult.Kind.PARSE, "La fuente no devolvió variables meteorológicas")
                }
                ApiResult.Ok(
                    Weather(
                        temperatureC = current?.temperature2m,
                        windKmh = current?.windSpeed10m,
                        precipitationMm = current?.precipitation,
                        precipitationIntervalSeconds = current?.interval,
                        precipProbNextHours = probabilities.maxOrNull(),
                        fetchedAtMs = clock(),
                    ),
                )
            }
        } catch (_: IOException) {
            ApiResult.Fail(ApiResult.Kind.NETWORK, "Sin conexión con Open-Meteo")
        } catch (_: Exception) {
            ApiResult.Fail(ApiResult.Kind.NETWORK, "No se pudo actualizar el clima")
        }
    }
}
