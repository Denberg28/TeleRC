package io.github.denberg28.telerc

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.*
import io.github.denberg28.telerc.go.R

/** One landscape control screen; configuration stays in compact, stopped popups. */
class GoActivity:Activity(){
 internal lateinit var link:GoLink
 private val ui=Handler(Looper.getMainLooper())
 private val prefs by lazy{getSharedPreferences("go",MODE_PRIVATE)}
 private var status:TextView?=null
 private var arm:Button?=null
 private var connect:Button?=null
 private var left:JoystickView?=null
 private var right:JoystickView?=null
 private var steerLabel:TextView?=null
 private var driveLabel:TextView?=null
 private var steer=1500
 private var drive=1500
 private var showing:Dialog?=null
 private var responseDirty=false
 private fun dp(value:Int)=(value*resources.displayMetrics.density+.5f).toInt()
 private fun row()=dp((48*maxOf(1f,resources.configuration.fontScale)).toInt())
 private fun text(value:String,size:Float=14f)=TextView(this).apply{
  text=value;textSize=size;setTextColor(Color.rgb(236,242,250));includeFontPadding=false
  setLineSpacing(dp(2).toFloat(),1f);setShadowLayer(dp(2).toFloat(),0f,dp(1).toFloat(),Color.BLACK)
 }
 private fun button(value:String,filled:Boolean=false,click:()->Unit)=AppUi.button(this,value,filled,click)
 private fun shape(color:Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(14).toFloat()}
 override fun onCreate(savedInstanceState:Bundle?){
  // Fixed dark controls keep labels readable against the supplied background.
  getSharedPreferences("appearance",0).edit().putString("background","DARK").putString("accent","Blue").apply()
  setTheme(android.R.style.Theme_Material_NoActionBar)
  super.onCreate(savedInstanceState)
  prefs.edit().putString("profile","WIFI").apply()
  link=GoLink(this){ui.removeCallbacks(refreshTask);ui.post(refreshTask)}
  render()
 }
 private val refreshTask=Runnable{if(!isFinishing&&!isDestroyed)refresh()}
 private fun endpoint()=GoEndpoint(GoProfile.WIFI,prefs.getString("WIFI.host","192.168.4.1")?:"192.168.4.1",
  prefs.getInt("WIFI.port",14550),prefs.getInt("WIFI.local",14550),prefs.getInt("WIFI.system",1))
 private fun phoneConnect(){
  if(link.snapshot().connected){link.disconnect();resetSticks();refresh();return}
  val value=endpoint()
  if(value.valid()){link.connect(value);refresh()}else connectionDetails()
 }
 private fun resetSticks(){left?.reset();right?.reset();steer=1500;drive=1500;refreshLabels()}
 private fun render(){
  showing?.setOnDismissListener(null);showing?.dismiss();showing=null;resetSticks();left=null;right=null
  val frame=FrameLayout(this)
  frame.addView(ImageView(this).apply{
   setImageResource(R.drawable.go_background);scaleType=ImageView.ScaleType.CENTER_CROP
   importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
  },FrameLayout.LayoutParams(-1,-1))
  // Light tint preserves the image while keeping the edge controls readable.
  frame.addView(View(this).apply{setBackgroundColor(0x28071120)},FrameLayout.LayoutParams(-1,-1))
  val root=LinearLayout(this).apply{
   keepScreenOn=true;orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(8))
   setOnApplyWindowInsetsListener{v,insets->
    if(android.os.Build.VERSION.SDK_INT>=30){
     val safe=insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
     v.setPadding(dp(12)+safe.left,dp(8)+safe.top,dp(12)+safe.right,dp(8)+safe.bottom)
    }else{
     @Suppress("DEPRECATION")
     v.setPadding(dp(12)+insets.systemWindowInsetLeft,dp(8)+insets.systemWindowInsetTop,dp(12)+insets.systemWindowInsetRight,dp(8)+insets.systemWindowInsetBottom)
    };insets}
  }
  val header=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;isBaselineAligned=false}
  header.addView(text("TeleRC Go",18f).apply{setTypeface(typeface,android.graphics.Typeface.BOLD)},LinearLayout.LayoutParams(0,-2,1f))
  connect=button("Phone connect",true){phoneConnect()}.apply{contentDescription="Phone connection; Wi-Fi by default"}
  header.addView(connect,LinearLayout.LayoutParams(dp(150),row()))
  header.addView(button("Settings"){settings()},LinearLayout.LayoutParams(dp(96),row()).apply{leftMargin=dp(8)})
  root.addView(header,LinearLayout.LayoutParams(-1,-2))
  val controls=LinearLayout(this).apply{isBaselineAligned=false}
  fun stick(vertical:Boolean)=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;setPadding(dp(4),dp(8),dp(4),0)
   val label=text(if(vertical)"DRIVE · CH2" else "STEER · CH1",13f).apply{gravity=Gravity.CENTER}
   if(vertical)driveLabel=label else steerLabel=label
   addView(label,LinearLayout.LayoutParams(-1,-2))
   val joystick=JoystickView(this@GoActivity,vertical){value->
    if(vertical)drive=value else steer=value;link.axes(steer,drive);refreshLabels()
   }
   if(vertical)this@GoActivity.right=joystick else this@GoActivity.left=joystick
   addView(joystick,LinearLayout.LayoutParams(-1,0,1f))
  }
  controls.addView(stick(false),LinearLayout.LayoutParams(0,-1,1.25f))
  val center=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(6),dp(8),dp(6),dp(8))}
  status=text("Join rover Wi-Fi, then Phone connect",13f).apply{
   gravity=Gravity.CENTER;maxLines=5;ellipsize=android.text.TextUtils.TruncateAt.END
   background=shape(0xc0112033.toInt());setPadding(dp(8),dp(8),dp(8),dp(8))
  }
  center.addView(status,LinearLayout.LayoutParams(-1,-2))
  arm=button("ARM",true){resetSticks();link.toggleArm();refresh()}
  center.addView(arm,LinearLayout.LayoutParams(-1,row()).apply{topMargin=dp(8)})
  controls.addView(center,LinearLayout.LayoutParams(0,-1,1f))
  controls.addView(stick(true),LinearLayout.LayoutParams(0,-1,1.25f))
  root.addView(controls,LinearLayout.LayoutParams(-1,0,1f))
  frame.addView(root,FrameLayout.LayoutParams(-1,-1));setContentView(frame);refresh()
 }
 private fun refreshLabels(){steerLabel?.text="STEER · $steer";driveLabel?.text="DRIVE · $drive"}
 private fun refresh(){
  if(!::link.isInitialized)return
  val s=link.snapshot()
  status?.text=s.text
  arm?.text=when{ s.handset->"Radio control";s.pending->"Cancel arming";s.armed->"DISARM";else->"ARM" }
  arm?.isEnabled=!s.handset&&(s.pending||s.fresh&&s.verified&&!s.stopped)
  val canDrive=s.fresh&&s.enabled&&s.armed&&!s.handset
  if(left?.isEnabled!=canDrive)left?.isEnabled=canDrive
  if(right?.isEnabled!=canDrive)right?.isEnabled=canDrive
  connect?.text=if(s.connected)"Disconnect" else "Phone connect"
 }
 private fun dialog(title:String,content:View){
  link.stop(true);resetSticks();showing?.setOnDismissListener(null);showing?.dismiss();showing=null
  val d=Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE)
  val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(12),dp(16),dp(12));background=shape(AppColors.surface(this@GoActivity))}
  panel.addView(text(title,18f).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(-1,row()))
  val scroll=ScrollView(this).apply{addView(content)};panel.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  panel.addView(button("Done"){d.dismiss()},LinearLayout.LayoutParams(-1,row()))
  d.setContentView(panel);PopupLayout.configure(this,d,panel,scroll,420)
  showing=d;d.setOnDismissListener{if(showing===d)showing=null};d.show();refresh()
 }
 private fun settings(){
  val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  body.addView(button("Wi-Fi settings"){startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))},LinearLayout.LayoutParams(-1,row()))
  body.addView(button("Connection details"){connectionDetails()},LinearLayout.LayoutParams(-1,row()).apply{topMargin=dp(8)})
  body.addView(button("Sensitivity"){sensitivity()},LinearLayout.LayoutParams(-1,row()).apply{topMargin=dp(8)})
  dialog("Controls settings",body)
 }
 private fun sensitivity(){
  val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  var value=ControlResponseStore.read(this)
  fun slider(label:String,initial:Int,save:(Int)->Unit){
   val caption=text("$label · $initial%");body.addView(caption)
   body.addView(SeekBar(this).apply{max=75;progress=initial-25;contentDescription="$label sensitivity";setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
    override fun onStartTrackingTouch(s:SeekBar?){};override fun onStopTrackingTouch(s:SeekBar?){}
    override fun onProgressChanged(s:SeekBar?,p:Int,user:Boolean){caption.text="$label · ${p+25}%";if(user){responseDirty=true;save(p+25)}}
   })},LinearLayout.LayoutParams(-1,row()))
  }
  slider("Steering",value.steeringPercent){value=value.copy(steeringPercent=it);ControlResponseStore.save(this,value)}
  slider("Drive",value.drivePercent){value=value.copy(drivePercent=it);ControlResponseStore.save(this,value)}
  dialog("Sensitivity",body);showing?.setOnDismissListener{showing=null;if(!isFinishing&&!isDestroyed){responseDirty=false;render()}}
 }
 private fun connectionDetails(){
  val value=endpoint();val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  fun field(label:String,content:String)=EditText(this).apply{hint=label;contentDescription=label;setText(content);setSingleLine();body.addView(this,LinearLayout.LayoutParams(-1,row()))}
  val host=field("IPv4",value.host);val remote=field("Rover port",value.port.toString());val local=field("Phone port",value.localPort.toString());val id=field("System ID",value.system.toString())
  body.addView(button("Save",true){
   val next=GoProfile.WIFI
   val v=GoEndpoint(next,host.text.toString().trim(),remote.text.toString().toIntOrNull()?:0,local.text.toString().toIntOrNull()?:0,id.text.toString().toIntOrNull()?:0)
   if(v.valid()){link.disconnect();prefs.edit().putString("profile",next.name).putString("${next.name}.host",v.host).putInt("${next.name}.port",v.port).putInt("${next.name}.local",v.localPort).putInt("${next.name}.system",v.system).apply();render()}else host.error="Check address, ports and rover ID"
  },LinearLayout.LayoutParams(-1,row()))
  dialog("Connection details",body)
 }
 override fun onConfigurationChanged(config:Configuration){super.onConfigurationChanged(config);link.stop(true);render()}
 override fun onResume(){super.onResume();if(responseDirty){responseDirty=false;render()};if(::link.isInitialized)link.foreground(true)}
 override fun onPause(){if(::link.isInitialized)link.foreground(false);showing?.setOnDismissListener(null);showing?.dismiss();showing=null;resetSticks();super.onPause()}
 override fun onDestroy(){if(::link.isInitialized)link.close();ui.removeCallbacksAndMessages(null);showing?.setOnDismissListener(null);showing?.dismiss();showing=null;super.onDestroy()}
}
