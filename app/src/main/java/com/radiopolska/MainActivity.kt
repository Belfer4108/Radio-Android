package com.radiopolska

import android.Manifest
import android.content.ClipData
import android.content.ContentValues
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.provider.MediaStore
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.radiopolska.alarm.RadioAlarmConfig
import com.radiopolska.alarm.RadioAlarmScheduler
import com.radiopolska.alarm.RadioAlarmStore
import com.radiopolska.data.PolishRadioStations
import com.radiopolska.data.RadioStation
import com.radiopolska.player.RadioPlaybackService
import com.radiopolska.player.RadioPlayerState
import com.radiopolska.ui.theme.RadioPolskaTheme
import com.radiopolska.ui.theme.RadioSkin
import com.radiopolska.ui.theme.RadioSkins
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.withTimeoutOrNull

private data class ListeningStats(
    val playCount: Int,
    val lastPlayedAt: Long,
)

private data class AdaptiveLayout(
    val horizontalPadding: Dp,
    val gap: Dp,
    val playerPadding: Dp,
    val compactPlayer: Boolean,
    val tileColumns: Int,
    val tileHeight: Dp,
    val menuHeight: Dp,
    val recordingsHeight: Dp,
    val twoPane: Boolean,
)

private const val PREFS_NAME = "radio_polska_preferences"
private const val FAVORITES_KEY = "favorite_station_ids"
private const val SKIN_KEY = "selected_skin_id"
private const val LAST_STATION_KEY = "last_station_id"
private const val HISTORY_KEY = "listening_history"
private const val BACKGROUND_PLAYBACK_KEY = "background_playback_enabled"
private const val MOBILE_WARNING_KEY = "mobile_warning_enabled"
private const val STATION_VIEW_MODE_KEY = "station_view_mode"
private const val STATION_SORT_MODE_KEY = "station_sort_mode"
private const val SEARCH_QUERY_KEY = "search_query"
private const val SELECTED_CATEGORY_KEY = "selected_category"
private const val SELECTED_REGION_KEY = "selected_region"

private enum class StationViewMode {
    List,
    Grid,
}

private enum class StationSortMode(val label: String) {
    Recommended("Polecane"),
    Name("Nazwa"),
    Bitrate("Bitrate"),
    Recent("Ostatnio sluchane"),
    Favorites("Ulubione najpierw"),
}

private enum class TrimMode(val label: String) {
    KeepSelection("Zostaw zaznaczone"),
    RemoveSelection("Wytnij zaznaczone"),
}

private enum class MenuModule {
    Main,
    Timer,
    Alarm,
    Transfer,
    Equalizer,
    Colorofon,
    Filters,
    Station,
    NowPlaying,
    CarMode,
    Backup,
    Skins,
    Permissions,
    Recordings,
    History,
    Settings,
    Help,
}

@UnstableApi
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: savedInstanceState=${savedInstanceState != null}")
        WindowCompat.setDecorFitsSystemWindows(window, true)
        configureAlarmWindow(intent)
        setContent {
            var selectedSkinId by remember { mutableStateOf(loadSkin(this)) }
            val systemDark = isSystemInDarkTheme()
            SideEffect {
                configureSystemBars(selectedSkinId, systemDark)
            }
            RadioPolskaTheme(skinId = selectedSkinId) {
                RadioPolskaApp(
                    selectedSkinId = selectedSkinId,
                    onSkinSelected = { skinId ->
                        selectedSkinId = skinId
                        saveSkin(this, skinId)
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent: action=${intent.action}")
        setIntent(intent)
        configureAlarmWindow(intent)
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")
    }

    override fun onStop() {
        Log.d(TAG, "onStop")
        super.onStop()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: isChangingConfigurations=$isChangingConfigurations")
        super.onDestroy()
    }

    private fun configureSystemBars(skinId: String, systemDark: Boolean) {
        val darkBars = skinId == "night" || (skinId == "system" && systemDark)
        val barColor = if (darkBars) Color(0xFF0B1120).toArgb() else Color(0xFFFFFBFE).toArgb()
        window.statusBarColor = barColor
        window.navigationBarColor = barColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkBars
            isAppearanceLightNavigationBars = !darkBars
        }
    }

    private fun configureAlarmWindow(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_SHOW_ALARM_SCREEN, false) != true) return
        Log.d(TAG, "configureAlarmWindow: show alarm over lockscreen")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    companion object {
        const val TAG = "MainActivity"
        private const val EXTRA_SHOW_ALARM_SCREEN = "show_alarm_screen"

        fun openAlarmScreen(context: Context) {
            Log.d(TAG, "openAlarmScreen")
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_SHOW_ALARM_SCREEN, true)
            }
            context.startActivity(intent)
        }
    }
}

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RadioPolskaApp(
    selectedSkinId: String,
    onSkinSelected: (String) -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val layout = remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        adaptiveLayout(configuration.screenWidthDp, configuration.screenHeightDp)
    }
    LaunchedEffect(context) {
        RadioPlaybackService.restoreSavedState(context)
    }
    val playerState by RadioPlaybackService.state.collectAsState()
    var query by rememberSaveable { mutableStateOf(loadSearchQuery(context)) }
    var selectedCategory by rememberSaveable { mutableStateOf(loadSelectedCategory(context)) }
    var selectedRegion by rememberSaveable { mutableStateOf(loadSelectedRegion(context)) }
    var favorites by remember { mutableStateOf(loadFavorites(context)) }
    var listeningHistory by remember { mutableStateOf(loadListeningHistory(context)) }
    var backgroundPlaybackEnabled by remember { mutableStateOf(loadBackgroundPlaybackEnabled(context)) }
    var mobileWarningEnabled by remember { mutableStateOf(loadMobileWarningEnabled(context)) }
    var stationViewMode by remember { mutableStateOf(loadStationViewMode(context)) }
    var stationSortMode by remember { mutableStateOf(loadStationSortMode(context)) }
    var alarmConfig by remember { mutableStateOf(RadioAlarmStore.load(context)) }
    var activeMenuModule by rememberSaveable { mutableStateOf<MenuModule?>(null) }
    var confirmExitRadio by rememberSaveable { mutableStateOf(false) }
    var permissionRefreshToken by remember { mutableStateOf(0) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        permissionRefreshToken += 1
    }
    val colorofonPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionRefreshToken += 1
    }

    val sortedStations = remember {
        PolishRadioStations.sortedWith(compareBy(::stationPriority, RadioStation::name))
    }
    val categories = remember {
        listOf("Wszystkie", "Ulubione", "Publiczne", "Prywatne", "Informacyjne", "Muzyczne", "Wysoka jakość")
    }
    val regions = remember {
        listOf("Wszystkie") + PolishRadioStations
            .mapNotNull { it.region }
            .distinct()
            .sortedWith(compareBy<String> { if (it == "Ogólnopolskie") 0 else 1 }.thenBy { it })
    }
    val stations = remember(query, selectedCategory, selectedRegion, favorites, stationSortMode, listeningHistory) {
        sortedStations.filter { station ->
            val normalized = query.trim()
            val matchesQuery = normalized.isBlank() ||
                station.name.contains(normalized, ignoreCase = true) ||
                station.city.contains(normalized, ignoreCase = true) ||
                station.region.orEmpty().contains(normalized, ignoreCase = true) ||
                station.genre.contains(normalized, ignoreCase = true) ||
                station.frequency.contains(normalized, ignoreCase = true)

            matchesQuery &&
                station.matchesCategory(selectedCategory, favorites) &&
                (selectedRegion == "Wszystkie" || station.region == selectedRegion)
        }.sortStations(stationSortMode, favorites, listeningHistory)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Radio Polska", fontWeight = FontWeight.Bold)
                        Text(
                            text = "${PolishRadioStations.size} stacji • ${favorites.size} ulubionych",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            stationViewMode = if (stationViewMode == StationViewMode.List) StationViewMode.Grid else StationViewMode.List
                            saveStationViewMode(context, stationViewMode)
                        },
                    ) {
                        StationViewModeIcon(stationViewMode)
                    }
                    IconButton(onClick = { activeMenuModule = MenuModule.Main }) {
                        MenuBarsIcon()
                    }
                },
            )
        },
    ) { innerPadding ->
        val playLastStation: () -> Unit = {
            loadLastStation(context)?.let { station ->
                RadioPlaybackService.play(context, station)
                listeningHistory = listeningHistory.record(station.id)
                saveListeningHistory(context, listeningHistory)
            }
        }
        val stationPlayed: (RadioStation) -> Unit = { station ->
            saveLastStation(context, station.id)
            listeningHistory = listeningHistory.record(station.id)
            saveListeningHistory(context, listeningHistory)
        }
        val toggleFavorite: (RadioStation) -> Unit = { station ->
            favorites = favorites.toggle(station.id)
            saveFavorites(context, favorites)
        }

        if (layout.twoPane) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = layout.horizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(layout.gap),
            ) {
                StationBrowserPanel(
                    query = query,
                    onQueryChange = {
                        query = it
                        saveSearchQuery(context, it)
                    },
                    selectedCategory = selectedCategory,
                    selectedRegion = selectedRegion,
                    stations = stations,
                    currentStation = playerState.currentStation,
                    favorites = favorites,
                    listeningHistory = listeningHistory,
                    viewMode = stationViewMode,
                    layout = layout,
                    modifier = Modifier.weight(1.45f),
                    onSelect = { station -> RadioPlaybackService.play(context, station) },
                    onStationPlayed = stationPlayed,
                    onToggleFavorite = toggleFavorite,
                )
                Column(
                    modifier = Modifier.weight(0.9f),
                    verticalArrangement = Arrangement.spacedBy(layout.gap),
                ) {
                    PlayerBar(
                        state = playerState,
                        lastStation = remember { loadLastStation(context) },
                        mobileWarningEnabled = mobileWarningEnabled,
                        layout = layout,
                        onPrevious = { RadioPlaybackService.previous(context) },
                        onToggle = { RadioPlaybackService.toggle(context) },
                        onNext = { RadioPlaybackService.next(context) },
                        onStop = { RadioPlaybackService.stop(context) },
                        onRecord = { RadioPlaybackService.toggleRecording(context) },
                        onSetVolume = { RadioPlaybackService.setVolume(context, it) },
                        onResumeLast = playLastStation,
                    )
                    Text(
                        text = filterSummary(selectedCategory, selectedRegion, stations.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = layout.horizontalPadding),
            ) {
                PlayerBar(
                    state = playerState,
                    lastStation = remember { loadLastStation(context) },
                    mobileWarningEnabled = mobileWarningEnabled,
                    layout = layout,
                    onPrevious = { RadioPlaybackService.previous(context) },
                    onToggle = { RadioPlaybackService.toggle(context) },
                    onNext = { RadioPlaybackService.next(context) },
                    onStop = { RadioPlaybackService.stop(context) },
                    onRecord = { RadioPlaybackService.toggleRecording(context) },
                    onSetVolume = { RadioPlaybackService.setVolume(context, it) },
                    onResumeLast = playLastStation,
                )
                Spacer(Modifier.height(layout.gap))
                StationBrowserPanel(
                    query = query,
                    onQueryChange = {
                        query = it
                        saveSearchQuery(context, it)
                    },
                    selectedCategory = selectedCategory,
                    selectedRegion = selectedRegion,
                    stations = stations,
                    currentStation = playerState.currentStation,
                    favorites = favorites,
                    listeningHistory = listeningHistory,
                    viewMode = stationViewMode,
                    layout = layout,
                    modifier = Modifier.fillMaxSize(),
                    onSelect = { station -> RadioPlaybackService.play(context, station) },
                    onStationPlayed = stationPlayed,
                    onToggleFavorite = toggleFavorite,
                )
            }
        }
    }

    when (activeMenuModule) {
        MenuModule.Main -> CompactModuleMenuDialog(
            layout = layout,
            onExitRadio = {
                confirmExitRadio = true
            },
            onOpenTimer = { activeMenuModule = MenuModule.Timer },
            onOpenAlarm = { activeMenuModule = MenuModule.Alarm },
            onOpenTransfer = { activeMenuModule = MenuModule.Transfer },
            onOpenEqualizer = { activeMenuModule = MenuModule.Equalizer },
            onOpenColorofon = { activeMenuModule = MenuModule.Colorofon },
            onOpenFilters = { activeMenuModule = MenuModule.Filters },
            onOpenStation = { activeMenuModule = MenuModule.Station },
            onOpenNowPlaying = { activeMenuModule = MenuModule.NowPlaying },
            onOpenCarMode = { activeMenuModule = MenuModule.CarMode },
            onOpenBackup = { activeMenuModule = MenuModule.Backup },
            onOpenSkins = { activeMenuModule = MenuModule.Skins },
            onOpenPermissions = { activeMenuModule = MenuModule.Permissions },
            onOpenRecordings = { activeMenuModule = MenuModule.Recordings },
            onOpenHistory = { activeMenuModule = MenuModule.History },
            onOpenSettings = { activeMenuModule = MenuModule.Settings },
            onOpenHelp = { activeMenuModule = MenuModule.Help },
            onDismiss = { activeMenuModule = null },
        )
        MenuModule.Timer -> TimerDialog(
            state = playerState,
            onSetTimer = { minutes -> RadioPlaybackService.setSleepTimer(context, minutes) },
            onCancelTimer = { RadioPlaybackService.cancelSleepTimer(context) },
            onBack = { activeMenuModule = MenuModule.Main },
            onDismiss = { activeMenuModule = null },
        )
        MenuModule.Alarm -> RadioAlarmDialog(
            config = alarmConfig,
            exactAlarmAllowed = RadioAlarmScheduler.canScheduleExact(context),
            onOpenExactAlarmSettings = { RadioAlarmScheduler.openExactAlarmSettings(context) },
            onSave = { config ->
                alarmConfig = config
                RadioAlarmStore.save(context, config)
                if (config.enabled) RadioAlarmScheduler.schedule(context, config) else RadioAlarmScheduler.cancel(context)
            },
            onBack = { activeMenuModule = MenuModule.Main },
            onDismiss = { activeMenuModule = null },
        )
        MenuModule.Transfer -> TransferDialog(
            state = playerState,
            onResetTransfer = { RadioPlaybackService.resetTransfer(context) },
            onBack = { activeMenuModule = MenuModule.Main },
            onDismiss = { activeMenuModule = null },
        )
        MenuModule.Equalizer -> EqualizerDialog(
            state = playerState,
            onSetEnabled = { RadioPlaybackService.setEqualizerEnabled(context, it) },
            onSetPreset = { RadioPlaybackService.setEqualizerPreset(context, it) },
            onSetBand = { index, level -> RadioPlaybackService.setEqualizerBand(context, index, level) },
            onBack = { activeMenuModule = MenuModule.Main },
            onDismiss = { activeMenuModule = null },
        )
        MenuModule.Colorofon -> ColorofonDialog(
            state = playerState,
            permissionsGranted = hasColorofonPermissions(context),
            onRequestPermissions = {
                colorofonPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
            },
            onSetEnabled = { RadioPlaybackService.setColorofonEnabled(context, it) },
            onSetIntensity = { RadioPlaybackService.setColorofonIntensity(context, it) },
            onSetBand = { band, level -> RadioPlaybackService.setColorofonBand(context, band, level) },
            onBack = { activeMenuModule = MenuModule.Main },
            onDismiss = { activeMenuModule = null },
        )
        MenuModule.Filters -> FilterDialog(
            categories = categories,
            regions = regions,
            selectedCategory = selectedCategory,
            selectedRegion = selectedRegion,
            selectedSortMode = stationSortMode,
            onCategorySelected = {
                selectedCategory = it
                saveSelectedCategory(context, it)
            },
            onRegionSelected = {
                selectedRegion = it
                saveSelectedRegion(context, it)
            },
            onSortSelected = {
                stationSortMode = it
                saveStationSortMode(context, it)
            },
            onClear = {
                selectedCategory = "Wszystkie"
                selectedRegion = "Wszystkie"
                stationSortMode = StationSortMode.Recommended
                saveSelectedCategory(context, selectedCategory)
                saveSelectedRegion(context, selectedRegion)
                saveStationSortMode(context, stationSortMode)
            },
            onBack = { activeMenuModule = MenuModule.Main },
            onDismiss = { activeMenuModule = null },
        )
        MenuModule.Station -> StationInfoDialog(
            state = playerState,
            isFavorite = playerState.currentStation?.id in favorites,
            onToggleFavorite = {
                val station = playerState.currentStation ?: return@StationInfoDialog
                favorites = favorites.toggle(station.id)
                saveFavorites(context, favorites)
            },
            onBack = { activeMenuModule = MenuModule.Main },
            onDismiss = { activeMenuModule = null },
        )
        MenuModule.NowPlaying -> NowPlayingDialog(
            state = playerState,
            isFavorite = playerState.currentStation?.id in favorites,
            onToggleFavorite = {
                val station = playerState.currentStation ?: return@NowPlayingDialog
                favorites = favorites.toggle(station.id)
                saveFavorites(context, favorites)
            },
            onPrevious = { RadioPlaybackService.previous(context) },
            onToggle = { RadioPlaybackService.toggle(context) },
            onNext = { RadioPlaybackService.next(context) },
            onStop = { RadioPlaybackService.stop(context) },
            onRecord = { RadioPlaybackService.toggleRecording(context) },
            onBack = { activeMenuModule = MenuModule.Main },
            onDismiss = { activeMenuModule = null },
        )
        MenuModule.CarMode -> CarModeDialog(
            state = playerState,
            onPrevious = { RadioPlaybackService.previous(context) },
            onToggle = { RadioPlaybackService.toggle(context) },
            onNext = { RadioPlaybackService.next(context) },
            onStop = { RadioPlaybackService.stop(context) },
            onBack = { activeMenuModule = MenuModule.Main },
            onDismiss = { activeMenuModule = null },
        )
        MenuModule.Backup -> BackupDialog(
            context = context,
            favorites = favorites,
            history = listeningHistory,
            selectedSkinId = selectedSkinId,
            stationViewMode = stationViewMode,
            stationSortMode = stationSortMode,
            backgroundPlaybackEnabled = backgroundPlaybackEnabled,
            mobileWarningEnabled = mobileWarningEnabled,
            alarmConfig = alarmConfig,
            onBack = { activeMenuModule = MenuModule.Main },
            onDismiss = { activeMenuModule = null },
        )
        MenuModule.Skins -> SkinDialog(
            skins = RadioSkins,
            selectedSkinId = selectedSkinId,
            onSkinSelected = onSkinSelected,
            onBack = { activeMenuModule = MenuModule.Main },
            onDismiss = { activeMenuModule = null },
        )
        MenuModule.Permissions -> PermissionsDialog(
            context = context,
            refreshToken = permissionRefreshToken,
            onRequestNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onRequestBatteryExemption = {
                requestBatteryOptimizationExemption(context)
                permissionRefreshToken += 1
            },
            onRequestColorofonPermissions = {
                colorofonPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
            },
            onOpenAppSettings = { openAppSettings(context) },
            onOpenBatterySettings = { openBatterySettings(context) },
            onBack = { activeMenuModule = MenuModule.Main },
            onDismiss = { activeMenuModule = null },
        )
        MenuModule.Recordings -> RecordingsDialog(
            context = context,
            layout = layout,
            onBack = { activeMenuModule = MenuModule.Main },
            onDismiss = { activeMenuModule = null },
        )
        MenuModule.History -> HistoryDialog(
            history = listeningHistory,
            onPlayStation = { station ->
                RadioPlaybackService.play(context, station)
                saveLastStation(context, station.id)
                listeningHistory = listeningHistory.record(station.id)
                saveListeningHistory(context, listeningHistory)
            },
            onClearHistory = {
                listeningHistory = emptyMap()
                saveListeningHistory(context, listeningHistory)
            },
            onBack = { activeMenuModule = MenuModule.Main },
            onDismiss = { activeMenuModule = null },
        )
        MenuModule.Settings -> AppSettingsDialog(
            backgroundPlaybackEnabled = backgroundPlaybackEnabled,
            mobileWarningEnabled = mobileWarningEnabled,
            onSetBackgroundPlayback = { enabled ->
                backgroundPlaybackEnabled = enabled
                saveBackgroundPlaybackEnabled(context, enabled)
            },
            onSetMobileWarning = { enabled ->
                mobileWarningEnabled = enabled
                saveMobileWarningEnabled(context, enabled)
            },
            onBack = { activeMenuModule = MenuModule.Main },
            onDismiss = { activeMenuModule = null },
        )
        MenuModule.Help -> HelpDialog(
            onBack = { activeMenuModule = MenuModule.Main },
            onDismiss = { activeMenuModule = null },
        )
        null -> Unit
    }

    if (confirmExitRadio) {
        AlertDialog(
            onDismissRequest = { confirmExitRadio = false },
            title = { Text("Wyjdz z radia") },
            text = { Text("Na pewno zatrzymac odtwarzanie i zamknac aplikacje?") },
            confirmButton = {
                Button(
                    onClick = {
                        RadioPlaybackService.stop(context)
                        activeMenuModule = null
                        confirmExitRadio = false
                        (context as? ComponentActivity)?.finish()
                    },
                ) {
                    Text("Wyjdz")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmExitRadio = false }) {
                    Text("Anuluj")
                }
            },
        )
    }

    if (playerState.alarmActive) {
        ActiveRadioAlarmDialog(
            state = playerState,
            alarmConfig = alarmConfig,
            onSnooze = { RadioPlaybackService.snoozeAlarm(context) },
            onDismissAlarm = { RadioPlaybackService.dismissAlarm(context, keepPlaying = false) },
            onKeepPlaying = { RadioPlaybackService.dismissAlarm(context, keepPlaying = true) },
        )
    }
}

