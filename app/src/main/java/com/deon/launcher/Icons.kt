package com.deon.launcher

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit

// coolicons by Kryston Schwarze (CC BY 4.0), subset to the glyphs used here. Credit shown in the settings.
private val Coolicons = FontFamily(Font(R.font.coolicons))

/** One coolicons glyph. */
@JvmInline
value class Glyph(val code: Char)

object Ui {
    val Apps = Glyph('')
    val Play = Glyph('')
    val Pause = Glyph('')
    val Next = Glyph('')
    val Previous = Glyph('')
    val Close = Glyph('')
    val Plus = Glyph('')
    val Music = Glyph('')
    val Settings = Glyph('')
    val Check = Glyph('')
    val Chevron = Glyph('')
    val Back = Glyph('')
    val Car = Glyph('')
    val Palette = Glyph('')
    val Layers = Glyph('')
    val Bulb = Glyph('')
    val Timer = Glyph('')
    val Hide = Glyph('')
    val Shield = Glyph('')
    val Home = Glyph('')
    val Text = Glyph('')
    val Wavy = Glyph('')
    val Edit = Glyph('')
    val Expand = Glyph('')
    val Heart = Glyph('')

    val Sun = Glyph('')
    val Moon = Glyph('')
    val Cloud = Glyph('')
    val Drop = Glyph('')
    val Star = Glyph('')
    val Warning = Glyph('')
}

@Composable
fun UIcon(glyph: Glyph, color: Color, size: TextUnit, modifier: Modifier = Modifier) = Text(
    glyph.code.toString(), modifier, color = color, fontSize = size, lineHeight = size,
    fontFamily = Coolicons, textAlign = TextAlign.Center
)

// Checked in order against "package label" in lower case; the first keyword found wins.
private val appGlyphs = listOf(
    listOf("voice", "vocal", "assistant") to '',                                  // User_Voice
    listOf("youtube.music", "music", "musique", "spotify", "deezer", "soundcloud", "podcast", "player") to '', // Headphones
    listOf("radio", "fm") to '',                                                  // Volume_Max
    listOf("youtube", "netflix", "twitch", "video", "vidéo", "tv") to '',         // Monitor_Play
    listOf("waze", "navigation", "navi", "gps") to '',                            // Navigation
    listOf("maps", "map", "carte") to '',                                         // Map
    listOf("dialer", "phone", "téléphone", "telephone", "call") to '',            // Phone
    listOf("whatsapp", "telegram", "messenger", "discord", "snapchat", "signal") to '', // Chat_Conversation_Circle
    listOf("messag", "sms", "mms") to '',                                         // Chat_Circle
    listOf("contacts") to '',                                                     // Users
    listOf("facebook", "twitter", "linkedin", "instagram") to '',                 // Users_Group
    listOf("camera", "caméra") to '',                                             // Camera
    listOf("photos", "gallery", "galerie") to '',                                 // Image_01
    listOf("settings", "paramètres", "réglages") to '',                           // Settings
    listOf("chrome", "browser", "firefox", "navigateur") to '',                   // Globe
    listOf("gmail", "mail", "email") to '',                                       // Mail
    listOf("calendar", "agenda", "calendrier") to '',                             // Calendar
    listOf("clock", "horloge", "alarm") to '',                                    // Alarm
    listOf("files", "documents", "fichiers", "explorer") to '',                   // Folder
    listOf("drive", "cloud") to '',                                               // Cloud
    listOf("googlequicksearchbox", "search", "recherche") to '',                  // Search_Magnifying_Glass
    listOf("vending", "store", "market") to '',                                   // Shopping_Bag_01
    listOf("note", "keep") to '',                                                 // Note
    listOf("game", "jeu") to '',                                                  // Puzzle
    listOf("book", "livre", "kindle") to '',                                      // Book_Open
    listOf("weather", "météo", "meteo") to '',                                    // Sun
    listOf("car", "auto", "obd") to '',                                           // Car_Auto
    listOf("wifi", "bluetooth", "hotspot") to '',                                 // Wifi_High
)

/** The coolicons glyph that stands for this app, or null to fall back on the app's own icon. */
fun appGlyph(pkg: String, label: String): Glyph? {
    val key = "$pkg ${label.lowercase()}"
    return appGlyphs.firstOrNull { (words, _) -> words.any { it in key } }?.let { Glyph(it.second) }
}
