package io.github.denberg28.telerc

internal enum class GoProfile(val label:String,val host:String,val port:Int=14550,val localPort:Int=14550) {
 WIFI("Wi-Fi · four-wheel rover","192.168.4.1"),
 ELRS("ELRS · four-wheel receiver","192.168.4.1"),
 FC_WIFI("Wi-Fi · ArduRover","192.168.4.1"),
 BACKPACK("ELRS · TX Backpack","10.0.0.1",14555);
 val handsetOnly get()=this==BACKPACK
 val standalone get()=this==WIFI || this==ELRS
}
internal data class GoEndpoint(val profile:GoProfile,val host:String=profile.host,val port:Int=profile.port,val localPort:Int=profile.localPort,val system:Int=1) {
 fun valid():Boolean {
  val octets=host.split('.')
  return octets.size==4 && octets.all { it.toIntOrNull() in 0..255 && it.isNotEmpty() && it.length<=3 } &&
   octets[0].toInt() in 1..223 && octets[0]!="127" && octets.last().toInt() in 1..254 &&
   port in 1..65535 && localPort in 1024..65535 && system in 1..254 &&
   (!profile.standalone || port==14550 && localPort==14550 && system==1)
 }
}
/** A recovered heartbeat never restores authority or an old ARM intention. */
internal class GoControl {
 var epoch=0L; private set
 var heartbeatAt:Long?=null;private set
 var armed=false;private set
 var enabled=false;private set
 var stopped=false;private set
 private var sentNeutralAt:Long?=null
 private var firstNeutral:Long?=null
 fun fresh(now:Long)=heartbeatAt?.let{now>=it && now-it<1500}==true
 fun heartbeat(value:Boolean,now:Long) {if(!fresh(now) && heartbeatAt!=null)stop();armed=value;heartbeatAt=now}
 fun enable(now:Long):Boolean {if(!fresh(now)||armed||stopped)return false;enabled=true;return true}
 fun sent(s:Int,d:Int,now:Long) {
  if(!enabled || s !in 1475..1525 || d !in 1475..1525){firstNeutral=null;sentNeutralAt=null;return}
  if(sentNeutralAt==null || now<sentNeutralAt!! || now-sentNeutralAt!!>=250)firstNeutral=now
  sentNeutralAt=now
 }
 fun armReady(now:Long)=enabled && !stopped && fresh(now) && !armed &&
  sentNeutralAt?.let{now>=it&&now-it<200}==true && firstNeutral?.let{now-it>=300}==true
 fun maySendArm(capturedEpoch:Long,requestedAt:Long,now:Long)=capturedEpoch==epoch && now>=requestedAt && now-requestedAt<500 && armReady(now)
 fun expire(now:Long):Boolean {if(enabled && !fresh(now)){stop();return true};return false}
 fun stop() {epoch++;enabled=false;firstNeutral=null;sentNeutralAt=null;armed=false}
 fun disconnect(){stop();heartbeatAt=null;stopped=false}
 fun receiverStop(value:Boolean){if(value)stop();stopped=value}
}
