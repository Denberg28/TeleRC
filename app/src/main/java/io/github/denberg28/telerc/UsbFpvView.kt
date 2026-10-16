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
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.herohan.uvcapp.CameraException
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper

/** USB UVC capture is independent of MAVLink, Wi-Fi and the USB CDC LoRa link. */
class UsbFpvView(context: Context) : FrameLayout(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val preview = TextureView(context)
    private val status = TextView(context).apply {
        setTextColor(Color.WHITE); textSize = 12f; gravity = Gravity.CENTER
        setPadding(8, 8, 8, 8)
    }
    private var helper: ICameraHelper? = null
    private var generation = 0
    private var selected: UsbDevice? = null
    private var deviceOpen = false
    private var cameraOpen = false
    private var foreground = true
    private var visiblePreview = false
    private var videoWidth = 640
    private var videoHeight = 480
    private var lastFrameAt = 0L
    private var connectingAt = 0L
    private var lastMessage = "Connect a USB UVC FPV receiver via Type-C OTG, then tap Connect FPV"
    private val health = object : Runnable {
        override fun run() {
            if (helper == null) return
            if (foreground && visiblePreview) {
                val now = SystemClock.elapsedRealtime()
                if (cameraOpen && (lastFrameAt == 0L || !FpvPreviewPolicy.frameFresh(lastFrameAt, now)))
                    showMessage("No current video frames · check receiver signal / reconnect FPV")
                else if (!cameraOpen && connectingAt != 0L && now - connectingAt > 15000)
                    showMessage("No video opened · check USB permission / receiver, then reconnect FPV")
            }
            handler.postDelayed(this, 500)
        }
    }

    init {
        setBackgroundColor(Color.BLACK)
        addView(preview, LayoutParams(-1, -1, Gravity.CENTER))
        addView(status, LayoutParams(-1, -1))
        preview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                if (cameraOpen && foreground && visiblePreview) helper?.addSurface(surface, false)
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                helper?.removeSurface(surface)
                lastFrameAt = 0L
                return true
            }
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                if (!cameraOpen || !foreground || !visiblePreview) return
                lastFrameAt = SystemClock.elapsedRealtime()
                status.text = "USB FPV · ${videoWidth}×${videoHeight}"
                status.gravity = Gravity.TOP or Gravity.START
                status.setBackgroundColor(Color.TRANSPARENT)
            }
        }
        showMessage(lastMessage)
    }

    private fun showMessage(message: String) {
        lastMessage = message
        status.text = message
        status.gravity = Gravity.CENTER
        // Obscure any retained TextureView image when the receiver has stopped.
        status.setBackgroundColor(Color.BLACK)
    }

    /** Call only after the Activity neutralizes controls and grants CAMERA permission. */
    fun connectCamera() {
        if (!foreground || context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        close()
        val attached = (context.getSystemService(Context.USB_SERVICE) as UsbManager).deviceList.values.toList()
        val devices = attached.filter(::isVideoDevice).sortedBy { it.deviceName }
        if (devices.size != 1) {
            showMessage(when {
                attached.isEmpty() -> "Android sees no USB device · enable OTG, check data cable / receiver power, then reconnect"
                devices.isEmpty() -> "USB connected, but no UVC video interface · hold Connect FPV for receiver details"
                else -> "Multiple UVC receivers found · leave one connected, then retry"
            })
            return
        }
        selected = devices.single()
        val token = ++generation
        showMessage("Connecting USB FPV · approve USB access if requested")
        connectingAt = SystemClock.elapsedRealtime()
        try {
            val camera = CameraHelper()
            helper = camera
            camera.setStateCallback(object : ICameraHelper.StateCallback {
                private fun current(device: UsbDevice, action: () -> Unit) {
                    handler.post {
                        if (generation == token && helper === camera && device.deviceName == selected?.deviceName) action()
                    }
                }
                // Never prompt for USB access from an unsolicited attachment callback.
                override fun onAttach(device: UsbDevice) = Unit
                override fun onDeviceOpen(device: UsbDevice, isFirstOpen: Boolean) = current(device) {
                    deviceOpen = true
                    if (foreground) camera.openCamera()
                }
                override fun onCameraOpen(device: UsbDevice) = current(device) {
                    cameraOpen = true
                    connectingAt = 0L
                    camera.previewSize?.let { videoWidth = it.width; videoHeight = it.height }
                    fitPreview()
                    if (foreground && visiblePreview) startPreview()
                }
                override fun onCameraClose(device: UsbDevice) = current(device) {
                    cameraOpen = false; lastFrameAt = 0L
                    showMessage("FPV camera closed · tap Connect FPV to retry")
                }
                override fun onDeviceClose(device: UsbDevice) = current(device) {
                    deviceOpen = false; cameraOpen = false; lastFrameAt = 0L
                    showMessage("FPV receiver closed · tap Connect FPV to retry")
                }
                override fun onDetach(device: UsbDevice) = current(device) {
                    close()
                    showMessage("FPV receiver disconnected · reconnect USB, then tap Connect FPV")
                }
                override fun onCancel(device: UsbDevice) = current(device) {
                    close()
                    showMessage("USB camera permission denied · tap Connect FPV to retry")
                }
                override fun onError(device: UsbDevice, error: CameraException) = current(device) {
                    close()
                    showMessage("FPV receiver could not open · close other USB camera apps and reconnect; hold Connect FPV for details")
                }
            })
            camera.selectDevice(devices.single())
            handler.post(health)
        } catch (_: RuntimeException) {
            close()
            showMessage("USB FPV unavailable · reconnect receiver and retry")
        } catch (_: LinkageError) {
            close()
            showMessage("USB video driver unavailable on this phone")
        }
    }

    private fun startPreview() {
        if (!cameraOpen || !foreground || !visiblePreview) return
        lastFrameAt = 0L
        showMessage("Waiting for USB FPV frames…")
        helper?.startPreview()
        preview.surfaceTexture?.let { helper?.addSurface(it, false) }
    }

    fun setVisiblePreview(visible: Boolean) {
        if (visiblePreview == visible) return
        visiblePreview = visible
        if (visible) startPreview() else {
            preview.surfaceTexture?.let { helper?.removeSurface(it) }
            helper?.stopPreview()
            lastFrameAt = 0L
            showMessage("FPV preview paused · tap Show camera to resume")
        }
    }

    fun pause() {
        foreground = false
        helper?.stopPreview()
        lastFrameAt = 0L
        showMessage("FPV paused")
    }

    fun resume() {
        foreground = true
        if (deviceOpen && !cameraOpen) helper?.openCamera() else startPreview()
    }

    fun close() {
        generation++ // Discard queued callbacks from the previous USB session.
        handler.removeCallbacks(health)
        val old = helper
        helper = null; selected = null; cameraOpen = false; deviceOpen = false
        lastFrameAt = 0L; connectingAt = 0L
        preview.surfaceTexture?.let { old?.removeSurface(it) }
        old?.release()
        showMessage("FPV stopped · tap Connect FPV to connect")
    }

    override fun onDetachedFromWindow() { close(); super.onDetachedFromWindow() }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); fitPreview() }
    private fun fitPreview() {
        if (width <= 0 || height <= 0) return
        val size = FpvPreviewPolicy.fit(width, height, videoWidth, videoHeight) ?: return
        preview.layoutParams = LayoutParams(size.first, size.second, Gravity.CENTER)
    }

    companion object {
        fun isVideoDevice(device: UsbDevice): Boolean = UsbVideoPolicy.isVideo(
            device.deviceClass, (0 until device.interfaceCount).map { device.getInterface(it).interfaceClass })

        fun usbDiagnosis(context: Context): String {
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val devices = manager.deviceList.values.sortedBy { it.deviceName }.take(8)
            val cameraGranted = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            val details = devices.joinToString("\n\n") { device ->
                val classes = (0 until device.interfaceCount).map { device.getInterface(it).interfaceClass }
                "VID:PID %04X:%04X".format(java.util.Locale.US, device.vendorId, device.productId) +
                    " · class ${device.deviceClass} · interfaces ${classes.joinToString()}" +
                    "\nUVC: ${isVideoDevice(device)} · USB permission: ${manager.hasPermission(device)}"
            }
            return "Camera permission: $cameraGranted\nUSB devices: ${manager.deviceList.size}\n\n" +
                details.ifEmpty { "Android has not enumerated a USB device. Check OTG, a data-capable cable and receiver power." } +
                "\n\nIf another camera app opens automatically, clear that app's Open by default settings, " +
                "then unplug/replug and select TeleRC. Close other USB camera apps before connecting. " +
                "A default-app selection does not prove video-format compatibility."
        }
    }
}
