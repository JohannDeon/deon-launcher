package com.deon.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Actions the settings need from the activity (system screens). */
class SettingsActions(
    val usageAccess: () -> Unit,
    val homeApp: () -> Unit,
    val appInfo: () -> Unit,
    val importCar: () -> Unit,
    val resetCar: () -> Unit,
    val editCar: () -> Unit,
    val version: String,
)

@Composable
fun SettingsScreen(actions: SettingsActions, onClose: () -> Unit) {
    val s = LocalSettings.current
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).tile(26.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                UIcon(Ui.Back, c.onBackground, 22.sp)
            }
            Spacer(Modifier.width(18.dp))
            Text("Réglages DEON", color = c.onBackground, fontSize = 30.sp, fontWeight = FontWeight.Light)
        }
        Row(
            Modifier.weight(1f).padding(top = 20.dp).verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Section("Apparence") {
                    Setting(Ui.Layers, "Style") { Choice(Style.entries, s.style, { it.label }) { s.style = it } }
                    Setting(Ui.Bulb, "Thème") { Choice(ThemeMode.entries, s.theme, { it.label }) { s.theme = it } }
                    Setting(Ui.Palette, "Couleur") { AccentPicker(s.accent) { s.accent = it } }
                }
                Section("Voiture") {
                    Setting(Ui.Car, "Image de la voiture") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallButton("Importer un PNG", actions.importCar)
                            if (s.customCar) SmallButton("Clio d'origine", actions.resetCar)
                        }
                    }
                    Text(
                        "PNG à fond transparent, voiture vue de l'arrière.",
                        Modifier.padding(start = 34.dp, bottom = 8.dp), color = c.onSurfaceVariant, fontSize = 13.sp
                    )
                    Setting(Ui.Edit, "Feux et plaque") { SmallButton("Ajuster", actions.editCar) }
                    Setting(Ui.Car, "Plaque d'immatriculation") { PlateField(s.plate) { s.plate = it } }
                    Setting(Ui.Timer, "Afficher la vitesse") { Toggle(s.showSpeed) { s.showSpeed = it } }
                    Setting(Ui.Timer, "Unité de vitesse") { Choice(SpeedUnit.entries, s.speedUnit, { it.label }) { s.speedUnit = it } }
                    Setting(Ui.Car, "Vibrations de la voiture") { Toggle(s.carMotion) { s.carMotion = it } }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Section("Météo et écran") {
                    Setting(Ui.Sun, "Température") { Choice(TempUnit.entries, s.tempUnit, { it.label }) { s.tempUnit = it } }
                    Setting(Ui.Hide, "Masquer la barre du bas") { Toggle(s.hideNavBar) { s.hideNavBar = it } }
                    Setting(Ui.Hide, "Masquer la barre du haut") { Toggle(s.hideStatusBar) { s.hideStatusBar = it } }
                }
                Section("Autorisations") {
                    Link(Ui.Apps, "Accès aux applis récentes", actions.usageAccess)
                    Link(Ui.Home, "Écran d'accueil par défaut", actions.homeApp)
                    Link(Ui.Shield, "Localisation et autorisations", actions.appInfo)
                }
                Section("À propos") {
                    Text(
                        "DEON Launcher ${actions.version}\n" +
                            "Icônes : coolicons par Kryston Schwarze (CC BY 4.0)\n" +
                            "Météo : Open-Meteo.com",
                        color = c.onSurfaceVariant, fontSize = 14.sp, lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    Column {
        Text(title.uppercase(), Modifier.padding(start = 8.dp, bottom = 10.dp), color = c.onSurfaceVariant, fontSize = 13.sp, letterSpacing = 2.sp)
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 10.dp), content = content)
        }
    }
}

@Composable
private fun Setting(icon: Glyph, label: String, control: @Composable () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        UIcon(icon, c.primary, 20.sp)
        Spacer(Modifier.width(14.dp))
        Text(label, Modifier.weight(1f), color = c.onSurface, fontSize = 17.sp)
        control()
    }
}

@Composable
private fun Link(icon: Glyph, label: String, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UIcon(icon, c.primary, 20.sp)
        Spacer(Modifier.width(14.dp))
        Text(label, Modifier.weight(1f), color = c.onSurface, fontSize = 17.sp)
        UIcon(Ui.Chevron, c.onSurfaceVariant, 18.sp)
    }
}

@Composable
private fun <T> Choice(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            val on = o == selected
            Box(
                Modifier.height(42.dp).tile(21.dp, selected = on).clickable { onPick(o) }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) { Text(label(o), color = if (on) c.primary else c.onSurfaceVariant, fontSize = 15.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal) }
        }
    }
}

@Composable
private fun SmallButton(label: String, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.height(42.dp).tile(21.dp).clickable(onClick = onClick).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Text(label, color = c.primary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AccentPicker(selected: Accent, onPick: (Accent) -> Unit) {
    val dark = LocalDark.current
    val c = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Accent.entries.forEach { a ->
            val color = if (dark) a.dark else a.light
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(color)
                    .border(3.dp, if (a == selected) c.onSurface else Color.Transparent, CircleShape)
                    .clickable { onPick(a) }
            )
        }
    }
}

@Composable
private fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val c = MaterialTheme.colorScheme
    Switch(
        on, onChange,
        colors = SwitchDefaults.colors(checkedTrackColor = c.primary, checkedThumbColor = Color.White)
    )
}

@Composable
private fun PlateField(value: String, onChange: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    // A small French-style plate: blue band on the left, black letters on white.
    Row(
        Modifier.width(210.dp).height(46.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)
            .border(1.5.dp, Color(0xFF222222), RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(22.dp).fillMaxHeight().background(Color(0xFF1F4BB5)), contentAlignment = Alignment.BottomCenter) {
            Text("F", Modifier.padding(bottom = 4.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        BasicTextField(
            value, { onChange(it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '-' || ch == ' ' }.take(12)) },
            Modifier.weight(1f).padding(horizontal = 8.dp),
            singleLine = true,
            textStyle = TextStyle(
                color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                fontFamily = FontFamily.SansSerif, letterSpacing = 1.sp
            ),
            cursorBrush = SolidColor(c.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
        )
    }
}
