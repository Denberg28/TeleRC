package io.github.denberg28.telerc

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.util.concurrent.FutureTask

/** Native navigation smoke test. It never opens a vehicle link. */
class CoreInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        try {
            val prefs = targetContext.getSharedPreferences("link", 0)
            prefs.edit().clear().putString("transport", "balance_wifi").putBoolean("auto_connect", false).commit()
            for (theme in listOf("LIGHT", "SEPIA", "DARK")) {
                targetContext.getSharedPreferences("appearance", 0).edit().putString("background", theme).putBoolean("setup_minimized", false).commit()
                val a = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                waitForIdleSync()
                val pageClass = MainActivity::class.java.declaredClasses.first { it.simpleName == "Page" }
                check(pageClass.enumConstants!!.map { it.toString() }.toSet() == setOf("SETUP", "CONTROLS", "TEST", "BALANCE", "LORA"))
                val method = MainActivity::class.java.getDeclaredMethod("switchTo", pageClass).apply { isAccessible = true }
                for ((page, label) in listOf("SETUP" to "Setup", "CONTROLS" to "Controls", "TEST" to "Test", "BALANCE" to "Balance", "LORA" to "LoRa setup")) {
                    main { method.invoke(a, pageClass.enumConstants!!.first { it.toString() == page }) }
                    waitForIdleSync(); SystemClock.sleep(200)
                    main {
                        val r = a.findViewById<ViewGroup>(android.R.id.content)
                        val menu = find(r) { it.contentDescription == "$label tab menu" } ?: error("Missing $label menu")
                        check(menu.isShown && menu.width > 0 && menu.height > 0)
                        val gate = MainActivity::class.java.getDeclaredField("controlEnabled").apply { isAccessible = true }.get(a) as LiveControlGate
                        check(!gate.get()) { "Navigation restored control authority" }
                        if (page == "BALANCE") check(find(r) { it is TextView && it.text.toString().contains("Disconnected") } != null)
                        if (page == "LORA") check(find(r) { it is TextView && it.text.toString() == "Read board settings" } != null)
                        menu.performClick()
                    }
                    waitForIdleSync(); SystemClock.sleep(100)
                    fun texts(n: android.view.accessibility.AccessibilityNodeInfo?): List<String> = if (n == null) emptyList() else
                        listOfNotNull(n.text?.toString()) + (0 until n.childCount).flatMap { texts(n.getChild(it)) }
                    val items = texts(uiAutomation.rootInActiveWindow)
                    check(items.containsAll(listOf("Setup", "Controls", "Test", "Balance", "LoRa setup"))) { "Missing navigation choices: $items" }
                    check("Waypoint" !in items && "Mission" !in items)
                    uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                    waitForIdleSync()
                }
                main { a.finish() }; waitForIdleSync()
            }
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS: five mission-free pages in three themes; native menus; disconnected Balance; LoRa provisioning; navigation never enables control\n") })
        } catch (t: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL: ${t.stackTraceToString()}\n") })
        }
    }
    private fun find(v: View, predicate: (View) -> Boolean): View? {
        if (predicate(v)) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), predicate)?.let { return it }
        return null
    }
    private fun <T> main(action: () -> T): T = FutureTask(action).also { runOnMainSync(it) }.get()
}
