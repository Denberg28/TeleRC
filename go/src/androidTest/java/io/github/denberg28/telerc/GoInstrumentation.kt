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
 override fun onStart(){try{
  val prefs=targetContext.getSharedPreferences("go",0);prefs.edit().putString("profile","WIFI").putString("WIFI.host","10.0.2.2").commit()
  for(scale in listOf("1.0","1.3")){
   android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("settings put system font_scale $scale")).use{it.readBytes()}
   waitFor{if(scale=="1.3")targetContext.resources.configuration.fontScale>=1.25f else targetContext.resources.configuration.fontScale<1.1f}
   val a=startActivitySync(Intent(targetContext,GoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as GoActivity
   waitFor{main{find(a.window.decorView,"Phone connect")!=null}}
   main{
    for(label in listOf("Setup","Controls","Test","Waypoint","Balance","LoRa"))check(find(a.window.decorView,label)==null)
    for(label in listOf("Phone connect","Settings","STOP / Disarm","ARM"))visible(a,label)
    check(!find(a.window.decorView,"ARM")!!.isEnabled);check(!find(a.window.decorView,"Use ELRS")!!.isEnabled)
   }
   shot("go-$scale-controls.png")
   click(a,"Settings");shot("go-$scale-settings.png")
   main{a.finish()};waitForIdleSync()
  }
  android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("settings put system font_scale 1.0")).use{it.readBytes()}
  val a=startActivitySync(Intent(targetContext,GoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as GoActivity
  click(a,"Phone connect");waitFor{a.link.snapshot().fresh};click(a,"Phone control");waitFor{a.link.snapshot().armReady};click(a,"ARM");waitFor{a.link.snapshot().armed}
  main{a.link.axes(1700,1800)};Thread.sleep(200);control("SILENCE");waitFor{!a.link.snapshot().enabled};check(!a.link.snapshot().fresh)
  control("RESTORE");waitFor{a.link.snapshot().fresh};check(!a.link.snapshot().enabled){"Recovered heartbeat restored control"}
  main{a.link.stop()};waitFor{!a.link.snapshot().armed}
  click(a,"Phone control");waitFor{a.link.snapshot().armReady};click(a,"ARM");waitFor{a.link.snapshot().armed}
  control("REBOOT");waitFor{!a.link.snapshot().enabled};check(a.link.snapshot().fresh);check(!a.link.snapshot().armed)
  click(a,"Phone control");waitFor{a.link.snapshot().armReady};click(a,"ARM");waitFor{a.link.snapshot().armed}
  click(a,"Settings");check(!a.link.snapshot().enabled){"Settings retained drive authority"}
  main{a.link.disconnect();a.finish()};Thread.sleep(300)
  control("OBSERVER_START")
  prefs.edit().putString("profile","ELRS").putString("ELRS.host","10.0.2.2").commit()
  val observer=startActivitySync(Intent(targetContext,GoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as GoActivity
  click(observer,"Phone connect");waitFor{observer.link.snapshot().fresh};check(observer.link.snapshot().armed);check(!observer.link.snapshot().enabled)
  click(observer,"Settings");main{observer.link.disconnect();observer.finish()};Thread.sleep(300)
  control("OBSERVER_END");control("ELRS_START")
  val elrs=startActivitySync(Intent(targetContext,GoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as GoActivity
  click(elrs,"Phone connect");waitFor{elrs.link.snapshot().fresh};click(elrs,"Take phone control");waitFor{elrs.link.snapshot().enabled};waitFor{elrs.link.snapshot().armReady};click(elrs,"ARM");waitFor{elrs.link.snapshot().armed}
  click(elrs,"Use ELRS");waitFor{!elrs.link.snapshot().armed&&!elrs.link.snapshot().enabled};check(elrs.link.snapshot().connected)
  Thread.sleep(300);check(!elrs.link.snapshot().enabled){"ELRS handover automatically resumed phone"}
  main{elrs.link.disconnect();elrs.finish()};Thread.sleep(300);control("FC_START")
  prefs.edit().putString("profile","FC_WIFI").putString("FC_WIFI.host","10.0.2.2").commit()
  val fc=startActivitySync(Intent(targetContext,GoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as GoActivity
  click(fc,"Phone connect")
  try{waitFor{fc.link.snapshot().fresh}}catch(t:Throwable){error("FC fresh: ${fc.link.snapshot()}")}
  click(fc,"Phone control")
  try{waitFor{fc.link.snapshot().armReady}}catch(t:Throwable){error("FC neutral dwell: ${fc.link.snapshot()}")}
  click(fc,"ARM")
  try{waitFor{fc.link.snapshot().armed}}catch(t:Throwable){error("FC ARM confirmation: ${fc.link.snapshot()}")}
  main{fc.link.axes(1700,1800);fc.link.stop()};waitFor{fc.link.snapshot().stopped};check(!fc.link.snapshot().armed);check(!fc.link.snapshot().enabled)
  main{fc.link.enable()};check(!fc.link.snapshot().enabled){"HOLD restored phone control"}
  main{fc.link.disconnect();fc.finish()};Thread.sleep(300);control("EXIT")
  finish(Activity.RESULT_OK,Bundle().apply{putString("stream","PASS: Go controls only; image background, compact settings, normal/large text; real UDP ARM, motion, telemetry recovery, reboot, popup stop, passive observer, ELRS takeover/release and ArduRover HOLD")})
 }catch(t:Throwable){runCatching{shot("go-failed.png")};finish(Activity.RESULT_CANCELED,Bundle().apply{putString("stream","FAIL: "+t.stackTraceToString())})}}
}
