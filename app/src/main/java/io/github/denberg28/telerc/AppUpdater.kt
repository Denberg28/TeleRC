package io.github.denberg28.telerc

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.concurrent.thread

/** Manual update path for public GitHub releases. No credential is stored in the APK. */
class AppUpdater(private val activity: Activity, private val report: (String) -> Unit) {
    private val downloads = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val apk = File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "telerc-update.apk")
    private var downloadId = -1L
    private var pendingInstall: Uri? = null
    private var expectedHash: String? = null
    private var receiverRegistered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE ||
                intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != downloadId) return
            val uri = downloads.getUriForDownloadedFile(downloadId)
            if (uri == null) { report("Update download failed. Try again."); return }
            thread(name = "telerc-verify-update") {
                val valid = runCatching { verifyApk() }.getOrDefault(false)
                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    if (valid) { pendingInstall = uri; offerInstall() }
                    else report("Update verification failed. APK discarded.")
                }
            }
        }
    }

    fun check() {
        report("Checking for updates…")
        thread(name = "telerc-check-update") {
            try {
                val connection = URL("https://api.github.com/repos/Denberg28/TeleRC/releases/latest")
                    .openConnection() as HttpURLConnection
                connection.connectTimeout = 8000; connection.readTimeout = 8000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "TeleRC-Android")
                try {
                    if (connection.responseCode == 404) {
                        activity.runOnUiThread { report("No public APK release is available yet.") }
                        return@thread
                    }
                    if (connection.responseCode != 200) error("Release check failed: HTTP ${connection.responseCode}")
                    val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    val tag = release.getString("tag_name").removePrefix("v")
                    if (compareVersion(tag, BuildConfig.VERSION_NAME) <= 0) {
                        activity.runOnUiThread { report("TeleRC is up to date (${BuildConfig.VERSION_NAME}).") }
                        return@thread
                    }
                    val assets = release.getJSONArray("assets")
                    var url: String? = null; var digest: String? = null
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.getString("name")
                        val hash = asset.optString("digest")
                        if (name.startsWith("TeleRC-v") && name.endsWith(".apk") &&
                            hash.startsWith("sha256:") && hash.length == 71) {
                            url = asset.getString("browser_download_url")
                            digest = hash.removePrefix("sha256:").lowercase()
                            break
                        }
                    }
                    if (url == null || digest == null) {
                        activity.runOnUiThread { report("Release $tag has no verified TeleRC APK.") }
                        return@thread
                    }
                    val downloadUrl = url ?: return@thread
                    val hash = digest ?: return@thread
                    activity.runOnUiThread {
                        if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                        AlertDialog.Builder(activity).setTitle("TeleRC $tag available")
                            .setMessage("Download the verified APK and open Android’s installer? Control will disconnect during installation.")
                            .setNegativeButton("Later", null)
                            .setPositiveButton("Download") { _, _ -> download(downloadUrl, hash) }
                            .show()
                        report("Version $tag available.")
                    }
                } finally { connection.disconnect() }
            } catch (e: Exception) {
                activity.runOnUiThread { report("Could not check updates. Check internet access.") }
            }
        }
    }
    private fun download(url: String, hash: String) {
        val parsed = Uri.parse(url)
        if (parsed.scheme != "https" || parsed.host != "github.com" ||
            !parsed.path.orEmpty().startsWith("/Denberg28/TeleRC/releases/download/")) {
            report("Update URL was rejected."); return
        }
        try {
            if (downloadId != -1L) downloads.remove(downloadId)
            if (apk.exists()) apk.delete()
            expectedHash = hash
            val request = DownloadManager.Request(parsed)
                .setTitle("TeleRC update")
                .setDescription("Downloading a verified TeleRC APK")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, apk.name)
            downloadId = downloads.enqueue(request)
            if (!receiverRegistered) {
                val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                if (Build.VERSION.SDK_INT >= 33) activity.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                else activity.registerReceiver(receiver, filter)
                receiverRegistered = true
            }
            report("Downloading TeleRC update…")
        } catch (e: Exception) { report("Could not start the update download.") }
    }
    private fun verifyApk(): Boolean {
        val expected = expectedHash ?: return false
        if (!apk.isFile || apk.length() !in 100_000L..100_000_000L) return false
        val digest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().buffered().use { input ->
            val bytes = ByteArray(8192)
            while (true) { val n = input.read(bytes); if (n < 0) break; digest.update(bytes, 0, n) }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        if (actual != expected) { apk.delete(); return false }
        val info = activity.packageManager.getPackageArchiveInfo(apk.absolutePath, 0) ?: return false
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        return info.packageName == activity.packageName && code > BuildConfig.VERSION_CODE
    }
    fun resumePendingInstall() {
        if (pendingInstall != null && activity.packageManager.canRequestPackageInstalls()) offerInstall()
    }
    private fun offerInstall() {
        val uri = pendingInstall ?: return
        if (!activity.packageManager.canRequestPackageInstalls()) {
            report("Allow TeleRC to install updates, then return here.")
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}")))
            return
        }
        pendingInstall = null
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                clipData = ClipData.newUri(activity.contentResolver, "TeleRC update", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(intent)
        } catch (e: Exception) { report("Android could not open the APK installer.") }
    }
    fun close() {
        if (receiverRegistered) { activity.unregisterReceiver(receiver); receiverRegistered = false }
    }
}

/** Numeric dotted release tags only; unknown tags never count as upgrades. */
internal fun compareVersion(candidate: String, current: String): Int {
    fun parts(value: String): List<Int>? {
        if (!Regex("[0-9]+(\\.[0-9]+){1,3}").matches(value)) return null
        return value.split('.').map { it.toIntOrNull() ?: return null }
    }
    val a = parts(candidate) ?: return -1
    val b = parts(current) ?: return -1
    for (i in 0 until maxOf(a.size, b.size)) {
        val difference = (a.getOrElse(i) { 0 }).compareTo(b.getOrElse(i) { 0 })
        if (difference != 0) return difference
    }
    return 0
}
