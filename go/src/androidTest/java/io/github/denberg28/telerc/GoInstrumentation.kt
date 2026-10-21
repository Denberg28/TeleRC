package io.github.denberg28.telerc
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import java.net.DatagramSocket
import java.net.DatagramPacket
import java.net.InetAddress
import java.io.File
class GoInstrumentation:Instrumentation(){
 override fun onCreate(args:Bundle?){super.onCreate(args);start()}
 private fun <T> main(action:()->T):T{var value:Any?=null;var failure:Throwable?=null;runOnMainSync{try{value=action()}catch(t:Throwable){failure=t}};failure?.let{throw it};@Suppress("UNCHECKED_CAST")return value as T}
 private fun waitFor(test:()->Boolean){val until=SystemClock.elapsedRealtime()+10000;while(SystemClock.elapsedRealtime()<until){if(test())return;Thread.sleep(30)};error("Timed out waiting for Go UI")}
 private fun find(v:View,text:String):View?{if(v is android.widget.Button&&v.text.toString()==text)return v;if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i),text)?.let{return it};return null}
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
 private fun await(a:GoActivity,phase:String,test:()->Boolean){try{waitFor(test)}catch(t:Throwable){error("$phase: ${a.link.snapshot()}")}}
 override fun onStart(){try{
  val prefs=targetContext.getSharedPreferences("go",0);prefs.edit().putString("profile","BACKPACK").putString("WIFI.host","10.0.2.2").commit()
  val icon=targetContext.packageManager.getApplicationIcon(targetContext.packageName)
  val bitmap=android.graphics.Bitmap.createBitmap(216,216,android.graphics.Bitmap.Config.ARGB_8888)
  icon.setBounds(0,0,216,216);icon.draw(android.graphics.Canvas(bitmap))
  File(targetContext.filesDir,"go-icon.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
  for(scale in listOf("1.0","1.3")){
   android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("settings put system font_scale $scale")).use{it.readBytes()}
   waitFor{if(scale=="1.3")targetContext.resources.configuration.fontScale>=1.25f else targetContext.resources.configuration.fontScale<1.1f}
   val a=startActivitySync(Intent(targetContext,GoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as GoActivity
   waitFor{main{find(a.window.decorView,"Phone connect")!=null}}
   main{
    for(label in listOf("Setup","Controls","Test","Waypoint","Balance","LoRa","Phone control","Use ELRS","STOP / Disarm"))check(find(a.window.decorView,label)==null)
    check(all(a.window.decorView).count{it is android.widget.Button}==3)
    for(label in listOf("Phone connect","Settings","ARM"))visible(a,label)
    check(!find(a.window.decorView,"ARM")!!.isEnabled)
   }
   shot("go-$scale-controls.png")
   tap(a,"Settings");main{
    val strings=all(currentRoot(a)).filterIsInstance<android.widget.TextView>().map{it.text.toString()}
    check(strings.none{it.contains("CH5")||it.contains("Connect →")||it=="1.0.1"||it=="1.0.2"})
   };shot("go-$scale-settings.png")
   tap(a,"Connection details");main{check(all(currentRoot(a)).none{it is android.widget.Spinner})};tap(a,"Done")
   tap(a,"Settings");tap(a,"Sensitivity");tap(a,"Done")
   main{a.finish()};waitForIdleSync()
  }
  android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("settings put system font_scale 1.0")).use{it.readBytes()}
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
  finish(Activity.RESULT_OK,Bundle().apply{putString("stream","PASS: Go Wi-Fi only; three buttons, merged ARM/DISARM, no footer text/profile dropdown; normal/large text; port collision, cancelled ARM, recovery, reboot, popup transitions, SA takeover/return, passive RF observer, auto ArduRover + HOLD")})
 }catch(t:Throwable){runCatching{shot("go-failed.png")};finish(Activity.RESULT_CANCELED,Bundle().apply{putString("stream","FAIL: "+t.stackTraceToString())})}}
}
