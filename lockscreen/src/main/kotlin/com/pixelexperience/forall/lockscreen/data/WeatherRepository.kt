package com.pixelexperience.forall.lockscreen.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

data class WeatherUi(
    val temp: Int, val code: Int, val isDay: Boolean, val fahrenheit: Boolean,
    val todayMax: Int? = null, val tomorrowMax: Int? = null, val city: String? = null,
)

/**
 * Clima atual via Open-Meteo (gratuito, sem chave). Usa a ÚLTIMA localização aproximada conhecida
 * (permissão ACCESS_COARSE_LOCATION, sem GPS ativo). Só as coordenadas aproximadas são enviadas.
 */
object WeatherRepository {
    val state = MutableStateFlow<WeatherUi?>(null)
    @Volatile private var lastOk = 0L

    suspend fun refresh(ctx: Context, force: Boolean = false) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && state.value != null && now - lastOk < 30 * 60_000L) return@withContext
        val app = ctx.applicationContext
        val loc = lastLocation(app) ?: return@withContext
        runCatching {
            val f = Locale.getDefault().country in setOf("US", "LR", "MM")
            val url = URL(
                "https://api.open-meteo.com/v1/forecast?latitude=${loc.first}&longitude=${loc.second}" +
                    "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max&forecast_days=2&timezone=auto" + if (f) "&temperature_unit=fahrenheit" else ""
            )
            val c = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 8000 }
            try {
                val root = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                val cur = root.getJSONObject("current")
                val max = root.optJSONObject("daily")?.optJSONArray("temperature_2m_max")
                state.value = WeatherUi(
                    cur.getDouble("temperature_2m").roundToInt(), cur.getInt("weather_code"), cur.getInt("is_day") == 1, f,
                    todayMax = max?.takeIf { it.length() > 0 }?.getDouble(0)?.roundToInt(),
                    tomorrowMax = max?.takeIf { it.length() > 1 }?.getDouble(1)?.roundToInt(),
                    city = cityOf(app, loc.first, loc.second),
                )
                lastOk = now
            } finally { c.disconnect() }
        }
    }

    /** Nome da cidade pelo Geocoder do sistema (pode não existir em aparelhos sem serviços do Google: então fica sem cidade). */
    @Suppress("DEPRECATION")
    private fun cityOf(ctx: Context, lat: Double, lon: Double): String? {
        val prefs = ctx.getSharedPreferences("pxe_weather", Context.MODE_PRIVATE)
        val key = "%.2f,%.2f".format(Locale.US, lat, lon)
        if (prefs.getString("city_key", null) == key) return prefs.getString("city", null)
        val name = runCatching {
            if (!android.location.Geocoder.isPresent()) return@runCatching null
            android.location.Geocoder(ctx, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
                ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
        }.getOrNull()
        if (name != null) prefs.edit().putString("city_key", key).putString("city", name).apply()
        return name
    }

    private fun lastLocation(ctx: Context): Pair<Double, Double>? {
        val prefs = ctx.getSharedPreferences("pxe_weather", Context.MODE_PRIVATE)
        val granted = ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            runCatching {
                val lm = ctx.getSystemService(LocationManager::class.java)
                val best: Location? = lm.getProviders(true)
                    .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                    .maxByOrNull { it.time }
                if (best != null) {
                    prefs.edit().putFloat("lat", best.latitude.toFloat()).putFloat("lon", best.longitude.toFloat()).apply()
                    return best.latitude to best.longitude
                }
            }
        }
        // sem permissão ou sem fix: usa a última posição guardada (se houver)
        if (!prefs.contains("lat")) return null
        return prefs.getFloat("lat", 0f).toDouble() to prefs.getFloat("lon", 0f).toDouble()
    }
}
