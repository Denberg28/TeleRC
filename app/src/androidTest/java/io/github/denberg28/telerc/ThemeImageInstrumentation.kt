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
                var activity = startActivitySync(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                waitUntil { onMain { root(activity).background is ThemeImageDrawable } }
                checkWallpaper(activity)
                checkTabMenu(activity,"Setup","tabs-${theme.lowercase()}-setup.png")
                screenshot("photo-${theme.lowercase()}-setup.png")
                activity = checkSetupCollapse(activity,theme)
                onMain { find(root(activity), "Controls ›")!!.performClick() }
                waitUntil { onMain { root(activity).background is ThemeImageDrawable && find(root(activity), "Connect FPV") != null } }
                checkWallpaper(activity); checkJoystickPanels(activity)
                checkTabMenu(activity,"Controls","tabs-${theme.lowercase()}-controls.png")
                screenshot("photo-${theme.lowercase()}-controls.png")
                onMain { find(root(activity), "Connect FPV")!!.performLongClick() }
                waitUntil { dismissLauncherAnr(); videoOption() != null }
                var option = videoOption() ?: error("FPV compatibility option missing")
                while (!option.isClickable && option.parent != null) option = option.parent
                check(option.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
                waitUntil { app.getSharedPreferences("fpv", 0).getString("mode", "") == "YUYV" }
                onMain { find(root(activity),"Controls ▾")!!.performClick() }
                waitUntil { windowText("Test")!=null }; clickWindow("Test")
                waitUntil { onMain { findMaze(root(activity))!=null } }
                checkWallpaper(activity); checkJoystickPanels(activity)
                checkTabMenu(activity,"Test","tabs-${theme.lowercase()}-test.png")
                screenshot("photo-${theme.lowercase()}-test.png")
                onMain { find(root(activity),"Test ▾")!!.performClick() }
                waitUntil { windowText("Setup")!=null }; clickWindow("Setup")
                waitUntil { onMain { description(root(activity),"Show setup panels")!=null } }
                checkSetupCorners(activity)
                onMain { description(root(activity),"Show setup panels")!!.performClick() }
                onMain { activity.finish() }
                waitForIdleSync()
                check(ThemeImageStore.exists(app)) { "Background did not persist across activity restart" }
            }
            checkThemeTransitions(photo = true)
            checkWallpaperOpacity()
            checkMazeLevels()
            checkMarket()
            checkPracticeDialogLayouts()
            ThemeImageStore.remove(app)
            check(!ThemeImageStore.exists(app))
            checkThemeTransitions(photo = false)
            val activity = startActivitySync(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitUntil { onMain { root(activity).background !is ThemeImageDrawable } }
            screenshot("photo-removed.png")
            onMain { activity.finish() }
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS: native photo decode/import, corrupt/oversize rollback, three theme renders, Controls, persistence/removal, FPV format menu; PASS: native maze levels, three-bump restart, clear/unlock/next, progress persistence; PASS: persistent red bump pixels, pause/delay retention and reset removal; PASS: native market purchase/equip/insufficient funds, coin collection and wallet persistence; PASS: flat practice dialogs, three themes and large font, level selection/restart, market tabs/purchases and expanded console geometry; PASS: wallpaper fidelity and opacity preview/apply/cancel/endpoints/persistence, transparent joystick panels, setup collapse/restore/corners/persistence and stable centered popups; PASS: sharp joystick captions, contrast and equal alignment, button-width tab menus across themes and font sizes; PASS: live theme transitions, repeated Light/Sepia/Dark and accent round trips, no Activity/session interruption or missing wallpaper draw frames, draft preservation, native dialog palette reset and compact menu rows\n") })
        } catch (error: Throwable) {
            val windows = runCatching { uiAutomation.windows.joinToString("\n") { window ->
                "Window ${window.type} active=${window.isActive}: " + nodeText(window.root)
            } }.getOrDefault("Accessibility unavailable")
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL: ${error.stackTraceToString()}\n$windows\n") })
        }
    }
    private fun checkThemeTransitions(photo:Boolean) {
        val prefs=targetContext.getSharedPreferences("appearance",0)
        prefs.edit().putString("background","LIGHT").putString("accent","Blue")
            .putBoolean("setup_minimized",false).commit()
        targetContext.getSharedPreferences("link",0).edit().putString("transport","wifi").putBoolean("auto_connect",false).commit()
        val activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitUntil { onMain { (root(activity).background is ThemeImageDrawable)==photo && root(activity).width>0 } }
        val bitmapField=MainActivity::class.java.getDeclaredField("themeBitmap").apply { isAccessible=true }
        val cached=onMain { bitmapField.get(activity) }
        var frames=0
        var failure:String?=null
        var interruptions=0
        val callbacks=object:android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(a:Activity,b:Bundle?) { if(a is MainActivity) interruptions++ }
            override fun onActivityPaused(a:Activity) { if(a===activity) interruptions++ }
            override fun onActivityDestroyed(a:Activity) { if(a===activity) interruptions++ }
            override fun onActivityStarted(a:Activity) {}
            override fun onActivityResumed(a:Activity) {}
            override fun onActivityStopped(a:Activity) {}
            override fun onActivitySaveInstanceState(a:Activity,b:Bundle) {}
        }
        val observer=android.view.ViewTreeObserver.OnPreDrawListener {
            frames++
            if((root(activity).background is ThemeImageDrawable)!=photo) failure="Wallpaper disappeared during a theme frame"
            if(bitmapField.get(activity)!==cached) failure="Theme change reloaded the wallpaper"
            true
        }
        onMain {
            activity.application.registerActivityLifecycleCallbacks(callbacks)
            activity.window.decorView.viewTreeObserver.addOnPreDrawListener(observer)
            (description(root(activity),"Bridge IPv4 address") as android.widget.EditText).setText("192.168.4.42")
            (description(root(activity),"UDP port") as android.widget.EditText).setText("14551")
        }
        try {
            for(name in listOf("LIGHT","SEPIA","DARK","SEPIA","LIGHT","DARK","DARK","LIGHT")) {
                onMain { description(root(activity),"Choose Light, Sepia or Dark background")!!.performClick() }
                val label=BackgroundTheme.valueOf(name).label
                waitUntil { windowText(label)!=null }
                clickWindow(label)
                waitUntil { onMain { find(root(activity),"${if(photo) "Photo" else "Background"} · $label ▾")!=null } && windowText("Cancel")==null }
                waitForIdleSync()
                onMain {
                    check(!activity.isDestroyed && !activity.isFinishing && interruptions==0) { "Appearance interrupted the Activity/session" }
                    check(prefs.getString("background",null)==name)
                    check((description(root(activity),"Bridge IPv4 address") as TextView).text.toString()=="192.168.4.42")
                    check((description(root(activity),"UDP port") as TextView).text.toString()=="14551")
                    val attributes=activity.obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
                    val primary=try { attributes.getColorStateList(0)!!.defaultColor } finally { attributes.recycle() }
                    check((Color.red(primary)>128)==(name=="DARK")) { "Native dialog theme retained old text attributes" }
                    check(!targetContext.getSharedPreferences("link",0).getBoolean("auto_connect",false))
                    check(failure==null) { failure.orEmpty() }
                }
                if(photo) checkWallpaper(activity)
                if(name=="DARK" || name=="SEPIA") screenshot("transition-${if(photo) "photo" else "plain"}-${name.lowercase()}.png")
            }
            for(accent in listOf("Teal","Violet","Slate","Navy","Navy")) {
                onMain { (description(root(activity),"$accent accent") ?: description(root(activity),"$accent accent, selected"))!!.performClick() }
                waitForIdleSync()
                onMain {
                    check(description(root(activity),"$accent accent, selected")!=null)
                    check(bitmapField.get(activity)===cached && !activity.isDestroyed && interruptions==0)
                    check((description(root(activity),"Bridge IPv4 address") as TextView).text.toString()=="192.168.4.42")
                    check(failure==null) { failure.orEmpty() }
                }
            }
            check(onMain { frames>8 }) { "No transition draw frames observed" }
        } finally {
            onMain {
                activity.window.decorView.viewTreeObserver.removeOnPreDrawListener(observer)
                activity.application.unregisterActivityLifecycleCallbacks(callbacks)
                activity.finish()
            }
            waitForIdleSync()
        }
    }
    private fun checkWallpaper(activity:Activity) {
        waitUntil { onMain {
            val view=root(activity)
            view.width>0 && view.height>0 && !view.isLayoutRequested && view.background.bounds.width()==view.width
        } }
        onMain {
            val view=root(activity)
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.background.draw(android.graphics.Canvas(bitmap))
            val percent=if(find(view,"Setup ▾")!=null) 100 else targetContext.getSharedPreferences("appearance",0)
                .getInt("console_wallpaper_opacity",70).coerceIn(0,100)
            val alpha=(percent*255/100)/255.0
            val base=AppColors.background(activity)
            for((fraction,photo) in listOf(.25f to Color.rgb(230,80,60),.75f to Color.rgb(40,160,210))) {
                val expected=Color.rgb((Color.red(photo)*alpha+Color.red(base)*(1-alpha)).roundToInt(),
                    (Color.green(photo)*alpha+Color.green(base)*(1-alpha)).roundToInt(),
                    (Color.blue(photo)*alpha+Color.blue(base)*(1-alpha)).roundToInt())
                val actual=bitmap.getPixel((view.width*fraction).toInt(),view.height/2)
                check(abs(Color.red(actual)-Color.red(expected))<=4 && abs(Color.green(actual)-Color.green(expected))<=4 &&
                    abs(Color.blue(actual)-Color.blue(expected))<=4) { "Wallpaper blend mismatch at $percent%: $actual vs $expected" }
            }
            bitmap.recycle()
        }
    }
    private fun checkJoystickPanels(activity:Activity) = onMain {
        fun sticks(view:View):List<JoystickView> = if(view is JoystickView) listOf(view) else if(view is ViewGroup)
            (0 until view.childCount).flatMap { sticks(view.getChildAt(it)) } else emptyList()
        checkConsoleGeometry(activity)
        val inputs=sticks(root(activity))
        check(inputs.size==2)
        val centers=mutableListOf<Float>()
        fun luminance(color:Int):Double {
            fun component(value:Int):Double { val v=value/255.0; return if(v<=.04045) v/12.92 else ((v+.055)/1.055).pow(2.4) }
            return .2126*component(Color.red(color))+.7152*component(Color.green(color))+.0722*component(Color.blue(color))
        }
        fun contrast(a:Int,b:Int):Double {
            val first=luminance(a); val second=luminance(b)
            return (maxOf(first,second)+.05)/(minOf(first,second)+.05)
        }
        for(input in inputs) {
            val panel=input.parent as ViewGroup
            val bitmap=Bitmap.createBitmap(panel.width,panel.height,Bitmap.Config.ARGB_8888)
            panel.draw(android.graphics.Canvas(bitmap))
            check(Color.alpha(bitmap.getPixel(1,1))==0) { "Joystick rectangle is opaque" }
            bitmap.recycle()
            val title=find(panel,"STEER") ?: find(panel,"DRIVE")!!
            val caption=title.parent as ViewGroup
            val feedback=caption.getChildAt(1) as TextView
            check(title.shadowRadius==0f && feedback.shadowRadius==0f) { "Joystick label has a blurred shadow" }
            val plate=Bitmap.createBitmap(caption.width,caption.height,Bitmap.Config.ARGB_8888)
            caption.draw(android.graphics.Canvas(plate))
            val fill=plate.getPixel(caption.width/2,1)
            check(Color.alpha(fill)==255) { "Wallpaper can wash out the caption" }
            check(contrast(title.currentTextColor,fill)>=7 && contrast(feedback.currentTextColor,fill)>=4.5) { "Joystick caption contrast is too low" }
            check(feedback.layout.lineCount<=2) { "Channel feedback is cramped" }
            plate.recycle()
            val xy=IntArray(2); input.getLocationOnScreen(xy); centers.add(xy[1]+input.height/2f)
        }
        check(abs(centers[0]-centers[1])<=2) { "Joystick centers are not aligned" }
    }
    private fun checkConsoleGeometry(activity:Activity) {
        val shell=root(activity) as ViewGroup
        check(shell.childCount==1) { "A full-width footer still reserves console space" }
        val row=shell.getChildAt(0) as ViewGroup
        check(row.childCount==3)
        val center=row.getChildAt(1)
        fun bounds(view:View)=android.graphics.Rect().also { check(view.getGlobalVisibleRect(it)) }
        val console=bounds(center)
        val available=bounds(row)
        check(console.top==available.top && console.bottom==available.bottom) { "Console does not fill the available height" }
        for(index in listOf(0,2)) {
            val column=row.getChildAt(index) as ViewGroup
            check(column.childCount==3)
            val bottom=bounds(column.getChildAt(2))
            check(bottom.bottom==console.bottom && !android.graphics.Rect.intersects(console,bottom)) { "Side actions overlap the console" }
            check(bottom.height()==column.getChildAt(2).height) { "Bottom actions are clipped" }
        }
        val test=findMaze(shell)!=null
        if(test) {
            check(find(shell,"TeleRC v${BuildConfig.VERSION_NAME}")==null)
            check(find(shell,"Start practice")!=null || find(shell,"Pause")!=null || find(shell,"Resume")!=null)
            check(find(shell,"Clear times")!=null)
        } else {
            check(find(shell,"DISCONNECTED")==null)
            check(find(shell,"ENABLE CONTROL")!=null || find(shell,"STOP CONTROL")!=null)
            check(find(shell,"F1")!=null && find(shell,"F2")!=null && find(shell,"F3")!=null)
        }
    }
    private fun checkTabMenu(activity:Activity,label:String,file:String) {
        val anchor=onMain { find(root(activity),"$label ▾")!! }
        val expected=onMain { android.graphics.Rect().also { check(anchor.getGlobalVisibleRect(it)); anchor.performClick() } }
        waitUntil { windowText("Tab menu")!=null }
        val actual=android.graphics.Rect().also { windowText("Tab menu")!!.getBoundsInScreen(it) }
        val tolerance=2*activity.resources.displayMetrics.density
        check(abs(actual.left-expected.left)<=tolerance && abs(actual.width()-expected.width())<=tolerance) { "Tab menu does not match its button: $actual vs $expected" }
        check(actual.top>=expected.bottom && actual.top-expected.bottom<=6*activity.resources.displayMetrics.density) { "Tab menu is detached from its button" }
        val density=activity.resources.displayMetrics.density
        val expectedHeight=(3*(40*maxOf(1f,activity.resources.configuration.fontScale)).toInt()+8)*density
        check(abs(actual.height()-expectedHeight)<=4*density) { "Tab menu rows are not compact: $actual" }
        screenshot(file)
        clickWindow(label)
        waitUntil { windowText("Tab menu")==null }
    }
    private fun checkWallpaperOpacity() {
        val prefs=targetContext.getSharedPreferences("appearance",0)
        var activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitUntil { onMain { root(activity).background is ThemeImageDrawable } }
        fun choose(percent:Int,apply:Boolean=true) {
            onMain { description(root(activity),"Wallpaper opacity in Controls and Test")!!.performClick() }
            waitUntil { windowText("Wallpaper opacity percentage")!=null }
            val seek=windowText("Wallpaper opacity percentage")!!
            check(seek.performAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
                Bundle().apply { putFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,percent.toFloat()) }))
            waitUntil { windowText("Controls / Test · $percent%")!=null }
            if(percent==0) screenshot("wallpaper-opacity-dialog.png")
            clickWindow(if(apply) "Apply" else "Cancel")
            waitUntil { windowText("Wallpaper opacity percentage")==null }
        }
        fun visit(percent:Int) {
            onMain { find(root(activity),"Controls ›")!!.performClick() }
            checkWallpaper(activity); checkJoystickPanels(activity)
            screenshot("wallpaper-$percent-controls.png")
            onMain { find(root(activity),"Controls ▾")!!.performClick() }
            waitUntil { windowText("Test")!=null }; clickWindow("Test")
            waitUntil { onMain { findMaze(root(activity))!=null } }
            checkWallpaper(activity); checkJoystickPanels(activity)
            screenshot("wallpaper-$percent-test.png")
            onMain { find(root(activity),"Test ▾")!!.performClick() }
            waitUntil { windowText("Setup")!=null }; clickWindow("Setup")
            checkWallpaper(activity)
        }
        choose(0)
        check(prefs.getInt("console_wallpaper_opacity",-1)==0)
        checkWallpaper(activity) // Setup remains clear even with console opacity zero.
        visit(0)
        choose(100,apply=false)
        check(prefs.getInt("console_wallpaper_opacity",-1)==0) { "Cancel saved a draft opacity" }
        choose(100)
        onMain { activity.finish() }; waitForIdleSync()
        activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitUntil { onMain { root(activity).background is ThemeImageDrawable } }
        check(prefs.getInt("console_wallpaper_opacity",-1)==100)
        visit(100)
        onMain { activity.finish() }; waitForIdleSync()
        prefs.edit().putInt("console_wallpaper_opacity",70).commit()
    }
    private fun checkSetupCorners(activity:Activity) {
        waitUntil { onMain { !root(activity).isLayoutRequested && find(root(activity),"Connect")?.isShown==true &&
            find(root(activity),"Connect")!!.height>0 && description(root(activity),"Connection settings")?.isShown==false &&
            description(root(activity),"Preferences")?.isShown==false } }
        onMain {
            val view=root(activity)
            val connect=find(view,"Connect")!!
            val updates=find(view,"Check for updates")!!
            fun bounds(v:View)=android.graphics.Rect().also { check(v.getGlobalVisibleRect(it)) }
            val parent=bounds(view); val left=bounds(connect); val right=bounds(updates)
            val tolerance=2*activity.resources.displayMetrics.density
            check(abs(left.left-(parent.left+view.paddingLeft))<=tolerance)
            check(abs(right.right-(parent.right-view.paddingRight))<=tolerance)
            check(abs(left.bottom-(parent.bottom-view.paddingBottom))<=tolerance && left.bottom==right.bottom)
            check(connect.hasOnClickListeners() && updates.hasOnClickListeners())
            check(left.height()>=48*activity.resources.displayMetrics.density-1 && right.height()>=48*activity.resources.displayMetrics.density-1)
            check(!targetContext.getSharedPreferences("link",0).getBoolean("auto_connect",false))
        }
    }
    private fun checkSetupCollapse(first:Activity,theme:String):Activity {
        val connect=onMain { find(root(first),"Connect")!! }
        val updates=onMain { find(root(first),"Check for updates")!! }
        val host=onMain { description(root(first),"Bridge IPv4 address") as TextView }
        onMain {
            host.text="192.168.4.42"; host.requestFocus()
            description(root(first),"Minimize setup panels")!!.performClick()
        }
        checkSetupCorners(first)
        screenshot("setup-${theme.lowercase()}-minimized.png")
        onMain {
            check(find(root(first),"Connect")===connect && find(root(first),"Check for updates")===updates)
            check(!host.hasFocus()) { "Hidden endpoint retained input focus" }
            description(root(first),"Show setup panels")!!.performClick()
            check(description(root(first),"Connection settings")!!.isShown)
            check(host.text.toString()=="192.168.4.42") { "Collapse lost a draft endpoint" }
            check(find(root(first),"Connect")===connect && find(root(first),"Check for updates")===updates)
            description(root(first),"Minimize setup panels")!!.performClick()
            first.finish()
        }
        waitForIdleSync()
        val restored=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitUntil { onMain { root(restored).background is ThemeImageDrawable && description(root(restored),"Show setup panels")!=null } }
        checkSetupCorners(restored); checkWallpaper(restored)
        return restored
    }
    private fun checkPopupStable(activity:Activity,dialog:android.app.Dialog) {
        fun bounds() = onMain {
            val decor=dialog.window!!.decorView
            val xy=IntArray(2); decor.getLocationOnScreen(xy)
            android.graphics.Rect(xy[0],xy[1],xy[0]+decor.width,xy[1]+decor.height)
        }
        waitUntil { onMain { dialog.window!!.decorView.width>0 } }
        val first=bounds()
        onMain {
            val window=dialog.window!!; val attributes=window.attributes
            check(attributes.gravity==android.view.Gravity.CENTER && attributes.x==0 && attributes.y==0 && attributes.windowAnimations==0)
            val decor=activity.window.decorView; val xy=IntArray(2); decor.getLocationOnScreen(xy)
            val tolerance=4*activity.resources.displayMetrics.density
            check(abs(first.exactCenterX()-(xy[0]+decor.width/2f))<=tolerance) { "Popup is horizontally off center" }
            check(abs(first.exactCenterY()-(xy[1]+decor.height/2f))<=tolerance) { "Popup is vertically off center" }
        }
        SystemClock.sleep(350)
        check(bounds()==first) { "Popup moved after opening" }
    }
    private fun description(view:View,value:String):View? {
        if(view.contentDescription?.toString()==value) return view
        if(view is ViewGroup) for(i in 0 until view.childCount) description(view.getChildAt(i),value)?.let { return it }
        return null
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
            check(find(root(activity),"TeleRC v${BuildConfig.VERSION_NAME}")==null)
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
        waitUntil { windowText("2/20")!=null }
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
        waitUntil { windowText("Market")!=null }
        check(onMain { scene.practice.state!=MazePractice.State.RUNNING })
        showMarketItem("Buy Rally · 20")
        screenshot("market-vehicles.png")
        clickWindow("Buy Rally · 20")
        waitUntil { MazeWallet.decode(prefs.getString("wallet","").orEmpty()).vehicle=="rally" }
        check(MazeWallet.decode(prefs.getString("wallet","").orEmpty()).balance==15)
        showMarketItem("Buy Hauler · 50")
        check(windowText("Buy Hauler · 50")?.isEnabled==false)
        clickWindow("Colors")
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
                .firstOrNull { it.text?.toString()?.equals(text,ignoreCase=true)==true || (it.contentDescription?.toString()?.equals(text,ignoreCase=true)==true || it.contentDescription?.toString()?.startsWith("$text,",ignoreCase=true)==true) }
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
                waitUntil { onMain { root(activity).background is ThemeImageDrawable } }
                onMain { description(root(activity),"Minimize setup panels")!!.performClick() }
                checkSetupCorners(activity)
                screenshot("setup-${theme.lowercase()}-large-minimized.png")
                onMain { description(root(activity),"Show setup panels")!!.performClick() }
                checkTabMenu(activity,"Setup","tabs-${theme.lowercase()}-large-setup.png")
                onMain { find(root(activity),"Controls ›")!!.performClick() }
                checkWallpaper(activity); checkJoystickPanels(activity)
                checkTabMenu(activity,"Controls","tabs-${theme.lowercase()}-large-controls.png")
                screenshot("labels-${theme.lowercase()}-large-controls.png")
                onMain { find(root(activity),"Controls ▾")!!.performClick() }
                waitUntil { windowText("Test")!=null }; clickWindow("Test")
                waitUntil { onMain { findMaze(root(activity))!=null } }
                checkWallpaper(activity); checkJoystickPanels(activity)
                checkTabMenu(activity,"Test","tabs-${theme.lowercase()}-large-test.png")
                screenshot("labels-${theme.lowercase()}-large-test.png")
                onMain { find(root(activity),"Test ▾")!!.performClick() }
                waitUntil { windowText("Setup")!=null }; clickWindow("Setup")
                var chosen=0
                val levels=onMain { MazePracticeDialogs(activity).levels(MazeProgress.restore(1,20),
                    listOf(MazeClear(37,65430,1000,3,1))) { chosen=it } }
                waitUntil { windowText("20/20")!=null }
                checkDialogButton(activity,levels,"Done")
                checkPopupStable(activity,levels)
                screenshot("dialogs-${theme.lowercase()}-levels.png")
                showMarketItem("Level 20"); clickWindow("Level 20")
                waitUntil { onMain { chosen==20 } }
                var restarted=false
                val restart=onMain { MazePracticeDialogs(activity).restart(20,75640,3) { restarted=true } }
                waitUntil { windowText("Restart level 20?")!=null }
                checkDialogButton(activity,restart,"Restart level")
                checkPopupStable(activity,restart)
                screenshot("dialogs-${theme.lowercase()}-restart.png")
                clickWindow("Restart level")
                waitUntil { onMain { restarted } }
                var wallet=MazeWallet.decode("")
                for(level in 2..5) for(slot in 0 until MazeMarket.quota(level)) wallet=wallet.claim(level,slot)!!
                for(slot in 0..2) wallet=wallet.claim(6,slot)!!
                val market=onMain { MazePracticeDialogs(activity).market({ wallet }) { wallet=it } }
                waitUntil { windowText("35 coins")!=null }
                checkDialogButton(activity,market,"Done")
                checkPopupStable(activity,market)
                checkDialogButton(activity,market,"Vehicles")
                showMarketItem("Buy Rally · 20")
                screenshot("dialogs-${theme.lowercase()}-market-vehicles.png")
                clickWindow("Buy Rally · 20")
                waitUntil { onMain { wallet.vehicle=="rally" && wallet.balance==15 } }
                clickWindow("Colors")
                checkDialogButton(activity,market,"Colors")
                showMarketItem("Buy Forest · 15")
                screenshot("dialogs-${theme.lowercase()}-market-colors.png")
                clickWindow("Buy Forest · 15")
                waitUntil { onMain { wallet.theme=="forest" && wallet.balance==0 } }
                clickWindow("Done")
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
        .firstOrNull { it.packageName?.toString()==targetContext.packageName && it.isVisibleToUser && (it.text?.toString()?.equals(text,ignoreCase=true)==true || (it.contentDescription?.toString()?.equals(text,ignoreCase=true)==true || it.contentDescription?.toString()?.startsWith("$text,",ignoreCase=true)==true)) }
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
