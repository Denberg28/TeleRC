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
            checkMarket()
            checkPracticeDialogLayouts()
            ThemeImageStore.remove(app)
            check(!ThemeImageStore.exists(app))
            val activity = startActivitySync(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitUntil { onMain { root(activity).background !is ThemeImageDrawable } }
            screenshot("photo-removed.png")
            onMain { activity.finish() }
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS: native photo decode/import, corrupt/oversize rollback, three theme renders, Controls, persistence/removal, FPV format menu; PASS: native maze levels, three-bump restart, clear/unlock/next, progress persistence; PASS: persistent red bump pixels, pause/delay retention and reset removal; PASS: native market purchase/equip/insufficient funds, coin collection and wallet persistence; PASS: padded practice dialogs, three themes and large font, level selection/restart and version footer\n") })
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
            check(find(root(activity),"TeleRC v${BuildConfig.VERSION_NAME}")!=null)
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
        waitUntil { windowText("2 levels unlocked · Select a level")!=null }
        screenshot("maze-levels-dialog.png")
        check(uiAutomation.windows.mapNotNull { it.root }.flatMap { it.findAccessibilityNodeInfosByText("Level 3") }
            .none { it.packageName?.toString()==targetContext.packageName && it.isVisibleToUser }) { "A locked level was selectable" }
        clickWindow("Done")
        onMain { activity.finish() }; waitForIdleSync()
    }
    private fun checkMarket() {
        val app=targetContext
        val prefs=app.getSharedPreferences("maze_practice",0)
        var fixture=MazeWallet.decode("")
        for(level in 2..5) for(slot in 0 until MazeMarket.quota(level)) fixture=fixture.claim(level,slot)!!
        for(slot in 0..2) fixture=fixture.claim(6,slot)!!
        check(fixture.balance==35)
        prefs.edit().clear().putString("wallet",fixture.encode()).commit()
        var activity=startActivitySync(Intent(app,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun enter() {
            onMain { find(root(activity),"Setup ▾")!!.performClick() }
            waitUntil { windowText("Test")!=null }; clickWindow("Test")
            waitUntil { onMain { findMaze(root(activity))!=null } }
        }
        enter()
        val scene=onMain { findMaze(root(activity))!! }
        check(onMain { scene.practice.coins.size }==8)
        screenshot("maze-coins.png")
        onMain { find(root(activity),"Market · 35")!!.performClick() }
        waitUntil { windowText("Market · offline cosmetics")!=null }
        check(onMain { scene.practice.state!=MazePractice.State.RUNNING })
        showMarketItem("Buy Rally · 20")
        screenshot("market-vehicles.png")
        clickWindow("Buy Rally · 20")
        waitUntil { MazeWallet.decode(prefs.getString("wallet","").orEmpty()).vehicle=="rally" }
        check(MazeWallet.decode(prefs.getString("wallet","").orEmpty()).balance==15)
        showMarketItem("Buy Hauler · 50")
        check(windowText("Buy Hauler · 50")?.isEnabled==false)
        showMarketItem("Buy Forest · 15"); clickWindow("Buy Forest · 15")
        waitUntil { MazeWallet.decode(prefs.getString("wallet","").orEmpty()).theme=="forest" }
        check(MazeWallet.decode(prefs.getString("wallet","").orEmpty()).balance==0)
        showMarketItem("Forest · Equipped")
        screenshot("market-colors.png")
        clickWindow("Done")
        onMain { check(scene.vehicleId=="rally" && scene.themeId=="forest") }
        screenshot("maze-equipped.png")
        val target=onMain { scene.practice.coins.first() }
        onMain {
            find(root(activity),"Start practice")!!.performClick()
            approachFinish(scene.practice,target.x.toInt() to target.y.toInt(),.60f)
            scene.changed()
        }
        val targetKey=MazeMarket.key(1,target.slot)!!
        waitUntil { targetKey in MazeWallet.decode(prefs.getString("wallet","").orEmpty()).claimed }
        val earned=MazeWallet.decode(prefs.getString("wallet","").orEmpty())
        check(earned.balance in 1..8)
        onMain {
            scene.pause()
            check(scene.practice.coins.none { it.slot==target.slot })
            scene.practice.reset(); scene.changed()
            check(scene.practice.coins.none { MazeMarket.key(1,it.slot) in earned.claimed })
        }
        check(MazeWallet.decode(prefs.getString("wallet","").orEmpty()).encode()==earned.encode())
        onMain { activity.finish() }; waitForIdleSync()
        activity=startActivitySync(Intent(app,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        enter()
        onMain {
            val restored=findMaze(root(activity))!!
            check(restored.vehicleId=="rally" && restored.themeId=="forest")
            check(restored.practice.coins.none { MazeMarket.key(1,it.slot) in earned.claimed })
            check(find(root(activity),"Market · ${earned.balance}")!=null)
        }
        check(MazeWallet.decode(prefs.getString("wallet","").orEmpty()).encode()==earned.encode())
        onMain { activity.finish() }; waitForIdleSync()
    }
    private fun showMarketItem(text:String) {
        repeat(12) {
            if(windowText(text)!=null) return
            val roots=uiAutomation.windows.mapNotNull { it.root }.filter { it.packageName?.toString()==targetContext.packageName }
            val node=roots.flatMap { root -> root.findAccessibilityNodeInfosByText(text)+
                root.findAccessibilityNodeInfosByText(text.uppercase(java.util.Locale.ROOT)) }
                .firstOrNull { it.text?.toString()?.equals(text,ignoreCase=true)==true || it.contentDescription?.toString()?.startsWith("$text,",ignoreCase=true)==true }
            node?.performAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
            SystemClock.sleep(150)
            if(windowText(text)!=null) return
            fun scrollable(n:android.view.accessibility.AccessibilityNodeInfo):android.view.accessibility.AccessibilityNodeInfo? {
                if(n.isScrollable) return n
                for(i in 0 until n.childCount) n.getChild(i)?.let { scrollable(it)?.let { found -> return found } }
                return null
            }
            roots.firstNotNullOfOrNull(::scrollable)?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            SystemClock.sleep(150)
        }
        error("Market item not visible after scrolling: $text")
    }
    private fun checkPracticeDialogLayouts() {
        // Exercise production layout at the system's larger font size, with all 20 levels.
        fun font(scale:String) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("settings put system font_scale $scale"))
                .use { it.readBytes() }
        }
        font("1.3")
        waitUntil { targetContext.resources.configuration.fontScale>=1.25f }
        try {
            for(theme in listOf("LIGHT","SEPIA","DARK")) {
                targetContext.getSharedPreferences("appearance",0).edit().putString("background",theme).commit()
                val activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                waitForIdleSync()
                var chosen=0
                val levels=onMain { MazePracticeDialogs(activity).levels(MazeProgress.restore(1,20),
                    listOf(MazeClear(37,65430,1000,3,1))) { chosen=it } }
                waitUntil { windowText("20 levels unlocked · Select a level")!=null }
                checkDialogButton(activity,levels,"Done")
                screenshot("dialogs-${theme.lowercase()}-levels.png")
                showMarketItem("Level 20"); clickWindow("Level 20")
                waitUntil { onMain { chosen==20 } }
                var restarted=false
                val restart=onMain { MazePracticeDialogs(activity).restart(20,75640,3) { restarted=true } }
                waitUntil { windowText("Restart level 20?")!=null }
                checkDialogButton(activity,restart,"Restart level")
                screenshot("dialogs-${theme.lowercase()}-restart.png")
                clickWindow("Restart level")
                waitUntil { onMain { restarted } }
                onMain { activity.finish() }; waitForIdleSync()
            }
        } finally { font("1.0") }
    }
    private fun checkDialogButton(activity:Activity,dialog:android.app.Dialog,text:String) {
        waitUntil { onMain {
            val button=find(dialog.window!!.decorView,text)
            val bounds=android.graphics.Rect()
            button!=null && button.height>0 && button.getGlobalVisibleRect(bounds) &&
                bounds.height()>=button.height-1 && button.height>=48*activity.resources.displayMetrics.density-1
        } }
        check(activity.resources.configuration.fontScale>=1.25f) { "Large font was not applied" }
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
    private fun approachFinish(p:MazePractice,target:Pair<Int,Int>?=null,lastTolerance:Float=.43f) {
        val maze=p.maze; val start=p.pose.x.toInt() to p.pose.y.toInt()
        val end=target ?: (maze.endX.toInt() to maze.endY.toInt())
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
            val tolerance=if(cell==end) lastTolerance else .12f
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
        .firstOrNull { it.packageName?.toString()==targetContext.packageName && it.isVisibleToUser && (it.text?.toString()?.equals(text,ignoreCase=true)==true || it.contentDescription?.toString()?.startsWith("$text,",ignoreCase=true)==true) }
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
