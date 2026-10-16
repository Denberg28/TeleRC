package io.github.denberg28.telerc

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.io.File

/** Native bitmap/storage/render and FPV options checks; no USB hardware is simulated. */
class ThemeImageInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        try {
            val automation = uiAutomation
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            }
            val app = targetContext
            val input = File(app.cacheDir, "theme-test.png")
            val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
            for (y in 0 until 600) for (x in 0 until 800) bitmap.setPixel(x, y, if (x < 400) Color.rgb(230,80,60) else Color.rgb(40,160,210))
            input.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
            val imported = ThemeImageStore.importImage(app, Uri.fromFile(input))
            check(imported.width == 800 && imported.height == 600)
            val stored = File(app.filesDir, "theme-background.jpg")
            val previous = stored.readBytes()
            val invalid = File(app.cacheDir, "invalid.png").apply { writeText("not an image") }
            check(runCatching { ThemeImageStore.importImage(app, Uri.fromFile(invalid)) }.isFailure)
            check(stored.readBytes().contentEquals(previous)) { "Invalid import replaced the old photo" }
            val excessive = File(app.cacheDir, "excessive.png")
            excessive.outputStream().use { out -> val bytes = ByteArray(65536); repeat(321) { out.write(bytes) } }
            check(runCatching { ThemeImageStore.importImage(app, Uri.fromFile(excessive)) }.isFailure)
            check(stored.readBytes().contentEquals(previous)) { "Oversized import replaced the old photo" }
            excessive.delete()
            for (theme in listOf("LIGHT", "SEPIA", "DARK")) {
                app.getSharedPreferences("appearance", 0).edit().putString("background", theme).commit()
                val activity = startActivitySync(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                waitUntil { onMain { root(activity).background is ThemeImageDrawable } }
                screenshot("photo-${theme.lowercase()}-setup.png")
                onMain { find(root(activity), "Controls ›")!!.performClick() }
                waitUntil { onMain { root(activity).background is ThemeImageDrawable && find(root(activity), "Connect FPV") != null } }
                screenshot("photo-${theme.lowercase()}-controls.png")
                onMain { find(root(activity), "Connect FPV")!!.performLongClick() }
                waitUntil { dismissLauncherAnr(); videoOption() != null }
                var option = videoOption() ?: error("FPV compatibility option missing")
                while (!option.isClickable && option.parent != null) option = option.parent
                check(option.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
                waitUntil { app.getSharedPreferences("fpv", 0).getString("mode", "") == "YUYV" }
                onMain { activity.finish() }
                waitForIdleSync()
                check(ThemeImageStore.exists(app)) { "Background did not persist across activity restart" }
            }
            ThemeImageStore.remove(app)
            check(!ThemeImageStore.exists(app))
            val activity = startActivitySync(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitUntil { onMain { root(activity).background !is ThemeImageDrawable } }
            screenshot("photo-removed.png")
            onMain { activity.finish() }
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS: native photo decode/import, corrupt/oversize rollback, three theme renders, Controls, persistence/removal, FPV format menu\n") })
        } catch (error: Throwable) {
            val windows = runCatching { uiAutomation.windows.joinToString("\n") { window ->
                "Window ${window.type} active=${window.isActive}: " + nodeText(window.root)
            } }.getOrDefault("Accessibility unavailable")
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL: ${error.stackTraceToString()}\n$windows\n") })
        }
    }
    private fun videoOption(): android.view.accessibility.AccessibilityNodeInfo? = uiAutomation.windows
        .mapNotNull { it.root }.flatMap { it.findAccessibilityNodeInfosByText("YUYV compatibility") }
        .firstOrNull { it.packageName?.toString() == targetContext.packageName && it.isVisibleToUser }
    private fun nodeText(node: android.view.accessibility.AccessibilityNodeInfo?): String {
        if (node == null) return "null"
        return "${node.packageName}:${node.text}" + (0 until node.childCount).joinToString(" ") { nodeText(node.getChild(it)) }
    }
    private fun dismissLauncherAnr() {
        val root = uiAutomation.rootInActiveWindow ?: return
        if (root.packageName?.toString() == "android" && root.findAccessibilityNodeInfosByText("Pixel Launcher isn't responding").isNotEmpty()) {
            root.findAccessibilityNodeInfosByViewId("android:id/aerr_close").firstOrNull()
                ?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
        }
    }
    private fun root(activity: Activity) = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
    private fun find(view: View, text: String): TextView? {
        if (view is TextView && view.text.toString() == text) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index), text)?.let { return it }
        return null
    }
    private fun <T> onMain(action: () -> T): T {
        var value: T? = null
        runOnMainSync { value = action() }
        @Suppress("UNCHECKED_CAST") return value as T
    }
    private fun waitUntil(condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 12000
        while (!condition()) { check(SystemClock.elapsedRealtime() < end) { "Native UI condition timed out" }; SystemClock.sleep(150) }
    }
    private fun screenshot(name: String) {
        val bitmap = uiAutomation.takeScreenshot() ?: error("No native screenshot")
        File(targetContext.filesDir, name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
