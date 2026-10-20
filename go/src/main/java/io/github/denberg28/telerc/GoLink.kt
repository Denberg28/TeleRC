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
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** One socket worker, one replaceable command slot, no motion/ARM retry queue. */
internal class GoLink(context:Context,private val update:()->Unit):AutoCloseable {
 data class Snapshot(val connected:Boolean,val verified:Boolean,val fresh:Boolean,val armed:Boolean,val enabled:Boolean,val armReady:Boolean,val handset:Boolean,val stopped:Boolean,val receiver:Boolean,val text:String,val rx:Long,val tx:Long,val age:Long)
 private data class Intent(val kind:String,val epoch:Long,val at:Long)
 private val cm=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
 private val gate=GoControl()
 private val lock=Any()
 private val intent=AtomicReference<Intent?>()
 @Volatile private var desired:GoEndpoint?=null
 @Volatile private var active=false
 @Volatile private var closed=false
 @Volatile private var socket:DatagramSocket?=null
 private var verified=false
 private var connected=false
 private var message="Choose a link, then Connect"
 private var receiver=false
 private var owned=false
 private var ownerAt=0L
 private var takeAt:Long?=null
 private var steer=1500;private var drive=1500
 private var rx=0L;private var tx=0L
 private var sequence=0
 private var needStop=false
 private fun now()=SystemClock.elapsedRealtime()
 private val worker=thread(name="telerc-go-link",isDaemon=true){run()}
 fun snapshot():Snapshot=synchronized(lock){val n=now();Snapshot(connected,verified,gate.fresh(n),gate.armed,gate.enabled,gate.armReady(n),desired?.profile?.handsetOnly==true,gate.stopped,receiver,message,rx,tx,gate.heartbeatAt?.let{n-it}?:-1)}
 fun connect(value:GoEndpoint){require(value.valid());disconnect();synchronized(lock){desired=value;active=true;message="Connecting…"};update()}
 fun foreground(value:Boolean){active=value;if(!value)stop(true)}
 fun axes(s:Int,d:Int)=synchronized(lock){steer=s.coerceIn(1100,1900);drive=d.coerceIn(1100,1900)}
 fun enable()=synchronized(lock){
  val p=desired?.profile ?:return@synchronized
  if(!active || p.handsetOnly || !gate.fresh(now()))return@synchronized
  if(p==GoProfile.ELRS && !owned){takeAt=now();intent.set(Intent("TAKE",gate.epoch,now()));message="Waiting for phone control"}
  else if(gate.enable(now()))message="Center sticks, then ARM"
 }
 fun arm()=synchronized(lock){if(active && gate.armReady(now()))intent.set(Intent("ARM",gate.epoch,now()))}
 fun stop(controlOnly:Boolean=false)=synchronized(lock){val controlled=gate.enabled || takeAt!=null;gate.stop();steer=1500;drive=1500;intent.set(null);takeAt=null;owned=false;if(!controlOnly || controlled)needStop=true;message="Stopped · press Enable to operate"}
 fun disconnect(){stop(true);synchronized(lock){desired=null;gate.disconnect();connected=false;verified=false;message="Disconnected"};update()}
 override fun close(){disconnect();closed=true;worker.interrupt()}
 private fun wifi():Network?=cm.allNetworks.firstOrNull{cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true}
 private fun run(){
  while(!closed){
   val config=desired
   if(config==null || !active){try{Thread.sleep(50)}catch(_:InterruptedException){};continue}
   var udp:DatagramSocket?=null
   try {
    val network=wifi() ?:throw java.net.SocketException("Join rover Wi-Fi")
    val remote=InetAddress.getByName(config.host)
    val channel=DatagramSocket(null).apply {reuseAddress=false;bind(InetSocketAddress(config.localPort));soTimeout=20;network.bindSocket(this)}
    udp=channel;socket=channel
    if(desired!==config){channel.close();continue}
    synchronized(lock){connected=true;verified=false;gate.disconnect();owned=false;receiver=false;ownerAt=0;takeAt=null;needStop=false;message="Waiting for rover"}
    var discover=0L;var sent=0L;var ui=0L;var networkAt=0L;var gcsAt=0L;var boot:Long?=null;var verifiedPeer=false
    fun send(bytes:ByteArray){if(config.profile.handsetOnly && !FcElrs.isRegistration(bytes))throw java.net.SocketException("Handset link rejects phone commands");channel.send(DatagramPacket(bytes,bytes.size,remote,config.port));synchronized(lock){tx++}}
    fun stopFrames(){if(verifiedPeer && !config.profile.handsetOnly){repeat(3){send(GoProtocol.rc(sequence++,config.system,1500,1500));send(Mavlink.armDisarm(sequence++,config.system,1,false));send(GoProtocol.rc(sequence++,config.system,0,0))}}}
    val input=ByteArray(8193)
    while(!closed && desired===config && active && socket===channel){
     val n=now()
     if(n-networkAt>=500){networkAt=n;if(network !in cm.allNetworks || wifi()!=network)throw java.net.SocketException("Wi-Fi changed")}
     synchronized(lock){
      if(gate.expire(n)){needStop=true;owned=false;takeAt=null;intent.set(null);steer=1500;drive=1500;message="Telemetry lost · control stopped"}
      if(config.profile==GoProfile.ELRS && gate.enabled && (n<ownerAt || n-ownerAt>=750 || !owned)){gate.stop();needStop=true;intent.set(null);message="Control ownership lost"}
      if(takeAt?.let{n>=it&&n-it>=1000}==true){takeAt=null;gate.stop();owned=false;needStop=true;message="Takeover not confirmed · retry deliberately"}
      if(needStop){stopFrames();needStop=false}
     }
     if(n-discover>=500){discover=n;send(if(config.profile.handsetOnly)FcElrs.registration(sequence++) else "TELERC_DISCOVER_V1".toByteArray(Charsets.US_ASCII))}
     synchronized(lock){
      val request=intent.getAndSet(null)
      if(request!=null && request.epoch==gate.epoch && n>=request.at && n-request.at<500 && active && gate.fresh(n)){
       if(request.kind=="TAKE" && config.profile==GoProfile.ELRS)send(GoElrs.take(sequence++,config.system))
       if(request.kind=="ARM" && gate.maySendArm(request.epoch,request.at,n)){send(Mavlink.armDisarm(sequence++,config.system,1,true));message="ARM requested · waiting for rover"}
      }
      if(config.profile==GoProfile.FC_WIFI && gate.enabled && gate.fresh(n) && n-gcsAt>=1000){send(GoProtocol.heartbeat(sequence++));gcsAt=n}
      if(gate.enabled && gate.fresh(n) && !config.profile.handsetOnly && n-sent>=50){
       val s=if(gate.armed)steer else 1500;val d=if(gate.armed)drive else 1500
       send(GoProtocol.rc(sequence++,config.system,s,d));gate.sent(s,d,n);sent=n
      }
     }
     try {
      val packet=DatagramPacket(input,input.size);channel.receive(packet)
      if(packet.address!=remote || packet.port!=config.port || packet.length>8192)continue
      synchronized(lock){
       rx++
       for(frame in Mavlink.frames(packet.data.copyOfRange(packet.offset,packet.offset+packet.length))){
        val hb=Mavlink.heartbeat(frame)
        if(hb!=null && hb.system==config.system){
         val offset=if(frame[0]==0xfe.toByte())6 else 10
         if(frame[offset+4].toInt()!=10 || frame[offset+5].toInt()!=(if(config.profile.standalone)0 else 3)){gate.disconnect();intent.set(null);verified=false;verifiedPeer=false;needStop=false;takeAt=null;owned=false;message="Wrong rover profile";continue}
         if(frame[offset+4].toInt()==10){
          var mode=0L;for(i in 0..3)mode=mode or ((frame[offset+i].toLong() and 255) shl (8*i))
          if(config.profile.standalone && mode==0L){gate.disconnect();intent.set(null);takeAt=null;owned=false;verified=false;verifiedPeer=false;needStop=false;message="Matched Go firmware required";continue}
          if(config.profile==GoProfile.FC_WIFI && mode!=0L){val wasControlled=gate.enabled;gate.disconnect();intent.set(null);needStop=needStop||wasControlled;message="Select MANUAL on ArduRover";continue}
          if(config.profile.standalone){
           if(boot!=null && boot!=mode){gate.disconnect();intent.set(null);takeAt=null;owned=false;steer=1500;drive=1500;needStop=true;message="Rover restarted · Enable again"}
           boot=mode
          }
          verified=true;verifiedPeer=true;gate.heartbeat(hb.armed,now())
          if(!gate.enabled)message=if(config.profile.handsetOnly)"ELRS handset control" else if(hb.armed)"Rover armed · stop before enabling" else if(config.profile==GoProfile.ELRS)"ELRS handset · Take control for phone" else "Connected · press Enable"
          else if(hb.armed)message="ARMED · drive ready" else if(gate.armReady(now()))message="Ready · ARM" 
         }
        }
        if(config.profile==GoProfile.ELRS)GoElrs.status(frame,config.system)?.let{status->
         val wasControlled=gate.enabled || takeAt!=null;receiver=status.receiver;ownerAt=now();owned=status.owner==1 && !status.stopped;gate.receiverStop(status.stopped)
         val at=takeAt
         if(at!=null && now()-at in 0..750 && owned && active){takeAt=null;gate.enable(now());message="Phone control · center sticks, then ARM"}
         if(status.stopped){needStop=needStop||wasControlled;message="Transmitter STOP active"}
        }
        Mavlink.commandAck(frame)?.takeIf{it.system==config.system && it.command==400 && it.result!=0}?.let{
         gate.stop();intent.set(null);needStop=true;message="ARM ${it.resultText} · check rover"
        }
       }
      }
     }catch(_:SocketTimeoutException){}
     if(n-ui>=200){ui=n;update()}
    }
    try{if(synchronized(lock){needStop || gate.enabled || takeAt!=null})stopFrames()}catch(_:Exception){}
   }catch(e:Exception){synchronized(lock){gate.disconnect();connected=false;verified=false;owned=false;intent.set(null);takeAt=null;steer=1500;drive=1500;message=if(desired==null)"Disconnected" else "${e.message?.take(80) ?: "Link lost"} · retrying"};update()}
   finally{udp?.close();if(socket===udp)socket=null;synchronized(lock){connected=false;verified=false;gate.disconnect()}}
   val until=now()+1000
   while(!closed && desired===config && now()<until){try{Thread.sleep(50)}catch(_:InterruptedException){} }
  }
 }
}
