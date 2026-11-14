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

data class WeatherUi(val temp: Int, val code: Int, val isDay: Boolean, val fahrenheit: Boolean)

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
                    "&current=temperature_2m,weather_code,is_day" + if (f) "&temperature_unit=fahrenheit" else ""
            )
            val c = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 8000 }
            try {
                val cur = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).getJSONObject("current")
                state.value = WeatherUi(cur.getDouble("temperature_2m").roundToInt(), cur.getInt("weather_code"), cur.getInt("is_day") == 1, f)
                lastOk = now
            } finally { c.disconnect() }
        }
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
