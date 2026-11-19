package com.wheresmysun.ui.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wheresmysun.orientation.CameraOptics
import kotlin.math.max
import kotlin.math.min

/**
 * Live back-camera preview, filling its bounds with centre-crop. Reports the camera's optics once
 * it is bound, so the overlay knows how many pixels a degree is.
 */
@Composable
fun CameraPreview(onOptics: (CameraOptics) -> Unit, onError: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val preview = Preview.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                            .build(),
                    )
                    .build()
                preview.surfaceProvider = previewView.surfaceProvider
                p.unbindAll()
                val camera = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                onOptics(readOptics(context, camera, preview.resolutionInfo?.resolution))
            } catch (e: Exception) {
                Log.w("CameraPreview", "Camera failed to start", e)
                onError(e.message ?: "Camera unavailable")
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose { provider?.unbindAll() }
    }
}

@OptIn(ExperimentalCamera2Interop::class)
private fun readOptics(context: Context, camera: Camera, resolution: Size?): CameraOptics {
    val aspect = resolution?.let { max(it.width, it.height).toDouble() / min(it.width, it.height) } ?: (4.0 / 3.0)
    return try {
        val id = Camera2CameraInfo.from(camera.cameraInfo).cameraId
        val chars = context.getSystemService(CameraManager::class.java).getCameraCharacteristics(id)
        val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
        val physical = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val pixelArray = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val active = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        if (focal == null || physical == null || focal <= 0f) return CameraOptics.FALLBACK.copy(previewAspect = aspect)

        // The physical size covers the whole pixel array; images come from the (slightly smaller)
        // active array, so scale down to it.
        var sensorLong = max(physical.width, physical.height).toDouble()
        if (pixelArray != null && active != null) {
            val pixLong = max(pixelArray.width, pixelArray.height).toDouble()
            val actLong = max(active.width(), active.height()).toDouble()
            if (pixLong > 0 && actLong > 0) sensorLong *= actLong / pixLong
        }
        CameraOptics(focal.toDouble(), sensorLong, aspect)
    } catch (e: Exception) {
        Log.w("CameraPreview", "Couldn't read camera optics, using defaults", e)
        CameraOptics.FALLBACK.copy(previewAspect = aspect)
    }
}
