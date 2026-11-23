package br.com.keyfast

import android.app.Service
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import android.text.InputType

class RouteWidgetService:Service() {
    companion object { const val ACTION_SHOW="br.com.keyfast.ROUTE_SHOW"; const val ACTION_HIDE="br.com.keyfast.ROUTE_HIDE" }
    private lateinit var wm:WindowManager
    private lateinit var store:RouteStore
    private var panel:LinearLayout?=null
    private var showingList=false
    private val blue=Color.rgb(18,99,217)
    private val navy=Color.rgb(7,23,50)
    private val ink=Color.rgb(12,31,65)
    private val pale=Color.rgb(225,237,255)
    override fun onCreate(){super.onCreate();wm=getSystemService(WINDOW_SERVICE) as WindowManager;store=RouteStore(this)}
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action==ACTION_HIDE){hide();stopSelf();return START_NOT_STICKY}
        if(Settings.canDrawOverlays(this))showWidget()
        return START_STICKY
    }
    override fun onBind(intent:Intent?):IBinder?=null
    private fun dp(v:Int)=(v*resources.displayMetrics.density+0.5f).toInt()
    private fun bg(color:Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(13).toFloat();setStroke(dp(1),Color.rgb(205,222,249))}
    private fun activeRoute():RouteDefinition? {
        val id=store.activeId()?:return null
        val route=store.routes().firstOrNull{it.id==id}?:return null
        return store.applyRules(route)
    }
    private fun hide(){panel?.let{runCatching{wm.removeView(it)}};panel=null}
    private fun showWidget(){
        hide();showingList=false
        val route=activeRoute()
        if(route==null){stopSelf();return}
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(8),dp(10),dp(8));background=bg(Color.rgb(250,252,255));elevation=dp(12).toFloat()}
        val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(7),dp(4),dp(7));background=bg(blue)}
        header.addView(TextView(this).apply{text="KEYFAST • ${route.name}";textSize=13f;setTextColor(Color.WHITE);setTypeface(null,1)},LinearLayout.LayoutParams(0,-2,1f))
        header.addView(iconButton("📋","Abrir lista completa"){showFullList()})
        header.addView(iconButton("✕","Fechar widget"){hide();stopSelf()})
        root.addView(header)
        val pending=route.addresses.filter{!it.delivered}.take(3)
        pending.forEachIndexed{index,address->root.addView(addressRow(route,address,index+1,false))}
        if(pending.isEmpty())root.addView(TextView(this).apply{text="Roteiro concluído!";setTextColor(Color.GREEN);setPadding(0,dp(12),0,dp(8))})
        root.addView(TextView(this).apply{text="${route.addresses.count{it.delivered}} / ${route.addresses.size} entregues";textSize=10f;setTextColor(ink);gravity=Gravity.END})
        panel=root
        val params=WindowManager.LayoutParams(dp(330),WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL;y=dp(28)}
        wm.addView(root,params)
    }
    private fun iconButton(label:String,description:String,action:()->Unit)=TextView(this).apply{text=label;textSize=19f;contentDescription=description;gravity=Gravity.CENTER;setTextColor(Color.WHITE);setPadding(dp(7),dp(3),dp(7),dp(3));setOnClickListener{action()}}
    private fun addressRow(route:RouteDefinition,address:RouteAddress,number:Int,full:Boolean):View {
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(5),dp(4),dp(5));background=bg(pale)}
        val label=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        label.addView(TextView(this).apply{text=if(full)"${address.street}, ${address.number}" else "${address.street} ${address.number}";textSize=13f;setTextColor(ink);setTypeface(null,1);maxLines=2})
        if(address.objectCode.isNotBlank())label.addView(TextView(this).apply{text="${address.objectCode} • CEP ${address.cep}";textSize=9f;setTextColor(blue)})
        if(store.rules().any{it.street.equals(address.street,true)&&it.number==address.number})label.addView(TextView(this).apply{text="REFERÊNCIA SALVA";textSize=9f;setTextColor(blue)})
        row.addView(label,LinearLayout.LayoutParams(0,-2,1f))
        row.addView(actionButton("✅",Color.rgb(22,130,75),"Confirmar entrega"){confirm(route,address)})
        row.addView(actionButton("🔀",Color.rgb(18,99,217),"Salvar posição numérica"){saveRandomReference(route,address)})
        return row
    }
    private fun actionButton(symbol:String,color:Int,description:String,action:()->Unit)=TextView(this).apply{
        text=symbol;textSize=17f;gravity=Gravity.CENTER;contentDescription=description;background=bg(color);setPadding(dp(7),dp(7),dp(7),dp(7));setOnClickListener{action()}
        layoutParams=LinearLayout.LayoutParams(dp(43),dp(40)).apply{leftMargin=dp(4)}
    }
    private fun confirm(route:RouteDefinition,address:RouteAddress){
        store.updateAddress(route.id,address.id){it.copy(delivered=true)}
        refreshCurrent()
    }
    private fun saveRandomReference(route:RouteDefinition,address:RouteAddress){
        val after=EditText(this).apply{hint="Referência numérica anterior";inputType=InputType.TYPE_CLASS_NUMBER}
        val before=EditText(this).apply{hint="Referência numérica posterior";inputType=InputType.TYPE_CLASS_NUMBER}
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(4),dp(18),0);addView(after);addView(before)}
        val dialog=AlertDialog.Builder(this).setTitle("Salvar número fora de ordem").setMessage("${address.street}, ${address.number}\nInforme os dois números entre os quais este endereço deve entrar. Isso NÃO confirma a entrega.").setView(box).setNegativeButton("Cancelar",null).setPositiveButton("Salvar referência"){_,_->
            val a=after.text.toString().trim();val b=before.text.toString().trim()
            if(a.isBlank()||b.isBlank()||a==b)Toast.makeText(this,"Informe duas referências diferentes.",Toast.LENGTH_SHORT).show()
            else {store.saveRule(NumberRule(address.street,address.number,minOf(a.toIntOrNull()?:Int.MAX_VALUE,b.toIntOrNull()?:Int.MAX_VALUE).toString(),maxOf(a.toIntOrNull()?:Int.MIN_VALUE,b.toIntOrNull()?:Int.MIN_VALUE).toString()));Toast.makeText(this,"Referência salva. Entrega continua pendente.",Toast.LENGTH_SHORT).show();refreshCurrent()}
        }.create()
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }
    private fun refreshCurrent(){if(showingList)showFullList()else showWidget()}
    private fun showFullList(){
        hide();showingList=true
        val route=activeRoute()?:return
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(8),dp(10),dp(8));background=bg(Color.rgb(16,24,39))}
        val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        header.addView(TextView(this).apply{text="LISTA COMPLETA • ${route.name}";textSize=13f;setTextColor(Color.WHITE);setTypeface(null,1)},LinearLayout.LayoutParams(0,-2,1f))
        header.addView(iconButton("↩","Voltar ao widget"){showWidget()})
        header.addView(iconButton("✕","Fechar widget"){hide();stopSelf()})
        root.addView(header)
        val scroll=ScrollView(this);val rows=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        route.addresses.forEachIndexed{index,address->
            if(!address.delivered){rows.addView(TextView(this).apply{text="${index+1}. ";setTextColor(blue);textSize=10f});rows.addView(addressRow(route,address,index+1,true),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(5)})}
            else rows.addView(TextView(this).apply{text="✓ ${address.street}, ${address.number}";textSize=11f;setTextColor(Color.rgb(22,130,75));setPadding(dp(8),dp(5),dp(8),dp(5))})
        }
        scroll.addView(rows);root.addView(scroll,LinearLayout.LayoutParams(-1,dp(420)))
        panel=root
        val params=WindowManager.LayoutParams(dp(350),WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL;y=dp(20)}
        wm.addView(root,params)
    }
    override fun onDestroy(){hide();super.onDestroy()}
}
