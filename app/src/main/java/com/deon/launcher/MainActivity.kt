package com.deon.launcher

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.unit.Dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val HOST_ID = 1024
private const val REQ_BIND = 1
private const val REQ_CONFIG = 2
private const val RECENT_COUNT = 8

class MainActivity : ComponentActivity(), LocationListener {
    private lateinit var host: AppWidgetHost
    private lateinit var awm: AppWidgetManager
    private lateinit var grid: WidgetGridState
    private var speedKmh by mutableFloatStateOf(0f)
    private var pendingId = -1
    private var recents by mutableStateOf(emptyList<App>())
    private var usageAllowed by mutableStateOf(false)
    private val prefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }
    private val settings by lazy { LauncherSettings(this) }
    private var customCar by mutableStateOf<ImageBitmap?>(null)
    private var carEditor by mutableStateOf(false)
    private val carFile by lazy { java.io.File(filesDir, "car.png") }
    private val pickCar = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::importCar) }

    private val pickSticker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::importSticker) }

    /** Reads a picture and keeps only its visible part (transparent margins cut off), at most [maxWidth] px wide. */
    private fun loadCropped(uri: Uri, maxWidth: Int): Bitmap? {
        val src = runCatching {
            contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
        }.getOrNull()
        if (src == null) {
            android.widget.Toast.makeText(this, "Image illisible", android.widget.Toast.LENGTH_LONG).show()
            return null
        }
        var l = src.width; var t = src.height; var r = -1; var b = -1
        val row = IntArray(src.width)
        for (y in 0 until src.height) {
            src.getPixels(row, 0, src.width, 0, y, src.width, 1)
            for (x in row.indices) if ((row[x] ushr 24) > 16) {
                if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y
            }
        }
        var out = if (r < 0) src else Bitmap.createBitmap(src, l, t, r - l + 1, b - t + 1)
        if (out.width > maxWidth) out = Bitmap.createScaledBitmap(out, maxWidth, out.height * maxWidth / out.width, true)
        return out
    }

    /** Saves the sticker in the app files and drops it in the middle of the car. */
    private fun importSticker(uri: Uri) {
        val img = loadCropped(uri, 512) ?: return
        val id = System.currentTimeMillis().toString(36)
        stickerFile(this, id).outputStream().use { img.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val l = settings.carLayout
        settings.carLayout = l.copy(stickers = l.stickers + Sticker(id, Offset(.5f, .4f), .25f, img.height.toFloat() / img.width, 0f))
    }

    private fun importCar(uri: Uri) {
        val car = loadCropped(uri, 1024) ?: return
        carFile.outputStream().use { car.compress(Bitmap.CompressFormat.PNG, 100, it) }
        customCar = car.asImageBitmap()
        settings.customCar = true
        // A new picture: start from the default guess and let the user place lights and plate.
        settings.resetCarLayout()
        carEditor = true
    }

    private fun resetCar() {
        carFile.delete()
        customCar = null
        settings.customCar = false
    }

    private val askLocation =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) startGps() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        awm = AppWidgetManager.getInstance(this)
        host = AppWidgetHost(this, HOST_ID)
        // The grid replaces the older single column: its widgets (and the separate YT Music slot) move onto it.
        val legacy = prefs.getString("widgets", "")!!.split(',').mapNotNull { it.toIntOrNull() } +
            listOfNotNull(prefs.getInt("music_widget", -1).takeIf { it != -1 })
        grid = WidgetGridState(prefs, legacy.distinct())
        if (settings.customCar) customCar = runCatching { android.graphics.BitmapFactory.decodeFile(carFile.path)?.asImageBitmap() }.getOrNull()
        if (!hasGps()) askLocation.launch(ACCESS_FINE_LOCATION)
        setContent { LauncherTheme(settings) { Home() } }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyNavBar()
    }

    /** Hide Android's own bars if asked; a swipe from the screen edge shows them again for a moment. */
    private fun applyNavBar() = WindowCompat.getInsetsController(window, window.decorView).run {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (settings.hideNavBar) hide(WindowInsetsCompat.Type.navigationBars()) else show(WindowInsetsCompat.Type.navigationBars())
        if (settings.hideStatusBar) hide(WindowInsetsCompat.Type.statusBars()) else show(WindowInsetsCompat.Type.statusBars())
    }

    private fun openSystem(intent: Intent) {
        runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private val settingsActions by lazy {
        SettingsActions(
            usageAccess = { openSystem(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
            homeApp = { openSystem(Intent(Settings.ACTION_HOME_SETTINGS)) },
            appInfo = { openSystem(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) },
            importCar = { pickCar.launch(arrayOf("image/png", "image/webp", "image/*")) },
            resetCar = ::resetCar,
            editCar = { carEditor = true },
            version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""
        )
    }

    override fun onStart() {
        super.onStart()
        host.startListening()
        startGps()
    }

    override fun onResume() {
        super.onResume()
        refreshRecents()
    }

    override fun onStop() {
        super.onStop()
        host.stopListening()
        getSystemService(LocationManager::class.java).removeUpdates(this)
    }

    // --- GPS speed ---

    private fun hasGps() = checkSelfPermission(ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun startGps() {
        if (!hasGps()) return
        runCatching {
            getSystemService(LocationManager::class.java)
                .requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, this)
        }
    }

    private var lastFix: Location? = null

    override fun onLocationChanged(location: Location) {
        val prev = lastFix
        val dt = prev?.let { (location.elapsedRealtimeNanos - it.elapsedRealtimeNanos) / 1e9f } ?: 0f
        // Some receivers don't report speed: derive it from the distance between two fixes.
        speedKmh = when {
            location.hasSpeed() -> location.speed * 3.6f
            prev != null && dt > 0.2f -> prev.distanceTo(location) / dt * 3.6f
            else -> 0f
        }
        lastFix = location
    }

    // API 29 has no default implementations for these.
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) { speedKmh = 0f }
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    // --- Widgets: pick -> bind (system prompt if needed) -> configure -> add ---

    private fun pickProvider(provider: ComponentName) {
        pendingId = host.allocateAppWidgetId()
        if (awm.bindAppWidgetIdIfAllowed(pendingId, provider)) configureOrAdd()
        else startActivityForResult(
            Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider),
            REQ_BIND
        )
    }

    private fun configureOrAdd() {
        if (awm.getAppWidgetInfo(pendingId)?.configure == null) return addWidget()
        try {
            host.startAppWidgetConfigureActivityForResult(this, pendingId, 0, REQ_CONFIG, null)
        } catch (e: Exception) {
            addWidget() // configure activity not reachable, add it unconfigured
        }
    }

    private fun addWidget() {
        if (grid.add(TileKind.APP, pendingId)) return
        host.deleteAppWidgetId(pendingId)
        android.widget.Toast.makeText(this, "Plus de place sur la grille : réduis ou retire un widget", android.widget.Toast.LENGTH_LONG).show()
    }

    private fun removeTile(tile: Tile) {
        if (tile.kind == TileKind.APP) host.deleteAppWidgetId(tile.appId)
        grid.remove(tile.key)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_BIND && requestCode != REQ_CONFIG) return
        if (resultCode != RESULT_OK) return host.deleteAppWidgetId(pendingId)
        if (requestCode == REQ_BIND) configureOrAdd() else addWidget()
    }

    // --- Apps ---

    /** [mono] is true when [icon] is the app's own single-colour (themed) icon, to be tinted. */
    private class App(val label: String, val component: ComponentName, val icon: ImageBitmap, val mono: Boolean, val glyph: Glyph?)

    private fun appIcon(d: Drawable): Pair<ImageBitmap, Boolean> {
        val mono = if (Build.VERSION.SDK_INT >= 33) (d as? AdaptiveIconDrawable)?.monochrome else null
        if (mono == null) return d.toBitmap(128, 128).asImageBitmap() to false
        // The themed layer is drawn on 108 dp with the glyph in the middle 72 dp: crop to that safe zone.
        val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        mono.setBounds(-32, -32, 160, 160)
        mono.draw(android.graphics.Canvas(bmp))
        return bmp.asImageBitmap() to true
    }

    private fun loadApps() = packageManager
        .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .filter { it.activityInfo.packageName != packageName }
        .map {
            val (icon, mono) = appIcon(it.loadIcon(packageManager))
            val label = it.loadLabel(packageManager).toString()
            val pkg = it.activityInfo.packageName
            App(label, ComponentName(pkg, it.activityInfo.name), icon, mono, appGlyph(pkg, label))
        }
        .sortedBy { it.label.lowercase() }

    private fun launch(app: App) {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        // Fallback history for when usage access is not granted.
        val history = listOf(app.component.packageName) + launched().filter { it != app.component.packageName }
        prefs.edit().putString("launched", history.take(RECENT_COUNT).joinToString(",")).apply()
    }

    private fun launched() = prefs.getString("launched", "")!!.split(',').filter { it.isNotEmpty() }

    // --- Recent apps: system usage stats when allowed, otherwise what was opened from this launcher ---

    private fun hasUsageAccess() = getSystemService(AppOpsManager::class.java)
        .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, applicationInfo.uid, packageName) ==
        AppOpsManager.MODE_ALLOWED

    private fun refreshRecents() {
        usageAllowed = hasUsageAccess()
        val packages = if (usageAllowed) {
            val now = System.currentTimeMillis()
            getSystemService(UsageStatsManager::class.java)
                .queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 7 * 24 * 3600_000L, now)
                .filter { it.lastTimeUsed > 0 && it.totalTimeInForeground > 0 }
                .groupBy { it.packageName }
                .mapValues { (_, stats) -> stats.maxOf { it.lastTimeUsed } }
                .entries.sortedByDescending { it.value }
                .map { it.key }
        } else launched()
        val apps = loadApps().associateBy { it.component.packageName }
        recents = packages.mapNotNull { apps[it] }.take(RECENT_COUNT)
    }

    // --- UI (designed for 1280x720 landscape, still fits 853x480 dp) ---

    @Composable
    private fun Home() {
        var drawer by remember { mutableStateOf(false) }
        var picker by remember { mutableStateOf(false) }
        var edit by remember { mutableStateOf(false) }
        var settingsOpen by remember { mutableStateOf(false) }
        var selected by remember { mutableStateOf<Long?>(null) }
        BackHandler(drawer || edit || settingsOpen || carEditor) {
            when {
                carEditor -> carEditor = false
                settingsOpen -> settingsOpen = false
                drawer -> drawer = false
                else -> { edit = false; selected = null }
            }
        }
        LaunchedEffect(settings.hideNavBar, settings.hideStatusBar) { applyNavBar() }
        // Long press anywhere on the free screen space shows the widget editing controls.
        val longPress = Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { edit = true }) }
        val c = MaterialTheme.colorScheme
        val speed by animateFloatAsState(speedKmh, tween(600), label = "speed")

        var rootSize by remember { mutableStateOf(IntSize.Zero) }
        CompositionLocalProvider(LocalRootSize provides rootSize) {
        Box(Modifier.fillMaxSize().onSizeChanged { rootSize = it }) {
            Backdrop(Modifier.fillMaxSize())
            Row(
                Modifier.fillMaxSize().then(longPress).padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(Modifier.weight(1.25f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        ClioScene(
                            speedKmh, LocalDark.current, c.onBackground, c.primary, settings.plate, settings.carLayout, settings.carMotion,
                            customCar, Modifier.fillMaxSize()
                        )
                        if (settings.showSpeed) Column(Modifier.padding(start = 8.dp, top = 4.dp)) {
                            Text(
                                "${(speed * settings.speedUnit.factor).roundToInt()}", color = c.onBackground,
                                fontSize = 88.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = (-2).sp, lineHeight = 88.sp
                            )
                            Text(settings.speedUnit.label.uppercase(), color = c.onSurfaceVariant, fontSize = 14.sp, letterSpacing = 3.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(56.dp).tile(18.dp).clickable { drawer = true }, contentAlignment = Alignment.Center) {
                            UIcon(Ui.Apps, c.primary, 24.sp)
                        }
                        Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(28.dp).background(c.outlineVariant))
                        RecentRow()
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Panel(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) { Clock(Modifier.padding(horizontal = 24.dp, vertical = 18.dp)) }
                    AnimatedVisibility(edit, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        val sel = grid.tiles.firstOrNull { it.key == selected }
                        if (sel != null) TileControls(
                            sel.radius, { grid.setRadius(sel.key, it) },
                            onRemove = { removeTile(sel); selected = null }, onDone = { selected = null },
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        else Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Pill("Ajouter un widget", Modifier.weight(1.5f), filled = true, icon = { UIcon(Ui.Plus, it, 16.sp) }) { picker = true }
                            Pill("Terminé", Modifier.weight(1f), filled = false) { edit = false; selected = null }
                        }
                    }
                    WidgetGrid(grid, edit, selected, { selected = it }, Modifier.weight(1f).fillMaxWidth()) { tile -> TileContent(tile) }
                }
            }
            AnimatedVisibility(
                drawer,
                enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 12 },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(200)) { it / 12 }
            ) { AppDrawer(onSettings = { settingsOpen = true }) { drawer = false } }
            AnimatedVisibility(
                settingsOpen,
                enter = fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 },
                exit = fadeOut(tween(180)) + slideOutHorizontally(tween(200)) { it / 12 }
            ) { Overlay { SettingsScreen(settingsActions) { settingsOpen = false } } }
            AnimatedVisibility(carEditor, enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
                Overlay { CarEditor(customCar, { pickSticker.launch(arrayOf("image/png", "image/webp", "image/*")) }) { carEditor = false } }
            }
        }
        }
        if (picker) WidgetPicker { picker = false }
    }

    @SuppressLint("MissingPermission")
    private fun currentLocation(): Location? = lastFix ?: if (!hasGps()) null else getSystemService(LocationManager::class.java).let { lm ->
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    }

    /** Recently used apps, next to the menu and navigation buttons. */
    @Composable
    private fun RecentRow() {
        val c = MaterialTheme.colorScheme
        if (!usageAllowed && recents.isEmpty()) Text(
            "Activer les applis récentes",
            Modifier.clip(RoundedCornerShape(50))
                .clickable { settingsActions.usageAccess() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            color = c.onSurfaceVariant, fontSize = 14.sp
        )
        else LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(8.dp)) {
            items(recents.take(6), key = { it.component.flattenToString() }) { app ->
                Box(Modifier.animateItem().size(56.dp).clip(CircleShape).clickable { launch(app) }, contentAlignment = Alignment.Center) {
                    AppIcon(app, 44.dp)
                }
            }
        }
    }

    /** Every app icon in the same style: a coolicons glyph, else its themed glyph tinted, else a muted grey version. */
    @Composable
    private fun AppIcon(app: App, size: Dp) {
        val c = MaterialTheme.colorScheme
        Box(
            Modifier.size(size).tile(size * 0.32f),
            contentAlignment = Alignment.Center
        ) {
            if (app.glyph != null) UIcon(app.glyph, c.onSurface, with(LocalDensity.current) { (size * 0.4f).toSp() })
            else if (app.mono) Image(app.icon, app.label, Modifier.size(size * 0.8f), colorFilter = ColorFilter.tint(c.onSurface))
            else Image(
                app.icon, app.label, Modifier.size(size * 0.7f),
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }), alpha = .85f
            )
        }
    }

    @Composable
    private fun BuiltIn(label: String, onClose: () -> Unit, add: () -> Boolean) {
        val c = MaterialTheme.colorScheme
        Text(
            label,
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                onClose()
                if (!add()) android.widget.Toast.makeText(this, "Plus de place sur la grille", android.widget.Toast.LENGTH_LONG).show()
            }.padding(14.dp),
            color = c.primary, fontSize = 17.sp, fontWeight = FontWeight.Medium
        )
    }

    /** What each grid tile draws: the launcher's own panels in the current style, or the app widget clipped to its corners. */
    @Composable
    private fun TileContent(tile: Tile) {
        val r = tile.radius.dp
        when (tile.kind) {
            TileKind.WEATHER -> Panel(Modifier.fillMaxSize(), r) {
                Box(Modifier.fillMaxSize().padding(horizontal = 22.dp), contentAlignment = Alignment.CenterStart) {
                    WeatherPanel(::currentLocation, Modifier.fillMaxWidth())
                }
            }
            TileKind.MUSIC -> Panel(Modifier.fillMaxSize(), r) { MusicTile(tile.h, Modifier.fillMaxSize()) }
            TileKind.APP -> {
                val info = awm.getAppWidgetInfo(tile.appId) ?: return
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(r))) {
                    AndroidView({ host.createView(it, tile.appId, info) }, Modifier.fillMaxSize())
                }
            }
        }
    }

    @Composable
    private fun AppDrawer(onSettings: () -> Unit, onClose: () -> Unit) {
        val apps = remember { loadApps() }
        val c = MaterialTheme.colorScheme
        Overlay {
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Applications", Modifier.weight(1f), color = c.onBackground, fontSize = 30.sp, fontWeight = FontWeight.Light)
                Box(Modifier.size(52.dp).tile(26.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    UIcon(Ui.Close, c.onBackground, 20.sp)
                }
            }
            LazyVerticalGrid(
                GridCells.Adaptive(120.dp), Modifier.weight(1f).padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    // The launcher's own settings, listed with the apps.
                    Column(
                        Modifier.clip(RoundedCornerShape(20.dp)).clickable { onSettings() }.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.size(64.dp).tile(20.dp), contentAlignment = Alignment.Center) { UIcon(Ui.Settings, c.primary, 26.sp) }
                        Spacer(Modifier.height(8.dp))
                        Text("Réglages DEON", color = c.primary, fontSize = 14.sp, maxLines = 1)
                    }
                }
                items(apps) { app ->
                    Column(
                        Modifier.clip(RoundedCornerShape(20.dp)).clickable { onClose(); launch(app) }.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AppIcon(app, 64.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            app.label, color = c.onSurfaceVariant, fontSize = 14.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        }
    }

    @Composable
    private fun WidgetPicker(onClose: () -> Unit) {
        val providers = remember { awm.installedProviders.sortedBy { it.loadLabel(packageManager).lowercase() } }
        val c = MaterialTheme.colorScheme
        AlertDialog(
            onDismissRequest = onClose,
            confirmButton = { TextButton(onClose) { Text("Annuler", color = c.primary) } },
            title = { Text("Ajouter un widget", fontWeight = FontWeight.Light) },
            shape = RoundedCornerShape(28.dp),
            containerColor = c.surface,
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    // The launcher's own widgets first, in the accent colour.
                    item { BuiltIn("Météo DEON", onClose) { grid.add(TileKind.WEATHER) } }
                    item { BuiltIn("Lecteur de musique DEON", onClose) { grid.add(TileKind.MUSIC) } }
                    items(providers) { p ->
                        Text(
                            p.loadLabel(packageManager),
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .clickable { onClose(); pickProvider(p.provider) }.padding(14.dp),
                            color = c.onSurface, fontSize = 17.sp
                        )
                    }
                }
            }
        )
    }
}

/** Full-screen page over the home screen: a frosted pane, or the flat soft-UI background. */
@Composable
private fun Overlay(content: @Composable BoxScope.() -> Unit) {
    if (LocalSettings.current.style == Style.GLASS) Glass(Modifier.fillMaxSize(), RectangleShape, blur = 40.dp) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = .35f)), content = content)
    }
    else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), content = content)
}

@Composable
private fun Clock(modifier: Modifier) {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000) } }
    // Formats are rebuilt each tick so the system 12/24 h setting, language and time zone apply live.
    val hour = android.text.format.DateFormat.getTimeFormat(context)
    val date = SimpleDateFormat(
        android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), Locale.getDefault()
    )
    Column(modifier) {
        Text(hour.format(now), color = c.onSurface, fontSize = 60.sp, fontWeight = FontWeight.ExtraLight, lineHeight = 64.sp)
        Text(date.format(now).replaceFirstChar { it.titlecase() }, color = c.onSurfaceVariant, fontSize = 16.sp)
    }
}
