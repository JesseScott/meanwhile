package tt.co.jesses.meanwhile.core

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.time.Instant
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Rough ocean basin for a point, from simple lat/lon rules. Good enough to say "the Indian Ocean";
 * it ignores marginal seas and gets the Atlantic/Pacific split wrong near the Americas' tips.
 */
fun oceanNameAt(point: LatLon): String = when {
    point.lat <= -60.0 -> "Southern Ocean"
    point.lat >= 66.0 -> "Arctic Ocean"
    point.lon >= -70.0 && point.lon < 20.0 -> "Atlantic Ocean"
    point.lon >= 20.0 && point.lon < 120.0 -> "Indian Ocean"
    else -> "Pacific Ocean"
}

enum class DayPhase { Day, Twilight, Night }

/** Height of the sun above the horizon in degrees, from the NOAA low-precision solar position formulas. */
fun sunAltitudeDeg(point: LatLon, at: Instant): Double {
    val daysSinceJ2000 = at.epochSecond / 86_400.0 + 2_440_587.5 - 2_451_545.0
    val meanLongitude = normalizeDeg(280.460 + 0.9856474 * daysSinceJ2000)
    val meanAnomaly = Math.toRadians(normalizeDeg(357.528 + 0.9856003 * daysSinceJ2000))
    val eclipticLongitude =
        Math.toRadians(meanLongitude + 1.915 * sin(meanAnomaly) + 0.020 * sin(2 * meanAnomaly))
    val obliquity = Math.toRadians(23.439 - 0.0000004 * daysSinceJ2000)

    val declination = asin(sin(obliquity) * sin(eclipticLongitude))
    val rightAscension = atan2(cos(obliquity) * sin(eclipticLongitude), cos(eclipticLongitude))

    val gmstDeg = normalizeDeg((18.697374558 + 24.06570982441908 * daysSinceJ2000) * 15.0)
    val hourAngle = Math.toRadians(gmstDeg + point.lon) - rightAscension

    val lat = Math.toRadians(point.lat)
    val altitude = asin(sin(lat) * sin(declination) + cos(lat) * cos(declination) * cos(hourAngle))
    return Math.toDegrees(altitude)
}

fun dayPhaseOf(sunAltitudeDeg: Double): DayPhase = when {
    sunAltitudeDeg >= 0.0 -> DayPhase.Day
    sunAltitudeDeg >= -6.0 -> DayPhase.Twilight
    else -> DayPhase.Night
}

private fun normalizeDeg(deg: Double): Double = deg - 360.0 * floor(deg / 360.0)

private val COMPASS_16 = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")

fun compassPoint(deg: Double): String = COMPASS_16[((normalizeDeg(deg) + 11.25) / 22.5).toInt() % 16]

/** Douglas sea scale, by significant wave height in metres. */
fun seaState(waveHeightM: Double): String = when {
    waveHeightM < 0.1 -> "Calm"
    waveHeightM < 0.5 -> "Calm, rippled"
    waveHeightM < 1.25 -> "Smooth"
    waveHeightM < 2.5 -> "Slight"
    waveHeightM < 4.0 -> "Moderate"
    waveHeightM < 6.0 -> "Rough"
    waveHeightM < 9.0 -> "Very rough"
    waveHeightM < 14.0 -> "High"
    else -> "Phenomenal"
}

data class MarineConditions(
    val waveHeightM: Double?,
    val wavePeriodS: Double?,
    /** Direction the waves come from, in degrees. */
    val waveDirectionDeg: Double?,
    val swellHeightM: Double?,
    val seaTempC: Double?,
    val currentKmh: Double?,
) {
    val hasAnyData: Boolean
        get() = listOf(waveHeightM, wavePeriodS, waveDirectionDeg, swellHeightM, seaTempC, currentKmh).any { it != null }
}

@Serializable
private data class MarineResponse(val current: MarineCurrent? = null)

@Serializable
private data class MarineCurrent(
    @SerialName("wave_height") val waveHeight: Double? = null,
    @SerialName("wave_period") val wavePeriod: Double? = null,
    @SerialName("wave_direction") val waveDirection: Double? = null,
    @SerialName("swell_wave_height") val swellWaveHeight: Double? = null,
    @SerialName("sea_surface_temperature") val seaSurfaceTemperature: Double? = null,
    @SerialName("ocean_current_velocity") val oceanCurrentVelocity: Double? = null,
)

/** Null when the body isn't a marine response, which is what a point with no sea data gives. */
fun parseMarine(body: String): MarineConditions? {
    val text = body.trim()
    if (!text.startsWith("{")) return null
    val current = try {
        NewsJson.decodeFromString<MarineResponse>(text).current
    } catch (e: SerializationException) {
        null
    } ?: return null
    return MarineConditions(
        waveHeightM = current.waveHeight,
        wavePeriodS = current.wavePeriod,
        waveDirectionDeg = current.waveDirection,
        swellHeightM = current.swellWaveHeight,
        seaTempC = current.seaSurfaceTemperature,
        currentKmh = current.oceanCurrentVelocity,
    )
}

interface MarineSource {
    suspend fun conditions(point: LatLon): MarineConditions?
}

/** Open-Meteo Marine API: free for non-commercial use, needs attribution (CC BY 4.0). */
class OpenMeteoMarineSource(
    private val client: HttpClient,
    private val baseUrl: String = "https://marine-api.open-meteo.com/v1/marine",
) : MarineSource {
    override suspend fun conditions(point: LatLon): MarineConditions? = Trace.timed("marine ${point.lat}, ${point.lon}") {
        val response = client.get(baseUrl) {
            parameter("latitude", point.lat)
            parameter("longitude", point.lon)
            parameter(
                "current",
                "wave_height,wave_period,wave_direction,swell_wave_height,sea_surface_temperature,ocean_current_velocity",
            )
        }
        if (response.status != HttpStatusCode.OK) error("Open-Meteo returned ${response.status.value}")
        parseMarine(response.bodyAsText())?.takeIf { it.hasAnyData }
    }
}
