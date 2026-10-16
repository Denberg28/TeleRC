package io.github.denberg28.telerc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.TextureView
import android.widget.FrameLayout
import android.widget.TextView
import com.herohan.uvcapp.CameraException
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import com.serenegiant.usb.Size

/** A desired preview has one USB session, one surface and a bounded recovery budget. */
class UsbFpvView(context: Context, private val beforeRecovery: () -> Unit = {}) : FrameLayout(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val manager get() = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val prefs = context.getSharedPreferences("fpv", Context.MODE_PRIVATE)
    private val preview = TextureView(context)
    private val status = TextView(context).apply {
        setTextColor(Color.WHITE); textSize = 12f; gravity = Gravity.CENTER; setPadding(8, 8, 8, 8)
    }
    private var helper: ICameraHelper? = null
    private var generation = 0
    private var selected: UsbDevice? = null
    private var cameraOpen = false
    private var foreground = true
    private var visiblePreview = false
    private var wanted = false
    private var previewing = false
    private var surface: SurfaceTexture? = null
    private var lastFrameAt = 0L
    private var previewAt = 0L
    private var connectingAt = 0L
    private var releaseUntil = 0L
    private var nextRetryAt = 0L
    private var attempts = 0
    private var profileIndex = 0
    private var negotiated = false
    private var desiredDeviceId: Int? = null
    private var widthVideo = 640
    private var heightVideo = 480
    private var profileText = ""
    private var supported = emptyList<FpvStreamPolicy.Profile>()
    private var lastError = ""
    private var reconnectAfterStop = false
    private val tick = object : Runnable {
        override fun run() {
            if (!wanted) return
            if (foreground && visiblePreview) {
                val now = SystemClock.elapsedRealtime()
                if (helper == null && now >= nextRetryAt) {
                    val candidates = manager.deviceList.values.filter { isVideoDevice(it) && it.deviceId == desiredDeviceId }
                    if (candidates.size == 1 && cameraPermission() && manager.hasPermission(candidates.single()))
                        openSession(candidates.single())
                    else showMessage(if (candidates.isEmpty()) "Waiting for USB receiver · check OTG / power"
                        else "Receiver needs USB access · tap Connect FPV")
                } else if (helper != null && connectingAt > 0 && now - connectingAt > 15000)
                    recover("USB camera open timed out")
                else if (previewing && ((lastFrameAt > 0 && !FpvPreviewPolicy.frameFresh(lastFrameAt, now)) ||
                    (lastFrameAt == 0L && now - previewAt > 8000))) recover("USB frames stalled")
            }
            if (wanted) handler.postDelayed(this, 500)
        }
    }

    init {
        setBackgroundColor(Color.BLACK)
        addView(preview, LayoutParams(-1, -1, Gravity.CENTER))
        addView(status, LayoutParams(-1, -1))
        preview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, w: Int, h: Int) { startPreview() }
            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, w: Int, h: Int) = Unit
            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                if (surface === texture) { helper?.removeSurface(texture); surface = null }
                lastFrameAt = 0; return true
            }
            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
                if (!previewing || !foreground || !visiblePreview) return
                lastFrameAt = SystemClock.elapsedRealtime()
                status.text = "USB FPV · $profileText"
                status.gravity = Gravity.TOP or Gravity.START
                status.setBackgroundColor(Color.TRANSPARENT)
            }
        }
        showMessage("Connect a USB UVC receiver, then tap Connect FPV · hold for video options")
    }

    fun mode(): FpvStreamPolicy.Mode = FpvStreamPolicy.Mode.entries.firstOrNull {
        it.name == prefs.getString("mode", "AUTO") } ?: FpvStreamPolicy.Mode.AUTO
    fun setMode(mode: FpvStreamPolicy.Mode) { prefs.edit().putString("mode", mode.name).apply(); connectCamera() }
    private fun cameraPermission() = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    private fun showMessage(message: String) {
        status.text = message; status.gravity = Gravity.CENTER; status.setBackgroundColor(Color.BLACK)
    }

    /** Only an explicit user action may create a USB permission request. */
    fun connectCamera() {
        if (!foreground || !cameraPermission()) return
        beforeRecovery()
        closeSession(); wanted = true; attempts = 0; profileIndex = 0; nextRetryAt = 0; lastError = ""; supported = emptyList(); desiredDeviceId = null
        val attached = manager.deviceList.values.toList()
        val devices = attached.filter(::isVideoDevice)
        if (devices.size == 1) {
            desiredDeviceId = devices.single().deviceId
            if (SystemClock.elapsedRealtime() >= releaseUntil) openSession(devices.single())
            else { nextRetryAt = releaseUntil; showMessage("Releasing previous USB session…") }
        }
        else {
            wanted = false
            showMessage(when {
                attached.isEmpty() -> "Android sees no USB device · enable OTG, check data cable / receiver power"
                devices.isEmpty() -> "USB connected without UVC video · hold Connect FPV for diagnostics"
                else -> "Multiple UVC receivers · leave one connected, then retry"
            })
        }
        handler.removeCallbacks(tick)
        if (wanted) handler.postDelayed(tick, 500)
    }

    private fun openSession(device: UsbDevice) {
        if (!foreground || !visiblePreview || !wanted || helper != null || SystemClock.elapsedRealtime() < releaseUntil) return
        if (device.deviceId != desiredDeviceId) return
        selected = device; negotiated = false
        val plansBeforeOpen = FpvStreamPolicy.profiles(supported, mode())
        val requested = plansBeforeOpen.getOrNull(profileIndex.coerceAtMost((plansBeforeOpen.size - 1).coerceAtLeast(0)))
        val token = ++generation
        connectingAt = SystemClock.elapsedRealtime()
        showMessage("Connecting FPV · ${mode().label} · approve USB access if requested")
        try {
            val camera = CameraHelper(); helper = camera
            camera.setStateCallback(object : ICameraHelper.StateCallback {
                private fun current(d: UsbDevice, action: () -> Unit) { handler.post {
                    if (generation == token && helper === camera && d.deviceName == selected?.deviceName) action()
                } }
                override fun onAttach(d: UsbDevice) = Unit
                override fun onDeviceOpen(d: UsbDevice, isFirstOpen: Boolean) = current(d) {
                    if (foreground && wanted) {
                        if (requested == null) camera.openCamera()
                        else camera.openCamera(Size(requested.type, requested.width, requested.height, requested.fps, listOf(requested.fps)))
                    }
                }
                override fun onCameraOpen(d: UsbDevice) = current(d) {
                    if (!foreground || !wanted) { closeSession(); return@current }
                    val actual = camera.previewSize
                    if (!negotiated) {
                        negotiated = true
                        supported = (camera.supportedSizeList ?: emptyList()).flatMap { size ->
                            val rates = size.fpsList?.takeIf { it.isNotEmpty() } ?: listOf(size.fps)
                            rates.map { FpvStreamPolicy.Profile(size.type, size.width, size.height, it) }
                        }
                        val plans = FpvStreamPolicy.profiles(supported, mode())
                        if (plans.isEmpty()) { wanted = false; closeSession(); showMessage("Requested video mode unavailable · hold Connect FPV and choose Auto"); return@current }
                        val chosen = plans[profileIndex.coerceAtMost(plans.lastIndex)]
                        if (actual == null || actual.type != chosen.type || actual.width != chosen.width || actual.height != chosen.height || actual.fps != chosen.fps) {
                            if (requested == chosen) { recover("Receiver rejected requested video mode"); return@current }
                            // A full generation change discards the library's USB-close callbacks.
                            closeSession(); nextRetryAt = SystemClock.elapsedRealtime() + 700
                            showMessage("Negotiating ${mode().label} · controls disabled")
                            return@current
                        }
                    }
                    cameraOpen = true; connectingAt = 0
                    actual?.let {
                        widthVideo = it.width; heightVideo = it.height
                        profileText = "${it.width}×${it.height} · ${if (it.type == FpvStreamPolicy.YUYV) "YUYV" else "MJPEG"} ${it.fps} fps"
                    }
                    fitPreview(); startPreview()
                }
                override fun onCameraClose(d: UsbDevice) = current(d) {
                    cameraOpen = false; previewing = false; surface = null; lastFrameAt = 0
                    if (wanted && foreground) recover("Camera closed")
                }
                override fun onDeviceClose(d: UsbDevice) = current(d) { if (wanted) recover("USB receiver closed") }
                override fun onDetach(d: UsbDevice) = current(d) {
                    beforeRecovery(); closeSession(); wanted = false
                    showMessage("USB receiver unplugged · reconnect it and tap Connect FPV")
                }
                override fun onCancel(d: UsbDevice) = current(d) {
                    wanted = false; closeSession(); showMessage("USB access denied · tap Connect FPV to retry")
                }
                override fun onError(d: UsbDevice, error: CameraException) = current(d) {
                    recover("Camera error ${error.code} · close other USB camera apps")
                }
            })
            camera.selectDevice(device)
        } catch (error: RuntimeException) { recover("USB camera error: ${error.javaClass.simpleName}") }
        catch (_: LinkageError) { wanted = false; closeSession(); showMessage("USB video driver unavailable on this phone") }
    }

    private fun recover(reason: String) {
        lastError = reason; beforeRecovery(); closeSession()
        val delay = FpvStreamPolicy.retryDelay(attempts)
        if (delay == null) { wanted = false; showMessage("$reason · recovery limit reached · tap Connect FPV / try YUYV"); return }
        attempts++; profileIndex++
        nextRetryAt = SystemClock.elapsedRealtime() + delay
        showMessage("$reason · retry $attempts/3 · controls disabled")
    }
    private fun startPreview() {
        if (!cameraOpen || !foreground || !visiblePreview || !wanted) return
        val texture = preview.surfaceTexture ?: return
        if (surface !== texture) {
            surface?.let { helper?.removeSurface(it) }
            texture.setDefaultBufferSize(widthVideo, heightVideo)
            helper?.addSurface(texture, false); surface = texture
        }
        if (!previewing) {
            previewing = true; previewAt = SystemClock.elapsedRealtime(); lastFrameAt = 0
            showMessage("Waiting for FPV frames · $profileText"); helper?.startPreview()
        }
    }
    private fun stopPreview() {
        surface?.let { helper?.removeSurface(it) }; surface = null
        helper?.stopPreview(); previewing = false; lastFrameAt = 0; previewAt = 0
    }
    fun setVisiblePreview(visible: Boolean) {
        if (visiblePreview == visible) return
        visiblePreview = visible
        if (visible) { startPreview(); if (wanted && helper == null) nextRetryAt = SystemClock.elapsedRealtime() + 500 }
        else { stopPreview(); showMessage("FPV preview paused · Show camera to resume") }
    }
    fun pause() { foreground = false; stopPreview(); showMessage("FPV paused") }
    fun suspendCamera() {
        reconnectAfterStop = wanted
        closeSession(); handler.removeCallbacks(tick); showMessage("FPV paused")
    }
    fun resume() {
        foreground = true
        if (reconnectAfterStop && wanted) {
            reconnectAfterStop = false; beforeRecovery(); nextRetryAt = SystemClock.elapsedRealtime() + 700
        }
        startPreview(); handler.removeCallbacks(tick)
        if (wanted) handler.postDelayed(tick, 500)
    }
    private fun closeSession() {
        generation++; val old = helper; helper = null
        if (old != null) releaseUntil = SystemClock.elapsedRealtime() + 700
        surface?.let { old?.removeSurface(it) }; surface = null
        old?.release(); selected = null; cameraOpen = false; previewing = false
        connectingAt = 0; lastFrameAt = 0; previewAt = 0
    }
    fun close() { wanted = false; reconnectAfterStop = false; handler.removeCallbacks(tick); closeSession() }
    override fun onDetachedFromWindow() { close(); super.onDetachedFromWindow() }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); fitPreview() }
    private fun fitPreview() { FpvPreviewPolicy.fit(width, height, widthVideo, heightVideo)?.let {
        preview.layoutParams = LayoutParams(it.first, it.second, Gravity.CENTER)
    } }
    fun diagnosis(): String = usbDiagnosis(context) + "\n\nMode: ${mode().label}\nActive: $profileText\nRecovery: $attempts/3\nLast error: ${lastError.ifEmpty { "none" }}\nAdvertised: " +
        supported.take(24).joinToString { "${it.width}x${it.height}/${it.type}@${it.fps}" } +
        "\n\nUSB frames do not prove a clean RF picture. For colored lines, try YUYV; also check FPV channel, PAL/NTSC and camera power."
    companion object {
        fun isVideoDevice(d: UsbDevice) = UsbVideoPolicy.isVideo(d.deviceClass, (0 until d.interfaceCount).map { d.getInterface(it).interfaceClass })
        fun usbDiagnosis(context: Context): String {
            val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager
            return "Camera permission: ${context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED}\nUSB devices: ${usb.deviceList.size}\n" +
                usb.deviceList.values.take(8).joinToString("\n") { d -> "VID:PID %04X:%04X".format(java.util.Locale.US, d.vendorId, d.productId) +
                    " · UVC ${isVideoDevice(d)} · permission ${usb.hasPermission(d)}" } +
                "\nClose other USB camera apps; use a data-capable OTG cable with adequate receiver power."
        }
    }
}
