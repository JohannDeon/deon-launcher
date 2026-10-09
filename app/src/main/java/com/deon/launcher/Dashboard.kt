package com.deon.launcher

import android.content.Context
import android.location.Geocoder
import android.location.Location
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

// --- Weather (Open-Meteo, no API key) ---

private const val WEATHER_REFRESH_MS = 30 * 60_000L

private class Weather(val temp: Double, val code: Int, val min: Double, val max: Double, val city: String?, val day: Boolean)

private enum class Sky { SUN, PARTLY, CLOUD, FOG, RAIN, SNOW, STORM }

private fun sky(code: Int) = when (code) {
    0 -> Sky.SUN
    1, 2 -> Sky.PARTLY
    3 -> Sky.CLOUD
    45, 48 -> Sky.FOG
    in 71..77, 85, 86 -> Sky.SNOW
    in 95..99 -> Sky.STORM
    else -> Sky.RAIN
}

private fun describe(code: Int) = when (code) {
    0 -> "Ciel dégagé"
    1 -> "Plutôt dégagé"
    2 -> "Peu nuageux"
    3 -> "Couvert"
    45, 48 -> "Brouillard"
    in 51..57 -> "Bruine"
    in 61..67 -> "Pluie"
    in 71..77 -> "Neige"
    in 80..82 -> "Averses"
    85, 86 -> "Averses de neige"
    in 95..99 -> "Orage"
    else -> "—"
}

private suspend fun fetchWeather(context: Context, loc: Location): Weather? = withContext(Dispatchers.IO) {
    runCatching {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f".format(Locale.US, loc.latitude, loc.longitude) +
                "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 8000 }
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val cur = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        @Suppress("DEPRECATION")
        val city = runCatching {
            Geocoder(context, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()?.locality
        }.getOrNull()
        Weather(
            cur.getDouble("temperature_2m"), cur.getInt("weather_code"),
            daily.getJSONArray("temperature_2m_min").getDouble(0),
            daily.getJSONArray("temperature_2m_max").getDouble(0),
            city, cur.optInt("is_day", 1) == 1
        )
    }.getOrNull()
}

@Composable
fun WeatherPanel(location: () -> Location?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    val unit = LocalSettings.current.tempUnit
    fun deg(t: Double) = (if (unit == TempUnit.F) t * 9 / 5 + 32 else t).roundToInt()
    var weather by remember { mutableStateOf<Weather?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            // Wait for a position, then refresh every half hour (sooner while nothing is shown yet).
            val loc = location()
            val fresh = loc?.let { fetchWeather(context, it) }
            if (fresh != null) weather = fresh
            delay(if (weather == null) 15_000L else WEATHER_REFRESH_MS)
        }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val w = weather
        WeatherIcon(if (w == null) Sky.PARTLY else sky(w.code), w?.day ?: true, c.primary)
        Spacer(Modifier.width(18.dp))
        if (w == null) {
            Text("Météo en attente de la position…", color = c.onSurfaceVariant, fontSize = 15.sp)
            return@Row
        }
        Text("${deg(w.temp)}°", color = c.onSurface, fontSize = 46.sp, fontWeight = FontWeight.ExtraLight)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(describe(w.code), color = c.onSurface, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(w.city, "${deg(w.min)}° / ${deg(w.max)}°").joinToString("  ·  "),
                color = c.onSurfaceVariant, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** coolicons has no weather set: a main glyph (sun, moon, cloud) with a small badge for rain, snow or storm. */
@Composable
private fun WeatherIcon(sky: Sky, day: Boolean, color: Color) = Box(Modifier.size(52.dp)) {
    val main = when (sky) {
        Sky.SUN -> if (day) Ui.Sun else Ui.Moon
        else -> Ui.Cloud
    }
    if (sky == Sky.PARTLY) UIcon(if (day) Ui.Sun else Ui.Moon, color.copy(alpha = .7f), 22.sp, Modifier.align(Alignment.TopEnd))
    UIcon(main, color, 40.sp, Modifier.align(Alignment.Center))
    val badge = when (sky) {
        Sky.RAIN -> Ui.Drop
        Sky.SNOW -> Ui.Star
        Sky.STORM -> Ui.Warning
        Sky.FOG -> Ui.Wavy
        else -> null
    }
    badge?.let { UIcon(it, color, 18.sp, Modifier.align(Alignment.BottomEnd)) }
}
