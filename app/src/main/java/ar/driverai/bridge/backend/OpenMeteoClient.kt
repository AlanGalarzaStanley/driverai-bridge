package ar.driverai.bridge.backend

import ar.driverai.bridge.data.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Clima real desde Open-Meteo (sin API key).
 * Fuente: https://open-meteo.com/en/docs — no es demanda de apps ni eventos.
 */
class OpenMeteoClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build(),
) {
    data class Weather(
        val temperatureC: Double?,
        val windKmh: Double?,
        val precipitationMm: Double?,
        val rainNow: Boolean,
        val precipProbNextHours: Int?,
        val source: String = "Open-Meteo",
        val fetchedAtMs: Long = System.currentTimeMillis(),
    )

    @Serializable
    private data class Current(
        @SerialName("temperature_2m") val temperature2m: Double? = null,
        @SerialName("precipitation") val precipitation: Double? = null,
        @SerialName("wind_speed_10m") val windSpeed10m: Double? = null,
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
            append("https://api.open-meteo.com/v1/forecast")
            append("?latitude=-34.6037&longitude=-58.3816")
            append("&current=temperature_2m,precipitation,wind_speed_10m")
            append("&hourly=precipitation_probability")
            append("&forecast_hours=3")
            append("&timezone=America%2FArgentina%2FBuenos_Aires")
            append("&wind_speed_unit=kmh")
        }
        val req = Request.Builder().url(url).get().header("Accept", "application/json").build()
        try {
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext ApiResult.Fail(ApiResult.Kind.HTTP, "HTTP ${resp.code}")
                val parsed = try {
                    AppJson.decodeFromString(Response.serializer(), text)
                } catch (e: Exception) {
                    return@withContext ApiResult.Fail(ApiResult.Kind.PARSE, "Respuesta de clima inesperada")
                }
                val cur = parsed.current
                val precip = cur?.precipitation ?: 0.0
                val probs = parsed.hourly?.precipitationProbability.orEmpty().filterNotNull()
                val maxProb = probs.maxOrNull()
                ApiResult.Ok(
                    Weather(
                        temperatureC = cur?.temperature2m,
                        windKmh = cur?.windSpeed10m,
                        precipitationMm = precip,
                        rainNow = precip > 0.05,
                        precipProbNextHours = maxProb,
                    ),
                )
            }
        } catch (e: Exception) {
            ApiResult.Fail(ApiResult.Kind.NETWORK, "Sin clima (Open-Meteo)")
        }
    }
}
