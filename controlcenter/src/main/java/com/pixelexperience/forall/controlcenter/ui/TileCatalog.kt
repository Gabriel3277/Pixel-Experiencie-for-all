package com.pixelexperience.forall.controlcenter.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.nfc.NfcAdapter
import android.os.PowerManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.annotation.DrawableRes
import com.pixelexperience.forall.controlcenter.PanelActions
import com.pixelexperience.forall.controlcenter.PanelState
import com.pixelexperience.forall.controlcenter.R

internal enum class TileCategory(val label: String) {
    CONNECTIVITY("Conectividade"),
    SYSTEM("Sistema"),
    PRIVACY("Privacidade"),
    TOOLS("Ferramentas"),
}

internal data class TileSpec(
    val id: String,
    val label: String,
    val category: TileCategory,
    @DrawableRes val icon: Int,
)

/** Um bloco no layout: id do catálogo e largura (1 ou 2 colunas). */
internal data class TilePlace(val id: String, val span: Int)

internal object TileCatalog {
    val all: List<TileSpec> = listOf(
        TileSpec("internet", "Internet", TileCategory.CONNECTIVITY, R.drawable.cc_ic_wifi),
        TileSpec("bluetooth", "Bluetooth", TileCategory.CONNECTIVITY, R.drawable.cc_ic_bluetooth),
        TileSpec("nfc", "NFC", TileCategory.CONNECTIVITY, R.drawable.cc_ic_nfc),
        TileSpec("vpn", "VPN", TileCategory.CONNECTIVITY, R.drawable.cc_ic_vpn),
        TileSpec("hotspot", "Hotspot", TileCategory.CONNECTIVITY, R.drawable.cc_ic_hotspot),
        TileSpec("quickshare", "Quick Share", TileCategory.CONNECTIVITY, R.drawable.cc_ic_share),
        TileSpec("airplane", "Modo avião", TileCategory.CONNECTIVITY, R.drawable.cc_ic_airplane),
        TileSpec("darktheme", "Tema escuro", TileCategory.SYSTEM, R.drawable.cc_ic_contrast),
        TileSpec("location", "Localização", TileCategory.SYSTEM, R.drawable.cc_ic_location),
        TileSpec("battery", "Economia de bateria", TileCategory.SYSTEM, R.drawable.cc_ic_battery_saver),
        TileSpec("rotate", "Rotação automática", TileCategory.SYSTEM, R.drawable.cc_ic_rotate),
        TileSpec("extradim", "Extra Dim", TileCategory.SYSTEM, R.drawable.cc_ic_brightness),
        TileSpec("nightlight", "Luz noturna", TileCategory.SYSTEM, R.drawable.cc_ic_moon),
        TileSpec("camera", "Câmera", TileCategory.PRIVACY, R.drawable.cc_ic_camera),
        TileSpec("mic", "Microfone", TileCategory.PRIVACY, R.drawable.cc_ic_mic),
        TileSpec("screenrec", "Gravação de tela", TileCategory.PRIVACY, R.drawable.cc_ic_screenrec),
        TileSpec("torch", "Lanterna", TileCategory.TOOLS, R.drawable.cc_ic_flashlight),
        TileSpec("calc", "Calculadora", TileCategory.TOOLS, R.drawable.cc_ic_calculator),
        TileSpec("alarm", "Alarme", TileCategory.TOOLS, R.drawable.cc_ic_alarm),
        TileSpec("stopwatch", "Cronômetro", TileCategory.TOOLS, R.drawable.cc_ic_timer),
        TileSpec("qr", "Scanner QR", TileCategory.TOOLS, R.drawable.cc_ic_qr),
    )

    val byId: Map<String, TileSpec> = all.associateBy { it.id }

    val defaultLayout: List<TilePlace> = listOf(
        TilePlace("internet", 2),
        TilePlace("bluetooth", 2),
        TilePlace("torch", 1),
        TilePlace("airplane", 1),
        TilePlace("rotate", 1),
        TilePlace("alarm", 1),
        TilePlace("darktheme", 1),
        TilePlace("location", 1),
        TilePlace("battery", 1),
        TilePlace("calc", 1),
    )
}

/** Guarda o layout escolhido no Edit Mode. */
internal object TileLayoutStore {
    private const val PREFS = "cc_tile_layout"
    private const val KEY = "layout_v1"

    fun load(ctx: Context): List<TilePlace> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return TileCatalog.defaultLayout
        if (raw.isEmpty()) return emptyList()
        return raw.split(",").mapNotNull { part ->
            val p = part.split(":")
            val id = p.getOrNull(0) ?: return@mapNotNull null
            if (TileCatalog.byId[id] == null) return@mapNotNull null
            TilePlace(id, (p.getOrNull(1)?.toIntOrNull() ?: 1).coerceIn(1, 2))
        }.distinctBy { it.id }
    }

    fun save(ctx: Context, list: List<TilePlace>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, list.joinToString(",") { "${it.id}:${it.span}" })
            .apply()
    }

    fun reset(ctx: Context): List<TilePlace> {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
        return TileCatalog.defaultLayout
    }
}

