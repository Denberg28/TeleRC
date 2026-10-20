package io.github.denberg28.telerc

internal object GoProtocol {
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
