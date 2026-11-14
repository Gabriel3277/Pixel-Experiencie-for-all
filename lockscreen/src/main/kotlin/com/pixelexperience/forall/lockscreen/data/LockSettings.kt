package com.pixelexperience.forall.lockscreen.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

enum class ClockStyle(val label: String) {
    STACKED("Empilhado"), INLINE("Em linha"), COMPACT("Compacto"), BOLD("Negrito"), OUTLINE("Contorno"), INFLATE("Inflado"), GROWTH("Crescer"), ROTATION("Rotação"), FRAME("Moldura"), RUBY("Ruby"), FLEX("Flex")
}

enum class ClockAlign(val label: String) { START("Esquerda"), CENTER("Centro") }
enum class ClockColor(val label: String) { DYNAMIC("Dinâmica"), ACCENT("Destaque"), WHITE("Branca") }
enum class ShortcutType(val label: String) {
    NONE("Nenhum"), CAMERA("Câmera"), FLASHLIGHT("Lanterna"), DIALER("Telefone"), ALARM("Alarmes"), ASSISTANT("Assistente")
}

data class LockSettings(
    val enabled: Boolean = false,
    val wallpaperId: String = "builtin:aurora",
    val wallpaperVersion: Int = 0,
    val clockStyle: ClockStyle = ClockStyle.STACKED,
    val clockAlign: ClockAlign = ClockAlign.CENTER,
    val clockColor: ClockColor = ClockColor.DYNAMIC,
    val clockScale: Float = 1f,
    val clockY: Float = 0.08f,
    val bgBlur: Float = 0.15f,
    val bgDim: Float = 0.30f,
    val cardOpacity: Float = 0.50f,
    val cardBlur: Float = 0.60f,
    val groupNotifications: Boolean = true,
    val hideOngoing: Boolean = true,
    val showContentWhenSecure: Boolean = false,
    val leftShortcut: ShortcutType = ShortcutType.FLASHLIGHT,
    val rightShortcut: ShortcutType = ShortcutType.CAMERA,
    val showWeather: Boolean = true,
)

private val Context.lockStore by preferencesDataStore("pxe_lockscreen")

private object K {
    val enabled = booleanPreferencesKey("enabled")
    val wallpaperId = stringPreferencesKey("wallpaper_id")
    val wallpaperVersion = intPreferencesKey("wallpaper_version")
    val clockStyle = stringPreferencesKey("clock_style")
    val clockAlign = stringPreferencesKey("clock_align")
    val clockColor = stringPreferencesKey("clock_color")
    val clockScale = floatPreferencesKey("clock_scale")
    val clockY = floatPreferencesKey("clock_y")
    val bgBlur = floatPreferencesKey("bg_blur")
    val bgDim = floatPreferencesKey("bg_dim")
    val cardOpacity = floatPreferencesKey("card_opacity")
    val cardBlur = floatPreferencesKey("card_blur")
    val group = booleanPreferencesKey("group")
    val hideOngoing = booleanPreferencesKey("hide_ongoing")
    val showSecure = booleanPreferencesKey("show_secure")
    val left = stringPreferencesKey("left_shortcut")
    val right = stringPreferencesKey("right_shortcut")
    val weather = booleanPreferencesKey("show_weather")
}

private inline fun <reified E : Enum<E>> Preferences.enumOf(key: Preferences.Key<String>, def: E): E {
    val name = this[key] ?: return def
    return enumValues<E>().firstOrNull { it.name == name } ?: def
}

private fun Preferences.toSettings(): LockSettings {
    val d = LockSettings()
    return LockSettings(
        enabled = this[K.enabled] ?: d.enabled,
        wallpaperId = this[K.wallpaperId] ?: d.wallpaperId,
        wallpaperVersion = this[K.wallpaperVersion] ?: d.wallpaperVersion,
        clockStyle = enumOf(K.clockStyle, d.clockStyle),
        clockAlign = enumOf(K.clockAlign, d.clockAlign),
        clockColor = enumOf(K.clockColor, d.clockColor),
        clockScale = this[K.clockScale] ?: d.clockScale,
        clockY = this[K.clockY] ?: d.clockY,
        bgBlur = this[K.bgBlur] ?: d.bgBlur,
        bgDim = this[K.bgDim] ?: d.bgDim,
        cardOpacity = this[K.cardOpacity] ?: d.cardOpacity,
        cardBlur = this[K.cardBlur] ?: d.cardBlur,
        groupNotifications = this[K.group] ?: d.groupNotifications,
        hideOngoing = this[K.hideOngoing] ?: d.hideOngoing,
        showContentWhenSecure = this[K.showSecure] ?: d.showContentWhenSecure,
        leftShortcut = enumOf(K.left, d.leftShortcut),
        rightShortcut = enumOf(K.right, d.rightShortcut),
        showWeather = this[K.weather] ?: d.showWeather,
    )
}

private fun MutablePreferences.write(s: LockSettings) {
    this[K.enabled] = s.enabled
    this[K.wallpaperId] = s.wallpaperId
    this[K.wallpaperVersion] = s.wallpaperVersion
    this[K.clockStyle] = s.clockStyle.name
    this[K.clockAlign] = s.clockAlign.name
    this[K.clockColor] = s.clockColor.name
    this[K.clockScale] = s.clockScale
    this[K.clockY] = s.clockY
    this[K.bgBlur] = s.bgBlur
    this[K.bgDim] = s.bgDim
    this[K.cardOpacity] = s.cardOpacity
    this[K.cardBlur] = s.cardBlur
    this[K.group] = s.groupNotifications
    this[K.hideOngoing] = s.hideOngoing
    this[K.showSecure] = s.showContentWhenSecure
    this[K.left] = s.leftShortcut.name
    this[K.right] = s.rightShortcut.name
    this[K.weather] = s.showWeather
}

/** Fonte única das configurações: estado em memória (instantâneo) + persistência em DataStore (com debounce). */
class LockSettingsRepository private constructor(app: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = app.lockStore
    private val _state = MutableStateFlow(
        runCatching { runBlocking { store.data.first().toSettings() } }.getOrDefault(LockSettings())
    )
    val settings: StateFlow<LockSettings> = _state.asStateFlow()
    private var job: Job? = null

    fun update(block: (LockSettings) -> LockSettings) {
        _state.update(block)
        job?.cancel()
        job = scope.launch {
            delay(200)
            store.edit { it.write(_state.value) }
        }
    }

    companion object {
        @Volatile private var inst: LockSettingsRepository? = null
        fun get(ctx: Context): LockSettingsRepository =
            inst ?: synchronized(this) {
                inst ?: LockSettingsRepository(ctx.applicationContext).also { inst = it }
            }
    }
}
