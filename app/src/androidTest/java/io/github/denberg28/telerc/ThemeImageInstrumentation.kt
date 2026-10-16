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
import kotlin.math.*

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
            checkMazeLevels()
            ThemeImageStore.remove(app)
            check(!ThemeImageStore.exists(app))
            val activity = startActivitySync(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitUntil { onMain { root(activity).background !is ThemeImageDrawable } }
            screenshot("photo-removed.png")
            onMain { activity.finish() }
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS: native photo decode/import, corrupt/oversize rollback, three theme renders, Controls, persistence/removal, FPV format menu; PASS: native maze levels, three-bump restart, clear/unlock/next, progress persistence; PASS: persistent red bump pixels, pause/delay retention and reset removal\n") })
        } catch (error: Throwable) {
            val windows = runCatching { uiAutomation.windows.joinToString("\n") { window ->
                "Window ${window.type} active=${window.isActive}: " + nodeText(window.root)
            } }.getOrDefault("Accessibility unavailable")
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL: ${error.stackTraceToString()}\n$windows\n") })
        }
    }
    private fun checkMazeLevels() {
        val prefs=targetContext.getSharedPreferences("maze_practice",0)
        prefs.edit().clear().putString("records","37,1200,1000,2").commit()
        var activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun enterTest() {
            onMain { find(root(activity),"Setup ▾")!!.performClick() }
            waitUntil { windowText("Test")!=null }
            clickWindow("Test")
            waitUntil { onMain { findMaze(root(activity))!=null } }
        }
        enterTest()
        val scene=onMain { findMaze(root(activity))!! }
        onMain {
            check(scene.practice.maze.level==1)
            check(find(root(activity),"Lv 1 · 00:00.00")!=null)
            check(find(root(activity),"OFFLINE · Bumps 0/3")!=null)
            check(find(root(activity),"Shuffle maze")==null)
            check(scene.height>scene.width*.45f) { "Maze has not gained vertical space" }
            check(root(activity).background is ThemeImageDrawable)
            check(!targetContext.getSharedPreferences("link",0).getBoolean("auto_connect",true))
            find(root(activity),"Start practice")!!.performClick()
            val p=scene.practice
            p.drive(1900); repeat(350) { p.advance(20) }
            check(p.bumps==1 && p.state==MazePractice.State.RUNNING)
            scene.pause()
        }
        val firstMarks=onMain { scene.practice.bumpMarks }
        checkBumpPixel(scene)
        screenshot("maze-bump-dot.png")
        SystemClock.sleep(1200)
        onMain { scene.changed(); check(scene.practice.bumpMarks==firstMarks) }
        checkBumpPixel(scene)
        onMain {
            val p=scene.practice
            p.start()
            repeat(2) {
                p.drive(1100); repeat(28) { p.advance(20) }
                p.drive(1900); repeat(350) { p.advance(20) }
            }
            check(p.bumps==3 && p.state==MazePractice.State.FAILED)
            scene.changed()
        }
        waitUntil { windowText("Restart level 1?")!=null }
        screenshot("maze-three-bumps.png")
        clickWindow("Restart level")
        waitUntil { onMain { scene.practice.state==MazePractice.State.READY } }
        checkBumpPixel(scene,firstMarks.single(),expectRed=false)
        screenshot("maze-bump-reset.png")
        onMain {
            check(scene.practice.elapsedMillis==0L && scene.practice.bumps==0)
            check(scene.practice.bumpMarks.isEmpty())
            check(find(root(activity),"Lv 1 · 00:00.00")!=null)
            find(root(activity),"Start practice")!!.performClick()
            approachFinish(scene.practice)
            scene.changed() // The real animation frame reaches END and invokes the clear callback.
        }
        waitUntil { onMain { find(root(activity),"Next level")!=null } }
        check(prefs.getInt("completed_level",0)==1)
        check(MazeRecords.decode(prefs.getString("records","").orEmpty()).let { it.size==2 && it.first().level==1 && it.last().courseVersion==2 })
        screenshot("maze-level-cleared.png")
        onMain { find(root(activity),"Next level")!!.performClick() }
        waitUntil { onMain { find(root(activity),"Lv 2 · 00:00.00")!=null } }
        check(prefs.getInt("level",0)==2)
        screenshot("maze-level-two.png")
        onMain { activity.finish() }; waitForIdleSync()
        activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        enterTest()
        onMain {
            check(findMaze(root(activity))!!.practice.maze.level==2)
            check(findMaze(root(activity))!!.practice.bumpMarks.isEmpty())
            check(find(root(activity),"Lv 2 · 00:00.00")!=null)
            find(root(activity),"Levels")!!.performClick()
        }
        waitUntil { windowText("Levels · 2/20 unlocked")!=null }
        check(uiAutomation.windows.mapNotNull { it.root }.flatMap { it.findAccessibilityNodeInfosByText("Lv 3") }
            .none { it.packageName?.toString()==targetContext.packageName && it.isVisibleToUser }) { "A locked level was selectable" }
        clickWindow("Cancel")
        onMain { activity.finish() }; waitForIdleSync()
    }
    private fun checkBumpPixel(scene:MazePracticeView, mark:MazeBump?=null, expectRed:Boolean=true) = onMain {
        val point=mark ?: scene.practice.bumpMarks.first()
        val bitmap=Bitmap.createBitmap(scene.width,scene.height,Bitmap.Config.ARGB_8888)
        scene.draw(android.graphics.Canvas(bitmap))
        val maze=scene.practice.maze
        val scale=minOf(scene.width/maze.width.toFloat(),scene.height/maze.height.toFloat())
        val x=((scene.width-maze.width*scale)/2+point.x*scale).roundToInt()
        val y=((scene.height-maze.height*scale)/2+point.y*scale).roundToInt()
        val color=bitmap.getPixel(x.coerceIn(0,bitmap.width-1),y.coerceIn(0,bitmap.height-1))
        bitmap.recycle()
        val red=Color.red(color)>200 && Color.green(color)<100 && Color.blue(color)<100
        check(red==expectRed) { "Persistent bump pixel: expected red=$expectRed, got $color" }
    }
    private fun approachFinish(p:MazePractice) {
        val maze=p.maze; val start=1 to maze.height-2; val end=maze.endX.toInt() to maze.endY.toInt()
        val previous=mutableMapOf<Pair<Int,Int>,Pair<Int,Int>>()
        val seen=mutableSetOf(start); val queue=java.util.ArrayDeque<Pair<Int,Int>>().apply { add(start) }
        while(queue.isNotEmpty()) {
            val v=queue.removeFirst()
            for((dx,dy) in listOf(1 to 0,-1 to 0,0 to 1,0 to -1)) {
                val n=v.first+dx to v.second+dy
                if(!maze.wall(n.first,n.second) && seen.add(n)) { previous[n]=v; queue.add(n) }
            }
        }
        val path=mutableListOf(end)
        while(path.last()!=start) path.add(previous.getValue(path.last()))
        path.reverse()
        for(cell in path.drop(1)) {
            var frames=0
            val tolerance=if(cell==end) .43f else .12f
            while(hypot(cell.first+.5f-p.pose.x,cell.second+.5f-p.pose.y)>tolerance) {
                check(frames++<1500 && p.state==MazePractice.State.RUNNING)
                val angle=atan2(cell.first+.5f-p.pose.x,-(cell.second+.5f-p.pose.y))-p.pose.heading
                val error=atan2(sin(angle),cos(angle))
                p.steering((1500+error.coerceIn(-1f,1f)*400).roundToInt())
                p.drive(if(abs(error)<.15f) 1740 else 1500)
                check(!p.advance(20))
            }
        }
        check(p.state==MazePractice.State.RUNNING)
    }
    private fun findMaze(view:View):MazePracticeView? {
        if(view is MazePracticeView) return view
        if(view is ViewGroup) for(index in 0 until view.childCount) findMaze(view.getChildAt(index))?.let { return it }
        return null
    }
    private fun windowText(text:String):android.view.accessibility.AccessibilityNodeInfo? = uiAutomation.windows
        .mapNotNull { it.root }.flatMap { root ->
            root.findAccessibilityNodeInfosByText(text) + root.findAccessibilityNodeInfosByText(text.uppercase(java.util.Locale.ROOT))
        }
        .firstOrNull { it.packageName?.toString()==targetContext.packageName && it.isVisibleToUser && it.text?.toString()?.equals(text,ignoreCase=true)==true }
    private fun clickWindow(text:String) {
        var node=windowText(text) ?: error("Missing UI: $text")
        while(!node.isClickable && node.parent!=null) node=node.parent
        check(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
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
        waitForIdleSync()
        SystemClock.sleep(150) // Allow the new hierarchy's display frame to reach SurfaceFlinger.
        val bitmap = uiAutomation.takeScreenshot() ?: error("No native screenshot")
        File(targetContext.filesDir, name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
