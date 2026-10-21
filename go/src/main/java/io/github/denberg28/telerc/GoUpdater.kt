package io.github.denberg28.telerc

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import io.github.denberg28.telerc.go.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Explicit stable-Go check, opt-in download, signer/hash verification, Android installer. */
internal class GoUpdater(private val activity:Activity,private val report:(String)->Unit,private val offer:(GoUpdateCandidate,()->Unit)->Unit,private val fetchOverride:((String)->String?)?=null) {
 private val checking=AtomicBoolean(false)
 private val verifying=AtomicBoolean(false)
 @Volatile private var closed=false
 private val downloads=activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
 private val apk=File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),"telerc-go-update.apk")
 private var downloadId=-1L
 private var expected:GoUpdateCandidate?=null
 private var pendingInstall:Uri?=null
 private var registered=false
 private fun ui(action:()->Unit){activity.runOnUiThread{if(!closed&&!activity.isFinishing&&!activity.isDestroyed)action()}}
 private val receiver=object:BroadcastReceiver(){
  override fun onReceive(context:Context,intent:Intent){
   val id=downloadId;val candidate=expected
   if(closed||id<0||candidate==null||intent.action!=DownloadManager.ACTION_DOWNLOAD_COMPLETE||intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID,-1)!=id||!verifying.compareAndSet(false,true))return
   thread(name="go-verify-update"){
    val status=runCatching{downloads.query(DownloadManager.Query().setFilterById(id)).use{c->if(c.moveToFirst())c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))else null}}.getOrNull()
    val complete=status==DownloadManager.STATUS_SUCCESSFUL
    val finished=complete||status==DownloadManager.STATUS_FAILED
    val uri=if(complete)runCatching{downloads.getUriForDownloadedFile(id)}.getOrNull()else null
    val valid=complete&&uri!=null&&runCatching{apk.length()==candidate.bytes&&GoApkVerifier.verify(activity,apk,candidate.sha256,candidate.version)}.getOrDefault(false)
    ui{
     verifying.set(false)
     if(id!=downloadId||!finished)return@ui
     downloadId=-1;expected=null
     if(valid){pendingInstall=uri;offerInstall()}else{apk.delete();report(if(complete)"Update verification failed. APK discarded."else"Download failed. Check internet and try again.")}
    }
   }
  }
 }
 fun check(){
  if(closed)return
  if(downloadId>=0||verifying.get()){report("An update is downloading or being verified.");return}
  if(!checking.compareAndSet(false,true)){report("Checking for updates…");return}
  report("Checking for updates…")
  thread(name="go-check-update"){
   try{
    // Matching refs is compact and Go-specific, unlike /releases/latest.
    val refs=JSONArray(get("https://api.github.com/repos/Denberg28/TeleRC/git/matching-refs/tags/go-v")?:"[]")
    val versions=(0 until refs.length()).mapNotNull{i->GoUpdateRules.tagVersion(refs.getJSONObject(i).optString("ref").removePrefix("refs/tags/"))}
     .filter{GoUpdateRules.compare(it,BuildConfig.VERSION_NAME)>0}.distinct().sortedWith{a,b->GoUpdateRules.compare(b,a)}
    var candidate:GoUpdateCandidate?=null
    for(v in versions.take(8)){
     if(closed)return@thread
     val metadata=get("https://api.github.com/repos/Denberg28/TeleRC/releases/tags/go-v$v")?:continue
     val release=JSONObject(metadata);val assets=release.optJSONArray("assets")?:continue
     for(i in 0 until assets.length()){
      val a=assets.getJSONObject(i)
      candidate=GoUpdateRules.candidate(release.optString("tag_name"),BuildConfig.VERSION_NAME,release.optBoolean("draft",true),release.optBoolean("prerelease",true),a.optString("name"),a.optString("browser_download_url"),a.optString("digest"),a.optLong("size",0))
      if(candidate!=null)break
     }
     if(candidate!=null)break
    }
    val found=candidate
    ui{if(found==null)report(if(versions.isNotEmpty())"No verified stable Go update found. Try again later."else"TeleRC Go is up to date (v${BuildConfig.VERSION_NAME}).")else offer(found){download(found)}}
   }catch(_:Exception){ui{report("Could not check updates. Connect to internet and try again.")}}
   finally{checking.set(false)}
  }
 }
 private fun get(url:String):String? {
  fetchOverride?.let{return it(url)}
  val c=URL(url).openConnection() as HttpURLConnection
  c.connectTimeout=8000;c.readTimeout=8000;c.instanceFollowRedirects=false
  c.setRequestProperty("Accept","application/vnd.github+json");c.setRequestProperty("User-Agent","TeleRC-Go-Android")
  try{if(c.responseCode==404)return null;check(c.responseCode==200){"HTTP ${c.responseCode}"};return c.inputStream.use(GoUpdateRules::read)}finally{c.disconnect()}
 }
 private fun download(candidate:GoUpdateCandidate){
  if(closed||downloadId>=0||verifying.get())return
  // Revalidate the approved asset immediately before giving it to DownloadManager.
  if(GoUpdateRules.candidate("go-v${candidate.version}",BuildConfig.VERSION_NAME,false,false,"TeleRC-Go-v${candidate.version}.apk",candidate.url,"sha256:${candidate.sha256}",candidate.bytes)==null){report("Update was rejected.");return}
  try{
   pendingInstall=null;apk.delete();expected=candidate
   if(!registered){activity.registerReceiver(receiver,IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),Context.RECEIVER_EXPORTED);registered=true}
   val request=DownloadManager.Request(Uri.parse(candidate.url)).setTitle("TeleRC Go update").setDescription("Downloading TeleRC Go v${candidate.version}")
    .setMimeType("application/vnd.android.package-archive").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
    .setDestinationInExternalFilesDir(activity,Environment.DIRECTORY_DOWNLOADS,apk.name)
   downloadId=downloads.enqueue(request);report("Downloading TeleRC Go v${candidate.version}…")
  }catch(_:Exception){downloadId=-1;expected=null;report("Could not start download. Check internet and try again.")}
 }
 fun resumePendingInstall(){if(pendingInstall!=null&&activity.packageManager.canRequestPackageInstalls())offerInstall()}
 private fun offerInstall(){
  val uri=pendingInstall?:return
  if(!activity.hasWindowFocus()){report("Update verified. Return to TeleRC Go to install.");return}
  if(!activity.packageManager.canRequestPackageInstalls()){
   report("Allow TeleRC Go to install updates, then return here.")
   runCatching{activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${activity.packageName}")))}
    .onFailure{report("Open Android settings to allow TeleRC Go updates.")}
   return
  }
  try{
   activity.startActivity(Intent(Intent.ACTION_VIEW).apply{setDataAndType(uri,"application/vnd.android.package-archive");clipData=ClipData.newUri(activity.contentResolver,"TeleRC Go update",uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)})
   pendingInstall=null
  }catch(_:Exception){report("Android could not open the APK installer.")}
 }
 fun close(){
  closed=true
  if(registered){runCatching{activity.unregisterReceiver(receiver)};registered=false}
  if(downloadId>=0){runCatching{downloads.remove(downloadId)};apk.delete()}
  // Android's installer can still be reading a verified APK after this Activity
  // is destroyed. Retain completed files until the next explicit download.
  downloadId=-1;expected=null;pendingInstall=null
 }
}

