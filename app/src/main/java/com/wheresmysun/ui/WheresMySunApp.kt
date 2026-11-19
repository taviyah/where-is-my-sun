package com.wheresmysun.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wheresmysun.AlignResult
import com.wheresmysun.SkyViewModel
import com.wheresmysun.astro.Body
import com.wheresmysun.data.ViewMode
import com.wheresmysun.orientation.CameraOptics
import com.wheresmysun.orientation.SkyProjection
import com.wheresmysun.ui.camera.CameraPreview
import com.wheresmysun.ui.camera.CameraSkyOverlay
import com.wheresmysun.ui.map.SkyMap
import com.wheresmysun.ui.theme.LocalSkyPalette
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private fun Context.granted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

@Composable
fun WheresMySunApp(vm: SkyViewModel = viewModel()) {
    val context = LocalContext.current
    var hasLocation by remember {
        mutableStateOf(context.granted(Manifest.permission.ACCESS_FINE_LOCATION) || context.granted(Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    var hasCamera by remember { mutableStateOf(context.granted(Manifest.permission.CAMERA)) }
    var askedCamera by rememberSaveable { mutableStateOf(false) }
    var showManualPlace by remember { mutableStateOf(false) }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasLocation = result.values.any { it }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        hasCamera = ok
    }

    // Sensors and GPS run only while the app is on screen.
    LifecycleResumeEffect(hasLocation) {
        hasLocation = context.granted(Manifest.permission.ACCESS_FINE_LOCATION) || context.granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        hasCamera = context.granted(Manifest.permission.CAMERA)
        vm.orientation.start()
        if (hasLocation) vm.locationTracker.start()
        onPauseOrDispose {
            vm.orientation.stop()
            vm.locationTracker.stop()
        }
    }

    LaunchedEffect(vm.mode, hasCamera) {
        if (vm.mode == ViewMode.CAMERA && !hasCamera && !askedCamera) {
            askedCamera = true
            cameraLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasLocation && vm.places.isEmpty()) {
        WelcomeScreen(
            onAllowLocation = {
                locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            onManual = { showManualPlace = true },
        )
        if (showManualPlace) {
            AddPlaceDialog(null, onDismiss = { showManualPlace = false }) { name, point ->
                vm.addPlace(name, point)
                showManualPlace = false
            }
        }
        return
    }

    MainScreen(
        vm = vm,
        hasCamera = hasCamera,
        hasLocation = hasLocation,
        onRequestCamera = { cameraLauncher.launch(Manifest.permission.CAMERA) },
        onRequestLocation = {
            locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    vm: SkyViewModel,
    hasCamera: Boolean,
    hasLocation: Boolean,
    onRequestCamera: () -> Unit,
    onRequestLocation: () -> Unit,
) {
    val density = LocalDensity.current
    var topHeight by remember { mutableStateOf(0) }
    var bottomHeight by remember { mutableStateOf(0) }
    var optics by remember { mutableStateOf<CameraOptics?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    var showPlaces by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var calibrationDismissed by remember { mutableStateOf(false) }

    val cameraMode = vm.mode == ViewMode.CAMERA
    val cameraLive = cameraMode && hasCamera && cameraError == null

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val safeArea = with(density) { PaddingValues(top = topHeight.toDp(), bottom = bottomHeight.toDp()) }

        when {
            cameraLive -> {
                CameraPreview(
                    onOptics = { optics = it },
                    onError = { cameraError = it },
                    modifier = Modifier.fillMaxSize(),
                )
                CameraSkyOverlay(vm, optics ?: CameraOptics.FALLBACK, safeArea)
            }
            cameraMode -> CameraUnavailable(
                message = cameraError ?: "The live sky view needs the camera.",
                canRequest = cameraError == null,
                onRequest = onRequestCamera,
                onUseMap = { vm.selectMode(ViewMode.MAP) },
                modifier = Modifier.padding(safeArea),
            )
            else -> SkyMap(vm, Modifier.padding(safeArea).padding(horizontal = 8.dp))
        }

        // Top: location, align, mode, settings, plus any banners.
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .onSizeChanged { topHeight = it.height }
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TopBar(
                vm = vm,
                cameraLive = cameraLive,
                onPlaces = { showPlaces = true },
                onSettings = { showSettings = true },
            )
            Banners(
                vm = vm,
                cameraLive = cameraLive,
                hasLocation = hasLocation,
                calibrationDismissed = calibrationDismissed,
                onDismissCalibration = { calibrationDismissed = true },
                onRequestLocation = onRequestLocation,
            )
        }

        TimeControls(
            vm = vm,
            onInfo = { showInfo = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { bottomHeight = it.height }
                .navigationBarsPadding()
                .padding(12.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
        )
    }

    // Once the compass reports good accuracy again, let the warning come back next time.
    LaunchedEffect(vm.orientation.compassTrouble) {
        if (!vm.orientation.compassTrouble) calibrationDismissed = false
    }

    if (showInfo) {
        ModalBottomSheet(onDismissRequest = { showInfo = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
            SkyInfo(vm)
        }
    }
    if (showPlaces) PlacesDialog(vm, onDismiss = { showPlaces = false })
    if (showSettings) SettingsDialog(vm, onDismiss = { showSettings = false })
}

@Composable
private fun TopBar(vm: SkyViewModel, cameraLive: Boolean, onPlaces: () -> Unit, onSettings: () -> Unit) {
    val palette = LocalSkyPalette.current
    val barColor = MaterialTheme.colorScheme.surface.copy(alpha = if (palette.isEink) 1f else 0.85f)
    val buttonBorder = palette.panelBorder?.let { Modifier.border(it, CircleShape) } ?: Modifier
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // The chip takes whatever width the buttons leave, and only shrinks (with an ellipsis)
        // when its label really doesn't fit.
        Box(Modifier.weight(1f)) {
            Surface(onClick = onPlaces, shape = RoundedCornerShape(50), color = barColor, border = palette.panelBorder) {
                Row(Modifier.padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val loc = vm.activeLocation
                    Icon(if (loc?.isGps != false) Icons.Outlined.MyLocation else Icons.Outlined.Place, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(loc?.label ?: "Finding location…", Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                    Icon(Icons.Outlined.ArrowDropDown, null)
                }
            }
        }
        val buttonColors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = barColor)
        if (cameraLive) {
            FilledTonalIconButton(
                onClick = { if (vm.aligning) vm.cancelAligning() else vm.startAligning() },
                modifier = buttonBorder,
                colors = if (vm.aligning) IconButtonDefaults.filledTonalIconButtonColors() else buttonColors,
            ) { Icon(Icons.Outlined.CenterFocusStrong, contentDescription = "Align with the real sun") }
        }
        FilledTonalIconButton(
            onClick = { vm.selectMode(if (vm.mode == ViewMode.CAMERA) ViewMode.MAP else ViewMode.CAMERA) },
            modifier = buttonBorder,
            colors = buttonColors,
        ) {
            if (vm.mode == ViewMode.CAMERA) Icon(Icons.Outlined.Explore, contentDescription = "Show sky map")
            else Icon(Icons.Outlined.CameraAlt, contentDescription = "Show camera view")
        }
        FilledTonalIconButton(onClick = onSettings, modifier = buttonBorder, colors = buttonColors) {
            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
        }
    }
}

@Composable
private fun Banners(
    vm: SkyViewModel,
    cameraLive: Boolean,
    hasLocation: Boolean,
    calibrationDismissed: Boolean,
    onDismissCalibration: () -> Unit,
    onRequestLocation: () -> Unit,
) {
    val palette = LocalSkyPalette.current
    val pointingDown by remember(vm) {
        derivedStateOf { vm.orientation.rotation?.let { SkyProjection.cameraAltitude(it) < -30.0 } ?: false }
    }

    val result = vm.lastAlignResult
    LaunchedEffect(result) {
        if (result != null) {
            delay(3500)
            vm.clearAlignResult()
        }
    }

    // One banner at a time, most important first, so the camera view stays clear.
    when {
        vm.activeLocation == null && hasLocation ->
            Banner(text = "Finding your location…", leading = { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) })

        vm.activeLocation == null ->
            Banner(text = "Location is off. Allow it, or pick a saved place.", action = "Allow" to onRequestLocation)

        !vm.orientation.isAvailable ->
            Banner(text = "This phone has no compass, so the views can't follow where you point it.", color = palette.warning)

        vm.aligning -> {
            val target = vm.alignmentTarget()
            Banner(
                text = when (target?.first) {
                    Body.SUN -> "Tap where you see the real sun. Don't look straight at it."
                    Body.MOON -> "Tap where you see the real moon."
                    null -> "The sun and moon are both below the horizon right now, so there's nothing to align with."
                },
                leading = { Icon(Icons.Outlined.CenterFocusStrong, null) },
                onClose = vm::cancelAligning,
            )
        }

        result != null -> Banner(
            text = when (result) {
                is AlignResult.Adjusted -> {
                    val deg = result.byDegrees
                    if (abs(deg) < 0.5) "Already aligned." else "Aligned: compass corrected by ${deg.roundToInt()}°."
                }
                AlignResult.NothingUp -> "Nothing to align with: the sun and moon are below the horizon."
                AlignResult.TooFar -> "That's more than 90° away from where the sun should be. Try again."
            },
        )

        vm.orientation.compassTrouble && !calibrationDismissed -> Banner(
            // An uncalibrated sensor also reads the wrong strength and dip, so when Android says
            // the compass needs calibrating, say that first rather than blaming the surroundings.
            text = if (vm.orientation.needsCalibration) {
                "Calibrate the compass: wave the phone in a figure-8."
            } else {
                "Metal nearby is bending the compass. Step away, or use tap-to-align."
            },
            color = palette.warning,
            onClose = onDismissCalibration,
        )

        cameraLive && pointingDown ->
            Banner(text = "Point the camera up at the sky.", leading = { Icon(Icons.Outlined.WbSunny, null, tint = palette.sun) })
    }
}

@Composable
private fun Banner(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    leading: (@Composable () -> Unit)? = null,
    action: Pair<String, () -> Unit>? = null,
    onClose: (() -> Unit)? = null,
) {
    val palette = LocalSkyPalette.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = palette.panelAlpha),
        border = palette.panelBorder,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(10.dp))
            }
            Text(text, color = color, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 4.dp))
            if (action != null) TextButton(onClick = action.second) { Text(action.first) }
            if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Dismiss") }
        }
    }
}

@Composable
private fun CameraUnavailable(message: String, canRequest: Boolean, onRequest: () -> Unit, onUseMap: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.CameraAlt, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        if (canRequest) Button(onClick = onRequest) { Text("Allow camera") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onUseMap) { Text("Use the sky map instead") }
    }
}

@Composable
private fun WelcomeScreen(onAllowLocation: () -> Unit, onManual: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.WbSunny, null, Modifier.size(72.dp), tint = LocalSkyPalette.current.sun)
        Spacer(Modifier.height(24.dp))
        Text("Where's my Sun", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "Point your phone at the sky to see the sun's and moon's paths for today or tomorrow, " +
                "and check whether a spot will be in the shade.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "The sun's position depends on where you are, so the app needs your location. " +
                "It stays on your phone: everything is calculated offline.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onAllowLocation, modifier = Modifier.fillMaxWidth()) { Text("Allow location") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onManual) { Text("Enter a place by coordinates instead") }
    }
}
