package br.com.keyfast
import android.inputmethodservice.InputMethodService
import android.view.*
import android.view.inputmethod.EditorInfo
import android.graphics.Color
import android.view.Gravity
import android.widget.*

class KeyFastImeService:InputMethodService() {
 private lateinit var root:LinearLayout
 private lateinit var suggestions:LinearLayout
 private lateinit var store:ShortcutStore
 private var caps=false
 private var symbols=false
 private val panel=Color.rgb(16,24,39)
 private val keyColor=Color.rgb(38,52,73)
 private val cyan=Color.rgb(32,199,217)
 override fun onCreate() { super.onCreate(); store=ShortcutStore(this) }
 override fun onCreateInputView():View {
  root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(3,5,3,5); setBackgroundColor(panel) }
  suggestions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
  root.addView(suggestions,LinearLayout.LayoutParams(-1,44))
  buildKeys()
  refreshFromCursor()
  return root
 }
 private fun buildKeys() {
  while(root.childCount>1) root.removeViewAt(1)
  if(!symbols) {
   addRow(listOf("q","w","e","r","t","y","u","i","o","p"))
   addRow(listOf("a","s","d","f","g","h","j","k","l"))
   addRow(listOf("⇧","z","x","c","v","b","n","m","⌫"))
  } else {
   addRow(listOf("1","2","3","4","5","6","7","8","9","0"))
   addRow(listOf("@","#","$","%","&","-","+","(",")","/"))
   addRow(listOf("ABC","*","\"","'",":",";","!","?","⌫"))
  }
  val bottom=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
  addKey(bottom,if(symbols) "ABC" else "?123",1f) { symbols=!symbols; buildKeys() }
  addKey(bottom,",",0.6f) { type(",") }
  addKey(bottom,"ESPAÇO",4f) { type(" ") }
  addKey(bottom,".",0.6f) { type(".") }
  addKey(bottom,"↵",1f) { enter() }
  root.addView(bottom,LinearLayout.LayoutParams(-1,49))
 }
 private fun addRow(labels:List<String>) {
  val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
  labels.forEach { label -> addKey(row,label,1f) {
   when(label) {
    "⇧" -> { caps=!caps }
    "⌫" -> currentInputConnection?.let { if(!it.deleteSurroundingText(1,0)) it.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL)) }
    "ABC" -> { symbols=false; buildKeys() }
    else -> type(if(!symbols && caps) label.uppercase() else label)
   }
  } }
  root.addView(row,LinearLayout.LayoutParams(-1,49))
 }
 private fun addKey(row:LinearLayout,label:String,weight:Float,action:()->Unit) {
  val b=Button(this).apply { text=label; textSize=if(label=="ESPAÇO") 12f else 16f; isAllCaps=false; setTextColor(Color.WHITE); setBackgroundColor(if(label=="↵") cyan else keyColor); setPadding(1,1,1,1); setOnClickListener { action() } }
  row.addView(b,LinearLayout.LayoutParams(0,-1,weight).apply { setMargins(2,2,2,2) })
 }
 private fun type(value:String) { currentInputConnection?.commitText(value,1); refreshFromCursor() }
 private fun enter() {
  val ic=currentInputConnection ?: return
  val action=currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
  if(action!=EditorInfo.IME_ACTION_NONE && action!=EditorInfo.IME_ACTION_UNSPECIFIED) ic.performEditorAction(action)
  else ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER))
 }
 private fun refreshFromCursor() {
  val before=currentInputConnection?.getTextBeforeCursor(48,0)?.toString().orEmpty()
  refreshSuggestions(before)
 }
 private fun refreshSuggestions(text:String) {
  if(!::suggestions.isInitialized) return
  suggestions.removeAllViews()
  val token=text.takeLastWhile { !it.isWhitespace() }.uppercase()
  val match=if(token.isNotBlank()) store.all().firstOrNull { it.trigger.equals(token,true) } else null
  if(match!=null) suggestions.addView(Button(this).apply {
   this.text=match.trigger+"  →  "+match.recipientName; textSize=13f; isAllCaps=false
   setTextColor(Color.WHITE); setBackgroundColor(cyan); setOnClickListener { expand(match) }
  },LinearLayout.LayoutParams(-1,-1))
  else suggestions.addView(TextView(this).apply {
   this.text="KeyFast  •  "+if(token.isBlank()) "Digite seu atalho" else "Nenhum atalho correspondente"
   setTextColor(Color.LTGRAY); textSize=12f; gravity=Gravity.CENTER_VERTICAL
  })
 }
 private fun expand(item:RecipientShortcut) {
  val ic=currentInputConnection ?: return
  ic.deleteSurroundingText(item.trigger.length,0)
  ic.commitText(item.recipientName,1)
  val moved=ic.performEditorAction(EditorInfo.IME_ACTION_NEXT)
  if(!moved) {
   ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_TAB))
   ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_TAB))
  }
  currentInputConnection?.commitText(item.document,1)
  store.markUsed(item.id); refreshFromCursor()
 }
}
