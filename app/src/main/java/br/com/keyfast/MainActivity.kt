package br.com.keyfast
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.graphics.drawable.GradientDrawable

class MainActivity:Activity() {
 private lateinit var store:ShortcutStore
 private lateinit var list:LinearLayout
 private val bg=Color.rgb(16,24,39); private val cyan=Color.rgb(32,199,217)
 private val interRegular by lazy { Typeface.createFromAsset(assets,"fonts/Inter-Regular.ttf") }
 private val interBold by lazy { Typeface.createFromAsset(assets,"fonts/Inter-Bold.ttf") }
 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); store=ShortcutStore(this); render() }
 private fun render() {
  val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(22,24,22,18); setBackgroundColor(bg) }
  val brand=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL}
  brand.addView(ImageView(this).apply{setImageResource(R.drawable.keyfast_logo);contentDescription="Logotipo KeyFast"},LinearLayout.LayoutParams(58,58))
  brand.addView(TextView(this).apply { text="KeyFast"; textSize=30f; setTextColor(Color.WHITE); typeface=interBold; setPadding(12,0,0,0) })
  root.addView(brand)

  root.addView(TextView(this).apply { text="Digite menos. Resolva mais."; textSize=14f; setTextColor(cyan); typeface=interRegular; setPadding(0,2,0,18) })
  root.addView(Button(this).apply { text="ATIVAR TECLADO"; setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) } })
  root.addView(Button(this).apply { text="TESTAR / SELECIONAR TECLADO"; setOnClickListener { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() } })
  root.addView(Button(this).apply { text="+ NOVO ATALHO"; setOnClickListener { edit(null) } })
  root.addView(TextView(this).apply { text="SEUS ATALHOS"; textSize=13f; typeface=Typeface.create("sans-serif",Typeface.BOLD); setTextColor(cyan); setPadding(0,18,0,8) })
  val scroll=ScrollView(this); list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; scroll.addView(list); root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  setContentView(root); refresh()
 }
 private fun refresh() {
  list.removeAllViews(); val items=store.all().sortedBy { it.trigger.lowercase() }
  if(items.isEmpty()) list.addView(TextView(this).apply { text="Nenhum atalho cadastrado. Toque em NOVO ATALHO para começar."; setTextColor(Color.LTGRAY); setPadding(0,12,0,12) })
  items.forEach { item ->
   val row=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(14,12,14,12); setBackgroundColor(Color.rgb(38,52,73)) }
   row.addView(TextView(this).apply { text=item.trigger; textSize=19f; typeface=Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
   row.addView(TextView(this).apply { text=item.recipientName+"\n"+item.document; setTextColor(Color.LTGRAY); textSize=13f })
   val actions=LinearLayout(this)
   actions.addView(Button(this).apply { text="EDITAR"; setOnClickListener { edit(item) } })
   actions.addView(Button(this).apply { text="EXCLUIR"; setOnClickListener { android.app.AlertDialog.Builder(this@MainActivity).setMessage("Excluir "+item.trigger+"?").setNegativeButton("Cancelar",null).setPositiveButton("Excluir") { _,_->store.delete(item.id); refresh() }.show() } })
   row.addView(actions); list.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=10 })
  }
 }
 private fun edit(existing:RecipientShortcut?) {
  val trigger=EditText(this).apply { hint="Atalho (ex.: JUL1)"; setSingleLine(); setText(existing?.trigger ?: "") }
  val name=EditText(this).apply { hint="Nome completo do destinatário"; setText(existing?.recipientName ?: "") }
  val doc=EditText(this).apply { hint="Documento (CPF ou outro)"; setText(existing?.document ?: "") }
  val form=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(22,4,22,4); addView(trigger); addView(name); addView(doc) }
  android.app.AlertDialog.Builder(this).setTitle(if(existing==null) "Novo atalho" else "Editar atalho").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Salvar") { _,_ ->
   val t=trigger.text.toString().trim().uppercase(); val n=name.text.toString().trim(); val d=doc.text.toString().trim()
   if(t.isBlank()||n.isBlank()||d.isBlank()) Toast.makeText(this,"Preencha os três campos.",Toast.LENGTH_LONG).show()
   else { store.save(RecipientShortcut(existing?.id ?: java.util.UUID.randomUUID().toString(),t,n,d)); refresh() }
  }.show()
 }
}
