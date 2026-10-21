package io.github.denberg28.telerc

import java.io.ByteArrayOutputStream
import java.io.InputStream

internal data class GoUpdateCandidate(val version:String,val url:String,val sha256:String,val bytes:Long)

/** Go has its own stable tags; the full TeleRC Latest release is never an update. */
internal object GoUpdateRules {
 const val MAX_METADATA_BYTES=1024*1024
 const val MAX_APK_BYTES=50_000_000L
 fun version(value:String):List<Int>? {
  if(!Regex("[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}").matches(value))return null
  return value.split('.').map{it.toInt()}
 }
 fun compare(a:String,b:String):Int {
  val aa=version(a)?:return -1;val bb=version(b)?:return -1
  for(i in 0..2){val c=aa[i].compareTo(bb[i]);if(c!=0)return c};return 0
 }
 fun tagVersion(tag:String)=if(tag.startsWith("go-v"))tag.removePrefix("go-v").takeIf{version(it)!=null}else null
 fun candidate(tag:String,current:String,draft:Boolean,prerelease:Boolean,name:String,url:String,digest:String,bytes:Long):GoUpdateCandidate? {
  val v=tagVersion(tag)?:return null
  if(draft||prerelease||compare(v,current)<=0||name!="TeleRC-Go-v$v.apk"||bytes !in 100_000L..MAX_APK_BYTES)return null
  if(url!="https://github.com/Denberg28/TeleRC/releases/download/go-v$v/TeleRC-Go-v$v.apk")return null
  if(!Regex("sha256:[a-fA-F0-9]{64}").matches(digest))return null
  return GoUpdateCandidate(v,url,digest.removePrefix("sha256:").lowercase(),bytes)
 }
 fun archiveAllowed(packageName:String,installedPackage:String,code:Long,installedCode:Long,signers:Set<String>?,installedSigners:Set<String>?)=
  packageName==installedPackage&&code>installedCode&&!signers.isNullOrEmpty()&&signers==installedSigners
 fun read(input:InputStream):String {
  val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
  while(true){val n=input.read(buffer);if(n<0)break;require(out.size()<=MAX_METADATA_BYTES-n){"Metadata too large"};out.write(buffer,0,n)}
  return out.toString("UTF-8")
 }
}
