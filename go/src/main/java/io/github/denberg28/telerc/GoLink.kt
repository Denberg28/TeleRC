package io.github.denberg28.telerc

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import java.net.DatagramSocket
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

/** Wi-Fi only. A single worker owns the socket, bounded ARM transaction and sends. */
internal class GoLink(context:Context,private val update:()->Unit):AutoCloseable {
 data class Snapshot(val connected:Boolean,val verified:Boolean,val fresh:Boolean,val armed:Boolean,val enabled:Boolean,val armReady:Boolean,val handset:Boolean,val stopped:Boolean,val receiver:Boolean,val text:String,val rx:Long,val tx:Long,val age:Long,val pending:Boolean)
 private val cm=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
 private val gate=GoControl()
 private val armRequest=GoArmRequest()
 private val lock=Any()
 @Volatile private var desired:GoEndpoint?=null
 @Volatile private var active=false
 @Volatile private var closed=false
 private var connected=false
 private var verified=false
 private var fc=false
 private var ownersSeen=false
 private var capable=false
 private var owner=0
 private var ownerAt=0L
 private var radioBlocked=false
 private var switchAt=0L
 private var receiver=false
 private var takeRequested=false
 private var awaitingArmUntil=0L
 private var stopPending=false
 private var message="Join rover Wi-Fi, then connect"
 private var steer=1500
 private var drive=1500
 private var rx=0L
 private var tx=0L
 private var sequence=0
 private fun now()=SystemClock.elapsedRealtime()
 private val worker=thread(name="telerc-go-link",isDaemon=true){run()}
 private fun ownerFresh(n:Long)=ownerAt>0&&n>=ownerAt&&n-ownerAt<750
 private fun switchFresh(n:Long)=switchAt>0&&n>=switchAt&&n-switchAt<750
 private fun mayUsePhone(n:Long)=!radioBlocked && (!fc || switchFresh(n)) && (!ownersSeen || ownerFresh(n)&&owner==1)
 fun snapshot():Snapshot=synchronized(lock){val n=now();Snapshot(connected,verified&&(if(fc)switchFresh(n) else capable&&ownersSeen&&ownerFresh(n)),gate.fresh(n),gate.armed,gate.enabled,gate.armReady(n),radioBlocked||ownersSeen&&owner==2,gate.stopped,receiver,message,rx,tx,gate.heartbeatAt?.let{n-it}?:-1,armRequest.active()||awaitingArmUntil>n)}
 fun connect(value:GoEndpoint){require(value.valid());disconnect();synchronized(lock){desired=value;active=true;message="Connecting…"};update()}
 fun foreground(value:Boolean){active=value;if(!value)stop(true)}
 fun axes(s:Int,d:Int)=synchronized(lock){steer=s.coerceIn(1100,1900);drive=d.coerceIn(1100,1900)}
 private fun revoke(text:String,sendStop:Boolean){
  gate.stop();armRequest.cancel();awaitingArmUntil=0;takeRequested=false;steer=1500;drive=1500
  if(sendStop)stopPending=true
  message=text
 }
 fun toggleArm()=synchronized(lock){
  val n=now()
  if(radioBlocked||ownersSeen&&owner==2)return@synchronized
  if(gate.armed||gate.enabled||armRequest.active()||awaitingArmUntil!=0L){revoke("Disarming…",true);return@synchronized}
  if(!active||!connected||!verified||!gate.fresh(n)||gate.stopped)return@synchronized
  if(fc&&!switchFresh(n)){message="Waiting for radio switch telemetry";return@synchronized}
  if(!fc&&(!ownersSeen||!capable)){message="Update Go rover firmware";return@synchronized}
  if(ownersSeen&&!ownerFresh(n)){message="Waiting for control ownership";return@synchronized}
  steer=1500;drive=1500;armRequest.begin(gate.epoch,n)
  if(ownersSeen&&owner!=1)takeRequested=true else gate.enable(n)
  message="Arming · checking neutral"
 }
 fun stop(controlOnly:Boolean=false)=synchronized(lock){
  val controlled=gate.enabled||armRequest.active()||awaitingArmUntil!=0L
  revoke("Disarmed",controlled||!controlOnly&&gate.armed&&!radioBlocked)
 }
 fun disconnect(){stop(true);synchronized(lock){desired=null;gate.disconnect();connected=false;verified=false;message="Disconnected"};update()}
 override fun close(){disconnect();closed=true;worker.interrupt()}
 private fun wifi():Network?=cm.allNetworks.firstOrNull{cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true}
 private fun run(){
  while(!closed){
   val config=desired
   if(config==null||!active){try{Thread.sleep(50)}catch(_:InterruptedException){};continue}
   var udp:DatagramSocket?=null
   var peerVerified=false
   try{
    val network=wifi()?:throw java.net.SocketException("Join rover Wi-Fi")
    val remote=InetAddress.getByName(config.host)
    // Assign before bind, so every failed bind is closed. Never reuse another
    // controller's UDP socket; new Go firmware replies to the actual source port.
    var fallback=false
    var channel=DatagramSocket(null);udp=channel
    try{channel.bind(InetSocketAddress(config.localPort))}catch(e:java.net.BindException){
     channel.close();if(config.localPort==0)throw e
     channel=DatagramSocket(null);udp=channel;channel.bind(InetSocketAddress(0));fallback=true
    }
    channel.soTimeout=20;network.bindSocket(channel)
    if(desired!==config){channel.close();continue}
    synchronized(lock){connected=true;verified=false;fc=false;gate.disconnect();armRequest.cancel();awaitingArmUntil=0;ownersSeen=false;capable=false;owner=0;ownerAt=0;switchAt=0;radioBlocked=false;receiver=false;stopPending=false;takeRequested=false;message="Waiting for rover"}
    var discover=0L;var sent=0L;var uiAt=0L;var networkAt=0L;var gcsAt=0L;var boot:Long?=null;var kind:Int?=null;var requestedRc=false;var lastFcBoot:Long?=null
    val opened=now()
    fun send(bytes:ByteArray){channel.send(DatagramPacket(bytes,bytes.size,remote,config.port));tx++}
    fun stopFrames(){
     val n=now()
     if(!peerVerified||!mayUsePhone(n))return
     repeat(3){
      send(GoProtocol.rc(sequence++,config.system,1500,1500))
      if(fc)send(GoProtocol.hold(sequence++,config.system))
      send(Mavlink.armDisarm(sequence++,config.system,1,false))
      if(!fc)send(GoProtocol.rc(sequence++,config.system,0,0))
     }
    }
    val input=ByteArray(8193)
    while(!closed&&desired===config&&active){
     val n=now()
     if(n-networkAt>=500){networkAt=n;if(network !in cm.allNetworks||wifi()!=network)throw java.net.SocketException("Wi-Fi changed")}
     synchronized(lock){
      if(gate.expire(n)){stopPending=true;revoke("Telemetry lost · disarmed",true)}
      if((gate.enabled||armRequest.active())&&((ownersSeen&&!ownerFresh(n))||(fc&&!switchFresh(n))||radioBlocked)){
       revoke(if(radioBlocked)"Transmitter control"else"Control telemetry lost",false);stopPending=false
      }
      if(armRequest.active()&&!armRequest.valid(gate.epoch,n))revoke("Arming expired · tap ARM",true)
      if(awaitingArmUntil!=0L&&n>=awaitingArmUntil)revoke("ARM not confirmed · tap ARM",true)
      if(stopPending){stopFrames();stopPending=false}
      if(n-discover>=500){discover=n;send("TELERC_DISCOVER_V1".toByteArray(Charsets.US_ASCII))}
      if(fc&&peerVerified&&!requestedRc){send(GoProtocol.requestRcChannels(sequence++,config.system));requestedRc=true}
      if(takeRequested&&armRequest.valid(gate.epoch,n)&&!radioBlocked){send(GoElrs.take(sequence++,config.system));takeRequested=false}
      if(fc&&gate.enabled&&gate.fresh(n)&&n-gcsAt>=1000){send(GoProtocol.heartbeat(sequence++));gcsAt=n}
      if(gate.enabled&&gate.fresh(n)&&mayUsePhone(n)&&n-sent>=50){
       val s=if(gate.armed)steer else 1500;val d=if(gate.armed)drive else 1500
       send(GoProtocol.rc(sequence++,config.system,s,d));gate.sent(s,d,n);sent=n
      }
      if(armRequest.valid(gate.epoch,n)&&mayUsePhone(n)&&gate.armReady(n)){
       send(Mavlink.armDisarm(sequence++,config.system,1,true));armRequest.cancel();awaitingArmUntil=n+1000;message="Confirming ARM…"
      }
      if(!peerVerified&&fallback&&n-opened>3000)message="No rover reply · close other controller"
     }
     try{
      val packet=DatagramPacket(input,input.size);channel.receive(packet)
      if(packet.address!=remote||packet.port!=config.port||packet.length>8192)continue
      synchronized(lock){
       rx++
       for(frame in Mavlink.frames(packet.data.copyOfRange(packet.offset,packet.offset+packet.length))){
        val hb=Mavlink.heartbeat(frame)
        if(hb!=null&&hb.system==config.system){
         val offset=if(frame[0]==0xfe.toByte())6 else 10
         val autopilot=frame[offset+5].toInt()and 255
         if(frame[offset+4].toInt()!=10||autopilot !in listOf(0,3)||kind!=null&&kind!=autopilot){revoke("Unsupported rover",true);verified=false;continue}
         var mode=0L;for(i in 0..3)mode=mode or((frame[offset+i].toLong()and 255)shl(8*i))
         if(autopilot==0&&mode==0L){revoke("Matched Go firmware required",true);verified=false;continue}
         kind=autopilot;fc=autopilot==3;peerVerified=true;verified=true
         if(!fc&&boot!=null&&boot!=mode){revoke("Rover restarted · tap ARM",false);ownersSeen=false;capable=false;ownerAt=0;owner=0;radioBlocked=false;stopPending=false}
         if(!fc)boot=mode
         if(fc&&mode!=0L){revoke(if(mode==4L)"HOLD · select MANUAL"else"Select MANUAL",false);gate.receiverStop(true)}else if(fc||!ownersSeen)gate.receiverStop(false)
         val recovering=gate.heartbeatAt!=null&&!gate.fresh(now())
         if(recovering)revoke("Link restored · tap ARM",false)
         val wasArmed=gate.armed;gate.heartbeat(hb.armed,now())
         if(hb.armed&&gate.enabled){awaitingArmUntil=0;message="ARMED"}
         else if(wasArmed&&!hb.armed&&gate.enabled)revoke("Disarmed",false)
         else if(!gate.enabled&&!armRequest.active())message=when{radioBlocked||ownersSeen&&owner==2->"Transmitter control";gate.stopped->"Select MANUAL";hb.armed->"Armed elsewhere";!fc&&!capable->if(now()-opened>1500)"Update Go rover firmware"else"Checking rover firmware";else->"Connected · tap ARM"}
        }
        if(peerVerified&&!fc)GoElrs.status(frame,config.system)?.let{s->
         ownersSeen=true;capable=s.capable;owner=s.owner;ownerAt=now();receiver=s.receiver;radioBlocked=s.takeover||s.owner==2
         gate.receiverStop(s.stopped)
         if(!capable){revoke("Update Go rover firmware",false);stopPending=false}
         else if(radioBlocked||s.stopped){revoke(if(s.stopped)"Radio STOP active"else"Transmitter control",false);stopPending=false}
         else if(armRequest.valid(gate.epoch,now())&&owner==1){gate.enable(now())}
         else if(gate.enabled&&owner!=1){revoke("Phone authority released",false);stopPending=false}
        }
        if(peerVerified&&fc)GoRadioTelemetry.sample(frame,config.system)?.let{s->
         val previousBoot=lastFcBoot
         if(previousBoot!=null&&s.bootMs<previousBoot){revoke("FC restarted · tap ARM",false);stopPending=false}
         lastFcBoot=s.bootMs;switchAt=now();radioBlocked=!s.phoneAllowed
         if(radioBlocked){revoke("Transmitter control",false);stopPending=false}
        }
        Mavlink.commandAck(frame)?.takeIf{it.system==config.system&&it.command==400&&it.result!=0}?.let{revoke("ARM ${it.resultText}",true)}
       }
      }
     }catch(_:SocketTimeoutException){}
     if(n-uiAt>=100){uiAt=n;update()}
    }
    synchronized(lock){if(stopPending||gate.enabled||armRequest.active()||awaitingArmUntil!=0L)stopFrames()}
   }catch(e:Exception){synchronized(lock){revoke(when{desired==null->"Disconnected";e is java.net.BindException->"Phone port busy · close other controller";else->"${e.message?.take(48)?:"Wi-Fi lost"} · reconnecting"},false);connected=false;verified=false};update()}
   finally{udp?.close();synchronized(lock){connected=false;verified=false;gate.disconnect();armRequest.cancel();awaitingArmUntil=0;stopPending=false}}
   val until=now()+1000
   while(!closed&&desired===config&&now()<until){try{Thread.sleep(50)}catch(_:InterruptedException){} }
  }
 }
}
