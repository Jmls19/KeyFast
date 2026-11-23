package br.com.keyfast

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.graphics.drawable.GradientDrawable

class MainActivity:Activity() {
 private lateinit var store:ShortcutStore
 private lateinit var list:LinearLayout
 private val navy=Color.rgb(7,23,50)
 private val blue=Color.rgb(18,99,217)
 private val pale=Color.rgb(225,237,255)
 private val ink=Color.rgb(12,31,65)
 private val interRegular by lazy { Typeface.createFromAsset(assets,"fonts/Inter-Regular.ttf") }
 private val interMedium by lazy { Typeface.createFromAsset(assets,"fonts/Inter-Medium.ttf") }
 private val interBold by lazy { Typeface.createFromAsset(assets,"fonts/Inter-Bold.ttf") }
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  window.statusBarColor=navy
  window.navigationBarColor=navy
  window.decorView.systemUiVisibility=0
  store=ShortcutStore(this)
  render()
 }
 private fun dp(v:Int)=(v*resources.displayMetrics.density+0.5f).toInt()
 private fun shape(color:Int,stroke:Int=Color.TRANSPARENT)=GradientDrawable().apply{
  setColor(color);cornerRadius=dp(18).toFloat()
  if(stroke!=Color.TRANSPARENT)setStroke(dp(1),stroke)
 }
 private fun actionButton(label:String,primary:Boolean,onClick:()->Unit)=TextView(this).apply{
  text=label;gravity=Gravity.CENTER;textSize=14f;typeface=interMedium
  setTextColor(if(primary)Color.WHITE else ink)
  background=shape(if(primary)blue else pale,if(primary)Color.rgb(65,145,255) else Color.rgb(205,222,249))
  isClickable=true;isFocusable=true;setPadding(dp(12),dp(13),dp(12),dp(13))
  elevation=dp(3).toFloat();setOnClickListener{onClick()}
 }
 private fun render() {
  val root=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   setPadding(dp(20),dp(22),dp(20),dp(18))
   background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(navy,Color.rgb(5,17,39)))
  }
  val brand=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
  brand.addView(ImageView(this).apply{setImageResource(R.drawable.keyfast_logo);contentDescription="Logotipo KeyFast"},LinearLayout.LayoutParams(dp(58),dp(58)))
  brand.addView(TextView(this).apply {
   text="KeyFast";textSize=30f;setTextColor(Color.WHITE);typeface=interBold;setPadding(dp(12),0,0,0)
  })
  root.addView(brand)
  root.addView(TextView(this).apply {
   text="Digite menos. Resolva mais.";textSize=14f;setTextColor(Color.rgb(120,190,255));typeface=interRegular
   setPadding(0,dp(3),0,dp(20))
  })
  root.addView(actionButton("ATIVAR TECLADO",true){startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))},
   LinearLayout.LayoutParams(-1,dp(52)).apply{bottomMargin=dp(10)})
  root.addView(actionButton("TESTAR / SELECIONAR TECLADO",false){
   (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
  },LinearLayout.LayoutParams(-1,dp(52)).apply{bottomMargin=dp(10)})
  root.addView(actionButton("+ NOVO ATALHO",true){edit(null)},
   LinearLayout.LayoutParams(-1,dp(52)).apply{bottomMargin=dp(18)})
  root.addView(TextView(this).apply {
   text="SEUS ATALHOS";textSize=13f;typeface=interBold;setTextColor(Color.rgb(120,190,255))
   setPadding(dp(2),dp(4),0,dp(10))
  })
  val scroll=ScrollView(this)
  list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  scroll.addView(list)
  root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  setContentView(root)
  refresh()
 }
 private fun refresh() {
  list.removeAllViews()
  val items=store.all().sortedBy{it.trigger.lowercase()}
  if(items.isEmpty())list.addView(TextView(this).apply{
   text="Nenhum atalho cadastrado. Toque em NOVO ATALHO para começar."
   setTextColor(Color.rgb(220,232,250));typeface=interRegular;textSize=14f
   setPadding(dp(4),dp(14),dp(4),dp(14))
  })
  items.forEach{item->
   val row=LinearLayout(this).apply{
    orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(12),dp(14),dp(12))
    background=shape(Color.rgb(250,252,255),Color.rgb(205,222,249));elevation=dp(3).toFloat()
   }
   row.addView(TextView(this).apply{text=item.trigger;textSize=19f;typeface=interBold;setTextColor(blue)})
   row.addView(TextView(this).apply{text=item.recipientName+"\n"+item.document;setTextColor(ink);textSize=13f;typeface=interRegular})
   val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.END}
   actions.addView(actionButton("EDITAR",false){edit(item)},LinearLayout.LayoutParams(dp(104),dp(42)).apply{setMargins(0,dp(10),dp(8),0)})
   actions.addView(actionButton("EXCLUIR",true){
    android.app.AlertDialog.Builder(this@MainActivity).setTitle("Excluir atalho?")
     .setMessage("Deseja excluir ${item.trigger}?").setNegativeButton("CANCELAR",null)
     .setPositiveButton("EXCLUIR"){_,_->store.delete(item.id);refresh()}.show()
   },LinearLayout.LayoutParams(dp(104),dp(42)).apply{setMargins(0,dp(10),0,0)})
   row.addView(actions)
   list.addView(row,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)})
  }
 }
 private fun edit(existing:RecipientShortcut?) {
  val trigger=EditText(this).apply{hint="Atalho (ex.: JUL1)";setSingleLine();setText(existing?.trigger?:"")}
  val name=EditText(this).apply{hint="Nome completo do destinatário";setText(existing?.recipientName?:"")}
  val doc=EditText(this).apply{hint="Documento (CPF ou outro)";setText(existing?.document?:"")}
  val form=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(8),dp(20),dp(8));addView(trigger);addView(name);addView(doc)}
  android.app.AlertDialog.Builder(this).setTitle(if(existing==null)"Novo atalho" else "Editar atalho").setView(form)
   .setNegativeButton("CANCELAR",null).setPositiveButton("SALVAR"){_,_->
    val t=trigger.text.toString().trim().uppercase();val n=name.text.toString().trim();val d=doc.text.toString().trim()
    if(t.isBlank()||n.isBlank()||d.isBlank())Toast.makeText(this,"Preencha os três campos.",Toast.LENGTH_LONG).show()
    else{store.save(RecipientShortcut(existing?.id?:java.util.UUID.randomUUID().toString(),t,n,d));refresh()}
   }.show()
 }
}