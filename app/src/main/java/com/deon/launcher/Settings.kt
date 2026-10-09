package com.deon.launcher

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

enum class Style(val label: String) { GLASS("Glassmorphism"), NEU("Neumorphism") }
enum class ThemeMode(val label: String) { AUTO("Auto"), LIGHT("Clair"), DARK("Sombre") }
enum class SpeedUnit(val label: String, val factor: Float) { KMH("km/h", 1f), MPH("mph", 0.621371f) }
enum class TempUnit(val label: String) { C("°C"), F("°F") }

/** Accent colours offered in the settings: dark-theme tone, light-theme tone. */
enum class Accent(val label: String, val dark: Color, val light: Color) {
    BLUE("Bleu", Color(0xFF4C8DFF), Color(0xFF2F6FEB)),
    VIOLET("Violet", Color(0xFF9B7BFF), Color(0xFF6E4BE0)),
    TEAL("Turquoise", Color(0xFF2EC5CE), Color(0xFF0E9AA5)),
    GREEN("Vert", Color(0xFF3DDC84), Color(0xFF18A558)),
    ORANGE("Orange", Color(0xFFFF9F43), Color(0xFFE07A10)),
    RED("Rouge", Color(0xFFFF5D5D), Color(0xFFD93636)),
}

/** Every launcher setting, kept in SharedPreferences and observable from Compose. */
class LauncherSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private inline fun <reified E : Enum<E>> enumPref(key: String, default: E) =
        mutableStateOf(prefs.getString(key, null)?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default)

    private val _style = enumPref("style", Style.GLASS)
    private val _theme = enumPref("theme", ThemeMode.AUTO)
    private val _accent = enumPref("accent", Accent.BLUE)
    private val _speedUnit = enumPref("speed_unit", SpeedUnit.KMH)
    private val _tempUnit = enumPref("temp_unit", TempUnit.C)
    private val _plate = mutableStateOf(prefs.getString("plate", "AB-123-CD")!!)
    private val _hideNavBar = mutableStateOf(prefs.getBoolean("hide_nav_bar", true))
    private val _carMotion = mutableStateOf(prefs.getBoolean("car_motion", true))
    private val _showSpeed = mutableStateOf(prefs.getBoolean("show_speed", true))
    private val _customCar = mutableStateOf(prefs.getBoolean("custom_car", false))
    private val _layoutClio = mutableStateOf(CarLayout.decode(prefs.getString("layout_clio", null), CarLayout.Clio))
    private val _layoutCustom = mutableStateOf(CarLayout.decode(prefs.getString("layout_custom", null), CarLayout.Imported))

    var style: Style
        get() = _style.value
        set(v) { _style.value = v; prefs.edit().putString("style", v.name).apply() }
    var theme: ThemeMode
        get() = _theme.value
        set(v) { _theme.value = v; prefs.edit().putString("theme", v.name).apply() }
    var accent: Accent
        get() = _accent.value
        set(v) { _accent.value = v; prefs.edit().putString("accent", v.name).apply() }
    var speedUnit: SpeedUnit
        get() = _speedUnit.value
        set(v) { _speedUnit.value = v; prefs.edit().putString("speed_unit", v.name).apply() }
    var tempUnit: TempUnit
        get() = _tempUnit.value
        set(v) { _tempUnit.value = v; prefs.edit().putString("temp_unit", v.name).apply() }
    var plate: String
        get() = _plate.value
        set(v) { _plate.value = v; prefs.edit().putString("plate", v).apply() }
    var hideNavBar: Boolean
        get() = _hideNavBar.value
        set(v) { _hideNavBar.value = v; prefs.edit().putBoolean("hide_nav_bar", v).apply() }
    var carMotion: Boolean
        get() = _carMotion.value
        set(v) { _carMotion.value = v; prefs.edit().putBoolean("car_motion", v).apply() }
    var showSpeed: Boolean
        get() = _showSpeed.value
        set(v) { _showSpeed.value = v; prefs.edit().putBoolean("show_speed", v).apply() }
    /** True when the car picture is one the user imported instead of the built-in Clio. */
    var customCar: Boolean
        get() = _customCar.value
        set(v) { _customCar.value = v; prefs.edit().putBoolean("custom_car", v).apply() }

    /** Tail lights and plate of the car shown now (built-in Clio or imported picture). */
    var carLayout: CarLayout
        get() = if (customCar) _layoutCustom.value else _layoutClio.value
        set(v) {
            val key = if (customCar) "layout_custom" else "layout_clio"
            (if (customCar) _layoutCustom else _layoutClio).value = v
            prefs.edit().putString(key, v.encode()).apply()
        }

    fun resetCarLayout() {
        carLayout = if (customCar) CarLayout.Imported else CarLayout.Clio
    }
}

val LocalSettings = staticCompositionLocalOf<LauncherSettings> { error("no settings") }
val LocalDark = staticCompositionLocalOf { true }

/** Neumorphism wants one flat mid-tone: background and surfaces share it, depth comes from shadows. */
object NeuPalette {
    val lightBg = Color(0xFFE3E7ED)
    val lightHi = Color(0xFFFFFFFF)
    val lightLo = Color(0xFFA9B5C6)
    val darkBg = Color(0xFF25282E)
    val darkHi = Color(0xFF32363E)
    val darkLo = Color(0xFF15171A)
}

@Composable
fun LauncherTheme(settings: LauncherSettings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val neu = settings.style == Style.NEU
    val accent = if (dark) settings.accent.dark else settings.accent.light
    val scheme = if (dark) darkColorScheme(
        background = if (neu) NeuPalette.darkBg else Color(0xFF0B0C0F),
        surface = if (neu) NeuPalette.darkBg else Color(0xFF15171C),
        onBackground = Color(0xFFECEDEF), onSurface = Color(0xFFECEDEF), onSurfaceVariant = Color(0xFF8A8F98),
        outlineVariant = Color(0x14FFFFFF), primary = accent, onPrimary = Color.White
    ) else lightColorScheme(
        background = if (neu) NeuPalette.lightBg else Color(0xFFF1F2F4),
        surface = if (neu) NeuPalette.lightBg else Color.White,
        onBackground = Color(0xFF121418), onSurface = Color(0xFF121418), onSurfaceVariant = Color(0xFF6B7079),
        outlineVariant = Color(0x12000000), primary = accent, onPrimary = Color.White
    )
    CompositionLocalProvider(LocalSettings provides settings, LocalDark provides dark) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
