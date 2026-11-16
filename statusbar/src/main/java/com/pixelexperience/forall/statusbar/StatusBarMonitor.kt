package com.pixelexperience.forall.statusbar

import android.Manifest
import android.app.NotificationManager
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.text.format.DateFormat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class StatusBarState(
    val time: String = "",
    val vpn: Boolean = false,
    val ringerMode: Int = AudioManager.RINGER_MODE_NORMAL,
    val dnd: Boolean = false,
    val bluetooth: Boolean = false,
    val signalLevel: Int = 0,
    val wifi: Boolean = false,
    val wifiLevel: Int = 0,
    val batteryPct: Int = 100,
    val charging: Boolean = false,
    val powerSaver: Boolean = false,
    val showMute: Boolean = true,
    val showVibrate: Boolean = true,
    val showBatteryPct: Boolean = true,
    val notifMode: Int = 0,
    val iconMode: Int = 0,
    val wallpaperLight: Boolean = false
) {
    /** Ícones escuros: manual (modo 2) ou, no automático, quando o papel de parede é claro. */
    val darkIcons: Boolean
        get() = when (iconMode) {
            1 -> false
            2 -> true
            else -> wallpaperLight
        }
}

/** Lê dados reais do sistema e os expõe como estado do Compose. */
internal class StatusBarMonitor(context: Context) {

    private val ctx = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private val cm = ctx.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = ctx.getSystemService(WifiManager::class.java)
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private val power = ctx.getSystemService(PowerManager::class.java)
    private val telephony = ctx.getSystemService(TelephonyManager::class.java)
    private val wallpaper = ctx.getSystemService(WallpaperManager::class.java)
    private val bluetoothManager = ctx.getSystemService(BluetoothManager::class.java)

    var state by mutableStateOf(StatusBarState())
        private set

    private var started = false

    @Volatile
    private var wifiOnDefault = false

    @Volatile
    private var vpnCount = 0

    private fun update(block: (StatusBarState) -> StatusBarState) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            state = block(state)
        } else {
            main.post { state = block(state) }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> readBattery(intent)
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED ->
                    update { it.copy(powerSaver = power.isPowerSaveMode) }
                AudioManager.RINGER_MODE_CHANGED_ACTION ->
                    update { it.copy(ringerMode = audio.ringerMode) }
                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED ->
                    update { it.copy(dnd = isDnd()) }
                BluetoothAdapter.ACTION_STATE_CHANGED,
                BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> refreshBluetooth()
                WifiManager.RSSI_CHANGED_ACTION,
                WifiManager.NETWORK_STATE_CHANGED_ACTION -> refreshWifi()
                else -> update { it.copy(time = formatTime()) }
            }
        }
    }

    private val defaultCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            wifiOnDefault = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            refreshWifi()
        }

        override fun onLost(network: Network) {
            wifiOnDefault = false
            refreshWifi()
        }
    }

    private val vpnCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            vpnCount++
            val on = vpnCount > 0
            update { it.copy(vpn = on) }
        }

        override fun onLost(network: Network) {
            if (vpnCount > 0) vpnCount--
            val on = vpnCount > 0
            update { it.copy(vpn = on) }
        }
    }

    private val telephonyCallback = object : TelephonyCallback(),
        TelephonyCallback.SignalStrengthsListener {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            update { it.copy(signalLevel = signalStrength.level.coerceIn(0, 4)) }
        }
    }

    private val wallpaperListener =
        WallpaperManager.OnColorsChangedListener { _, _ -> readWallpaper() }

    // Precisa ficar guardado em propriedade: o SharedPreferences só guarda referência fraca.
    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> loadPrefs() }

    fun start() {
        if (started) return
        started = true

        update {
            it.copy(
                time = formatTime(),
                ringerMode = audio.ringerMode,
                dnd = isDnd(),
                powerSaver = power.isPowerSaveMode
            )
        }
        loadPrefs()
        readWallpaper()
        refreshBluetooth()
        StatusBarPrefs.prefs(ctx).registerOnSharedPreferenceChangeListener(prefsListener)
        runCatching { wallpaper.addOnColorsChangedListener(wallpaperListener, main) }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(WifiManager.RSSI_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        val sticky = ContextCompat.registerReceiver(
            ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        if (sticky?.action == Intent.ACTION_BATTERY_CHANGED) readBattery(sticky)

        runCatching { cm.registerDefaultNetworkCallback(defaultCallback) }
        runCatching {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            cm.registerNetworkCallback(request, vpnCallback)
        }
        runCatching {
            telephony.registerTelephonyCallback(
                ContextCompat.getMainExecutor(ctx), telephonyCallback
            )
        }
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { ctx.unregisterReceiver(receiver) }
        runCatching { cm.unregisterNetworkCallback(defaultCallback) }
        runCatching { cm.unregisterNetworkCallback(vpnCallback) }
        runCatching { telephony.unregisterTelephonyCallback(telephonyCallback) }
        runCatching { wallpaper.removeOnColorsChangedListener(wallpaperListener) }
        StatusBarPrefs.prefs(ctx).unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    /**
     * Com a permissão BLUETOOTH_CONNECT mostra só quando há dispositivo conectado.
     * Sem ela, o sistema não deixa ver conexões: mostra enquanto o Bluetooth estiver ligado.
     */
    private fun refreshBluetooth() {
        val adapter = bluetoothManager?.adapter
        val enabled = runCatching { adapter?.isEnabled == true }.getOrDefault(false)
        val granted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
        val visible = when {
            !enabled || adapter == null -> false
            !granted -> true
            else -> runCatching {
                listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP, BluetoothProfile.GATT)
                    .any { adapter.getProfileConnectionState(it) == BluetoothProfile.STATE_CONNECTED }
            }.getOrDefault(false)
        }
        update { it.copy(bluetooth = visible) }
    }

    private fun loadPrefs() {
        refreshBluetooth()
        update {
            it.copy(
                showMute = StatusBarPrefs.bool(ctx, StatusBarPrefs.SHOW_MUTE, true),
                showVibrate = StatusBarPrefs.bool(ctx, StatusBarPrefs.SHOW_VIBRATE, true),
                showBatteryPct = StatusBarPrefs.bool(ctx, StatusBarPrefs.SHOW_BATTERY_PCT, true),
                notifMode = StatusBarPrefs.int(ctx, StatusBarPrefs.NOTIF_MODE, 0),
                iconMode = StatusBarPrefs.iconMode(ctx)
            )
        }
    }

    private fun readWallpaper() {
        val colors = runCatching { wallpaper.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }
            .getOrNull()
        val light = colors != null &&
            (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
        update { it.copy(wallpaperLight = light) }
    }

    private fun readBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        if (level >= 0 && scale > 0) {
            update { it.copy(batteryPct = level * 100 / scale, charging = plugged) }
        }
    }

    @Suppress("DEPRECATION")
    private fun refreshWifi() {
        val on = wifiOnDefault
        var level = 0
        if (on) {
            runCatching {
                val info = wifiManager.connectionInfo
                level = wifiManager.calculateSignalLevel(info.rssi)
            }
        }
        val finalLevel = level.coerceIn(0, 4)
        update { it.copy(wifi = on, wifiLevel = finalLevel) }
    }

    private fun isDnd(): Boolean {
        val f = nm.currentInterruptionFilter
        return f != NotificationManager.INTERRUPTION_FILTER_ALL &&
            f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    private fun formatTime(): String {
        val pattern = if (DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
    }
}
