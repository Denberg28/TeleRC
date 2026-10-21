package io.github.denberg28.telerc
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import io.github.denberg28.telerc.go.BuildConfig
import java.net.DatagramSocket
import java.net.DatagramPacket
import java.net.InetAddress
import java.io.File
class GoInstrumentation:Instrumentation(){
 override fun onCreate(args:Bundle?){super.onCreate(args);start()}
 private fun <T> main(action:()->T):T{var value:Any?=null;var failure:Throwable?=null;runOnMainSync{try{value=action()}catch(t:Throwable){failure=t}};failure?.let{throw it};@Suppress("UNCHECKED_CAST")return value as T}
 private fun waitFor(test:()->Boolean){val until=SystemClock.elapsedRealtime()+10000;while(SystemClock.elapsedRealtime()<until){if(test())return;Thread.sleep(30)};error("Timed out waiting for Go UI")}
 private fun find(v:View,text:String):View?{if(v is android.widget.TextView&&v.text.toString()==text)return v;if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i),text)?.let{return it};return null}
 private fun click(a:Activity,text:String){waitFor{main{find(a.window.decorView,text)?.isEnabled==true}};main{check(find(a.window.decorView,text)?.performClick()==true){"Missing $text"}}}
 private fun shot(name:String){
  val until=SystemClock.elapsedRealtime()+10000
  while(true){
   waitForIdleSync();Thread.sleep(500)
   val b=uiAutomation.takeScreenshot()
   // Starting/rotation windows can obscure a rendered View hierarchy. Require
   // the navy app surface in every quadrant before recording visual evidence.
   val ready=(0..1).all{qx->(0..1).all{qy->
    var blue=0;var total=0
    for(x in qx*b.width/2 until (qx+1)*b.width/2 step 24)for(y in qy*b.height/2 until (qy+1)*b.height/2 step 24){
     val c=b.getPixel(x,y);if(android.graphics.Color.blue(c)-android.graphics.Color.red(c)>=12)blue++;total++
    }
    blue>total/10
   }}
   if(ready){File(targetContext.filesDir,name).outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle();return}
   b.recycle();check(SystemClock.elapsedRealtime()<until){"Launch surface still obscures $name"}
  }
 }
 private fun visible(a:Activity,label:String){val v=find(a.window.decorView,label)?:error("Missing $label");val r=android.graphics.Rect();check(v.getGlobalVisibleRect(r)&&r.height()>=v.height-1){"Clipped $label"}}
 private fun control(value:String){DatagramSocket().use{s->val b=value.toByteArray();s.send(DatagramPacket(b,b.size,InetAddress.getByName("10.0.2.2"),14551))}}
 private fun currentRoot(a:GoActivity):View {
  val field=GoActivity::class.java.getDeclaredField("showing");field.isAccessible=true
  val d=field.get(a) as? android.app.Dialog
  return d?.window?.decorView?:a.window.decorView
 }
 private fun tap(a:GoActivity,label:String){waitFor{main{find(currentRoot(a),label)?.isEnabled==true}};main{check(find(currentRoot(a),label)!!.performClick())}}
 private fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{all(v.getChildAt(it))}else emptyList()
 private fun fit(a:GoActivity){waitFor{main{(all(currentRoot(a)).firstOrNull{it.tag=="go-sheet"}?.height?:0)>0}};waitForIdleSync();main{fitted(a)}}
 private fun fitted(a:GoActivity){
  val root=currentRoot(a)
  check(all(root).none{it is android.widget.ScrollView||it is android.widget.HorizontalScrollView}){"Popup contains a scrolling container"}
  val sheet=all(root).first{it.tag=="go-sheet"}
  val available=android.graphics.Rect().also{a.window.decorView.getWindowVisibleDisplayFrame(it)}
  val seen=android.graphics.Rect();check(sheet.getGlobalVisibleRect(seen))
  val xy=IntArray(2);sheet.getLocationOnScreen(xy);val bounds=android.graphics.Rect(xy[0],xy[1],xy[0]+sheet.width,xy[1]+sheet.height)
  check(seen.height()>=sheet.height-1&&seen.width()>=sheet.width-1&&available.contains(bounds)){"Sheet clipped: $bounds / $available"}
  val widgets=all(sheet).filter{it is android.widget.Button||it is android.widget.EditText||it is android.widget.SeekBar}
  val rectangles=widgets.map{v->android.graphics.Rect().also{r->check(v.getGlobalVisibleRect(r)&&r.height()>=v.height-1&&r.width()>=v.width-1){"Clipped ${v.contentDescription} / ${(v as? android.widget.TextView)?.text}"}}}
  for(i in rectangles.indices)for(j in i+1 until rectangles.size)check(!android.graphics.Rect.intersects(rectangles[i],rectangles[j])){"Overlapping form actions"}
 }
 private fun controlsPosition(a:GoActivity){
  val arm=all(a.window.decorView).first{it.tag=="go-arm"};val status=all(a.window.decorView).first{it.tag=="go-status"}
  fun bounds(v:View)=android.graphics.Rect().also{check(v.getGlobalVisibleRect(it))}
  val ar=bounds(arm);val sr=bounds(status);val cr=bounds(arm.parent.parent as View)
  check(ar.top>cr.centerY()&&ar.bottom<=cr.bottom&&sr.bottom<ar.top){"ARM/status not in lower center"}
  check(kotlin.math.abs(ar.centerX()-cr.centerX())<=2&&kotlin.math.abs(sr.centerX()-ar.centerX())<=2){"ARM/status not centered"}
  val version=all(a.window.decorView).filterIsInstance<android.widget.TextView>().first{it.text.toString()=="TeleRC Go v${BuildConfig.VERSION_NAME}"}
  val vr=bounds(version);check(version.isClickable&&vr.left<cr.centerX()&&vr.top<cr.top){"Version not clickable at upper left"}
 }
 private fun verifyUpdater(a:GoActivity){
  val candidate=File(targetContext.filesDir,"update-candidate.apk");check(candidate.isFile)
  val digest=GoApkVerifier.hash(candidate)
  check(GoApkVerifier.verify(a,candidate,digest,"1.0.4")){"Newer same-package/signer APK rejected"}
  check(!GoApkVerifier.verify(a,candidate,"00".repeat(32),"1.0.4")){"Wrong hash accepted"}
  check(!GoApkVerifier.verify(a,candidate,digest,"9.9.9")){"Wrong version accepted"}
  val current=File(a.applicationInfo.sourceDir);check(!GoApkVerifier.verify(a,current,GoApkVerifier.hash(current))){"Same-code APK accepted"}
  val other=File(targetContext.filesDir,"update-wrong-package.apk");check(!GoApkVerifier.verify(a,other,GoApkVerifier.hash(other))){"Full TeleRC APK accepted"}
  val calls=java.util.Collections.synchronizedList(mutableListOf<String>())
  val offered=java.util.concurrent.atomic.AtomicReference<GoUpdateCandidate?>()
  val messages=java.util.Collections.synchronizedList(mutableListOf<String>())
  val fake=GoUpdater(a,{messages.add(it)},{value,_->offered.set(value)},{url->
   calls.add(url)
   if(url.endsWith("matching-refs/tags/go-v"))"""[{"ref":"refs/tags/v9.9.9"},{"ref":"refs/tags/go-v1.0.4"},{"ref":"refs/tags/go-v1.0.5"}]"""
   else {
    val v=if(url.endsWith("1.0.5"))"1.0.5" else "1.0.4"
    """{"tag_name":"go-v$v","draft":false,"prerelease":${v=="1.0.5"},"assets":[{"name":"TeleRC-Go-v$v.apk","browser_download_url":"https://github.com/Denberg28/TeleRC/releases/download/go-v$v/TeleRC-Go-v$v.apk","digest":"sha256:$digest","size":${candidate.length()}}]}"""
   }
  })
  main{fake.check()}
  try{waitFor{offered.get()!=null||messages.any{it!="Checking for updates…"}}}catch(t:Throwable){error("Update callback timed out: calls=$calls messages=$messages finishing=${a.isFinishing} destroyed=${a.isDestroyed} fixtureBytes=${candidate.length()}")}
  check(offered.get()?.version=="1.0.4"){"Stable Go selection failed: calls=$calls messages=$messages fixtureBytes=${candidate.length()}"}
  check(calls.none{it.endsWith("releases/latest")||it.endsWith("v9.9.9")});main{fake.close()}
 }
 private fun await(a:GoActivity,phase:String,test:()->Boolean){try{waitFor(test)}catch(t:Throwable){error("$phase: ${a.link.snapshot()}")}}
 override fun onStart(){try{
  val prefs=targetContext.getSharedPreferences("go",0);prefs.edit().putString("profile","BACKPACK").putString("WIFI.host","10.0.2.2").commit()
  val icon=targetContext.packageManager.getApplicationIcon(targetContext.packageName)
  val bitmap=android.graphics.Bitmap.createBitmap(216,216,android.graphics.Bitmap.Config.ARGB_8888)
  icon.setBounds(0,0,216,216);icon.draw(android.graphics.Canvas(bitmap))
  File(targetContext.filesDir,"go-icon.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
  for(scale in listOf("1.0","1.3")){
   // The large-text pass also exercises a shorter, 640 x 360 dp landscape phone.
   if(scale=="1.3"){
    android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("wm size 1280x720")).use{it.readBytes()}
    android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("wm density 320")).use{it.readBytes()}
    Thread.sleep(1500)
   }
   android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("settings put system font_scale $scale")).use{it.readBytes()}
   waitFor{if(scale=="1.3")targetContext.resources.configuration.fontScale>=1.25f else targetContext.resources.configuration.fontScale<1.1f}
   val a=startActivitySync(Intent(targetContext,GoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as GoActivity
   waitFor{main{find(a.window.decorView,"Phone connect")!=null}}
   main{
    for(label in listOf("Setup","Controls","Test","Waypoint","Balance","LoRa","Phone control","Use ELRS","STOP / Disarm"))check(find(a.window.decorView,label)==null)
    check(all(a.window.decorView).count{it is android.widget.Button}==3)
    for(label in listOf("Phone connect","Settings","ARM"))visible(a,label)
    check(!find(a.window.decorView,"ARM")!!.isEnabled);controlsPosition(a)
   }
   shot("go-$scale-controls.png")
   tap(a,"Settings");main{
    val strings=all(currentRoot(a)).filterIsInstance<android.widget.TextView>().map{it.text.toString()}
    check(strings.none{it.contains("CH5")||it.contains("Connect →")||it=="1.0.1"||it=="1.0.2"})
   };fit(a);shot("go-$scale-settings.png")
   tap(a,"Connection details");main{check(all(currentRoot(a)).none{it is android.widget.Spinner});check(all(currentRoot(a)).filterIsInstance<android.widget.EditText>().size==4)}
   shot("go-$scale-connection.png")
   main{all(currentRoot(a)).filterIsInstance<android.widget.EditText>().first().setText("192.168.4.19")};tap(a,"Cancel")
   check(prefs.getString("WIFI.host",null)=="10.0.2.2")
   tap(a,"Settings");tap(a,"Connection details");fit(a);tap(a,"Save")
   main{check(all(currentRoot(a)).none{it.tag=="go-sheet"})}
   tap(a,"Settings");tap(a,"Sensitivity");fit(a);shot("go-$scale-sensitivity.png");tap(a,"Done")
   tap(a,"TeleRC Go v${BuildConfig.VERSION_NAME}");fit(a);main{check(!a.link.snapshot().enabled)};shot("go-$scale-updates.png");tap(a,"Done")
   if(scale=="1.0")verifyUpdater(a)
   main{a.finish()};waitForIdleSync()
  }
  android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("settings put system font_scale 1.0")).use{it.readBytes()}
  android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("wm size reset")).use{it.readBytes()}
  android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("wm density reset")).use{it.readBytes()}
  Thread.sleep(1500)
  val occupied=DatagramSocket(14550)
  val a=startActivitySync(Intent(targetContext,GoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as GoActivity
  tap(a,"Phone connect");await(a,"Port conflict recovery"){a.link.snapshot().fresh&&a.link.snapshot().verified};check(occupied.isBound)
  tap(a,"ARM");tap(a,"Cancel arming");await(a,"Cancel ARM"){!a.link.snapshot().pending&&!a.link.snapshot().enabled};Thread.sleep(500);check(!a.link.snapshot().armed)
  tap(a,"ARM");await(a,"Merged ARM"){a.link.snapshot().armed}
  main{a.link.axes(1700,1800)};Thread.sleep(200);control("SILENCE");await(a,"Telemetry loss"){!a.link.snapshot().enabled}
  control("RESTORE");await(a,"Telemetry recovery"){a.link.snapshot().verified&&a.link.snapshot().fresh};check(!a.link.snapshot().enabled)
  await(a,"Watchdog disarm"){!a.link.snapshot().armed}
  tap(a,"ARM");await(a,"Re-arm"){a.link.snapshot().armed};tap(a,"DISARM");await(a,"Merged DISARM"){!a.link.snapshot().armed&&!a.link.snapshot().enabled}
  tap(a,"ARM");await(a,"Reboot setup"){a.link.snapshot().armed};control("REBOOT");await(a,"Reboot revokes control"){!a.link.snapshot().enabled};check(!a.link.snapshot().pending)
  tap(a,"ARM");tap(a,"Settings");check(!a.link.snapshot().enabled&&!a.link.snapshot().pending);tap(a,"Done")
  for(i in 1..5){tap(a,"Settings");tap(a,"Done");check(a.link.snapshot().connected);check(!a.link.snapshot().enabled)}
  main{a.link.disconnect();a.finish()};occupied.close();Thread.sleep(400)
  control("ELRS_START")
  val elrs=startActivitySync(Intent(targetContext,GoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as GoActivity
  tap(elrs,"Phone connect");await(elrs,"RF Wi-Fi connection"){elrs.link.snapshot().verified}
  tap(elrs,"ARM");await(elrs,"RF phone ARM"){elrs.link.snapshot().armed}
  control("SA_RADIO");await(elrs,"SA takeover"){elrs.link.snapshot().handset&&!elrs.link.snapshot().enabled};check(!elrs.link.snapshot().pending)
  control("SA_DRIVE");await(elrs,"Handset armed"){elrs.link.snapshot().armed};tap(elrs,"Settings");tap(elrs,"Done");Thread.sleep(600);check(elrs.link.snapshot().armed)
  control("SA_PHONE");await(elrs,"SA return"){!elrs.link.snapshot().handset&&!elrs.link.snapshot().armed};check(!elrs.link.snapshot().enabled)
  tap(elrs,"ARM");await(elrs,"Deliberate phone return"){elrs.link.snapshot().armed};tap(elrs,"DISARM");await(elrs,"Disarm before close"){!elrs.link.snapshot().armed}
  main{elrs.link.disconnect();elrs.finish()};Thread.sleep(400)
  control("FC_START")
  val fc=startActivitySync(Intent(targetContext,GoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as GoActivity
  tap(fc,"Phone connect");await(fc,"Auto ArduRover + RC telemetry"){fc.link.snapshot().verified&&fc.link.snapshot().fresh}
  tap(fc,"ARM");await(fc,"FC ARM"){fc.link.snapshot().armed};control("SA_RADIO");await(fc,"FC SA takeover"){fc.link.snapshot().handset&&!fc.link.snapshot().enabled}
  control("SA_DRIVE");await(fc,"FC RF observer"){fc.link.snapshot().armed};tap(fc,"Settings");tap(fc,"Done");Thread.sleep(600);check(fc.link.snapshot().armed)
  control("SA_PHONE");await(fc,"FC phone return"){!fc.link.snapshot().handset&&!fc.link.snapshot().armed};check(!fc.link.snapshot().enabled)
  tap(fc,"ARM");await(fc,"FC deliberate ARM"){fc.link.snapshot().armed};tap(fc,"DISARM");await(fc,"FC HOLD"){fc.link.snapshot().stopped&&!fc.link.snapshot().enabled&&!fc.link.snapshot().armed}
  main{fc.link.disconnect();fc.finish()};Thread.sleep(400);control("EXIT")
  finish(Activity.RESULT_OK,Bundle().apply{putString("stream","PASS: Go Wi-Fi only; fitted non-scrolling sheets, lower center ARM/status, clickable upper-left version, verified Go-only updater archives/metadata; normal/large text and short landscape; port collision, cancelled ARM, recovery, reboot, popup transitions, SA takeover/return, passive RF observer, auto ArduRover + HOLD")})
 }catch(t:Throwable){runCatching{shot("go-failed.png")};finish(Activity.RESULT_CANCELED,Bundle().apply{putString("stream","FAIL: "+t.stackTraceToString())})}}
}