@Composable
private fun ActiveRadioAlarmDialog(
    state: RadioPlayerState,
    alarmConfig: RadioAlarmConfig,
    onSnooze: () -> Unit,
    onDismissAlarm: () -> Unit,
    onKeepPlaying: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { },
        title = {
            Text(
                text = "Alarm radiowy",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = state.currentStation?.name ?: "Radio gra",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Glosnosc: ${alarmConfig.alarmVolumePercent}% - Drzemka: ${alarmConfig.snoozeMinutes} min - Auto stop: ${alarmConfig.autoOffMinutes} min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.isBuffering) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(onClick = onSnooze) {
                Text("Drzemka")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismissAlarm) {
                    Text("Wylacz alarm")
                }
                TextButton(onClick = onKeepPlaying) {
                    Text("Zostaw radio")
                }
            }
        },
    )
}

@Composable
private fun StationBrowserPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedCategory: String,
    selectedRegion: String,
    stations: List<RadioStation>,
    currentStation: RadioStation?,
    favorites: Set<String>,
    listeningHistory: Map<String, ListeningStats>,
    viewMode: StationViewMode,
    layout: AdaptiveLayout,
    modifier: Modifier = Modifier,
    onSelect: (RadioStation) -> Unit,
    onStationPlayed: (RadioStation) -> Unit,
    onToggleFavorite: (RadioStation) -> Unit,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Szukaj stacji") },
        )
        if (query.isNotBlank()) {
            TextButton(onClick = { onQueryChange("") }, modifier = Modifier.fillMaxWidth()) {
                Text("Wyczysc wyszukiwanie")
            }
        }
        Spacer(Modifier.height(if (layout.compactPlayer) 4.dp else 6.dp))
        Text(
            text = filterSummary(selectedCategory, selectedRegion, stations.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        StationList(
            stations = stations,
            currentStation = currentStation,
            favorites = favorites,
            viewMode = viewMode,
            layout = layout,
            onSelect = onSelect,
            listeningHistory = listeningHistory,
            onStationPlayed = onStationPlayed,
            onToggleFavorite = onToggleFavorite,
        )
    }
}

@Composable
private fun StationViewModeIcon(mode: StationViewMode) {
    if (mode == StationViewMode.List) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .border(1.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(3.dp))
                .padding(5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(2) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(1.dp)),
                            )
                        }
                    }
                }
            }
        }
    } else {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                repeat(3) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(1.dp)),
                        )
                        Box(
                            modifier = Modifier
                                .width(17.dp)
                                .height(3.dp)
                                .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuBarsIcon() {
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(4) {
            Box(
                modifier = Modifier
                    .width(22.dp)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(1.dp)),
            )
        }
    }
}