/** Estado do sistema que não vem do PanelState (lido a cada segundo). */
internal data class SysSnapshot(
    val dark: Boolean = false,
    val location: Boolean = false,
    val battery: Boolean = false,
    val nfc: Boolean = false,
    val vpn: Boolean = false,
    val extraDim: Boolean = false,
    val night: Boolean = false,
)

internal fun readSystem(ctx: Context): SysSnapshot {
    fun secure(key: String): Boolean = try {
        Settings.Secure.getInt(ctx.contentResolver, key, 0) == 1
    } catch (_: Exception) {
        false
    }

    val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    val location = try {
        ctx.getSystemService(LocationManager::class.java)?.isLocationEnabled == true
    } catch (_: Exception) {
        false
    }
    val battery = try {
        ctx.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
    } catch (_: Exception) {
        false
    }
    val nfc = try {
        NfcAdapter.getDefaultAdapter(ctx)?.isEnabled == true
    } catch (_: Exception) {
        false
    }
    val vpn = try {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    } catch (_: Exception) {
        false
    }
    return SysSnapshot(
        dark = dark,
        location = location,
        battery = battery,
        nfc = nfc,
        vpn = vpn,
        extraDim = secure("reduce_bright_colors_activated"),
        night = secure("night_display_activated"),
    )
}

/** Como um bloco aparece e o que ele faz ao tocar. */
internal class TileUi(
    val active: Boolean,
    val subtitle: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

internal fun tileUi(
    id: String,
    ctx: Context,
    state: PanelState,
    actions: PanelActions,
    sys: SysSnapshot,
): TileUi {
    fun open(vararg intents: Intent): () -> Unit = {
        for (intent in intents) {
            try {
                ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                break
            } catch (_: Exception) {
            }
        }
        actions.collapse()
    }

    fun onOff(on: Boolean): String = if (on) "Ativado" else "Desativado"

    return when (id) {
        "internet" -> TileUi(state.internetActive, state.internetSubtitle, true, actions::openInternet)
        "bluetooth" -> TileUi(state.bluetoothActive, state.bluetoothSubtitle, true, actions::openBluetooth)
        "nfc" -> TileUi(sys.nfc, onOff(sys.nfc), true, open(Intent(Settings.ACTION_NFC_SETTINGS)))
        "vpn" -> TileUi(sys.vpn, onOff(sys.vpn), true, open(Intent(Settings.ACTION_VPN_SETTINGS)))
        "hotspot" -> TileUi(
            false, "", true,
            open(Intent("android.settings.TETHER_SETTINGS"), Intent(Settings.ACTION_WIRELESS_SETTINGS)),
        )
        "quickshare" -> TileUi(
            false, "", true,
            open(Intent("com.google.android.gms.settings.QUICK_SHARE"), Intent(Settings.ACTION_SETTINGS)),
        )
        "airplane" -> TileUi(state.airplaneOn, onOff(state.airplaneOn), true, actions::openAirplane)
        "darktheme" -> TileUi(sys.dark, onOff(sys.dark), true, open(Intent(Settings.ACTION_DISPLAY_SETTINGS)))
        "location" -> TileUi(
            sys.location, onOff(sys.location), true,
            open(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)),
        )
        "battery" -> TileUi(
            sys.battery, onOff(sys.battery), true,
            open(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)),
        )
        "rotate" -> TileUi(state.autoRotate, onOff(state.autoRotate), true) {
            if (state.canWriteSettings) state.toggleRotation() else actions.requestWriteSettings()
        }
        "extradim" -> TileUi(
            sys.extraDim, onOff(sys.extraDim), true,
            open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
        )
        "nightlight" -> TileUi(
            sys.night, onOff(sys.night), true,
            open(Intent("android.settings.NIGHT_DISPLAY_SETTINGS"), Intent(Settings.ACTION_DISPLAY_SETTINGS)),
        )
        "camera" -> TileUi(false, "", true, open(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)))
        "mic" -> TileUi(
            false, "", true,
            open(Intent("android.settings.PRIVACY_SETTINGS"), Intent(Settings.ACTION_SETTINGS)),
        )
        "screenrec" -> TileUi(false, "", true, open(Intent(Settings.ACTION_SETTINGS)))
        "torch" -> TileUi(state.torchOn, onOff(state.torchOn), state.hasTorch, state::toggleTorch)
        "calc" -> TileUi(
            false, "", true,
            open(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)),
        )
        "alarm" -> TileUi(false, "", true, actions::openAlarms)
        "stopwatch" -> TileUi(false, "", true, open(Intent(AlarmClock.ACTION_SHOW_TIMERS)))
        "qr" -> TileUi(false, "", true, open(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)))
        else -> TileUi(false, "", false) {}
    }
}