internal object GoApkVerifier {
 fun hash(file:File):String {
  val digest=MessageDigest.getInstance("SHA-256")
  file.inputStream().buffered().use{input->val bytes=ByteArray(8192);while(true){val n=input.read(bytes);if(n<0)break;digest.update(bytes,0,n)}}
  return digest.digest().joinToString(""){"%02x".format(it.toInt()and 255)}
 }
 @Suppress("DEPRECATION")
 fun verify(activity:Activity,apk:File,expectedHash:String,expectedVersion:String?=null):Boolean {
  if(!apk.isFile||apk.length() !in 100_000L..GoUpdateRules.MAX_APK_BYTES||hash(apk)!=expectedHash)return false
  val pm=activity.packageManager;val flags=if(Build.VERSION.SDK_INT>=28)PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
  val candidate=pm.getPackageArchiveInfo(apk.absolutePath,flags)?:return false
  val installed=pm.getPackageInfo(activity.packageName,flags)
  val code=if(Build.VERSION.SDK_INT>=28)candidate.longVersionCode else candidate.versionCode.toLong()
  val current=if(Build.VERSION.SDK_INT>=28)installed.longVersionCode else installed.versionCode.toLong()
  val signers=if(Build.VERSION.SDK_INT>=28)candidate.signingInfo?.apkContentsSigners?.map{it.toCharsString()}?.toSet()else candidate.signatures?.map{it.toCharsString()}?.toSet()
  val currentSigners=if(Build.VERSION.SDK_INT>=28)installed.signingInfo?.apkContentsSigners?.map{it.toCharsString()}?.toSet()else installed.signatures?.map{it.toCharsString()}?.toSet()
  return (expectedVersion==null||candidate.versionName==expectedVersion)&&GoUpdateRules.archiveAllowed(candidate.packageName,activity.packageName,code,current,signers,currentSigners)
 }
}
