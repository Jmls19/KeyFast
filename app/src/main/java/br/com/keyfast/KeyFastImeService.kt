package br.com.keyfast
import android.inputmethodservice.InputMethodService
import android.view.*
import android.view.inputmethod.EditorInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.widget.*

class KeyFastImeService:InputMethodService() {
 private lateinit var root:LinearLayout
 private lateinit var suggestions:LinearLayout
 private lateinit var store:ShortcutStore
 private var caps=false
 private var symbols=false
 private val panel=Color.rgb(24,28,35)
 private val keyColor=Color.rgb( sixty(),66,76)
 private val specialColor=Color.rgb(55,63,75)
 private val cyan=Color.rgb(32,199,217)

 private fun sixty()=60

 override fun onCreate() { super.onCreate(); store=ShortcutStore(this) }

 override fun onCreateInputView():View {
  // Keep the system navigation area visually separate from the key rows.
  window?.navigationBarColor=panel
  window?.statusBarColor=panel
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
   window?.isNavigationBarContrastEnforced=false
   window?.isStatusBarContrastEnforced=false
  }
  window?.decorView?.systemUiVisibility=0

  val keyboardHeight=(resources.displayMetrics.heightPixels*0.34f).toInt()
  root=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   setPadding(dp(4),dp(5),dp(4),dp(5))
   setBackgroundColor(panel)
   minimumHeight=keyboardHeight
   layoutParams=ViewGroup.LayoutParams(-1,keyboardHeight)
  }
  suggestions=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL
   gravity=Gravity.CENTER_VERTICAL
   setPadding(dp(5),0,dp(5),dp(4))
  }
  root.addView(suggestions,LinearLayout.LayoutParams(-1,dp(42)))
  buildKeys()
  refreshFromCursor()
  return root
 }

 private fun dp(value:Int)= (value*resources.displayMetrics.density+0.5f).toInt()

 private fun buildKeys() {
  while(root.childCount>1) root.removeViewAt(1)
  if(!symbols) {
   addRow(listOf("q","w","e","r","t","y","u","i","o","p"))
   addRow(listOf("a","s","d","f","g","h","j","k","l"),0.5f,0.5f)
   addRow(listOf("⇧","z","x","c","v","b","n","m","⌫"),0.2f,0.2f)
  } else {
   addRow(listOf("1","2","3","4","5","6","7","8","9","0"))
   addRow(listOf("@","#","$","%","&","-","+","(",")","/"))
   addRow(listOf("ABC","*","\"","'",":",";","!","?","⌫"),0.2f,0.2f)
  }
  val bottom=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
  addKey(bottom,if(symbols) "ABC" else "?123",1.25f, specialColor) { symbols=!symbols; buildKeys() }
  addKey(bottom,",",0.75f, specialColor) { type(",") }
  addKey(bottom,"ESPAÇO",4.5f,keyColor) { type(" ") }
  addKey(bottom,".",0.75f,specialColor) { type(".") }
  addKey(bottom,"↵",1.25f,cyan) { enter() }
  root.addView(bottom,LinearLayout.LayoutParams(-1,0,1f))
 }

 private fun addRow(labels:List<String>,left:Float=0f,right:Float=0f) {
  val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
  if(left>0f) row.addView(Space(this),LinearLayout.LayoutParams(0,1,left))
  labels.forEach { label ->
   val color=when(label) {
    "⇧","⌫","ABC" -> specialColor
    else -> keyColor
   }
   addKey(row,label,1f,color) {
    when(label) {
     "⇧" -> { caps=!caps; refreshFromCursor() }
     "⌫" -> currentInputConnection?.let {
      if(!it.deleteSurroundingText(1,0)) it.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL))
      refreshFromCursor()
     }
     "ABC" -> { symbols=false; buildKeys() }
     else -> type(if(!symbols && caps) label.uppercase() else label)
    }
   }
  }
  if(right>0f) row.addView(Space(this),LinearLayout.LayoutParams(0,1,right))
  root.addView(row,LinearLayout.LayoutParams(-1,0,1f))
 }

 private fun addKey(row:LinearLayout,label:String,weight:Float,color:Int,action:()->Unit) {
  val key=TextView(this).apply {
   text=label
   textSize=if(label=="ESPAÇO") 12f else 19f
   gravity=Gravity.CENTER
   setTextColor(Color.WHITE)
   background=GradientDrawable().apply {
    setColor(color)
    cornerRadius=dp(if(label=="ESPAÇO") 9 else 7).toFloat()
   }
   isClickable=true
   isFocusable=true
   setOnClickListener { action() }
   if(label=="ESPAÇO") contentDescription="Espaço"
  }
  row.addView(key,LinearLayout.LayoutParams(0,-1,weight).apply {
   setMargins(dp(2),dp(3),dp(2),dp(3))
  })
 }

 private fun type(value:String) {
  currentInputConnection?.commitText(value,1)
  refreshFromCursor()
 }

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
  if(match!=null) {
   val suggestion=TextView(this).apply {
    this.text=match.trigger+"   ›   "+match.recipientName
    textSize=14f
    gravity=Gravity.CENTER_VERTICAL
    setTextColor(Color.WHITE)
    setPadding(dp(13),0,dp(13),0)
    background=GradientDrawable().apply { setColor(Color.rgb(45,75,88)); cornerRadius=dp(9).toFloat() }
    isClickable=true
    setOnClickListener { expand(match) }
   }
   suggestions.addView(suggestion,LinearLayout.LayoutParams(-1,-1))
  } else {
   suggestions.addView(TextView(this).apply {
    this.text="KeyFast   •   "+if(token.isBlank()) "Digite seu atalho" else "Nenhum atalho correspondente"
    setTextColor(Color.rgb(190,198,208))
    textSize=13f
    gravity=Gravity.CENTER_VERTICAL
   })
  }
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
  store.markUsed(item.id)
  refreshFromCursor()
 }
}