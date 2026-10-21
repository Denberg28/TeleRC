package io.github.denberg28.telerc

internal object GoProtocol {
 fun radioSwitch(frame:ByteArray,target:Int)=GoRadioTelemetry.allowed(frame,target)
 fun requestRcChannels(sequence:Int,target:Int):ByteArray {
  val f=Mavlink.armDisarm(sequence,target,1,true).copyOf(39)
  val b=java.nio.ByteBuffer.wrap(f).order(java.nio.ByteOrder.LITTLE_ENDIAN)
  b.putFloat(6,65f);b.putFloat(10,200000f);f[34]=0xff.toByte();f[35]=1
  var crc=65535
  for(value in f.drop(1).map{it.toInt()and 255}+152){var t=value xor(crc and 255);t=(t xor(t shl 4))and 255;crc=((crc ushr 8)xor(t shl 8)xor(t shl 3)xor(t ushr 4))and 65535}
  return f+byteArrayOf(crc.toByte(),(crc ushr 8).toByte())
 }

 fun hold(sequence:Int,target:Int):ByteArray {
  val f=Mavlink.armDisarm(sequence,target,1,true).copyOf(39)
  f[10]=0;f[11]=0;f[12]=0x80.toByte();f[13]=0x40
  f[34]=176.toByte();f[35]=0
  var c=65535
  for(b in f.drop(1).map{it.toInt()and 255}+152){var t=b xor(c and 255);t=(t xor(t shl 4))and 255;c=((c ushr 8)xor(t shl 8)xor(t shl 3)xor(t ushr 4))and 65535}
  return f+byteArrayOf(c.toByte(),(c ushr 8).toByte())
 }

 fun heartbeat(sequence:Int):ByteArray {
  val p=byteArrayOf(0,0,0,0,6,8,0,0,3)
  val f=byteArrayOf(254.toByte(),9,sequence.toByte(),255.toByte(),190.toByte(),0)+p
  var c=65535
  for(b in f.drop(1).map{it.toInt()and 255}+50){var t=b xor(c and 255);t=(t xor(t shl 4))and 255;c=((c ushr 8)xor(t shl 8)xor(t shl 3)xor(t ushr 4))and 65535}
  return f+byteArrayOf(c.toByte(),(c ushr 8).toByte())
 }

 fun rc(sequence:Int,target:Int,steer:Int,drive:Int):ByteArray {
  require(target in 1..254 && listOf(steer,drive).all{it==0 || it in 1000..2000})
  val payload=ByteArray(18){0xff.toByte()}
  for((i,v) in listOf(steer,drive).withIndex()){payload[i*2]=v.toByte();payload[i*2+1]=(v ushr 8).toByte()}
  payload[16]=target.toByte();payload[17]=1
  val frame=byteArrayOf(0xfe.toByte(),18,sequence.toByte(),255.toByte(),190.toByte(),70)+payload
  var crc=65535
  for(value in frame.drop(1).map{it.toInt() and 255}+124){var t=value xor(crc and 255);t=(t xor(t shl 4))and 255;crc=((crc ushr 8)xor(t shl 8)xor(t shl 3)xor(t ushr 4))and 65535}
  return frame+byteArrayOf(crc.toByte(),(crc ushr 8).toByte())
 }
}

/** MAVLink common RC_CHANNELS (65, CRC118); CH7 is never overridden by Go. */
internal object GoRadioTelemetry {
 data class Sample(val phoneAllowed:Boolean,val bootMs:Long)
 fun allowed(frame:ByteArray,target:Int)=sample(frame,target)?.phoneAllowed
 fun sample(frame:ByteArray,target:Int):Sample? {
  if(frame.size<8)return null
  val v1=frame[0]==0xfe.toByte();val v2=frame[0]==0xfd.toByte()
  if(!v1&&!v2)return null
  val h=if(v1)6 else 10;val len=frame[1].toInt()and 255
  if(frame.size!=h+len+2||len !in (if(v1)42 else 41)..42)return null
  if(v2&&(frame[2].toInt()!=0||frame[8].toInt()!=0||frame[9].toInt()!=0))return null
  if((frame[if(v1)5 else 7].toInt()and 255)!=65||(frame[if(v1)3 else 5].toInt()and 255)!=target||(frame[if(v1)4 else 6].toInt()and 255)!=1)return null
  var crc=65535
  for(b in frame.copyOfRange(1,h+len).map{it.toInt()and 255}+118){var t=b xor(crc and 255);t=(t xor(t shl 4))and 255;crc=((crc ushr 8)xor(t shl 8)xor(t shl 3)xor(t ushr 4))and 65535}
  if((frame[h+len].toInt()and 255)!=(crc and 255)||(frame[h+len+1].toInt()and 255)!=(crc ushr 8))return null
  if((frame[h+40].toInt()and 255)<7)return null
  val ch7=(frame[h+16].toInt()and 255)or((frame[h+17].toInt()and 255)shl 8)
  val bootMs=(0..3).fold(0L){v,i->v or((frame[h+i].toLong()and 255)shl(i*8))}
  return Sample(ch7 in 1801..2200,bootMs)
 }
}
