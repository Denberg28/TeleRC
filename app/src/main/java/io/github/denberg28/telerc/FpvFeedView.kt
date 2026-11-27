package io.github.denberg28.telerc

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Size
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.abs

// FPV — Zero-flicker FPV camera view supporting Wi-Fi LAN MJPEG and Android Camera2 (USB External / Phone Test).
// Uses TextureView + Canvas (no SurfaceView) so MissionPlannerView can swap Map <-> FPV PiP smoothly with zero reload.
class FpvFeedView(
    context: Context,
    private var config: FpvConfig,
    private val onStatusChanged: (live: Boolean, label: String) -> Unit
) : FrameLayout(context) {
    private val ui = Handler(Looper.getMainLooper())
    private val supervisor = FpvLinkSupervisor()
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val mjpegCanvas = MjpegCanvasView(context)
    private val cameraTexture = TextureView(context)
    private val crosshairOverlay = CrosshairView(context)
    private val emptyHint = TextView(context).apply {
        text = "FPV standby"
        textSize = 12f
        setTextColor(Color.rgb(198, 212, 228))
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }

    private var active = false
    private var compactMode = true
    private var mjpegClient: FpvMjpegClient? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var cameraSurface: Surface? = null
    private var activeCameraId: String? = null
    private var activeCameraGen = 0
    private var cameraPreviewSize = Size(640, 480)
    private var pendingCameraId: String? = null
    private var availabilityRegistered = false

    private val watchdog = object : Runnable {
        override fun run() {
            if (!active) return
            when (supervisor.tick(SystemClock.elapsedRealtime())) {
                FpvLinkSupervisor.Action.REOPEN -> {
                    mjpegCanvas.setStaleDim(supervisor.holdFrameVisible)
                    emitStatus()
                    reopenActiveSource()
                }
                FpvLinkSupervisor.Action.CLEAR_HOLD -> {
                    mjpegCanvas.clearFrame()
                    updatePlaceholderVisibility()
                    emitStatus()
                }
                FpvLinkSupervisor.Action.NONE -> {
                    mjpegCanvas.setStaleDim(supervisor.state == FpvLinkSupervisor.State.STALE_HOLD)
                    emitStatus()
                }
            }
            ui.postDelayed(this, 500L)
        }
    }

    private val cameraAvailability = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) {
            if (!active) return
            if ((config.mode == FpvSourceMode.AUTO || config.mode == FpvSourceMode.USB_EXTERNAL) &&
                isExternalCamera(cameraId) && activeCameraId != cameraId
            ) {
                // Hot-plugged USB external camera: automatically switch to it (zero friction).
                ui.post { if (active) reopenActiveSource() }
            }
        }

        override fun onCameraUnavailable(cameraId: String) {
            if (!active) return
            if (cameraId == activeCameraId) {
                ui.post {
                    if (!active) return@post
                    closeCameraLocked()
                    if (config.mode == FpvSourceMode.AUTO) {
                        reopenActiveSource()
                    } else {
                        supervisor.onError(activeCameraGen, "USB camera unplugged", SystemClock.elapsedRealtime())
                        emitStatus()
                        updatePlaceholderVisibility()
                    }
                }
            }
        }
    }

    init {
        setBackgroundColor(Color.rgb(10, 15, 22))
        addView(mjpegCanvas, LayoutParams(-1, -1))
        addView(cameraTexture, LayoutParams(-1, -1))
        addView(crosshairOverlay, LayoutParams(-1, -1))
        addView(emptyHint, LayoutParams(-1, -1))
        cameraTexture.visibility = View.GONE
        mjpegCanvas.setScaleMode(config.scale)
        crosshairOverlay.visibility = View.GONE

        cameraTexture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                configureCameraTransform(width, height)
                val targetId = pendingCameraId
                if (active && targetId != null) {
                    pendingCameraId = null
                    openCameraById(targetId)
                }
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                configureCameraTransform(width, height)
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                closeCameraLocked()
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                if (active && activeCameraId != null) {
                    if (supervisor.onFrame(activeCameraGen, SystemClock.elapsedRealtime())) {
                        updatePlaceholderVisibility()
                        emitStatus()
                    }
                }
            }
        }
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()

    fun setCompact(compact: Boolean) {
        compactMode = compact
        crosshairOverlay.visibility = if (!compact && config.crosshair && supervisor.state == FpvLinkSupervisor.State.STREAMING) {
            View.VISIBLE
        } else {
            View.GONE
        }
        emptyHint.textSize = if (compact) 10.5f else 13f
        if (cameraTexture.isAvailable && cameraTexture.width > 0 && cameraTexture.height > 0) {
            configureCameraTransform(cameraTexture.width, cameraTexture.height)
        }
        mjpegCanvas.invalidate()
    }

    fun updateConfig(next: FpvConfig) {
        val changed = next != config
        config = next
        mjpegCanvas.setScaleMode(next.scale)
        setCompact(compactMode)
        if (active && changed) {
            mjpegCanvas.clearFrame()
            reopenActiveSource()
        }
    }

    fun start() {
        if (active) return
        active = true
        registerCameraAvailability()
        reopenActiveSource()
        ui.removeCallbacks(watchdog)
        ui.postDelayed(watchdog, 500L)
    }

    fun pause() {
        if (!active) return
        active = false
        ui.removeCallbacks(watchdog)
        unregisterCameraAvailability()
        stopTransports()
        supervisor.stop()
        mjpegCanvas.clearFrame()
        updatePlaceholderVisibility()
        emitStatus()
    }

    fun destroy() {
        pause()
        mjpegCanvas.clearFrame()
    }

    fun needsCameraPermission(): Boolean = supervisor.state == FpvLinkSupervisor.State.PERMISSION_NEEDED

    fun statusText(): String = supervisor.message

    private fun registerCameraAvailability() {
        if (availabilityRegistered) return
        try {
            cameraManager.registerAvailabilityCallback(cameraAvailability, ui)
            availabilityRegistered = true
        } catch (_: Exception) {}
    }

    private fun unregisterCameraAvailability() {
        if (!availabilityRegistered) return
        try {
            cameraManager.unregisterAvailabilityCallback(cameraAvailability)
        } catch (_: Exception) {}
        availabilityRegistered = false
    }

    private fun stopTransports() {
        pendingCameraId = null
        mjpegClient?.close()
        mjpegClient = null
        closeCameraLocked()
    }

    private fun reopenActiveSource() {
        stopTransports()
        if (!active) return

        val externalId = findCameraId(externalOnly = true)
        when (config.mode) {
            FpvSourceMode.AUTO -> {
                if (externalId != null) {
                    startCameraSource(externalId, "USB Cam")
                } else {
                    startWifiMjpegSource()
                }
            }
            FpvSourceMode.WIFI_MJPEG -> startWifiMjpegSource()
            FpvSourceMode.USB_EXTERNAL -> {
                if (externalId != null) {
                    startCameraSource(externalId, "USB Cam")
                } else {
                    supervisor.markNoSource("Plug in USB UVC camera")
                    updatePlaceholderVisibility()
                    emitStatus()
                }
            }
            FpvSourceMode.PHONE_REAR -> {
                val rearId = findCameraId(externalOnly = false)
                if (rearId != null) {
                    startCameraSource(rearId, "Phone Cam")
                } else {
                    supervisor.markNoSource("No phone camera available")
                    updatePlaceholderVisibility()
                    emitStatus()
                }
            }
        }
    }

    private fun startWifiMjpegSource() {
        cameraTexture.visibility = View.GONE
        mjpegCanvas.visibility = View.VISIBLE
        val endpoint = FpvUrlValidator.parse(config.url)
        if (endpoint == null) {
            supervisor.markNoSource("Set private LAN FPV URL in Cam")
            updatePlaceholderVisibility()
            emitStatus()
            return
        }
        val gen = supervisor.startConnecting("Wi-Fi FPV", SystemClock.elapsedRealtime())
        updatePlaceholderVisibility()
        emitStatus()
        val client = FpvMjpegClient(
            context = context.applicationContext,
            endpoint = endpoint,
            generation = gen,
            onBitmap = { frameGen, bmp ->
                ui.post {
                    if (!active || !supervisor.onFrame(frameGen, SystemClock.elapsedRealtime())) {
                        bmp.recycle()
                        return@post
                    }
                    mjpegCanvas.setFrame(bmp)
                    mjpegCanvas.setStaleDim(false)
                    updatePlaceholderVisibility()
                    emitStatus()
                }
            },
            onError = { errGen, reason ->
                ui.post {
                    if (!active) return@post
                    if (supervisor.onError(errGen, reason, SystemClock.elapsedRealtime())) {
                        mjpegCanvas.setStaleDim(supervisor.holdFrameVisible)
                        updatePlaceholderVisibility()
                        emitStatus()
                    }
                }
            }
        )
        mjpegClient = client
        client.start()
    }

    private fun startCameraSource(cameraId: String, label: String) {
        mjpegCanvas.visibility = View.GONE
        cameraTexture.visibility = View.VISIBLE
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            supervisor.markPermissionNeeded("Grant Camera permission in Cam")
            updatePlaceholderVisibility()
            emitStatus()
            return
        }
        activeCameraGen = supervisor.startConnecting(label, SystemClock.elapsedRealtime())
        updatePlaceholderVisibility()
        emitStatus()
        if (cameraTexture.isAvailable) {
            openCameraById(cameraId)
        } else {
            pendingCameraId = cameraId
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCameraById(cameraId: String) {
        if (!active) return
        val gen = activeCameraGen
        try {
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val choices = map?.getOutputSizes(SurfaceTexture::class.java)
            cameraPreviewSize = choosePreviewSize(choices)
            activeCameraId = cameraId
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (!active || gen != activeCameraGen) {
                        camera.close()
                        return
                    }
                    cameraDevice = camera
                    startCameraPreviewSession(camera, gen)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    if (cameraDevice === camera) cameraDevice = null
                    if (active && gen == activeCameraGen) {
                        supervisor.onError(gen, "Camera disconnected", SystemClock.elapsedRealtime())
                        updatePlaceholderVisibility()
                        emitStatus()
                    }
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    if (cameraDevice === camera) cameraDevice = null
                    if (active && gen == activeCameraGen) {
                        supervisor.onError(gen, "Camera error ($error)", SystemClock.elapsedRealtime())
                        updatePlaceholderVisibility()
                        emitStatus()
                    }
                }
            }, ui)
        } catch (_: Exception) {
            supervisor.onError(gen, "Could not open camera", SystemClock.elapsedRealtime())
            updatePlaceholderVisibility()
            emitStatus()
        }
    }

    @Suppress("DEPRECATION")
    private fun startCameraPreviewSession(camera: CameraDevice, gen: Int) {
        val tex = cameraTexture.surfaceTexture ?: return
        tex.setDefaultBufferSize(cameraPreviewSize.width, cameraPreviewSize.height)
        configureCameraTransform(cameraTexture.width, cameraTexture.height)
        val surface = Surface(tex)
        cameraSurface = surface
        try {
            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            }
            camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (!active || gen != activeCameraGen || cameraDevice !== camera) {
                        try { session.close() } catch (_: Exception) {}
                        return
                    }
                    captureSession = session
                    try {
                        session.setRepeatingRequest(request.build(), null, ui)
                    } catch (_: Exception) {
                        supervisor.onError(gen, "Camera preview failed", SystemClock.elapsedRealtime())
                        emitStatus()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    if (active && gen == activeCameraGen) {
                        supervisor.onError(gen, "Camera session failed", SystemClock.elapsedRealtime())
                        emitStatus()
                    }
                }
            }, ui)
        } catch (_: Exception) {
            supervisor.onError(gen, "Camera setup failed", SystemClock.elapsedRealtime())
            emitStatus()
        }
    }

    private fun closeCameraLocked() {
        activeCameraId = null
        try { captureSession?.close() } catch (_: Exception) {}
        captureSession = null
        try { cameraDevice?.close() } catch (_: Exception) {}
        cameraDevice = null
        try { cameraSurface?.release() } catch (_: Exception) {}
        cameraSurface = null
    }

    private fun isExternalCamera(cameraId: String): Boolean = try {
        val facing = cameraManager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING)
        facing == CameraCharacteristics.LENS_FACING_EXTERNAL
    } catch (_: Exception) {
        false
    }

    private fun findCameraId(externalOnly: Boolean): String? = try {
        val ids = cameraManager.cameraIdList
        if (externalOnly) {
            ids.firstOrNull { isExternalCamera(it) }
        } else {
            ids.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
            } ?: ids.firstOrNull()
        }
    } catch (_: Exception) {
        null
    }

    private fun choosePreviewSize(choices: Array<Size>?): Size {
        if (choices.isNullOrEmpty()) return Size(640, 480)
        // Prefer low-latency 720p/VGA landscape stream (~640..1280px wide).
        return choices
            .filter { it.width in 480..1280 && it.height in 320..720 }
            .minByOrNull { abs(it.width - 800) }
            ?: choices.minByOrNull { abs(it.width - 640) }
            ?: Size(640, 480)
    }

    private fun configureCameraTransform(viewWidth: Int, viewHeight: Int) {
        if (viewWidth <= 0 || viewHeight <= 0) return
        val matrix = Matrix()
        val cx = viewWidth / 2f
        val cy = viewHeight / 2f
        @Suppress("DEPRECATION")
        val rotation = (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        val bufferW = cameraPreviewSize.width.toFloat().coerceAtLeast(1f)
        val bufferH = cameraPreviewSize.height.toFloat().coerceAtLeast(1f)
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufRect = RectF(0f, 0f, bufferH, bufferW)
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            bufRect.offset(cx - bufRect.centerX(), cy - bufRect.centerY())
            matrix.setRectToRect(viewRect, bufRect, Matrix.ScaleToFit.FILL)
            val scale = if (config.scale == FpvScaleMode.FILL) {
                maxOf(viewHeight / bufferH, viewWidth / bufferW)
            } else {
                minOf(viewHeight / bufferH, viewWidth / bufferW)
            }
            matrix.postScale(scale, scale, cx, cy)
            matrix.postRotate(90f * (rotation - 2), cx, cy)
        } else if (rotation == Surface.ROTATION_180) {
            matrix.postRotate(180f, cx, cy)
        }
        cameraTexture.setTransform(matrix)
    }

    private fun updatePlaceholderVisibility() {
        val showingVideo = supervisor.state == FpvLinkSupervisor.State.STREAMING ||
            (supervisor.state == FpvLinkSupervisor.State.STALE_HOLD && supervisor.holdFrameVisible)
        emptyHint.visibility = if (showingVideo) View.GONE else View.VISIBLE
        emptyHint.text = supervisor.message
        crosshairOverlay.visibility = if (!compactMode && config.crosshair && supervisor.state == FpvLinkSupervisor.State.STREAMING) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun emitStatus() {
        onStatusChanged(supervisor.state == FpvLinkSupervisor.State.STREAMING, supervisor.message)
    }

    private class MjpegCanvasView(context: Context) : View(context) {
        private var frame: Bitmap? = null
        private var scaleMode = FpvScaleMode.FILL
        private var staleDim = false
        private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val dimPaint = Paint().apply { color = Color.argb(115, 8, 12, 18) }
        private val dst = RectF()

        fun setScaleMode(mode: FpvScaleMode) {
            scaleMode = mode
            invalidate()
        }

        fun setStaleDim(dim: Boolean) {
            if (staleDim != dim) {
                staleDim = dim
                invalidate()
            }
        }

        fun setFrame(next: Bitmap) {
            val prev = frame
            frame = next
            invalidate()
            if (prev != null && prev !== next && !prev.isRecycled) {
                prev.recycle()
            }
        }

        fun clearFrame() {
            val prev = frame
            frame = null
            staleDim = false
            invalidate()
            if (prev != null && !prev.isRecycled) {
                prev.recycle()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val bmp = frame ?: return
            if (bmp.isRecycled || width <= 0 || height <= 0) return
            val vw = width.toFloat()
            val vh = height.toFloat()
            val bw = bmp.width.toFloat().coerceAtLeast(1f)
            val bh = bmp.height.toFloat().coerceAtLeast(1f)
            val scale = if (scaleMode == FpvScaleMode.FILL) maxOf(vw / bw, vh / bh) else minOf(vw / bw, vh / bh)
            val dw = bw * scale
            val dh = bh * scale
            dst.set((vw - dw) / 2f, (vh - dh) / 2f, (vw + dw) / 2f, (vh + dh) / 2f)
            canvas.drawBitmap(bmp, null, dst, bmpPaint)
            if (staleDim) {
                canvas.drawRect(0f, 0f, vw, vh, dimPaint)
            }
        }
    }

    private class CrosshairView(context: Context) : View(context) {
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(145, 220, 240, 255)
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val gap = 10f * resources.displayMetrics.density
            val arm = 22f * resources.displayMetrics.density
            canvas.drawLine(cx - arm, cy, cx - gap, cy, stroke)
            canvas.drawLine(cx + gap, cy, cx + arm, cy, stroke)
            canvas.drawLine(cx, cy - arm * 0.7f, cx, cy - gap, stroke)
            canvas.drawLine(cx, cy + gap, cx, cy + arm * 0.7f, stroke)
            canvas.drawCircle(cx, cy, 3f * resources.displayMetrics.density, stroke)
        }
    }
}
