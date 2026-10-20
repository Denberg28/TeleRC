package io.github.denberg28.telerc

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.*

/** Small standalone edition: no mission, Balance, LoRa, map or background driving. */
class GoActivity:Activity(){
 private enum class Tab{Setup,Controls,Test}
 private var page=Tab.Setup
 internal lateinit var link:GoLink
 private val ui=Handler(Looper.getMainLooper())
 private val prefs by lazy{getSharedPreferences("go",MODE_PRIVATE)}
 private var profile=GoProfile.WIFI
 private var endpoint:GoEndpoint?=null
 private var status:TextView?=null
 private var arm:Button?=null
 private var enable:Button?=null
 private var stop:Button?=null
 private var connect:Button?=null
 private var left:JoystickView?=null
 private var right:JoystickView?=null
 private var steerLabel:TextView?=null;private var driveLabel:TextView?=null
 private var steer=1500;private var drive=1500
 private var showing:Dialog?=null
 private var practice:MazePractice?=null
 private var scene:MazePracticeView?=null
 private var level=1
 private var started=false
 private fun dp(value:Int)=(value*resources.displayMetrics.density+.5f).toInt()
 private fun row()=dp((48*maxOf(1f,resources.configuration.fontScale)).toInt())
 private fun ink()=AppColors.ink(this)
 private fun text(value:String,size:Float=14f)=TextView(this).apply{text=value;textSize=size;setTextColor(ink());includeFontPadding=false;setLineSpacing(dp(2).toFloat(),1f)}
 private fun button(value:String,filled:Boolean=false,click:()->Unit)=AppUi.button(this,value,filled,click)
 private fun shape(color:Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(12).toFloat()}
 override fun onCreate(savedInstanceState:Bundle?){
  if(!getSharedPreferences("appearance",0).contains("background"))getSharedPreferences("appearance",0).edit().putString("background","DARK").apply()
  setTheme(if(AppColors.theme(this)==BackgroundTheme.DARK)android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
  super.onCreate(savedInstanceState)
  profile=GoProfile.entries.firstOrNull{it.name==prefs.getString("profile",null)}?:GoProfile.WIFI
  level=prefs.getInt("level",1).coerceIn(1,20)
  link=GoLink(this){ui.removeCallbacks(refreshTask);ui.post(refreshTask)}
  render()
 }
 private val refreshTask=Runnable{if(!isFinishing&&!isDestroyed)refresh()}
 private fun endpoint():GoEndpoint=GoEndpoint(profile,prefs.getString("${profile.name}.host",profile.host)?:profile.host,
  prefs.getInt("${profile.name}.port",profile.port),prefs.getInt("${profile.name}.local",profile.localPort),prefs.getInt("${profile.name}.system",1))
 private fun switch(tab:Tab){if(page==tab)return;showing?.dismiss();link.stop(true);scene?.pause();left?.reset();right?.reset();if(tab==Tab.Test)link.disconnect();page=tab;render()}
 private fun render(){
  showing?.dismiss();showing=null;scene?.pause();left?.reset();right?.reset();left=null;right=null
  status=null;arm=null;enable=null;stop=null;connect=null;scene=null
  val root=LinearLayout(this).apply{
   keepScreenOn=page!=Tab.Setup;orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(8));setBackgroundColor(AppColors.background(this@GoActivity))
   setOnApplyWindowInsetsListener{v,insets->
    if(android.os.Build.VERSION.SDK_INT>=30){
     val safe=insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
     v.setPadding(dp(12)+safe.left,dp(8)+safe.top,dp(12)+safe.right,dp(8)+safe.bottom)
    }else{
     @Suppress("DEPRECATION")
     v.setPadding(dp(12)+insets.systemWindowInsetLeft,dp(8)+insets.systemWindowInsetTop,dp(12)+insets.systemWindowInsetRight,dp(8)+insets.systemWindowInsetBottom)
    };insets}
  }
  val tabs=LinearLayout(this).apply{isBaselineAligned=false;gravity=Gravity.CENTER_VERTICAL;contentDescription="Go tabs"}
  tabs.addView(text("TeleRC Go",18f).apply{setTypeface(typeface,android.graphics.Typeface.BOLD)},LinearLayout.LayoutParams(0,-2,1f))
  for(tab in Tab.entries)tabs.addView(button(tab.name,tab==page){switch(tab)}.apply{contentDescription="${tab.name} tab"},LinearLayout.LayoutParams(dp(104),row()).apply{leftMargin=dp(6)})
  root.addView(tabs,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)})
  when(page){Tab.Setup->setup(root);Tab.Controls->controls(root,false);Tab.Test->controls(root,true)}
  setContentView(root);refresh()
 }
 private fun setup(root:LinearLayout){
  val columns=LinearLayout(this).apply{isBaselineAligned=false}
  fun panel():LinearLayout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=shape(AppColors.surface(this@GoActivity));setPadding(dp(16),dp(12),dp(16),dp(12))}
  val connection=panel();val response=panel()
  connection.addView(text("Connect",18f),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)})
  val choices=Spinner(this).apply{adapter=ArrayAdapter(this@GoActivity,android.R.layout.simple_spinner_dropdown_item,GoProfile.entries.map{it.label});setSelection(profile.ordinal)}
  choices.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{
   override fun onNothingSelected(p:AdapterView<*>?){}
   override fun onItemSelected(p:AdapterView<*>?,v:View?,position:Int,id:Long){val next=GoProfile.entries[position];if(next!=profile){link.disconnect();profile=next;endpoint=null;prefs.edit().putString("profile",next.name).apply();render()}}
  }
  connection.addView(choices,LinearLayout.LayoutParams(-1,row()))
  connection.addView(button("Wi-Fi settings"){startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))},LinearLayout.LayoutParams(-1,row()).apply{topMargin=dp(8)})
  connect=button("Connect",true){
   if(link.snapshot().connected){link.disconnect();refresh()}else{val value=endpoint();if(value.valid()){endpoint=value;link.connect(value);page=Tab.Controls;render()}else dialog("Check address",text("Use a valid IPv4 address and ports. Four-wheel firmware uses system 1 and UDP 14550."))}
  }
  connection.addView(button("Connection details"){connectionDetails()},LinearLayout.LayoutParams(-1,row()).apply{topMargin=dp(6)})
  response.addView(text("Response",18f),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)})
  var value=ControlResponseStore.read(this)
  fun slider(label:String,initial:Int,save:(Int)->Unit){
   val caption=text("$label · $initial%")
   response.addView(caption)
   response.addView(SeekBar(this).apply{max=75;progress=initial-25;contentDescription="$label sensitivity";setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
    override fun onStartTrackingTouch(s:SeekBar?){};override fun onStopTrackingTouch(s:SeekBar?){}
    override fun onProgressChanged(s:SeekBar?,p:Int,user:Boolean){caption.text="$label · ${p+25}%";if(user)save(p+25)}
   })},LinearLayout.LayoutParams(-1,row()))
  }
  slider("Steering",value.steeringPercent){value=value.copy(steeringPercent=it);ControlResponseStore.save(this,value)}
  slider("Drive",value.drivePercent){value=value.copy(drivePercent=it);ControlResponseStore.save(this,value)}
  val themes=LinearLayout(this).apply{isBaselineAligned=false}
  for(theme in BackgroundTheme.entries)themes.addView(button(theme.label){getSharedPreferences("appearance",0).edit().putString("background",theme.name).apply();recreate()},LinearLayout.LayoutParams(0,row(),1f).apply{rightMargin=dp(4)})
  response.addView(themes,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)})
  response.addView(text("Connect → Enable → ARM. Reconnect stays stopped. Test is offline.",13f),LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)})
  connection.addView(text("Four-wheel: join TeleRC-MiniRover. ELRS receiver: handset drives; Take control switches deliberately. Backpack: handset only.",13f),LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10)})
  columns.addView(ScrollView(this).apply{addView(connection)},LinearLayout.LayoutParams(0,-1,1f).apply{rightMargin=dp(8)})
  columns.addView(ScrollView(this).apply{addView(response)},LinearLayout.LayoutParams(0,-1,1f))
  root.addView(columns,LinearLayout.LayoutParams(-1,0,1f))
  root.addView(connect,LinearLayout.LayoutParams(-1,row()).apply{topMargin=dp(8)})
  root.addView(text("1.0.0 · Wi-Fi / ELRS · four-wheel",11f).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(4)})
 }
 private fun controls(root:LinearLayout,test:Boolean){
  val bar=LinearLayout(this).apply{isBaselineAligned=false;gravity=Gravity.CENTER_VERTICAL}
  status=text(if(test)"Offline practice" else "Connect in Setup",13f).apply{maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END}
  bar.addView(status,LinearLayout.LayoutParams(0,-2,1f))
  if(test){
   bar.addView(button("Level $level"){val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};for(n in 1..20)body.addView(button("Level $n"){level=n;prefs.edit().putInt("level",n).apply();practice=null;showing?.dismiss();render()},LinearLayout.LayoutParams(-1,row()));dialog("Practice levels",body)},LinearLayout.LayoutParams(dp(110),row()))
  }else{
   enable=button(if(profile==GoProfile.ELRS)"Take control" else "Enable"){link.enable();refresh()};bar.addView(enable,LinearLayout.LayoutParams(dp(130),row()))
   arm=button("ARM",true){if(link.snapshot().armed)link.stop()else link.arm();refresh()};bar.addView(arm,LinearLayout.LayoutParams(dp(110),row()).apply{leftMargin=dp(6)})
  }
  root.addView(bar,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)})
  val center=LinearLayout(this).apply{isBaselineAligned=false}
  fun stick(vertical:Boolean):LinearLayout=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;val label=text(if(vertical)"DRIVE · 1500"else"STEER · 1500",13f).apply{gravity=Gravity.CENTER}
   if(vertical)driveLabel=label else steerLabel=label
   addView(label,LinearLayout.LayoutParams(-1,-2))
   val joystick=JoystickView(this@GoActivity,vertical){channel->if(vertical)drive=channel else steer=channel;if(test){practice?.steering(steer);practice?.drive(drive);scene?.changed()}else link.axes(steer,drive);refreshLabels()}
   if(vertical)this@GoActivity.right=joystick else this@GoActivity.left=joystick
   addView(joystick,LinearLayout.LayoutParams(-1,0,1f))
  }
  center.addView(stick(false),LinearLayout.LayoutParams(0,-1,1f))
  if(test){
   val p=practice?:MazePractice(MazeLevels.layout(level)).also{practice=it}
   val view=MazePracticeView(this,p).also{scene=it}
   view.onUpdate={status?.text="Level $level · ${p.elapsedMillis/1000}s · bumps ${p.bumps}/3"}
   view.onFailure={status?.text="Three bumps · Restart to try again"}
   view.onClear={status?.text="Level $level complete · ${it/1000}s"}
   center.addView(view,LinearLayout.LayoutParams(0,-1,1.6f).apply{leftMargin=dp(8);rightMargin=dp(8)})
  }else center.addView(text("4WD\n\nCH1 · steering\nCH2 · drive",14f).apply{gravity=Gravity.CENTER;background=shape(AppColors.surface(this@GoActivity))},LinearLayout.LayoutParams(0,-1,1.4f).apply{leftMargin=dp(8);rightMargin=dp(8)})
  center.addView(stick(true),LinearLayout.LayoutParams(0,-1,1f))
  root.addView(center,LinearLayout.LayoutParams(-1,0,1f))
  val footer=LinearLayout(this).apply{isBaselineAligned=false}
  if(test){
   footer.addView(button("Start / Pause",true){val p=practice?:return@button;if(p.state==MazePractice.State.RUNNING)scene?.pause()else{p.start();scene?.changed()};refresh()},LinearLayout.LayoutParams(0,row(),1f))
   footer.addView(button("Restart"){practice?.reset();practice?.start();scene?.changed();refresh()},LinearLayout.LayoutParams(0,row(),1f).apply{leftMargin=dp(8)})
  }else{
   stop=button(if(profile.handsetOnly)"Handset controls rover"else"STOP / Disarm",true){link.stop();left?.reset();right?.reset();refresh()}
   footer.addView(stop,LinearLayout.LayoutParams(0,row(),1.5f))
   footer.addView(button("Disconnect"){link.disconnect();refresh()},LinearLayout.LayoutParams(0,row(),1f).apply{leftMargin=dp(8)})
  }
  root.addView(footer,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)})
 }
 private fun refreshLabels(){steerLabel?.text="STEER · $steer";driveLabel?.text="DRIVE · $drive"}
 private fun refresh(){
  if(!::link.isInitialized)return
  if(page==Tab.Test){left?.isEnabled=true;right?.isEnabled=true;return}
  val s=link.snapshot()
  status?.text=s.text+(if(profile==GoProfile.ELRS)" · RX ${if(s.receiver) "linked" else "lost"}"else "")+if(s.fresh)" · HB ${s.age}ms · RX ${s.rx} / TX ${s.tx}"else if(s.connected)" · waiting for heartbeat"else""
  enable?.isEnabled=s.fresh&&!s.enabled&&!s.armed&&!s.handset&&!s.stopped
  arm?.text=if(s.armed)"DISARM"else if(s.enabled&&!s.armReady)"CENTER"else"ARM"
  arm?.isEnabled=s.armed&&!s.handset || s.armReady&&!s.handset
  stop?.isEnabled=s.connected&&s.verified&&!s.handset
  val canDrive=s.fresh&&s.enabled&&s.armed&&!s.handset
  if(left?.isEnabled!=canDrive)left?.isEnabled=canDrive
  if(right?.isEnabled!=canDrive)right?.isEnabled=canDrive
  connect?.text=if(s.connected)"Disconnect"else"Connect"
 }
 private fun dialog(title:String,content:View){
  link.stop(true);left?.reset();right?.reset();scene?.pause();showing?.dismiss()
  val d=Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE)
  val frame=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(12),dp(16),dp(12));background=shape(AppColors.surface(this@GoActivity))}
  frame.addView(text(title,18f).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(-1,row()))
  val scroll=ScrollView(this).apply{addView(content)};frame.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  frame.addView(button("Done"){d.dismiss()},LinearLayout.LayoutParams(-1,row()))
  d.setContentView(frame)
  PopupLayout.configure(this,d,frame,scroll,420)
  showing=d;d.show();refresh()
 }
 private fun connectionDetails(){
  val value=endpoint();val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  fun field(label:String,content:String)=EditText(this).apply{hint=label;setText(content);setSingleLine();body.addView(this,LinearLayout.LayoutParams(-1,row()))}
  val host=field("IPv4",value.host);val remote=field("Rover port",value.port.toString());val local=field("Phone port",value.localPort.toString());val id=field("System ID",value.system.toString())
  body.addView(button("Save",true){val v=GoEndpoint(profile,host.text.toString().trim(),remote.text.toString().toIntOrNull()?:0,local.text.toString().toIntOrNull()?:0,id.text.toString().toIntOrNull()?:0)
   if(v.valid()){link.disconnect();prefs.edit().putString("${profile.name}.host",v.host).putInt("${profile.name}.port",v.port).putInt("${profile.name}.local",v.localPort).putInt("${profile.name}.system",v.system).apply();showing?.dismiss();render()}else host.error="Check address, ports and rover ID"
  },LinearLayout.LayoutParams(-1,row()))
  dialog("Connection details",body)
 }
 override fun onConfigurationChanged(config:Configuration){super.onConfigurationChanged(config);link.stop(true);render()}
 override fun onResume(){super.onResume();started=true;if(::link.isInitialized)link.foreground(true);if(page==Tab.Test)scene?.resume()}
 override fun onPause(){started=false;if(::link.isInitialized)link.foreground(false);showing?.dismiss();scene?.pause();left?.reset();right?.reset();super.onPause()}
 override fun onDestroy(){if(::link.isInitialized)link.close();ui.removeCallbacksAndMessages(null);showing?.dismiss();super.onDestroy()}
}