@Composable
private fun PlayerBar(
    state: RadioPlayerState,
    lastStation: RadioStation?,
    mobileWarningEnabled: Boolean,
    layout: AdaptiveLayout,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onRecord: () -> Unit,
    onSetVolume: (Float) -> Unit,
    onResumeLast: () -> Unit,
) {
    Surface(
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(layout.playerPadding)) {
            if (state.isBuffering) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = state.currentStation?.name ?: "Nie wybrano stacji",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.currentStation?.let {
                    "${it.city} • ${it.bitrate} kbps • ${state.status} • ${formatBytes(state.dataUsedBytes)}"
                } ?: "Wybierz stację z listy",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (mobileWarningEnabled && state.currentNetworkLabel == "Dane komórkowe" && state.isPlaying) {
                Text(
                    text = "Uwaga: odtwarzanie korzysta z danych komórkowych.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = "RDS: ${state.rdsText.removePrefix("RDS: ")}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MiniVisualizer(
                active = state.isPlaying,
                buffering = state.isBuffering,
            )
            Spacer(Modifier.height(if (layout.compactPlayer) 6.dp else 8.dp))
            if (state.currentStation == null && lastStation != null) {
                Button(onClick = onResumeLast, modifier = Modifier.fillMaxWidth()) {
                    Text("Wznów: ${lastStation.name}")
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onPrevious, enabled = state.currentStation != null) { Text("Poprz.") }
                Button(onClick = onToggle, enabled = state.currentStation != null) { Text(if (state.isPlaying) "Pauza" else "Play") }
                TextButton(onClick = onNext, enabled = state.currentStation != null) { Text("Nast.") }
                TextButton(onClick = onStop, enabled = state.currentStation != null) { Text("Stop") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RecordingButton(
                    isRecording = state.isRecording,
                    enabled = state.currentStation != null,
                    onToggleRecording = onRecord,
                    modifier = Modifier.weight(0.32f),
                )
                Slider(
                    value = state.volume,
                    onValueChange = onSetVolume,
                    enabled = state.currentStation != null,
                    modifier = Modifier.weight(0.68f),
                )
            }
            if (state.isRecording) {
                Text(
                    text = "Nagrywanie: ${state.recordingFileName ?: "plik"} • ${formatBytes(state.recordingBytes)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RecordingButton(
    isRecording: Boolean,
    enabled: Boolean,
    onToggleRecording: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Surface(
        modifier = modifier
            .height(42.dp)
            .pointerInput(enabled, isRecording) {
                detectTapGestures(
                    onTap = {
                        if (enabled && isRecording) onToggleRecording()
                    },
                    onPress = {
                        if (!enabled || isRecording) return@detectTapGestures
                        val releasedBeforeDelay = withTimeoutOrNull(2000L) {
                            tryAwaitRelease()
                            true
                        } ?: false
                        if (!releasedBeforeDelay) onToggleRecording()
                    },
                )
            },
        shape = RoundedCornerShape(8.dp),
        color = when {
            !enabled -> MaterialTheme.colorScheme.surface
            isRecording -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.error
        },
        border = if (enabled) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (isRecording) "STOP" else "REC",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                    isRecording -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onError
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ScreenColorofon(state: RadioPlayerState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ColorofonLight(
            color = Color(0xFFE53935),
            level = state.colorofonBassLevel,
            modifier = Modifier.weight(1f),
        )
        ColorofonLight(
            color = Color(0xFF43A047),
            level = state.colorofonMidLevel,
            modifier = Modifier.weight(1f),
        )
        ColorofonLight(
            color = Color(0xFFFDD835),
            level = state.colorofonTrebleLevel,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ColorofonLight(
    color: Color,
    level: Int,
    modifier: Modifier = Modifier,
) {
    val alpha = (0.12f + level.coerceIn(0, 100) / 100f * 0.88f).coerceIn(0.12f, 1f)
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = alpha)),
    )
}

@Composable
private fun MiniVisualizer(
    active: Boolean,
    buffering: Boolean,
) {
    val transition = rememberInfiniteTransition(label = "player_visualizer")
    val levels = List(6) { index ->
        transition.animateFloat(
            initialValue = if (active || buffering) 0.25f else 0.18f,
            targetValue = if (active || buffering) 0.55f + (index % 3) * 0.15f else 0.18f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 420 + index * 90, delayMillis = index * 35),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bar_$index",
        ).value
    }
    val barColor = when {
        buffering -> MaterialTheme.colorScheme.tertiary
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
    ) {
        levels.forEach { level ->
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height((22f * level).dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor),
            )
        }
    }
}

@Composable
private fun ModuleMenuDialog(
    state: RadioPlayerState,
    stationViewMode: StationViewMode,
    onToggleViewMode: () -> Unit,
    onToggleRecording: () -> Unit,
    onExitRadio: () -> Unit,
    onOpenTimer: () -> Unit,
    onOpenAlarm: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenColorofon: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenStation: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenCarMode: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenSkins: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenRecordings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Menu") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenTimer, modifier = Modifier.fillMaxWidth()) { Text("Timer") }
                Button(onClick = onOpenTransfer, modifier = Modifier.fillMaxWidth()) { Text("Transfer i analiza danych") }
                Button(onClick = onOpenFilters, modifier = Modifier.fillMaxWidth()) { Text("Filtrowanie stacji") }
                Button(onClick = onOpenStation, modifier = Modifier.fillMaxWidth()) { Text("Moduł stacji") }
                Button(onClick = onOpenRecordings, modifier = Modifier.fillMaxWidth()) { Text("Nagrania") }
                Button(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) { Text("Historia słuchania") }
                Button(onClick = onOpenSkins, modifier = Modifier.fillMaxWidth()) { Text("Skórki") }
                Button(onClick = onOpenPermissions, modifier = Modifier.fillMaxWidth()) { Text("Uprawnienia i ustawienia") }
                Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("Ustawienia aplikacji") }
                Button(onClick = onOpenHelp, modifier = Modifier.fillMaxWidth()) { Text("Pomoc i FAQ") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}

@Composable
private fun CompactModuleMenuDialog(
    layout: AdaptiveLayout,
    onExitRadio: () -> Unit,
    onOpenTimer: () -> Unit,
    onOpenAlarm: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenColorofon: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenStation: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenCarMode: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenSkins: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenRecordings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Menu") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.height(layout.menuHeight),
            ) {
                item { CompactMenuAction("TM", "Timer", "Wylaczanie o wybranym czasie", onOpenTimer) }
                item { CompactMenuAction("AL", "Alarm radiowy", "Budzenie wybrana stacja", onOpenAlarm) }
                item { CompactMenuAction("MB", "Transfer", "Wi-Fi, dane komorkowe i analiza", onOpenTransfer) }
                item { CompactMenuAction("EQ", "Equalizer", "Presety i reczne pasma dzwieku", onOpenEqualizer) }
                item { CompactMenuAction("FX", "Kolorofon", "Latarka reagujaca na muzyke", onOpenColorofon) }
                item { CompactMenuAction("FL", "Filtrowanie", "Kategorie, region i ulubione", onOpenFilters) }
                item { CompactMenuAction("ST", "Stacja", "Informacje o aktualnej stacji", onOpenStation) }
                item { CompactMenuAction("ON", "Teraz grane", "Duzy widok aktualnej stacji", onOpenNowPlaying) }
                item { CompactMenuAction("AU", "Tryb auto", "Duze przyciski do samochodu", onOpenCarMode) }
                item { CompactMenuAction("BK", "Backup", "Eksport ustawien aplikacji", onOpenBackup) }
                item { CompactMenuAction("REC", "Nagrania", "Odtwarzanie zapisanych plikow", onOpenRecordings) }
                item { CompactMenuAction("HI", "Historia", "Ostatnio sluchane stacje", onOpenHistory) }
                item { CompactMenuAction("SK", "Skorki", "Wyglad aplikacji", onOpenSkins) }
                item { CompactMenuAction("UP", "Uprawnienia", "Tlo, bateria i powiadomienia", onOpenPermissions) }
                item { CompactMenuAction("US", "Ustawienia", "Dzialanie w tle i ostrzezenia", onOpenSettings) }
                item { CompactMenuAction("?", "Pomoc / FAQ", "Instrukcja radia i ustawien telefonu", onOpenHelp) }
                item { CompactMenuAction("X", "Wyjdz z radia", "Zatrzymaj odtwarzanie i zamknij ekran", onExitRadio) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}

@Composable
private fun CompactMenuAction(
    icon: String,
    title: String,
    detail: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (enabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        tonalElevation = if (enabled) 3.dp else 0.dp,
        border = BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.outline.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(8.dp),
                color = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        icon,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TimerDialog(
    state: RadioPlayerState,
    onSetTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    var customMinutes by remember { mutableStateOf("") }
    val parsedMinutes = customMinutes.toIntOrNull()?.coerceIn(1, 24 * 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Timer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (state.sleepTimerEndAtMillis != null) {
                        "Pozostało: ${formatDuration(state.remainingSleepSeconds)}"
                    } else {
                        "Timer wyłączony"
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(15, 30, 60).forEach { minutes ->
                        Button(onClick = { onSetTimer(minutes) }) { Text("${minutes}m") }
                    }
                }
                OutlinedTextField(
                    value = customMinutes,
                    onValueChange = { value -> customMinutes = value.filter { it.isDigit() }.take(4) },
                    singleLine = true,
                    label = { Text("Własny czas w minutach") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Button(
                    onClick = { parsedMinutes?.let(onSetTimer) },
                    enabled = parsedMinutes != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Ustaw własny czas") }
                TextButton(onClick = onCancelTimer, enabled = state.sleepTimerEndAtMillis != null) {
                    Text("Anuluj timer")
                }
            }
        },
        confirmButton = { Button(onClick = onBack) { Text("Menu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}

@Composable
private fun RadioAlarmDialog(
    config: RadioAlarmConfig,
    exactAlarmAllowed: Boolean,
    onOpenExactAlarmSettings: () -> Unit,
    onSave: (RadioAlarmConfig) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val fallbackStation = remember { PolishRadioStations.first() }
    var enabled by remember(config) { mutableStateOf(config.enabled) }
    var stationId by remember(config) { mutableStateOf(config.stationId.ifBlank { fallbackStation.id }) }
    var stationQuery by remember { mutableStateOf("") }
    var hourText by remember(config) { mutableStateOf(config.hour.toString().padStart(2, '0')) }
    var minuteText by remember(config) { mutableStateOf(config.minute.toString().padStart(2, '0')) }
    var repeat by remember(config) { mutableStateOf(config.repeat) }
    var weekdays by remember(config) { mutableStateOf(config.weekdays) }
    var rampVolume by remember(config) { mutableStateOf(config.rampVolume) }
    var snoozeText by remember(config) { mutableStateOf(config.snoozeMinutes.toString()) }
    var autoOffText by remember(config) { mutableStateOf(config.autoOffMinutes.toString()) }
    var alarmVolumePercent by remember(config) { mutableStateOf(config.alarmVolumePercent.toFloat()) }

    val selectedStation = PolishRadioStations.firstOrNull { it.id == stationId } ?: fallbackStation
    val hour = hourText.toIntOrNull()?.coerceIn(0, 23)
    val minute = minuteText.toIntOrNull()?.coerceIn(0, 59)
    val snooze = snoozeText.toIntOrNull()?.coerceIn(1, 60) ?: 10
    val autoOff = autoOffText.toIntOrNull()?.coerceIn(0, 240) ?: 60
    val filteredStations = remember(stationQuery) {
        PolishRadioStations
            .filter {
                stationQuery.isBlank() ||
                    it.name.contains(stationQuery, ignoreCase = true) ||
                    it.city.contains(stationQuery, ignoreCase = true)
            }
            .take(10)
    }
    val days = listOf(
        Calendar.MONDAY to "Pn",
        Calendar.TUESDAY to "Wt",
        Calendar.WEDNESDAY to "Sr",
        Calendar.THURSDAY to "Cz",
        Calendar.FRIDAY to "Pt",
        Calendar.SATURDAY to "So",
        Calendar.SUNDAY to "Nd",
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Alarm radiowy") },
        text = {
            LazyColumn(
                modifier = Modifier.height(460.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Wlacz alarm", fontWeight = FontWeight.SemiBold)
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                }
                if (!exactAlarmAllowed) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "Android wymaga zgody na dokladne alarmy, zeby radio moglo wlaczyc sie o wybranej godzinie.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Button(onClick = onOpenExactAlarmSettings, modifier = Modifier.fillMaxWidth()) {
                                    Text("Zezwol na dokladne alarmy")
                                }
                            }
                        }
                    }
                }
                item {
                    Text("Stacja: ${selectedStation.name}", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = stationQuery,
                        onValueChange = { stationQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Szukaj stacji do alarmu") },
                    )
                }
                items(filteredStations) { station ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { stationId = station.id },
                        shape = RoundedCornerShape(8.dp),
                        color = if (station.id == stationId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = station.iconUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(station.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                Text("${station.city} - ${station.bitrate} kbps", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                item {
                    Text(
                        text = "Glosnosc budzenia: ${alarmVolumePercent.roundToInt()}%",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Slider(
                        value = alarmVolumePercent,
                        onValueChange = { alarmVolumePercent = it.coerceIn(5f, 100f) },
                        valueRange = 5f..100f,
                        steps = 18,
                    )
                    Text(
                        text = "Alarm ustawi glosnosc multimediow telefonu na ten poziom.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = hourText,
                            onValueChange = { hourText = it.filter(Char::isDigit).take(2) },
                            label = { Text("Godz.") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = minuteText,
                            onValueChange = { minuteText = it.filter(Char::isDigit).take(2) },
                            label = { Text("Min.") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Powtarzalny", fontWeight = FontWeight.SemiBold)
                        Switch(checked = repeat, onCheckedChange = { repeat = it })
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        days.forEach { (day, label) ->
                            FilterChip(
                                selected = day in weekdays,
                                onClick = {
                                    weekdays = if (day in weekdays) weekdays - day else weekdays + day
                                },
                                label = { Text(label) },
                                enabled = repeat,
                            )
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Narastanie glosnosci", fontWeight = FontWeight.SemiBold)
                        Switch(checked = rampVolume, onCheckedChange = { rampVolume = it })
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = snoozeText,
                            onValueChange = { snoozeText = it.filter(Char::isDigit).take(2) },
                            label = { Text("Drzemka min") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = autoOffText,
                            onValueChange = { autoOffText = it.filter(Char::isDigit).take(3) },
                            label = { Text("Auto stop min") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        RadioAlarmConfig(
                            enabled = enabled,
                            stationId = stationId,
                            hour = hour ?: 7,
                            minute = minute ?: 0,
                            repeat = repeat,
                            weekdays = if (repeat) weekdays else emptySet(),
                            rampVolume = rampVolume,
                            snoozeMinutes = snooze,
                            autoOffMinutes = autoOff,
                            alarmVolumePercent = alarmVolumePercent.roundToInt().coerceIn(5, 100),
                        ),
                    )
                    onBack()
                },
                enabled = hour != null && minute != null && stationId.isNotBlank(),
            ) {
                Text("Zapisz")
            }
        },
        dismissButton = { TextButton(onClick = onBack) { Text("Menu") } },
    )
}

@Composable
private fun TransferDialog(
    state: RadioPlayerState,
    onResetTransfer: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transfer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatLine("Aktualna sieć", state.currentNetworkLabel)
                StatLine("Razem", formatBytes(state.dataUsedBytes))
                StatLine("Wi‑Fi", formatBytes(state.wifiDataUsedBytes))
                StatLine("Dane komórkowe", formatBytes(state.mobileDataUsedBytes))
                StatLine("Inne połączenia", formatBytes(state.otherDataUsedBytes))
                val mobilePercent = if (state.dataUsedBytes > 0L) {
                    (state.mobileDataUsedBytes * 100.0 / state.dataUsedBytes)
                } else {
                    0.0
                }
                Text(
                    text = "Analiza: dane komórkowe stanowią ${"%.1f".format(mobilePercent)}% transferu od ostatniego resetu.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onResetTransfer, modifier = Modifier.fillMaxWidth()) {
                    Text("Resetuj analizę transferu")
                }
            }
        },
        confirmButton = { Button(onClick = onBack) { Text("Menu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EqualizerDialog(
    state: RadioPlayerState,
    onSetEnabled: (Boolean) -> Unit,
    onSetPreset: (String) -> Unit,
    onSetBand: (Int, Int) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Equalizer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Wlaczony", fontWeight = FontWeight.SemiBold)
                    Switch(checked = state.equalizerEnabled, onCheckedChange = onSetEnabled)
                }
                Text("Preset: ${state.equalizerPreset}", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RadioPlaybackService.EqualizerPresets.keys.forEach { preset ->
                        FilterChip(
                            selected = preset == state.equalizerPreset,
                            onClick = { onSetPreset(preset) },
                            label = { Text(preset) },
                        )
                    }
                }
                val labels = listOf("Bass", "Low", "Mid", "High", "Treble")
                state.equalizerBands.forEachIndexed { index, level ->
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(labels.getOrElse(index) { "Band ${index + 1}" }, style = MaterialTheme.typography.labelMedium)
                            Text("${level} dB", style = MaterialTheme.typography.labelMedium)
                        }
                        Slider(
                            value = level.toFloat(),
                            onValueChange = { onSetBand(index, it.roundToInt()) },
                            valueRange = -12f..12f,
                            steps = 23,
                        )
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onBack) { Text("Menu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}

@Composable
private fun ColorofonDialog(
    state: RadioPlayerState,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onSetIntensity: (Int) -> Unit,
    onSetBand: (String, Int) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kolorofon") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Latarka reaguje na pasma muzyki: bas daje mocniejszy blysk, a wysokie/perkusyjne dzwieki krotkie migniecia. Wymaga aparatu i analizy audio.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!permissionsGranted) {
                    Button(onClick = onRequestPermissions, modifier = Modifier.fillMaxWidth()) {
                        Text("Nadaj uprawnienia")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Wlaczony", fontWeight = FontWeight.SemiBold)
                    Switch(
                        checked = state.colorofonEnabled,
                        enabled = permissionsGranted,
                        onCheckedChange = onSetEnabled,
                    )
                }
                Text("Czulosc: ${state.colorofonIntensity}", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = state.colorofonIntensity.toFloat(),
                    onValueChange = { onSetIntensity(it.roundToInt()) },
                    valueRange = -100f..100f,
                    steps = 199,
                )
                ColorofonBandSlider("Bas", state.colorofonBass) { onSetBand("bass", it) }
                ColorofonBandSlider("Srodek", state.colorofonMid) { onSetBand("mid", it) }
                ColorofonBandSlider("Sopran", state.colorofonTreble) { onSetBand("treble", it) }
            }
        },
        confirmButton = { Button(onClick = onBack) { Text("Menu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorofonBandSlider(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text("${value}%", style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..100f,
            steps = 99,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDialog(
    categories: List<String>,
    regions: List<String>,
    selectedCategory: String,
    selectedRegion: String,
    selectedSortMode: StationSortMode,
    onCategorySelected: (String) -> Unit,
    onRegionSelected: (String) -> Unit,
    onSortSelected: (StationSortMode) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filtrowanie") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChipGroup("Kategorie", categories, selectedCategory, onCategorySelected)
                FilterChipGroup("Region", regions, selectedRegion, onRegionSelected)
                SortChipGroup("Sortowanie", StationSortMode.entries, selectedSortMode, onSortSelected)
            }
        },
        confirmButton = { Button(onClick = onBack) { Text("Menu") } },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text("Wyczyść") }
                TextButton(onClick = onDismiss) { Text("Zamknij") }
            }
        },
    )
}

@Composable
private fun StationInfoDialog(
    state: RadioPlayerState,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val station = state.currentStation
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Moduł stacji") },
        text = {
            if (station == null) {
                Text("Nie wybrano stacji.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatLine("Nazwa", station.name)
                    StatLine("Miasto", station.city)
                    StatLine("Region", station.region ?: "Online")
                    StatLine("Kategoria", station.category)
                    StatLine("Gatunek", station.genre)
                    StatLine("Bitrate", "${station.bitrate} kbps")
                    StatLine("Jakość", if (station.isHighQuality) "Wysoka" else "Standardowa")
                    StatLine("Status", state.status)
                    Button(onClick = onToggleFavorite, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isFavorite) "Usuń z ulubionych" else "Dodaj do ulubionych")
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onBack) { Text("Menu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}

@Composable
private fun NowPlayingDialog(
    state: RadioPlayerState,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onRecord: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val station = state.currentStation
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Teraz grane") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (station != null) {
                    AsyncImage(
                        model = station.iconUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(112.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Text(station.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${station.city} - ${station.bitrate} kbps - ${state.status}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("RDS: ${state.rdsText.removePrefix("RDS: ")}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    MiniVisualizer(active = state.isPlaying, buffering = state.isBuffering)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onPrevious) { Text("Poprz.") }
                        Button(onClick = onToggle) { Text(if (state.isPlaying) "Pauza" else "Play") }
                        Button(onClick = onNext) { Text("Nast.") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onStop) { Text("Stop") }
                        TextButton(onClick = onToggleFavorite) { Text(if (isFavorite) "Serce pelne" else "Serce puste") }
                        TextButton(onClick = onRecord) { Text(if (state.isRecording) "Stop REC" else "REC") }
                    }
                } else {
                    Text("Nie wybrano stacji.")
                }
            }
        },
        confirmButton = { Button(onClick = onBack) { Text("Menu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}

@Composable
private fun CarModeDialog(
    state: RadioPlayerState,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tryb auto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.currentStation?.name ?: "Nie wybrano stacji", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(state.status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onPrevious, modifier = Modifier.weight(1f).height(64.dp)) { Text("<<") }
                    Button(onClick = onToggle, modifier = Modifier.weight(1.2f).height(64.dp)) { Text(if (state.isPlaying) "Pauza" else "Play") }
                    Button(onClick = onNext, modifier = Modifier.weight(1f).height(64.dp)) { Text(">>") }
                }
                Button(onClick = onStop, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Stop") }
            }
        },
        confirmButton = { Button(onClick = onBack) { Text("Menu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}

@Composable
private fun BackupDialog(
    context: Context,
    favorites: Set<String>,
    history: Map<String, ListeningStats>,
    selectedSkinId: String,
    stationViewMode: StationViewMode,
    stationSortMode: StationSortMode,
    backgroundPlaybackEnabled: Boolean,
    mobileWarningEnabled: Boolean,
    alarmConfig: RadioAlarmConfig,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val backupText = remember(favorites, history, selectedSkinId, stationViewMode, stationSortMode, backgroundPlaybackEnabled, mobileWarningEnabled, alarmConfig) {
        buildSettingsBackupText(
            favorites = favorites,
            history = history,
            selectedSkinId = selectedSkinId,
            stationViewMode = stationViewMode,
            stationSortMode = stationSortMode,
            backgroundPlaybackEnabled = backgroundPlaybackEnabled,
            mobileWarningEnabled = mobileWarningEnabled,
            alarmConfig = alarmConfig,
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Eksportuje ulubione, historie, wyglad, widok listy, sortowanie i alarm jako tekst.", style = MaterialTheme.typography.bodyMedium)
                StatLine("Ulubione", favorites.size.toString())
                StatLine("Historia", history.size.toString())
                Button(onClick = { shareText(context, "Backup Radio Polska", backupText) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Udostepnij backup")
                }
            }
        },
        confirmButton = { Button(onClick = onBack) { Text("Menu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}

@Composable
private fun SkinDialog(
    skins: List<RadioSkin>,
    selectedSkinId: String,
    onSkinSelected: (String) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Skórki") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                skins.forEach { skin ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSkinSelected(skin.id) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (skin.id == selectedSkinId) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(skin.accent),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(skin.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    skin.description,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (skin.id == selectedSkinId) {
                                Text("Wybrana", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onBack) { Text("Menu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}

@Composable
private fun PermissionsDialog(
    context: Context,
    refreshToken: Int,
    onRequestNotifications: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onRequestColorofonPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ignored = remember(refreshToken) { isIgnoringBatteryOptimizations(context) }
    val notificationsGranted = remember(refreshToken) { areNotificationsGranted(context) }
    val colorofonGranted = remember(refreshToken) { hasColorofonPermissions(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Uprawnienia i ustawienia") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatLine("Powiadomienia", if (notificationsGranted) "Włączone" else "Wymagają zgody")
                StatLine("Optymalizacja baterii", if (ignored) "Wyłączona dla aplikacji" else "Może zatrzymywać radio w tle")
                StatLine("Praca w tle", "ExoPlayer używa wake lock dla strumienia sieciowego.")
                Button(
                    onClick = onRequestNotifications,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsGranted,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Zezwól na powiadomienia") }
                Button(onClick = onRequestBatteryExemption, enabled = !ignored, modifier = Modifier.fillMaxWidth()) {
                    Text("Wyłącz oszczędzanie baterii")
                }
                Button(onClick = onOpenBatterySettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Ustawienia baterii Androida")
                }
                Button(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Ustawienia aplikacji")
                }
            }
        },
        confirmButton = { Button(onClick = onBack) { Text("Menu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}

@Composable
private fun RecordingsDialog(
    context: Context,
    layout: AdaptiveLayout,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    var refreshToken by remember { mutableStateOf(0) }
    val recordings = remember(refreshToken) { listRecordings(context) }
    var activeFile by remember { mutableStateOf<File?>(null) }
    var isPlaybackActive by remember { mutableStateOf(false) }
    var selectedFiles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmRingtonePath by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmMp3Path by rememberSaveable { mutableStateOf<String?>(null) }
    var trimFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    var recordingsMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val confirmRingtoneFile = confirmRingtonePath?.let(::File)?.takeIf { it.exists() }
    val confirmMp3File = confirmMp3Path?.let(::File)?.takeIf { it.exists() }
    val trimFile = trimFilePath?.let(::File)?.takeIf { it.exists() }
    val recordingPlayer = remember { MediaPlayer() }
    val selectionMode = selectedFiles.isNotEmpty()
    val singleSelectedFile = recordings.firstOrNull { it.absolutePath in selectedFiles }?.takeIf { selectedFiles.size == 1 }
    var playbackPositionMs by remember { mutableStateOf(0L) }
    var playbackDurationMs by remember { mutableStateOf(0L) }

    DisposableEffect(recordingPlayer) {
        onDispose {
            runCatching {
                recordingPlayer.stop()
                recordingPlayer.release()
            }
        }
    }

    DisposableEffect(recordingPlayer, activeFile, isPlaybackActive) {
        val handler = Handler(Looper.getMainLooper())
        val ticker = object : Runnable {
            override fun run() {
                if (activeFile != null) {
                    playbackPositionMs = runCatching { recordingPlayer.currentPosition.toLong() }.getOrDefault(playbackPositionMs)
                    playbackDurationMs = runCatching { recordingPlayer.duration.toLong() }.getOrDefault(playbackDurationMs).coerceAtLeast(0L)
                }
                if (isPlaybackActive) handler.postDelayed(this, 500L)
            }
        }
        handler.post(ticker)
        onDispose { handler.removeCallbacks(ticker) }
    }

    fun stopPlayback() {
        runCatching {
            if (recordingPlayer.isPlaying) recordingPlayer.stop()
            recordingPlayer.reset()
        }
        activeFile = null
        isPlaybackActive = false
        playbackPositionMs = 0L
        playbackDurationMs = 0L
    }

    fun pausePlayback() {
        runCatching {
            if (recordingPlayer.isPlaying) recordingPlayer.pause()
        }
        isPlaybackActive = false
    }

    fun playRecording(file: File) {
        runCatching {
            recordingPlayer.reset()
            recordingPlayer.setDataSource(file.absolutePath)
            recordingPlayer.setOnCompletionListener {
                activeFile = null
                isPlaybackActive = false
                playbackPositionMs = 0L
            }
            recordingPlayer.prepare()
            playbackDurationMs = recordingPlayer.duration.toLong().coerceAtLeast(0L)
            playbackPositionMs = 0L
            recordingPlayer.start()
            activeFile = file
            isPlaybackActive = true
        }.onFailure {
            activeFile = null
            isPlaybackActive = false
        }
    }

    fun togglePlayback(file: File) {
        if (activeFile == file) {
            if (isPlaybackActive) {
                pausePlayback()
            } else {
                runCatching {
                    recordingPlayer.start()
                    isPlaybackActive = true
                }.onFailure {
                    activeFile = null
                    isPlaybackActive = false
                }
            }
        } else {
            playRecording(file)
        }
    }

    fun toggleSelection(file: File) {
        selectedFiles = if (file.absolutePath in selectedFiles) {
            selectedFiles - file.absolutePath
        } else {
            selectedFiles + file.absolutePath
        }
    }

    fun deleteSelected() {
        val selected = recordings.filter { it.absolutePath in selectedFiles }
        if (activeFile?.absolutePath in selectedFiles) stopPlayback()
        selected.forEach { it.delete() }
        selectedFiles = emptySet()
        confirmDelete = false
        refreshToken += 1
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Nagrania")
                if (selectionMode) {
                    Text(
                        text = "Zaznaczono: ${selectedFiles.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            if (recordings.isEmpty()) {
                Text("Brak nagran. Wlacz stacje i przytrzymaj REC w odtwarzaczu.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selectionMode) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextButton(onClick = { selectedFiles = recordings.map { it.absolutePath }.toSet() }) {
                                Text("Wszystkie")
                            }
                            TextButton(onClick = { shareRecordings(context, recordings.filter { it.absolutePath in selectedFiles }) }) {
                                Text("Udostepnij")
                            }
                            TextButton(
                                onClick = {
                                    singleSelectedFile?.let { file ->
                                        if (file.extension.equals("mp3", ignoreCase = true)) {
                                            recordingsMessage = "Ten plik jest juz plikiem MP3."
                                        } else {
                                            confirmMp3Path = file.absolutePath
                                        }
                                    }
                                },
                                enabled = singleSelectedFile != null,
                            ) {
                                Text("MP3")
                            }
                            TextButton(
                                onClick = {
                                    trimFilePath = singleSelectedFile?.absolutePath
                                },
                                enabled = singleSelectedFile?.extension?.lowercase() == "mp3",
                            ) {
                                Text("Przytnij")
                            }
                            TextButton(
                                onClick = { confirmRingtonePath = singleSelectedFile?.absolutePath },
                                enabled = singleSelectedFile != null,
                            ) {
                                Text("Dzwonki")
                            }
                            TextButton(onClick = { confirmDelete = true }) {
                                Text("Usun")
                            }
                            TextButton(onClick = { selectedFiles = emptySet() }) {
                                Text("Anuluj")
                            }
                        }
                    }
                    recordingsMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.height(layout.recordingsHeight),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(recordings, key = { it.absolutePath }) { file ->
                            RecordingRow(
                                file = file,
                                selected = file.absolutePath in selectedFiles,
                                selectionMode = selectionMode,
                                isActive = activeFile == file,
                                isPlaying = activeFile == file && isPlaybackActive,
                                progressMs = if (activeFile == file) playbackPositionMs else 0L,
                                durationMs = if (activeFile == file) playbackDurationMs else 0L,
                                onPlayPause = { togglePlayback(file) },
                                onToggleSelection = { toggleSelection(file) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onBack) { Text("Menu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Usun nagrania") },
            text = { Text("Na pewno usunac zaznaczone nagrania: ${selectedFiles.size}?") },
            confirmButton = {
                Button(onClick = { deleteSelected() }) {
                    Text("Usun")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Anuluj")
                }
            },
        )
    }

    confirmMp3File?.let { file ->
        AlertDialog(
            onDismissRequest = { confirmMp3Path = null },
            title = { Text("Konwersja do MP3") },
            text = { Text("Czy chcesz przygotowac wersje MP3 pliku \"${file.name}\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        recordingsMessage = prepareMp3Copy(file).fold(
                            onSuccess = { "Utworzono: ${it.name}" },
                            onFailure = { "Nie udalo sie utworzyc MP3: ${it.message}" },
                        )
                        confirmMp3Path = null
                        refreshToken += 1
                    },
                ) {
                    Text("Konwertuj")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmMp3Path = null }) {
                    Text("Anuluj")
                }
            },
        )
    }

    confirmRingtoneFile?.let { file ->
        AlertDialog(
            onDismissRequest = { confirmRingtonePath = null },
            title = { Text("Dodac do dzwonkow?") },
            text = { Text("Czy chcesz dodac nagranie \"${file.name}\" do folderu dzwonkow telefonu?") },
            confirmButton = {
                Button(
                    onClick = {
                        recordingsMessage = copyRecordingToRingtones(context, file).fold(
                            onSuccess = { "Zapisano w dzwonkach: $it" },
                            onFailure = { "Nie udalo sie zapisac dzwonka: ${it.message}" },
                        )
                        confirmRingtonePath = null
                    },
                ) {
                    Text("Dodaj")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRingtonePath = null }) {
                    Text("Anuluj")
                }
            },
        )
    }

    trimFile?.let { file ->
        RecordingTrimEditorDialog(
            file = file,
            player = recordingPlayer,
            onSave = { start, end, mode, fadeIn, fadeOut, fadeSeconds ->
                recordingsMessage = trimMp3Recording(file, start, end, mode == TrimMode.RemoveSelection, fadeIn, fadeOut, fadeSeconds).fold(
                    onSuccess = { "Przycieto: ${it.name}" },
                    onFailure = { "Nie udalo sie przyciac: ${it.message}" },
                )
                trimFilePath = null
                selectedFiles = emptySet()
                refreshToken += 1
            },
            onDismiss = { trimFilePath = null },
        )
    }
}

@Composable
private fun RecordingTrimEditorDialog(
    file: File,
    player: MediaPlayer,
    onSave: (Long, Long, TrimMode, Boolean, Boolean, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val editorHeight = (configuration.screenHeightDp * 0.58f).roundToInt().coerceIn(300, 520).dp
    val durationMillis = remember(file) {
        (estimateAudioDurationSeconds(file).takeIf { it > 0L } ?: estimateMp3DurationSecondsFromFrames(file).coerceAtLeast(1L)) * 1000L
    }
    val minWaveformWidth = (configuration.screenWidthDp - 80).coerceAtLeast(320)
    val waveformWidth = ((durationMillis / 1000L) * 10L).coerceIn(minWaveformWidth.toLong(), 7200L).toInt().dp
    val waveformPoints = ((durationMillis / 1000L) * 2L).coerceIn(96L, 1400L).toInt()
    val waveform = remember(file, waveformPoints) { buildMp3Waveform(file, points = waveformPoints) }
    var startFraction by rememberSaveable(file.absolutePath) { mutableStateOf(0f) }
    var endFraction by rememberSaveable(file.absolutePath) { mutableStateOf(1f) }
    var previewFraction by rememberSaveable(file.absolutePath) { mutableStateOf(0f) }
    val minGapFraction = (100f / durationMillis.toFloat()).coerceAtMost(0.05f)
    val startMillis = (startFraction * durationMillis).toLong().coerceIn(0L, durationMillis)
    val endMillis = (endFraction * durationMillis).toLong().coerceIn(startMillis + 100L, durationMillis)
    val previewMillis = (previewFraction * durationMillis).toLong().coerceIn(startMillis, endMillis)
    var trimModeName by rememberSaveable(file.absolutePath) { mutableStateOf(TrimMode.KeepSelection.name) }
    val trimMode = TrimMode.entries.firstOrNull { it.name == trimModeName } ?: TrimMode.KeepSelection
    var fadeIn by rememberSaveable(file.absolutePath) { mutableStateOf(false) }
    var fadeOut by rememberSaveable(file.absolutePath) { mutableStateOf(false) }
    var fadeSeconds by rememberSaveable(file.absolutePath) { mutableStateOf(2) }
    val previewHandler = remember { Handler(Looper.getMainLooper()) }
    val stopPreview = remember(player) {
        Runnable {
            runCatching {
                if (player.isPlaying) player.pause()
            }
        }
    }

    fun setHandleFromFraction(fraction: Float, preferStart: Boolean) {
        if (preferStart) {
            startFraction = fraction.coerceIn(0f, endFraction - minGapFraction)
            previewFraction = previewFraction.coerceIn(startFraction, endFraction)
        } else {
            endFraction = fraction.coerceIn(startFraction + minGapFraction, 1f)
            previewFraction = previewFraction.coerceIn(startFraction, endFraction)
        }
    }

    fun nudgeStart(delta: Long) {
        startFraction = (startFraction + (delta * 1000f / durationMillis.toFloat())).coerceIn(0f, endFraction - minGapFraction)
        previewFraction = previewFraction.coerceIn(startFraction, endFraction)
    }

    fun nudgeEnd(delta: Long) {
        endFraction = (endFraction + (delta * 1000f / durationMillis.toFloat())).coerceIn(startFraction + minGapFraction, 1f)
        previewFraction = previewFraction.coerceIn(startFraction, endFraction)
    }

    fun setPreview(fraction: Float) {
        previewFraction = fraction.coerceIn(startFraction, endFraction)
    }

    fun playPreview(fromSelectionStart: Boolean) {
        previewHandler.removeCallbacks(stopPreview)
        runCatching {
            player.reset()
            player.setDataSource(file.absolutePath)
            player.prepare()
            val startAt = if (fromSelectionStart) startMillis else previewMillis.coerceIn(startMillis, endMillis)
            player.seekTo(startAt.toInt())
            player.start()
            previewHandler.postDelayed(stopPreview, (endMillis - startAt).coerceAtLeast(500L))
        }
    }

    fun stop() {
        previewHandler.removeCallbacks(stopPreview)
        runCatching {
            if (player.isPlaying) player.stop()
            player.reset()
        }
    }

    AlertDialog(
        onDismissRequest = {
            stop()
            onDismiss()
        },
        title = { Text("Edytor nagrania") },
        text = {
            Column(
                modifier = Modifier
                    .height(editorHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                WaveformTrimSelector(
                    waveform = waveform,
                    waveformWidth = waveformWidth,
                    startFraction = startFraction,
                    endFraction = endFraction,
                    playFraction = previewFraction,
                    onSetStart = { setHandleFromFraction(it, preferStart = true) },
                    onSetEnd = { setHandleFromFraction(it, preferStart = false) },
                    onScrub = { setPreview(it) },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Button(
                        onClick = { playPreview(fromSelectionStart = true) },
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp),
                    ) {
                        Text("Start", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium)
                    }
                    Button(
                        onClick = { playPreview(fromSelectionStart = false) },
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp),
                    ) {
                        Text("Podglad", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(
                        onClick = { stop() },
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp),
                    ) {
                        Text("Stop", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Start: ${formatMillis(startMillis)}", style = MaterialTheme.typography.labelMedium)
                    Text("Koniec: ${formatMillis(endMillis)}", style = MaterialTheme.typography.labelMedium)
                }
                Text("Podglad od: ${formatMillis(previewMillis)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Slider(
                    value = previewFraction,
                    onValueChange = { previewFraction = it.coerceIn(startFraction, endFraction) },
                    valueRange = startFraction..endFraction,
                )
                Text("Dlugosc: ${formatMillis(endMillis - startMillis)}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { nudgeStart(-1) }) { Text("Start -1s") }
                    TextButton(onClick = { nudgeStart(1) }) { Text("Start +1s") }
                    TextButton(onClick = { nudgeEnd(-1) }) { Text("Koniec -1s") }
                    TextButton(onClick = { nudgeEnd(1) }) { Text("Koniec +1s") }
                }
                Text("Tryb zapisu", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrimMode.entries.forEach { mode ->
                        FilterChip(
                            selected = trimMode == mode,
                            onClick = { trimModeName = mode.name },
                            label = { Text(mode.label) },
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Plynne wejscie")
                    Switch(checked = fadeIn, onCheckedChange = { fadeIn = it })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Plynne wyciszenie")
                    Switch(checked = fadeOut, onCheckedChange = { fadeOut = it })
                }
                if (fadeIn || fadeOut) {
                    Text("Czas efektu: ${fadeSeconds}s", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 3, 5).forEach { seconds ->
                            FilterChip(
                                selected = fadeSeconds == seconds,
                                onClick = { fadeSeconds = seconds },
                                label = { Text("${seconds}s") },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    stop()
                    onSave(startMillis, endMillis, trimMode, fadeIn, fadeOut, fadeSeconds)
                },
                enabled = endMillis > startMillis,
            ) {
                Text("Zapisz")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    stop()
                    onDismiss()
                },
            ) {
                Text("Anuluj")
            }
        },
    )
}

@Composable
private fun WaveformTrimSelector(
    waveform: List<Int>,
    waveformWidth: Dp,
    startFraction: Float,
    endFraction: Float,
    playFraction: Float,
    onSetStart: (Float) -> Unit,
    onSetEnd: (Float) -> Unit,
    onScrub: (Float) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val selected = MaterialTheme.colorScheme.secondary
    val playColor = MaterialTheme.colorScheme.error

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(startFraction, endFraction) {
                    detectTapGestures { offset ->
                        onScrub((offset.x / size.width.coerceAtLeast(1).toFloat()).coerceIn(startFraction, endFraction))
                    }
                },
        ) {
            val centerY = size.height / 2f
            val step = size.width / waveform.size.coerceAtLeast(1)
            waveform.forEachIndexed { index, level ->
                val x = index * step + step / 2f
                val height = (level.coerceIn(1, 100) / 100f) * size.height * 0.44f
                val color = if (x / size.width in startFraction..endFraction) selected else muted
                drawLine(
                    color = color,
                    start = Offset(x, centerY - height),
                    end = Offset(x, centerY + height),
                    strokeWidth = max(2f, step * 0.55f),
                    cap = StrokeCap.Round,
                )
            }
            val startX = startFraction.coerceIn(0f, 1f) * size.width
            val endX = endFraction.coerceIn(0f, 1f) * size.width
            val playX = playFraction.coerceIn(0f, 1f) * size.width
            drawLine(primary, Offset(startX, 0f), Offset(startX, size.height), strokeWidth = 5f, cap = StrokeCap.Round)
            drawLine(primary, Offset(endX, 0f), Offset(endX, size.height), strokeWidth = 5f, cap = StrokeCap.Round)
            drawLine(playColor, Offset(playX, size.height * 0.12f), Offset(playX, size.height * 0.88f), strokeWidth = 3f, cap = StrokeCap.Round)
        }

        RangeSlider(
            value = startFraction.coerceIn(0f, endFraction)..endFraction.coerceIn(startFraction, 1f),
            onValueChange = { range ->
                onSetStart(range.start)
                onSetEnd(range.endInclusive)
            },
            valueRange = 0f..1f,
            steps = 0,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingRow(
    file: File,
    selected: Boolean,
    selectionMode: Boolean,
    isActive: Boolean,
    isPlaying: Boolean,
    progressMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onToggleSelection: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (selectionMode) onToggleSelection() else onPlayPause()
                },
                onLongClick = onToggleSelection,
            ),
        shape = RoundedCornerShape(8.dp),
        color = when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            isActive -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelection() },
                    )
                }
                Surface(
                    modifier = Modifier
                        .size(38.dp)
                        .clickable { onPlayPause() },
                    shape = CircleShape,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (isPlaying) "II" else "\u25B6",
                            fontWeight = FontWeight.Bold,
                            color = if (isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${formatBytes(file.length())} | ${formatRecordingDate(file)} | ${formatRecordingDuration(file)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isActive && durationMs > 0L) {
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = { (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${formatMillis(progressMs)} / ${formatMillis(durationMs)} | zostalo ${formatMillis((durationMs - progressMs).coerceAtLeast(0L))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LegacyRecordingsDialog(
    context: Context,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    var refreshToken by remember { mutableStateOf(0) }
    val recordings = remember(refreshToken) { listRecordings(context) }
    var activeFile by remember { mutableStateOf<File?>(null) }
    var isPlaybackActive by remember { mutableStateOf(false) }
    val recordingPlayer = remember { MediaPlayer() }

    DisposableEffect(recordingPlayer) {
        onDispose {
            runCatching {
                recordingPlayer.stop()
                recordingPlayer.release()
            }
        }
    }

    fun stopPlayback() {
        runCatching {
            if (recordingPlayer.isPlaying) recordingPlayer.stop()
            recordingPlayer.reset()
        }
        activeFile = null
        isPlaybackActive = false
    }

    fun playRecording(file: File) {
        runCatching {
            recordingPlayer.reset()
            recordingPlayer.setDataSource(file.absolutePath)
            recordingPlayer.setOnCompletionListener {
                activeFile = null
                isPlaybackActive = false
            }
            recordingPlayer.prepare()
            recordingPlayer.start()
            activeFile = file
            isPlaybackActive = true
        }.onFailure {
            activeFile = null
            isPlaybackActive = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nagrania") },
        text = {
            if (recordings.isEmpty()) {
                Text("Brak nagrań. Włącz stację i użyj czerwonego przycisku REC w odtwarzaczu.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeFile?.let { file ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text("Odtwarzanie", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                TextButton(onClick = { stopPlayback() }) {
                                    Text("Stop")
                                }
                            }
                        }
                    }
                    recordings.forEach { file ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (activeFile == file && isPlaybackActive) {
                                        stopPlayback()
                                    } else {
                                        playRecording(file)
                                    }
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${formatBytes(file.length())} • ${formatRecordingDate(file)} • ${formatRecordingDuration(file)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        onClick = {
                                            if (activeFile == file && isPlaybackActive) stopPlayback() else playRecording(file)
                                        },
                                    ) {
                                        Text(if (activeFile == file && isPlaybackActive) "Stop" else "Odtwórz")
                                    }
                                    TextButton(onClick = { openRecording(context, file) }) {
                                        Text("System")
                                    }
                                    TextButton(
                                        onClick = {
                                            if (activeFile == file) stopPlayback()
                                            file.delete()
                                            refreshToken += 1
                                        },
                                    ) {
                                        Text("Usuń")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onBack) { Text("Menu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}

@Composable
private fun HistoryDialog(
    history: Map<String, ListeningStats>,
    onPlayStation: (RadioStation) -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    val items = remember(history) {
        history.entries
            .mapNotNull { (stationId, stats) ->
                PolishRadioStations.firstOrNull { it.id == stationId }?.let { station -> station to stats }
            }
            .sortedWith(compareByDescending<Pair<RadioStation, ListeningStats>> { it.second.lastPlayedAt }.thenByDescending { it.second.playCount })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Historia słuchania") },
        text = {
            if (items.isEmpty()) {
                Text("Brak historii. Włącz kilka stacji, a pojawią się tutaj.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.take(12).forEach { (station, stats) ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlayStation(station) },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(station.accentColor.toComposeColor()),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AsyncImage(
                                        model = station.iconUrl,
                                        contentDescription = station.name,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        station.name,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${stats.playCount} odtw. • ${formatHistoryDate(stats.lastPlayedAt)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onBack) { Text("Menu") } },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmClear = true }, enabled = items.isNotEmpty()) { Text("Wyczyść") }
                TextButton(onClick = onDismiss) { Text("Zamknij") }
            }
        },
    )

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Wyczysc historie") },
            text = { Text("Na pewno usunac cala historie sluchania?") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearHistory()
                        confirmClear = false
                    },
                ) {
                    Text("Wyczysc")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("Anuluj")
                }
            },
        )
    }
}

@Composable
private fun AppSettingsDialog(
    backgroundPlaybackEnabled: Boolean,
    mobileWarningEnabled: Boolean,
    onSetBackgroundPlayback: (Boolean) -> Unit,
    onSetMobileWarning: (Boolean) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ustawienia aplikacji") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsToggleRow(
                    title = "Praca w tle",
                    description = if (backgroundPlaybackEnabled) {
                        "Radio gra po zablokowaniu ekranu i po opuszczeniu aplikacji."
                    } else {
                        "Radio zatrzyma się po zejściu z aplikacji."
                    },
                    enabled = backgroundPlaybackEnabled,
                    onToggle = { onSetBackgroundPlayback(!backgroundPlaybackEnabled) },
                )
                SettingsToggleRow(
                    title = "Ostrzeżenie o danych komórkowych",
                    description = if (mobileWarningEnabled) {
                        "Odtwarzacz pokazuje ostrzeżenie, gdy aktywna jest sieć komórkowa."
                    } else {
                        "Ostrzeżenie jest ukryte."
                    },
                    enabled = mobileWarningEnabled,
                    onToggle = { onSetMobileWarning(!mobileWarningEnabled) },
                )
            }
        },
        confirmButton = { Button(onClick = onBack) { Text("Menu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}

@Composable
private fun HelpDialog(
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pomoc / FAQ") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(560.dp),
            ) {
                item {
                    HelpSection(
                        title = "Szybki start",
                        lines = listOf(
                            "Wybierz stacje z listy lub siatki. Radio zacznie laczyc sie ze strumieniem.",
                            "Przyciski w odtwarzaczu: poprzednia stacja, play/pauza, nastepna stacja, stop i nagrywanie.",
                            "Ulubione dodajesz przy stacji. Historia zapisuje ostatnio sluchane stacje.",
                        ),
                    )
                }
                item {
                    HelpSection(
                        title = "Powiadomienia i ekran blokady",
                        lines = listOf(
                            "Podczas odtwarzania powinno byc widoczne stale powiadomienie z przyciskami sterowania.",
                            "Po wyczyszczeniu powiadomien radio ma zostac, bo dziala jako odtwarzacz multimedialny.",
                            "Na ekranie blokady Android pokazuje sterowanie tylko wtedy, gdy w ustawieniach telefonu wlaczone sa powiadomienia na blokadzie.",
                            "Jesli sterowania nie widac: Ustawienia Androida > Aplikacje > Radio Polska > Powiadomienia > Odtwarzanie radia > pokaz na ekranie blokady.",
                            "W ustawieniach ekranu blokady wlacz pokazywanie powiadomien i ustaw pokaz tresc, nie ukrywaj tresci.",
                            "Jesli telefon ma opcje Sterowanie multimediami / Media controls on lock screen, wlacz ja.",
                        ),
                    )
                }
                item {
                    HelpSection(
                        title = "Praca w tle i bateria",
                        lines = listOf(
                            "Radio moze grac po zgaszeniu ekranu, gdy Android pozwala aplikacji dzialac w tle.",
                            "W menu Uprawnienia otworz ustawienia baterii i wylacz optymalizacje baterii dla Radio Polska.",
                            "Na telefonach Xiaomi, Oppo, Vivo, Huawei, Samsung lub Realme ustaw baterie aplikacji na Bez ograniczen, Bez optymalizacji albo Zezwalaj na prace w tle.",
                            "Nie zamykaj aplikacji przez wymuszone zatrzymanie w ustawieniach Androida, bo wtedy system zatrzyma tez radio.",
                        ),
                    )
                }
                item {
                    HelpSection(
                        title = "Nagrywanie",
                        lines = listOf(
                            "Przycisk nagrywania w odtwarzaczu rozpoczyna i konczy zapis aktualnej stacji.",
                            "Nagrania znajdziesz w menu Nagrania.",
                            "W module Nagrania mozna odtworzyc plik, udostepnic go, przyciac fragment i skopiowac jako dzwonek.",
                            "Aby zaznaczac nagrania do operacji, przytrzymaj dluzej nagranie na liscie.",
                            "Nazwy nagran moga korzystac z informacji RDS, jesli dana stacja je wysyla.",
                            "Podczas nagrywania aplikacja pokazuje nazwe pliku i rozmiar zapisu.",
                            "Jesli nagranie sie nie zapisuje, sprawdz wolne miejsce w pamieci telefonu i stabilnosc internetu.",
                            "Najlepiej nagrywaja sie stabilne strumienie MP3.",
                        ),
                    )
                }
                item {
                    HelpSection(
                        title = "Nagrania: odtwarzanie i zaznaczanie",
                        lines = listOf(
                            "Krotkie dotkniecie nagrania odtwarza albo pauzuje plik.",
                            "Dlugie przytrzymanie nagrania wlacza tryb zaznaczania.",
                            "Po zaznaczeniu mozesz wybrac wszystkie pliki, udostepnic je, usunac albo wykonac operacje na pojedynczym pliku.",
                            "Checkbox przy nagraniu dodaje lub zdejmuje zaznaczenie.",
                            "Przy odtwarzanym pliku widac pasek postepu, aktualny czas, calkowity czas i czas pozostaly.",
                        ),
                    )
                }
                item {
                    HelpSection(
                        title = "Udostepnianie nagran",
                        lines = listOf(
                            "Zaznacz jeden lub wiecej plikow i wybierz Udostepnij.",
                            "Android otworzy systemowe menu wysylania.",
                            "Mozesz wyslac nagrania przez Bluetooth, e-mail, komunikator, Dysk Google albo inna aplikacje.",
                            "Aplikacja udostepnia tylko wybrane pliki przez bezpieczny FileProvider.",
                        ),
                    )
                }
                item {
                    HelpSection(
                        title = "Trymer i przycinanie MP3",
                        lines = listOf(
                            "Trymer jest dostepny po dlugim przytrzymaniu nagrania, zaznaczeniu jednego pliku MP3 i wybraniu Przytnij.",
                            "Edytor pokazuje przebieg nagrania oraz uchwyty poczatku i konca.",
                            "Mozesz przesuwac uchwyty, uzywac suwaka podgladu albo przyciskow Start -1s, Start +1s, Koniec -1s i Koniec +1s.",
                            "Start odtwarza zaznaczony fragment od poczatku. Podglad odtwarza od wybranego miejsca. Stop zatrzymuje podglad.",
                            "Zostaw zaznaczone tworzy plik tylko z wybranego fragmentu.",
                            "Wytnij zaznaczone usuwa wskazany fragment i zostawia reszte nagrania.",
                            "Plynne wejscie i Plynne wyciszenie dodaja fade in/fade out. Czas efektu: 1, 2, 3 albo 5 sekund.",
                            "Trymer dziala dla MP3. Dla innych plikow najpierw uzyj opcji MP3, jesli jest dostepna.",
                        ),
                    )
                }
                item {
                    HelpSection(
                        title = "MP3 i dzwonki",
                        lines = listOf(
                            "Opcja MP3 przygotowuje kopie pliku MP3, jesli wybrany plik nie jest juz MP3.",
                            "Opcja Dzwonki kopiuje jedno zaznaczone nagranie do folderu dzwonkow telefonu.",
                            "Po dodaniu plik powinien byc widoczny w ustawieniach dzwonka Androida.",
                            "Na Androidzie 10+ zapis dzwonka odbywa sie przez MediaStore.",
                            "Jesli dzwonek nie pojawia sie od razu, otworz ponownie ustawienia dzwonka albo uruchom telefon ponownie.",
                        ),
                    )
                }
                item {
                    HelpSection(
                        title = "Timer i alarm",
                        lines = listOf(
                            "Timer zatrzymuje radio po wybranym czasie.",
                            "Alarm radiowy uruchamia wybrana stacje o ustawionej godzinie.",
                            "Aplikacja nie musi stale grac ani byc na ekranie. Alarm korzysta z alarmu systemowego Androida, wiec nie zuzywa baterii tylko dlatego, ze aplikacja jest zainstalowana.",
                            "Aplikacja moze byc normalnie odlozona w tle, podobnie jak program schowany do tray. Nie uzywaj jednak Wymus zatrzymanie w ustawieniach Androida, bo wtedy system blokuje samoczynne wzbudzenie aplikacji.",
                            "Glosnosc budzenia ustawia poziom glosnosci multimediow telefonu przy starcie alarmu.",
                            "Dla alarmu Android moze wymagac zgody na dokladne alarmy. Otworz ja z okna Alarm radiowy.",
                            "Opcja drzemki przesuwa alarm o ustawiona liczbe minut.",
                        ),
                    )
                }
                item {
                    HelpSection(
                        title = "Aktywny alarm",
                        lines = listOf(
                            "Gdy alarm sie wlaczy, aplikacja pokazuje okno Alarm radiowy.",
                            "Drzemka zatrzymuje aktualne budzenie i planuje kolejne po ustawionym czasie.",
                            "Wylacz zatrzymuje alarm i radio.",
                            "Graj dalej zamyka tryb alarmu, ale zostawia radio wlaczone.",
                            "Okno alarmu moze pojawic sie nad ekranem blokady, jesli Android i ustawienia telefonu na to pozwalaja.",
                        ),
                    )
                }
                item {
                    HelpSection(
                        title = "Stacje, filtry i widoki",
                        lines = listOf(
                            "Mozesz wyszukiwac po nazwie, miescie, regionie, gatunku lub czestotliwosci.",
                            "Filtry pozwalaja zawęzic liste wedlug kategorii, regionu i ulubionych.",
                            "Widok listy i siatki przelaczasz przyciskiem w gornym pasku.",
                            "Sortowanie pozwala pokazac polecane, nazwe, bitrate, ostatnio sluchane lub ulubione najpierw.",
                        ),
                    )
                }
                item {
                    HelpSection(
                        title = "Transfer i dane komorkowe",
                        lines = listOf(
                            "Modul Transfer pokazuje szacowane zuzycie danych podczas sluchania.",
                            "W ustawieniach aplikacji mozesz wlaczyc lub ukryc ostrzezenie o danych komorkowych.",
                            "Im wyzszy bitrate stacji, tym wiecej danych zuzywa radio.",
                        ),
                    )
                }
                item {
                    HelpSection(
                        title = "Szczegoly transferu",
                        lines = listOf(
                            "Transfer jest rozbity na Wi-Fi, dane komorkowe i inne polaczenia.",
                            "Analiza pokazuje procent danych komorkowych od ostatniego resetu.",
                            "Resetuj analize transferu zeruje liczniki.",
                            "Liczniki sa szacowane na podstawie bitrate aktualnej stacji i czasu odtwarzania.",
                        ),
                    )
                }
                item {
                    HelpSection(
                        title = "Equalizer, kolorofon i tryb auto",
                        lines = listOf(
                            "Equalizer pozwala wlaczyc presety lub recznie ustawic pasma dzwieku.",
                            "Kolorofon reaguje na muzyke i moze uzywac latarki. Wymaga uprawnien kamery i mikrofonu.",
                            "Tryb auto pokazuje duze przyciski do wygodnego sterowania w samochodzie.",
                        ),
                    )
                }
                item {
                    HelpSection(
                        title = "Equalizer szczegolowo",
                        lines = listOf(
                            "Equalizer mozesz wlaczyc albo wylaczyc w module Equalizer.",
                            "Presety obejmuja Flat, Bass, Rock, Pop, Vocal, Dance i Manual.",
                            "Reczne suwaki zmieniaja pasma Bass, Low, Mid, High i Treble od -12 dB do +12 dB.",
                            "Zmiana pojedynczego pasma ustawia tryb Manual.",
                        ),
                    )
                }
                item {
                    HelpSection(
                        title = "Kolorofon szczegolowo",
                        lines = listOf(
                            "Kolorofon analizuje muzyke i steruje efektem latarki.",
                            "Wymaga uprawnien kamery i mikrofonu.",
                            "Czulosc zmienia ogolna reakcje efektu.",
                            "Suwaki Bas, Srodek i Sopran ustawiaja reakcje na konkretne pasma muzyki.",
                            "Na telefonach z regulacja mocy latarki efekt moze zmieniac sile swiatla. Na innych bedzie dzialac jako miganie.",
                        ),
                    )
                }
                item {
                    HelpSection(
                        title = "Teraz grane, Modul stacji i Tryb auto",
                        lines = listOf(
                            "Teraz grane pokazuje duzy widok aktualnej stacji, logo, status, RDS i wizualizacje.",
                            "W Teraz grane mozesz sterowac radiem, dodac stacje do ulubionych i wlaczyc REC.",
                            "Modul Stacja pokazuje nazwe, miasto, region, kategorie, gatunek, bitrate, jakosc i status.",
                            "Tryb auto pokazuje duze przyciski poprzednia, play/pauza, nastepna i stop.",
                        ),
                    )
                }
                item {
                    HelpSection(
                        title = "Backup i skorki",
                        lines = listOf(
                            "Backup eksportuje ustawienia aplikacji, ulubione, historie i konfiguracje.",
                            "Skorki zmieniaja wyglad aplikacji.",
                        ),
                    )
                }
                item {
                    HelpSection(
                        title = "Backup, skorki i ustawienia szczegolowo",
                        lines = listOf(
                            "Backup eksportuje ulubione, historie, skorke, widok listy, sortowanie, ustawienia tla, ostrzezenia i alarm jako tekst.",
                            "Backup udostepniasz przez systemowe menu Androida.",
                            "Skorki zmieniaja wyglad i kolory aplikacji.",
                            "Ustawienia aplikacji zawieraja Prace w tle oraz ostrzezenie o danych komorkowych.",
                            "Praca w tle pomaga radiu grac po opuszczeniu aplikacji, ale stabilnosc zalezy tez od ustawien baterii Androida.",
                        ),
                    )
                }
                item {
                    HelpSection(
                        title = "Uprawnienia w aplikacji",
                        lines = listOf(
                            "Powiadomienia sa potrzebne do odtwarzacza w tle, sterowania na ekranie blokady i informacji o pracy radia.",
                            "Optymalizacja baterii moze zatrzymywac radio po zgaszeniu ekranu.",
                            "Dokladne alarmy sa potrzebne, zeby budzik radiowy mogl wystartowac o konkretnej godzinie.",
                            "Kamera i mikrofon sa potrzebne dla kolorofonu.",
                            "Internet i stan sieci sa potrzebne do odtwarzania stacji oraz analizy Wi-Fi/danych komorkowych.",
                        ),
                    )
                }
                item {
                    HelpSection(
                        title = "Gdy cos nie dziala",
                        lines = listOf(
                            "Brak powiadomienia: sprawdz zgode na powiadomienia i kanal Odtwarzanie radia.",
                            "Brak sterowania na blokadzie: wlacz powiadomienia na ekranie blokady i pokaz tresc.",
                            "Radio zatrzymuje sie po zgaszeniu ekranu: wylacz optymalizacje baterii dla aplikacji.",
                            "Stacja nie gra: wybierz inna stacje albo sprobuj ponownie, bo strumien danej stacji moze byc chwilowo niedostepny.",
                            "Nagrywanie nie zapisuje pliku: sprawdz wolne miejsce w pamieci telefonu.",
                            "Trymer jest niedostepny: zaznacz dokladnie jeden plik MP3.",
                            "Nie da sie ustawic dzwonka: sprawdz, czy plik istnieje i czy Android pokazal go w ustawieniach dzwonkow.",
                            "Alarm sie nie wlaczyl: sprawdz dokladne alarmy, baterie i czy aplikacja nie byla wymuszona zatrzymana.",
                            "Kolorofon nie reaguje: nadaj uprawnienia aparatu i mikrofonu.",
                        ),
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onBack) { Text("Menu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}

@Composable
private fun HelpSection(
    title: String,
    lines: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        lines.forEach { line ->
            Text(
                text = "- $line",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onToggle) {
                Text(if (enabled) "Wł." else "Wył.")
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipGroup(
    title: String,
    values: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            values.forEach { value ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}

@Composable
private fun SortChipGroup(
    title: String,
    values: List<StationSortMode>,
    selected: StationSortMode,
    onSelect: (StationSortMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            values.forEach { value ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(value.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}

@Composable
private fun StationList(
    stations: List<RadioStation>,
    currentStation: RadioStation?,
    favorites: Set<String>,
    viewMode: StationViewMode,
    layout: AdaptiveLayout,
    listeningHistory: Map<String, ListeningStats>,
    onSelect: (RadioStation) -> Unit,
    onStationPlayed: (RadioStation) -> Unit,
    onToggleFavorite: (RadioStation) -> Unit,
) {
    if (viewMode == StationViewMode.Grid) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(layout.tileColumns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(stations, key = { it.id }) { station ->
                StationTile(
                    station = station,
                    isCurrent = station.id == currentStation?.id,
                    isFavorite = station.id in favorites,
                    height = layout.tileHeight,
                    onClick = { onSelect(station) },
                    onStationPlayed = { onStationPlayed(station) },
                    onToggleFavorite = { onToggleFavorite(station) },
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(stations, key = { it.id }) { station ->
            StationRow(
                station = station,
                isCurrent = station.id == currentStation?.id,
                isFavorite = station.id in favorites,
                stats = listeningHistory[station.id],
                onClick = { onSelect(station) },
                onStationPlayed = { onStationPlayed(station) },
                onToggleFavorite = { onToggleFavorite(station) },
            )
        }
    }
}

@Composable
private fun StationTile(
    station: RadioStation,
    isCurrent: Boolean,
    isFavorite: Boolean,
    height: Dp,
    onClick: () -> Unit,
    onStationPlayed: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val accent = remember(station.accentColor) { station.accentColor.toComposeColor() }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clickable {
                onClick()
                onStationPlayed()
            },
        shape = RoundedCornerShape(8.dp),
        tonalElevation = if (isCurrent) 4.dp else 1.dp,
        color = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = station.iconUrl,
                        contentDescription = station.name,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                    Text(station.name.initials(), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${station.bitrate} kbps",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(30.dp)
                    .clickable { onToggleFavorite() },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (isFavorite) "\u2665" else "\u2661",
                        color = if (isFavorite) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun StationRow(
    station: RadioStation,
    isCurrent: Boolean,
    isFavorite: Boolean,
    stats: ListeningStats?,
    onClick: () -> Unit,
    onStationPlayed: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val accent = remember(station.accentColor) { station.accentColor.toComposeColor() }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
                onStationPlayed()
            },
        shape = RoundedCornerShape(8.dp),
        tonalElevation = if (isCurrent) 4.dp else 1.dp,
        color = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = station.iconUrl,
                    contentDescription = station.name,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Text(station.name.initials(), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = station.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (station.isHighQuality) {
                        Text("HQ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    text = "${station.city} • ${station.region ?: "Online"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (stats != null) {
                        "${station.genre} • ${station.category} • ${stats.playCount} odtw."
                    } else {
                        "${station.genre} • ${station.category}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onToggleFavorite) {
                    Text(
                        text = if (isFavorite) "\u2665" else "\u2661",
                        color = if (isFavorite) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text("${station.bitrate} kbps", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun RadioStation.matchesCategory(category: String, favorites: Set<String>): Boolean =
    when (category) {
        "Ulubione" -> id in favorites
        "Publiczne" -> this.category == "Publiczna"
        "Prywatne" -> this.category == "Prywatna"
        "Informacyjne" -> genre.contains("news", ignoreCase = true) ||
            genre.contains("informac", ignoreCase = true) ||
            genre.contains("publicyst", ignoreCase = true)
        "Muzyczne" -> genre.contains("pop", ignoreCase = true) ||
            genre.contains("rock", ignoreCase = true) ||
            genre.contains("hity", ignoreCase = true) ||
            genre.contains("club", ignoreCase = true) ||
            genre.contains("dance", ignoreCase = true) ||
            genre.contains("muzyka", ignoreCase = true)
        "Wysoka jakość" -> isHighQuality
        else -> true
    }

private fun List<RadioStation>.sortStations(
    mode: StationSortMode,
    favorites: Set<String>,
    history: Map<String, ListeningStats>,
): List<RadioStation> =
    when (mode) {
        StationSortMode.Recommended -> sortedWith(compareBy(::stationPriority, RadioStation::name))
        StationSortMode.Name -> sortedBy { it.name.lowercase(Locale.getDefault()) }
        StationSortMode.Bitrate -> sortedWith(compareByDescending<RadioStation> { it.bitrate }.thenBy { it.name })
        StationSortMode.Recent -> sortedWith(
            compareByDescending<RadioStation> { history[it.id]?.lastPlayedAt ?: 0L }
                .thenBy { it.name },
        )
        StationSortMode.Favorites -> sortedWith(
            compareByDescending<RadioStation> { it.id in favorites }
                .thenBy { it.name },
        )
    }

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value

private fun loadFavorites(context: Context): Set<String> =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getStringSet(FAVORITES_KEY, emptySet())
        ?.toSet()
        .orEmpty()

private fun saveFavorites(context: Context, favorites: Set<String>) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putStringSet(FAVORITES_KEY, favorites)
        .apply()
}

private fun loadSkin(context: Context): String =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(SKIN_KEY, "system")
        ?: "system"

private fun saveSkin(context: Context, skinId: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(SKIN_KEY, skinId)
        .apply()
}

private fun loadStationViewMode(context: Context): StationViewMode {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(STATION_VIEW_MODE_KEY, StationViewMode.List.name)
    return StationViewMode.entries.firstOrNull { it.name == raw } ?: StationViewMode.List
}

private fun saveStationViewMode(context: Context, mode: StationViewMode) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(STATION_VIEW_MODE_KEY, mode.name)
        .apply()
}

private fun loadStationSortMode(context: Context): StationSortMode {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(STATION_SORT_MODE_KEY, StationSortMode.Recommended.name)
    return StationSortMode.entries.firstOrNull { it.name == raw } ?: StationSortMode.Recommended
}

private fun saveStationSortMode(context: Context, mode: StationSortMode) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(STATION_SORT_MODE_KEY, mode.name)
        .apply()
}

private fun loadSearchQuery(context: Context): String =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(SEARCH_QUERY_KEY, "")
        .orEmpty()

private fun saveSearchQuery(context: Context, query: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(SEARCH_QUERY_KEY, query)
        .apply()
}

private fun loadSelectedCategory(context: Context): String =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(SELECTED_CATEGORY_KEY, "Wszystkie")
        ?: "Wszystkie"

private fun saveSelectedCategory(context: Context, category: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(SELECTED_CATEGORY_KEY, category)
        .apply()
}

private fun loadSelectedRegion(context: Context): String =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(SELECTED_REGION_KEY, "Wszystkie")
        ?: "Wszystkie"

private fun saveSelectedRegion(context: Context, region: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(SELECTED_REGION_KEY, region)
        .apply()
}

private fun loadLastStation(context: Context): RadioStation? {
    val stationId = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(LAST_STATION_KEY, null)
    return PolishRadioStations.firstOrNull { it.id == stationId }
}

private fun saveLastStation(context: Context, stationId: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(LAST_STATION_KEY, stationId)
        .apply()
}

private fun loadListeningHistory(context: Context): Map<String, ListeningStats> {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(HISTORY_KEY, "")
        .orEmpty()
    if (raw.isBlank()) return emptyMap()
    return raw.split("|")
        .mapNotNull { entry ->
            val parts = entry.split(",", limit = 3)
            if (parts.size != 3) return@mapNotNull null
            val count = parts[1].toIntOrNull() ?: return@mapNotNull null
            val lastPlayedAt = parts[2].toLongOrNull() ?: return@mapNotNull null
            parts[0] to ListeningStats(count, lastPlayedAt)
        }
        .toMap()
}

private fun saveListeningHistory(context: Context, history: Map<String, ListeningStats>) {
    val raw = history.entries.joinToString("|") { (stationId, stats) ->
        "$stationId,${stats.playCount},${stats.lastPlayedAt}"
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(HISTORY_KEY, raw)
        .apply()
}

private fun Map<String, ListeningStats>.record(stationId: String): Map<String, ListeningStats> {
    val current = this[stationId]
    return this + (stationId to ListeningStats((current?.playCount ?: 0) + 1, System.currentTimeMillis()))
}

private fun loadBackgroundPlaybackEnabled(context: Context): Boolean =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(BACKGROUND_PLAYBACK_KEY, true)

private fun saveBackgroundPlaybackEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(BACKGROUND_PLAYBACK_KEY, enabled)
        .apply()
}

private fun loadMobileWarningEnabled(context: Context): Boolean =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(MOBILE_WARNING_KEY, true)

private fun saveMobileWarningEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(MOBILE_WARNING_KEY, enabled)
        .apply()
}

private fun recordingsDir(context: Context): File =
    File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "recordings").apply { mkdirs() }

private fun listRecordings(context: Context): List<File> =
    recordingsDir(context)
        .listFiles { file -> file.isFile && file.extension.lowercase() in setOf("mp3", "aac", "m4a") }
        ?.sortedByDescending { it.lastModified() }
        .orEmpty()

private fun openRecording(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "audio/mpeg")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

private fun shareRecordings(context: Context, files: List<File>) {
    if (files.isEmpty()) return
    val uris = ArrayList(files.map { file ->
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    })
    val shareClipData = ClipData.newUri(context.contentResolver, files.first().name, uris.first()).apply {
        uris.drop(1).forEach { uri -> addItem(ClipData.Item(uri)) }
    }
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uris.first())
            putExtra(Intent.EXTRA_SUBJECT, files.first().name)
            clipData = shareClipData
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "audio/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, "Nagrania Radio Polska")
            clipData = shareClipData
        }
    }.apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, "Udostepnij nagrania").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(chooser) }
}

private fun shareText(context: Context, title: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(intent, title).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(chooser) }
}

private fun buildSettingsBackupText(
    favorites: Set<String>,
    history: Map<String, ListeningStats>,
    selectedSkinId: String,
    stationViewMode: StationViewMode,
    stationSortMode: StationSortMode,
    backgroundPlaybackEnabled: Boolean,
    mobileWarningEnabled: Boolean,
    alarmConfig: RadioAlarmConfig,
): String = buildString {
    appendLine("Radio Polska backup")
    appendLine("version=1")
    appendLine("skin=$selectedSkinId")
    appendLine("view=${stationViewMode.name}")
    appendLine("sort=${stationSortMode.name}")
    appendLine("background=$backgroundPlaybackEnabled")
    appendLine("mobileWarning=$mobileWarningEnabled")
    appendLine("favorites=${favorites.sorted().joinToString(",")}")
    appendLine("history=${history.entries.joinToString("|") { "${it.key},${it.value.playCount},${it.value.lastPlayedAt}" }}")
    appendLine("alarm=${alarmConfig.enabled};${alarmConfig.stationId};${alarmConfig.hour};${alarmConfig.minute};${alarmConfig.repeat};${alarmConfig.weekdays.sorted().joinToString(",")};${alarmConfig.rampVolume};${alarmConfig.snoozeMinutes};${alarmConfig.autoOffMinutes};${alarmConfig.alarmVolumePercent}")
}

private fun prepareMp3Copy(file: File): Result<File> = runCatching {
    val target = if (file.extension.equals("mp3", ignoreCase = true)) {
        File(file.parentFile, file.nameWithoutExtension + "-kopia.mp3")
    } else {
        File(file.parentFile, file.nameWithoutExtension + ".mp3")
    }
    file.copyToUnique(target)
}

private fun trimMp3Recording(
    file: File,
    startMillis: Long,
    endMillis: Long,
    removeSelection: Boolean = false,
    fadeIn: Boolean = false,
    fadeOut: Boolean = false,
    fadeSeconds: Int = 2,
): Result<File> = runCatching {
    require(file.extension.equals("mp3", ignoreCase = true)) { "Przycinanie dziala teraz dla MP3" }
    require(endMillis > startMillis) { "Koniec musi byc pozniej niz poczatek" }
    val frameRange = findMp3FrameRange(file, startMillis.coerceAtLeast(0L), endMillis)
    val startOffset = frameRange.first
    val endOffset = frameRange.second
    require(endOffset > startOffset) { "Zakres jest pusty" }
    val startLabel = (startMillis / 1000.0).let { "%.1f".format(Locale.US, it) }.replace('.', '_')
    val endLabel = (endMillis / 1000.0).let { "%.1f".format(Locale.US, it) }.replace('.', '_')
    val suffix = if (removeSelection) "-bez-${startLabel}-${endLabel}.mp3" else "-fragment-${startLabel}-${endLabel}.mp3"
    val target = File(file.parentFile, file.nameWithoutExtension + suffix).uniqueFile()
    if (removeSelection) {
        val audioStart = firstMp3FrameOffset(file)
        require(startOffset > audioStart || endOffset < file.length()) { "Zaznaczono caly plik" }
        FileInputStream(file).use { input ->
            FileOutputStream(target).use { output ->
                copyFileRange(input, output, audioStart, startOffset)
                copyFileRange(input, output, endOffset, file.length())
            }
        }
    } else {
        FileInputStream(file).use { input ->
            FileOutputStream(target).use { output ->
                copyFileRange(input, output, startOffset, endOffset)
            }
        }
    }
    if (fadeIn || fadeOut) {
        applyMp3FrameFade(target, fadeIn, fadeOut, fadeSeconds.coerceIn(1, 10))
    }
    target
}

private fun copyFileRange(input: FileInputStream, output: FileOutputStream, startOffset: Long, endOffset: Long) {
    input.channel.position(startOffset)
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var remaining = (endOffset - startOffset).coerceAtLeast(0L)
    while (remaining > 0L) {
        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
        if (read <= 0) break
        output.write(buffer, 0, read)
        remaining -= read
    }
}

private fun firstMp3FrameOffset(file: File): Long =
    RandomAccessFile(file, "r").use { skipId3v2Tag(it) }

private fun findMp3FrameRange(file: File, startMillis: Long, endMillis: Long): Pair<Long, Long> {
    val startUs = startMillis * 1000L
    val endUs = endMillis * 1000L
    RandomAccessFile(file, "r").use { raf ->
        var offset = skipId3v2Tag(raf)
        var currentUs = 0L
        var startOffset: Long? = null
        var endOffset: Long? = null
        val header = ByteArray(4)
        while (offset + 4 < raf.length()) {
            raf.seek(offset)
            raf.readFully(header)
            val frame = parseMp3FrameHeader(header)
            if (frame == null) {
                offset += 1
                continue
            }
            val nextUs = currentUs + frame.durationUs
            if (startOffset == null && nextUs >= startUs) {
                startOffset = offset
            }
            if (startOffset != null && currentUs >= endUs) {
                endOffset = offset
                break
            }
            offset += frame.frameSize
            currentUs = nextUs
        }
        return (startOffset ?: error("Nie znaleziono poczatku fragmentu")) to (endOffset ?: offset.coerceAtMost(file.length()))
    }
}

private fun estimateMp3DurationSecondsFromFrames(file: File): Long =
    (estimateMp3DurationUsFromFrames(file) / 1_000_000L).coerceAtLeast(0L)

private fun estimateMp3DurationUsFromFrames(file: File): Long {
    var durationUs = 0L
    runCatching {
        RandomAccessFile(file, "r").use { raf ->
            var offset = skipId3v2Tag(raf)
            val header = ByteArray(4)
            while (offset + 4 < raf.length()) {
                raf.seek(offset)
                raf.readFully(header)
                val frame = parseMp3FrameHeader(header)
                if (frame == null) {
                    offset += 1
                } else {
                    durationUs += frame.durationUs
                    offset += frame.frameSize
                }
            }
        }
    }
    return durationUs
}

private fun buildMp3Waveform(file: File, points: Int): List<Int> {
    val buckets = IntArray(points.coerceAtLeast(16))
    val counts = IntArray(buckets.size)
    runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val start = skipId3v2Tag(raf)
            val total = (raf.length() - start).coerceAtLeast(1L)
            var offset = start
            val header = ByteArray(4)
            val sample = ByteArray(160)
            while (offset + 4 < raf.length()) {
                raf.seek(offset)
                raf.readFully(header)
                val frame = parseMp3FrameHeader(header)
                if (frame == null) {
                    offset += 1
                    continue
                }
                val payloadStart = offset + 4L + if (frame.hasCrc) 2L else 0L
                val available = min(sample.size.toLong(), (offset + frame.frameSize - payloadStart).coerceAtLeast(0L)).toInt()
                if (available > 0) {
                    raf.seek(payloadStart)
                    raf.read(sample, 0, available)
                    var energy = 0
                    for (i in 0 until available) energy += kotlin.math.abs(sample[i].toInt())
                    val bucket = (((offset - start).toDouble() / total.toDouble()) * buckets.size).toInt().coerceIn(0, buckets.lastIndex)
                    buckets[bucket] += energy / available
                    counts[bucket] += 1
                }
                offset += frame.frameSize
            }
        }
    }
    val averaged = buckets.mapIndexed { index, value -> if (counts[index] > 0) value / counts[index] else 0 }
    val maxValue = averaged.maxOrNull()?.coerceAtLeast(1) ?: 1
    return averaged.map { ((it.toFloat() / maxValue.toFloat()) * 100f).roundToInt().coerceIn(5, 100) }
}

private fun applyMp3FrameFade(file: File, fadeIn: Boolean, fadeOut: Boolean, fadeSeconds: Int) {
    val totalUs = estimateMp3DurationUsFromFrames(file).takeIf { it > 0L } ?: return
    val fadeUs = fadeSeconds * 1_000_000L
    RandomAccessFile(file, "rw").use { raf ->
        var offset = skipId3v2Tag(raf)
        var currentUs = 0L
        val header = ByteArray(4)
        while (offset + 4 < raf.length()) {
            raf.seek(offset)
            raf.readFully(header)
            val frame = parseMp3FrameHeader(header)
            if (frame == null) {
                offset += 1
                continue
            }
            val frameMiddleUs = currentUs + frame.durationUs / 2L
            val inGain = if (fadeIn && frameMiddleUs < fadeUs) {
                (frameMiddleUs.toDouble() / fadeUs.toDouble()).coerceIn(0.05, 1.0)
            } else {
                1.0
            }
            val outRemainingUs = totalUs - frameMiddleUs
            val outGain = if (fadeOut && outRemainingUs < fadeUs) {
                (outRemainingUs.toDouble() / fadeUs.toDouble()).coerceIn(0.05, 1.0)
            } else {
                1.0
            }
            adjustMp3Layer3GlobalGain(raf, offset, frame, min(inGain, outGain))
            offset += frame.frameSize
            currentUs += frame.durationUs
        }
    }
}

private fun adjustMp3Layer3GlobalGain(raf: RandomAccessFile, frameOffset: Long, frame: Mp3FrameInfo, gain: Double) {
    if (frame.layer != 3 || gain >= 0.995) return
    val delta = (4.0 * (ln(gain) / ln(2.0))).roundToInt()
    if (delta >= 0) return
    val sideInfoOffset = frameOffset + 4L + if (frame.hasCrc) 2L else 0L
    val sideInfoSize = when {
        frame.version == 1 && frame.channels == 1 -> 17
        frame.version == 1 -> 32
        frame.channels == 1 -> 9
        else -> 17
    }
    if (sideInfoOffset + sideInfoSize > raf.length()) return
    val sideInfo = ByteArray(sideInfoSize)
    raf.seek(sideInfoOffset)
    raf.readFully(sideInfo)
    val positions = mp3GlobalGainBitPositions(sideInfo, frame)
    positions.forEach { bitPosition ->
        val current = readBits(sideInfo, bitPosition, 8)
        writeBits(sideInfo, bitPosition, 8, (current + delta).coerceIn(0, 255))
    }
    raf.seek(sideInfoOffset)
    raf.write(sideInfo)
}

private fun mp3GlobalGainBitPositions(sideInfo: ByteArray, frame: Mp3FrameInfo): List<Int> {
    val positions = mutableListOf<Int>()
    var bit = when {
        frame.version == 1 && frame.channels == 1 -> 9 + 5 + 4
        frame.version == 1 -> 9 + 3 + 8
        frame.channels == 1 -> 8 + 1
        else -> 8 + 2
    }
    val granules = if (frame.version == 1) 2 else 1
    repeat(granules) {
        repeat(frame.channels) {
            bit += 12 + 9
            positions += bit
            bit += 8 + 4
            val windowSwitching = readBits(sideInfo, bit, 1) == 1
            bit += 1
            bit += if (windowSwitching) {
                2 + 1 + 10 + 9
            } else {
                5 + 5 + 5 + 4 + 3
            }
            bit += if (frame.version == 1) 1 + 1 + 1 else 1
        }
    }
    return positions
}

private fun readBits(data: ByteArray, startBit: Int, count: Int): Int {
    var value = 0
    repeat(count) { index ->
        val bitIndex = startBit + index
        val byte = data.getOrNull(bitIndex / 8)?.toInt() ?: 0
        val bit = (byte shr (7 - (bitIndex % 8))) and 1
        value = (value shl 1) or bit
    }
    return value
}

private fun writeBits(data: ByteArray, startBit: Int, count: Int, value: Int) {
    repeat(count) { index ->
        val bitIndex = startBit + index
        val byteIndex = bitIndex / 8
        if (byteIndex !in data.indices) return@repeat
        val mask = 1 shl (7 - (bitIndex % 8))
        val bit = (value shr (count - index - 1)) and 1
        data[byteIndex] = if (bit == 1) {
            (data[byteIndex].toInt() or mask).toByte()
        } else {
            (data[byteIndex].toInt() and mask.inv()).toByte()
        }
    }
}

private data class Mp3FrameInfo(
    val frameSize: Int,
    val durationUs: Long,
    val version: Int,
    val layer: Int,
    val hasCrc: Boolean,
    val channels: Int,
)

private fun skipId3v2Tag(raf: RandomAccessFile): Long {
    if (raf.length() < 10) return 0L
    raf.seek(0L)
    val header = ByteArray(10)
    raf.readFully(header)
    if (header[0].toInt().toChar() != 'I' || header[1].toInt().toChar() != 'D' || header[2].toInt().toChar() != '3') return 0L
    val size = ((header[6].toInt() and 0x7F) shl 21) or
        ((header[7].toInt() and 0x7F) shl 14) or
        ((header[8].toInt() and 0x7F) shl 7) or
        (header[9].toInt() and 0x7F)
    return 10L + size
}

private fun parseMp3FrameHeader(bytes: ByteArray): Mp3FrameInfo? {
    val header = ((bytes[0].toInt() and 0xFF) shl 24) or
        ((bytes[1].toInt() and 0xFF) shl 16) or
        ((bytes[2].toInt() and 0xFF) shl 8) or
        (bytes[3].toInt() and 0xFF)
    if ((header and 0xFFE00000.toInt()) != 0xFFE00000.toInt()) return null
    val versionBits = (header shr 19) and 0x3
    val layerBits = (header shr 17) and 0x3
    val bitrateIndex = (header shr 12) and 0xF
    val sampleRateIndex = (header shr 10) and 0x3
    val padding = (header shr 9) and 0x1
    val protectionBit = (header shr 16) and 0x1
    val channelMode = (header shr 6) and 0x3
    if (versionBits == 1 || layerBits == 0 || bitrateIndex == 0 || bitrateIndex == 15 || sampleRateIndex == 3) return null

    val version = when (versionBits) {
        3 -> 1
        2 -> 2
        else -> 25
    }
    val layer = 4 - layerBits
    val bitrate = mp3BitrateKbps(version, layer, bitrateIndex) * 1000
    val sampleRate = mp3SampleRate(version, sampleRateIndex)
    if (bitrate <= 0 || sampleRate <= 0) return null

    val samplesPerFrame = when {
        layer == 1 -> 384
        layer == 3 && version != 1 -> 576
        else -> 1152
    }
    val frameSize = if (layer == 1) {
        ((12 * bitrate / sampleRate) + padding) * 4
    } else {
        val coefficient = if (layer == 3 && version != 1) 72 else 144
        (coefficient * bitrate / sampleRate) + padding
    }
    if (frameSize < 24) return null
    return Mp3FrameInfo(
        frameSize = frameSize,
        durationUs = samplesPerFrame * 1_000_000L / sampleRate,
        version = version,
        layer = layer,
        hasCrc = protectionBit == 0,
        channels = if (channelMode == 3) 1 else 2,
    )
}

private fun mp3SampleRate(version: Int, index: Int): Int {
    val base = intArrayOf(44100, 48000, 32000)[index]
    return when (version) {
        1 -> base
        2 -> base / 2
        else -> base / 4
    }
}

private fun mp3BitrateKbps(version: Int, layer: Int, index: Int): Int {
    val mpeg1Layer1 = intArrayOf(0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448)
    val mpeg1Layer2 = intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384)
    val mpeg1Layer3 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
    val mpeg2Layer1 = intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256)
    val mpeg2Layer23 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
    return when {
        version == 1 && layer == 1 -> mpeg1Layer1[index]
        version == 1 && layer == 2 -> mpeg1Layer2[index]
        version == 1 -> mpeg1Layer3[index]
        layer == 1 -> mpeg2Layer1[index]
        else -> mpeg2Layer23[index]
    }
}

private fun copyRecordingToRingtones(context: Context, file: File): Result<String> = runCatching {
    val name = file.nameWithoutExtension.toSafeDisplayName() + ".mp3"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_RINGTONES)
            put(MediaStore.Audio.Media.IS_RINGTONE, true)
            put(MediaStore.Audio.Media.IS_MUSIC, false)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore nie zwrocil miejsca zapisu")
        resolver.openOutputStream(uri)?.use { output ->
            FileInputStream(file).use { input -> input.copyTo(output) }
        } ?: error("Nie mozna otworzyc pliku docelowego")
        name
    } else {
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES).apply { mkdirs() }
        file.copyToUnique(File(dir, name)).name
    }
}

private fun File.copyToUnique(target: File): File =
    copyTo(target.uniqueFile(), overwrite = false)

private fun File.uniqueFile(): File {
    if (!exists()) return this
    var index = 2
    while (true) {
        val candidate = File(parentFile, "$nameWithoutExtension-$index.$extension")
        if (!candidate.exists()) return candidate
        index++
    }
}

private fun estimateAudioDurationSeconds(file: File): Long =
    runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.div(1000L) ?: 0L
        }
    }.getOrDefault(0L)

private fun estimateMp3BitrateKbps(file: File): Int =
    runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.div(1000) ?: 128
        }
    }.getOrDefault(128)

private fun String.toSafeDisplayName(): String =
    replace(Regex("[\\\\/:*?\"<>|]"), "-").take(80).ifBlank { "radio-polska" }

private fun formatRecordingDate(file: File): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified()))

private fun formatHistoryDate(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun formatRecordingDuration(file: File): String {
    val durationMs = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        }
    }.getOrNull() ?: return "czas nieznany"
    return formatDuration(durationMs / 1000L)
}

private fun areNotificationsGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun hasColorofonPermissions(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

private fun requestBatteryOptimizationExemption(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { openBatterySettings(context) }
}

private fun openBatterySettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        openAppSettings(context)
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun filterSummary(category: String, region: String, count: Int): String {
    val parts = listOfNotNull(
        if (category != "Wszystkie") category else null,
        if (region != "Wszystkie") region else null,
    )
    return if (parts.isEmpty()) "Stacje: $count" else "Stacje: $count • ${parts.joinToString(" • ")}"
}

private fun adaptiveLayout(widthDp: Int, heightDp: Int): AdaptiveLayout {
    val smallest = minOf(widthDp, heightDp)
    val tablet = smallest >= 600
    return when {
        tablet -> AdaptiveLayout(
            horizontalPadding = 24.dp,
            gap = 16.dp,
            playerPadding = 14.dp,
            compactPlayer = false,
            tileColumns = when {
                widthDp >= 1100 -> 6
                widthDp >= 900 -> 5
                else -> 4
            },
            tileHeight = 140.dp,
            menuHeight = 560.dp,
            recordingsHeight = 560.dp,
            twoPane = true,
        )
        widthDp < 370 || heightDp < 720 -> AdaptiveLayout(
            horizontalPadding = 10.dp,
            gap = 6.dp,
            playerPadding = 8.dp,
            compactPlayer = true,
            tileColumns = 2,
            tileHeight = 122.dp,
            menuHeight = 360.dp,
            recordingsHeight = 330.dp,
            twoPane = false,
        )
        widthDp >= 430 -> AdaptiveLayout(
            horizontalPadding = 16.dp,
            gap = 10.dp,
            playerPadding = 12.dp,
            compactPlayer = false,
            tileColumns = 3,
            tileHeight = 136.dp,
            menuHeight = 460.dp,
            recordingsHeight = 440.dp,
            twoPane = false,
        )
        else -> AdaptiveLayout(
            horizontalPadding = 12.dp,
            gap = 8.dp,
            playerPadding = 10.dp,
            compactPlayer = true,
            tileColumns = 2,
            tileHeight = 128.dp,
            menuHeight = 400.dp,
            recordingsHeight = 380.dp,
            twoPane = false,
        )
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb < 10) "%.2f MB".format(mb) else "%.1f MB".format(mb)
}

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}

private fun formatMillis(millis: Long): String {
    val totalSeconds = millis / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val tenths = (millis % 1000L) / 100L
    return "%02d:%02d.%d".format(minutes, seconds, tenths)
}

private fun stationPriority(station: RadioStation): Int {
    val name = station.name.lowercase()
    return when {
        "rmf fm" in name -> 1
        "radio zet" in name -> 2
        "vox fm" in name -> 3
        "radio eska" in name || "eska warszawa" in name -> 4
        "tok fm" in name -> 5
        "antyradio" in name -> 6
        "polskie radio program 1" in name -> 7
        "polskie radio 24" in name -> 8
        else -> 100
    }
}

private fun String.initials(): String =
    split(" ", "-", "_")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "RP" }

private fun String.toComposeColor(): Color =
    runCatching { Color(android.graphics.Color.parseColor(this)) }.getOrDefault(Color(0xFF2563EB))
