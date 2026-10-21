package io.github.denberg28.telerc
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
 private fun shot(name:String){waitForIdleSync();Thread.sleep(300);val b=uiAutomation.takeScreenshot();File(targetContext.filesDir,name).outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
 private fun control(value:String){DatagramSocket().use{s->val b=value.toByteArray();s.send(DatagramPacket(b,b.size,InetAddress.getByName("10.0.2.2"),14551))}}
 override fun onStart(){try{
  val prefs=targetContext.getSharedPreferences("go",0);prefs.edit().putString("profile","WIFI").putString("WIFI.host","10.0.2.2").commit()
  for(scale in listOf("1.0","1.3")){
   android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("settings put system font_scale $scale")).use{it.readBytes()}
   waitFor{if(scale=="1.3")targetContext.resources.configuration.fontScale>=1.25f else targetContext.resources.configuration.fontScale<1.1f}
   for(theme in listOf("DARK","LIGHT","SEPIA")){
    targetContext.getSharedPreferences("appearance",0).edit().putString("background",theme).commit()
    val a=startActivitySync(Intent(targetContext,GoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as GoActivity
    waitFor{main{find(a.window.decorView,"Connect")!=null}}
    main{for(label in listOf("Setup","Controls","Test"))check(find(a.window.decorView,label)!=null);for(label in listOf("Waypoint","Balance","LoRa"))check(find(a.window.decorView,label)==null)}
    main{val v=find(a.window.decorView,"Connect")!!;val r=android.graphics.Rect();check(v.getGlobalVisibleRect(r)&&r.height()>=v.height-1){"Setup Connect clipped"}}
    shot("go-$theme-$scale-setup.png")
    click(a,"Controls");main{check(!find(a.window.decorView,"ARM")!!.isEnabled)}
    main{val v=find(a.window.decorView,"STOP / Disarm")!!;val r=android.graphics.Rect();check(v.getGlobalVisibleRect(r)&&r.height()>=v.height-1){"Controls STOP clipped"}}
    shot("go-$theme-$scale-controls.png")
    click(a,"Test");click(a,"Start / Pause");shot("go-$theme-$scale-test.png");click(a,"Restart")
    main{check(!a.link.snapshot().connected);a.finish()};waitForIdleSync()
   }
  }
  android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("settings put system font_scale 1.0")).use{it.readBytes()}
  val a=startActivitySync(Intent(targetContext,GoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as GoActivity
  click(a,"Connect");waitFor{a.link.snapshot().fresh};click(a,"Enable");waitFor{a.link.snapshot().armReady};click(a,"ARM");waitFor{a.link.snapshot().armed}
  main{a.link.axes(1700,1800)};Thread.sleep(200);control("SILENCE");waitFor{!a.link.snapshot().enabled};check(!a.link.snapshot().fresh)
  control("RESTORE");waitFor{a.link.snapshot().fresh};check(!a.link.snapshot().enabled){"Recovered heartbeat restored control"}
  main{a.link.stop()};Thread.sleep(300);waitFor{!a.link.snapshot().armed}
  click(a,"Enable");waitFor{a.link.snapshot().armReady};click(a,"ARM");waitFor{a.link.snapshot().armed}
  control("REBOOT");waitFor{!a.link.snapshot().enabled};check(a.link.snapshot().fresh);check(!a.link.snapshot().armed);
  click(a,"Test");waitFor{!a.link.snapshot().connected};main{check(!a.link.snapshot().enabled)}
  click(a,"Setup");check(!a.link.snapshot().connected){"Test reconnects live transport"}
  main{a.finish()};Thread.sleep(300)
  control("OBSERVER_START")
  prefs.edit().putString("profile","ELRS").putString("ELRS.host","10.0.2.2").commit()
  val observer=startActivitySync(Intent(targetContext,GoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as GoActivity
  click(observer,"Connect");waitFor{observer.link.snapshot().fresh};check(observer.link.snapshot().armed);check(!observer.link.snapshot().enabled)
  click(observer,"Setup");main{observer.link.disconnect();observer.finish()};Thread.sleep(300)
  control("OBSERVER_END");control("FC_START")
  prefs.edit().putString("profile","FC_WIFI").putString("FC_WIFI.host","10.0.2.2").commit()
  val fc=startActivitySync(Intent(targetContext,GoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as GoActivity
  click(fc,"Connect");waitFor{fc.link.snapshot().fresh};click(fc,"Enable");waitFor{fc.link.snapshot().armReady};click(fc,"ARM");waitFor{fc.link.snapshot().armed}
  main{fc.link.axes(1700,1800);fc.link.stop()};waitFor{fc.link.snapshot().stopped};check(!fc.link.snapshot().armed);check(!fc.link.snapshot().enabled)
  main{fc.link.enable()};check(!fc.link.snapshot().enabled){"HOLD restored phone control"}
  main{fc.link.disconnect();fc.finish()};Thread.sleep(300);control("EXIT")
  finish(Activity.RESULT_OK,Bundle().apply{putString("stream","PASS: Go three tabs, no mission/Balance/LoRa, three themes and normal/large text; real UDP fixture discovery, neutral ARM, movement, telemetry loss, no auto-rearm, STOP, passive ELRS observer, ArduRover HOLD and offline Test isolation")})
 }catch(t:Throwable){runCatching{shot("go-failed.png")};finish(Activity.RESULT_CANCELED,Bundle().apply{putString("stream","FAIL: "+t.stackTraceToString())})}}
}
