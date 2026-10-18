package io.github.denberg28.telerc

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.Build
import android.view.Gravity
import android.view.TextureView
import android.widget.FrameLayout
import android.widget.TextView
import com.herohan.uvcapp.CameraException
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import com.serenegiant.usb.Size
import java.util.UUID

/** One desired receiver, one permission transaction, one session and bounded retry frequency. */
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
    private var deviceOpen = false
    private var requestedSize: Size? = null
    private var cameraOpen = false
    private var foreground = false
    private var visiblePreview = false
    private val connection = FpvReconnectPolicy(readReceiver(), prefs.getBoolean("auto_connect", false))
    private val wanted get() = connection.wanted
    private var disposed = false
    private var previewing = false
    private var surface: SurfaceTexture? = null
    private var lastFrameAt = 0L
    private var previewAt = 0L
    private var connectingAt = 0L
    private var profileIndex = 0
    private var negotiated = false
    private var sessionMode: FpvStreamPolicy.Mode? = null
    private val permissionAction = context.packageName + ".FPV_USB_PERMISSION." + UUID.randomUUID()
    private var permissionIntent: PendingIntent? = null
    private var permissionDevice: UsbDevice? = null
    private var receiverRegistered = false
    private val permissionReceiver = object : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        override fun onReceive(context: Context, intent: Intent) {
            if (disposed || intent.action != permissionAction) return
            val expected = permissionDevice ?: return
            val supplied = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE) ?: return
            if (attachment(supplied) != attachment(expected)) return
            val current = manager.deviceList.values.firstOrNull { attachment(it) == attachment(expected) }
            val granted = current != null && cameraPermission() && manager.hasPermission(current)
            if (!connection.permissionResult(attachment(expected), granted)) return
            clearPermission()
            if (!granted) {
                prefs.edit().putBoolean("auto_connect", false).apply()
                showMessage("USB access not granted · tap Connect FPV to retry")
            } else driveConnection()
        }
    }
    private var widthVideo = 640
    private var heightVideo = 480
    private var profileText = ""
    private var supported = emptyList<FpvStreamPolicy.Profile>()
    private var lastError = ""
    private var reconnectAfterStop = false
    private val tick = object : Runnable {
        override fun run() {
            if (!wanted || disposed) return
            if (foreground && visiblePreview) {
                val now = SystemClock.elapsedRealtime()
                driveConnection()
                if (helper != null && connectingAt > 0 && now - connectingAt > 15000)
                    recover("USB camera open timed out")
                else if (previewing && ((lastFrameAt > 0 && !FpvPreviewPolicy.frameFresh(lastFrameAt, now)) ||
                    (lastFrameAt == 0L && now - previewAt > 8000))) recover("USB frames stalled")
            }
            if (wanted) handler.postDelayed(this, 500)
        }
    }

    init {
        setBackgroundColor(Color.BLACK)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(permissionReceiver, IntentFilter(permissionAction), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(permissionReceiver, IntentFilter(permissionAction))
        }
        receiverRegistered = true
        addView(preview, LayoutParams(-1, -1, Gravity.CENTER))
        addView(status, LayoutParams(-1, -1))
        preview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, w: Int, h: Int) { startPreview() }
            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, w: Int, h: Int) = Unit
            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                if (surface === texture) { beforeRecovery(); stopPreview() }
                return true
            }
            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
                if (!previewing || !foreground || !visiblePreview) return
                lastFrameAt = SystemClock.elapsedRealtime()
                connection.frame(lastFrameAt)
                val caption = "USB FPV · $profileText"
                if (status.text != caption) {
                    status.text = caption; status.gravity = Gravity.TOP or Gravity.START
                    status.setBackgroundColor(Color.TRANSPARENT)
                }
            }
        }
        showMessage("Connect a USB UVC receiver, then tap Connect FPV · hold for video options")
        if (wanted) handler.postDelayed(tick, 500)
    }

    fun mode(): FpvStreamPolicy.Mode = FpvStreamPolicy.Mode.entries.firstOrNull {
        it.name == prefs.getString("mode", "AUTO") } ?: FpvStreamPolicy.Mode.AUTO
    fun setMode(mode: FpvStreamPolicy.Mode) { prefs.edit().putString("mode", mode.name).apply(); connectCamera() }
    private fun cameraPermission() = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    private fun showMessage(message: String) {
        status.text = message; status.gravity = Gravity.CENTER; status.setBackgroundColor(Color.BLACK)
    }

    private fun readReceiver(): FpvReconnectPolicy.Receiver? {
        val vendor = prefs.getInt("receiver_vendor", -1)
        val product = prefs.getInt("receiver_product", -1)
        return if (vendor in 0..65535 && product in 0..65535) FpvReconnectPolicy.Receiver(vendor, product) else null
    }
    private fun attachment(device: UsbDevice) = "${device.deviceName}#${device.deviceId}"
    private fun remember(device: UsbDevice) {
        connection.remember(FpvReconnectPolicy.Receiver(device.vendorId, device.productId))
        prefs.edit().putInt("receiver_vendor", device.vendorId).putInt("receiver_product", device.productId)
            .putBoolean("auto_connect", true).apply()
    }
    fun autoReconnect() = prefs.getBoolean("auto_connect", false)
    fun handles(device: UsbDevice) = !disposed && wanted && helper != null &&
        selected?.let(::attachment) == attachment(device) && cameraPermission() && manager.hasPermission(device)
    fun setAutoReconnect(enabled: Boolean) {
        prefs.edit().putBoolean("auto_connect", enabled).apply()
        if (enabled) connectCamera() else disconnectCamera()
    }
    fun disconnectCamera() {
        prefs.edit().putBoolean("auto_connect", false).apply()
        connection.stop(); clearPermission(); closeSession(); handler.removeCallbacks(tick)
        showMessage("FPV disconnected · tap Connect FPV")
    }
    /** Explicit requests may ask once. Repeated Connect never tears down a healthy session. */
    fun connectCamera(allowPermissionRequest: Boolean = true) {
        if (disposed || !foreground || !cameraPermission()) return
        if (!allowPermissionRequest && prefs.contains("auto_connect") && !autoReconnect()) return
        val devices = manager.deviceList.values.filter(::isVideoDevice)
        val chosen = devices.singleOrNull()
        if (chosen != null) remember(chosen)
        connection.request(chosen?.let { FpvReconnectPolicy.Receiver(it.vendorId, it.productId) }, allowPermissionRequest)
        if (helper != null && sessionMode != mode()) {
            beforeRecovery(); closeSession(); profileIndex = 0; supported = emptyList()
        }
        driveConnection()
        handler.removeCallbacks(tick)
        if (wanted) handler.postDelayed(tick, 500)
    }
    private fun clearPermission() {
        permissionIntent?.cancel(); permissionIntent = null; permissionDevice = null
    }
    private fun driveConnection() {
        if (disposed || !foreground || !visiblePreview || !wanted) return
        if (!cameraPermission()) { showMessage("Camera access required · tap Connect FPV"); return }
        val devices = manager.deviceList.values.filter(::isVideoDevice)
        val snapshots = devices.map { FpvReconnectPolicy.Device(attachment(it),
            FpvReconnectPolicy.Receiver(it.vendorId, it.productId), manager.hasPermission(it)) }
        val decision = connection.decide(snapshots, true, selected?.let(::attachment), SystemClock.elapsedRealtime())
        if (permissionDevice?.let(::attachment) != connection.pendingPermission) clearPermission()
        val device = decision.device?.let { expected -> devices.firstOrNull { attachment(it) == expected.attachment } }
        when (decision.action) {
            FpvReconnectPolicy.Action.LOST -> {
                beforeRecovery(); closeSession(); clearPermission(); connection.detached()
                profileIndex = 0; supported = emptyList()
                showMessage("USB receiver reconnecting · waiting for access")
            }
            FpvReconnectPolicy.Action.WAIT_DEVICE -> {
                profileIndex = 0; supported = emptyList()
                showMessage("Waiting for remembered USB receiver · check OTG / power")
            }
            FpvReconnectPolicy.Action.AMBIGUOUS -> showMessage("Multiple matching USB receivers · leave one connected")
            FpvReconnectPolicy.Action.NEED_PERMISSION -> showMessage("Receiver needs Android USB access · tap Connect FPV")
            FpvReconnectPolicy.Action.WAIT_PERMISSION -> showMessage("Waiting for Android USB approval")
            FpvReconnectPolicy.Action.REQUEST_PERMISSION -> if (device != null) {
                beforeRecovery(); remember(device); permissionDevice = device
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                permissionIntent = PendingIntent.getBroadcast(context, 0,
                    Intent(permissionAction).setPackage(context.packageName), flags)
                showMessage("Allow USB access · choose Always / use by default if Android offers it")
                try { manager.requestPermission(device, permissionIntent!!) }
                catch (_: RuntimeException) {
                    connection.permissionResult(attachment(device), false); clearPermission()
                    prefs.edit().putBoolean("auto_connect", false).apply()
                    showMessage("USB access request failed · tap Connect FPV")
                }
            }
            FpvReconnectPolicy.Action.OPEN -> if (device != null && SystemClock.elapsedRealtime() >= releaseUntil) {
                remember(device); openSession(device)
            }
            else -> Unit
        }
    }

    private fun openSession(device: UsbDevice) {
        if (!foreground || !visiblePreview || !wanted || helper != null || SystemClock.elapsedRealtime() < releaseUntil) return
        if (!cameraPermission() || !manager.hasPermission(device)) return
        selected = device; negotiated = false; sessionMode = mode()
        beforeRecovery()
        val plansBeforeOpen = FpvStreamPolicy.profiles(supported, mode())
        val requested = plansBeforeOpen.getOrNull(if (plansBeforeOpen.isEmpty()) 0 else profileIndex % plansBeforeOpen.size)
        requestedSize = requested?.let { Size(it.type, it.width, it.height, it.fps, listOf(it.fps)) }
        val token = ++generation
        connectingAt = SystemClock.elapsedRealtime()
        showMessage("Connecting FPV · ${mode().label}")
        try {
            val camera = CameraHelper(); helper = camera
            camera.setStateCallback(object : ICameraHelper.StateCallback {
                private fun current(d: UsbDevice, action: () -> Unit) { handler.post {
                    if (!disposed && generation == token && helper === camera && attachment(d) == selected?.let(::attachment)) action()
                } }
                override fun onAttach(d: UsbDevice) = Unit
                override fun onDeviceOpen(d: UsbDevice, isFirstOpen: Boolean) = current(d) {
                    deviceOpen = true
                    openRequested(camera)
                }
                override fun onCameraOpen(d: UsbDevice) = current(d) {
                    if (!foreground || !wanted) { closeSession(); return@current }
                    val actual = camera.previewSize
                    if (!negotiated) {
                        negotiated = true
                        supported = (camera.supportedSizeList ?: emptyList()).take(64).flatMap { size ->
                            val rates = size.fpsList?.takeIf { it.isNotEmpty() } ?: listOf(size.fps)
                            rates.take(16).map { FpvStreamPolicy.Profile(size.type, size.width, size.height, it) }
                        }
                        val plans = FpvStreamPolicy.profiles(supported, mode())
                        if (plans.isEmpty()) { connection.stop(); closeSession(); showMessage("Requested video mode unavailable · hold Connect FPV and choose Auto"); return@current }
                        val chosen = plans[profileIndex % plans.size]
                        if (actual == null || actual.type != chosen.type || actual.width != chosen.width || actual.height != chosen.height || actual.fps != chosen.fps) {
                            if (requested == chosen) { recover("Receiver rejected requested video mode"); return@current }
                            // A full generation change discards the library's USB-close callbacks.
                            closeSession(); connection.defer(SystemClock.elapsedRealtime() + 700)
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
                    beforeRecovery(); closeSession(); clearPermission(); connection.detached()
                    profileIndex = 0; supported = emptyList()
                    showMessage("USB receiver unplugged · reconnecting automatically when Android grants access")
                }
                override fun onCancel(d: UsbDevice) = current(d) {
                    connection.disallowPrompt()
                    recover("USB access changed · waiting for Android access")
                }
                override fun onError(d: UsbDevice, error: CameraException) = current(d) {
                    recover("Camera error ${error.code} · close other USB camera apps")
                }
            })
            camera.selectDevice(device)
        } catch (error: RuntimeException) { recover("USB camera error: ${error.javaClass.simpleName}") }
        catch (_: LinkageError) { connection.stop(); closeSession(); showMessage("USB video driver unavailable on this phone") }
    }

    private fun openRequested(camera: ICameraHelper) {
        if (!foreground || !wanted || !deviceOpen) return
        requestedSize?.let { camera.openCamera(it) } ?: camera.openCamera()
    }
    private fun recover(reason: String) {
        lastError = reason; beforeRecovery(); closeSession()
        val delay = connection.failed(SystemClock.elapsedRealtime())
        profileIndex = (profileIndex + 1) % 1024
        showMessage(if (delay >= 30000) "$reason · waiting 30 s before retry · controls disabled"
            else "$reason · retry ${connection.failures}/3 · controls disabled")
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
        helper?.stopPreview(); previewing = false; lastFrameAt = 0; previewAt = 0; connection.resetFrames()
    }
    fun setVisiblePreview(visible: Boolean) {
        if (visiblePreview == visible) return
        visiblePreview = visible
        if (visible) { startPreview(); if (wanted && helper == null) connection.defer(SystemClock.elapsedRealtime() + 500) }
        else { connection.disallowPrompt(); stopPreview(); showMessage("FPV preview paused · Show camera to resume") }
    }
    fun pause() { foreground = false; connection.disallowPrompt(); stopPreview(); showMessage("FPV paused") }
    fun suspendCamera() {
        reconnectAfterStop = wanted
        closeSession(); handler.removeCallbacks(tick); showMessage("FPV paused")
    }
    fun resume() {
        if (disposed) return
        foreground = true
        if (reconnectAfterStop && wanted) {
            reconnectAfterStop = false; beforeRecovery(); connection.defer(SystemClock.elapsedRealtime() + 700)
        }
        if (deviceOpen && !cameraOpen) helper?.let { openRequested(it) } else startPreview()
        handler.removeCallbacks(tick)
        if (wanted) handler.postDelayed(tick, 500)
    }
    private fun closeSession() {
        generation++; val old = helper; helper = null
        sessionMode = null; connection.resetFrames()
        if (old != null) releaseUntil = SystemClock.elapsedRealtime() + 700
        surface?.let { old?.removeSurface(it) }; surface = null
        old?.stopPreview(); old?.release(); selected = null; deviceOpen = false; requestedSize = null; cameraOpen = false; previewing = false
        connectingAt = 0; lastFrameAt = 0; previewAt = 0
    }
    fun close() {
        if (disposed) return
        disposed = true; connection.stop(); reconnectAfterStop = false
        clearPermission(); handler.removeCallbacks(tick); closeSession()
        if (receiverRegistered) { context.unregisterReceiver(permissionReceiver); receiverRegistered = false }
    }
    override fun onDetachedFromWindow() { close(); super.onDetachedFromWindow() }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); fitPreview() }
    private fun fitPreview() { FpvPreviewPolicy.fit(width, height, widthVideo, heightVideo)?.let {
        preview.layoutParams = LayoutParams(it.first, it.second, Gravity.CENTER)
    } }
    fun diagnosis(): String = usbDiagnosis(context) + "\n\nMode: ${mode().label}\nActive: $profileText\nRecovery: ${connection.failures}/3 · auto ${autoReconnect()}\nLast error: ${lastError.ifEmpty { "none" }}\nAdvertised: " +
        supported.take(24).joinToString { "${it.width}x${it.height}/${it.type}@${it.fps}" } +
        "\n\nUSB frames do not prove a clean RF picture. For colored lines, try YUYV; also check FPV channel, PAL/NTSC and camera power."
    companion object {
        // Shared across view recreation so a new view cannot immediately race old native release.
        private var releaseUntil = 0L
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
