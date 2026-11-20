package br.com.keyfast

import android.inputmethodservice.InputMethodService
import android.view.*
import android.view.inputmethod.EditorInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
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
 private val keyColor=Color.rgb(60,66,76)
 private val specialColor=Color.rgb(55,63,75)
 private val cyan=Color.rgb(32,199,217)

 override fun onCreate() { super.onCreate(); store=ShortcutStore(this) }

 override fun onCreateInputView():View {
  val baseKeyboardHeight=(resources.displayMetrics.heightPixels*0.34f).toInt()
  root=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   setPadding(dp(4),dp(5),dp(4),dp(5))
   setBackgroundColor(panel)
   minimumHeight=baseKeyboardHeight
   layoutParams=ViewGroup.LayoutParams(-1,baseKeyboardHeight)
   setOnApplyWindowInsetsListener { view, insets ->
    val bottomInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
     insets.getInsets(WindowInsets.Type.navigationBars()).bottom
    } else {
     @Suppress("DEPRECATION")
     insets.systemWindowInsetBottom
    }
    view.setPadding(dp(4),dp(5),dp(4),dp(5)+bottomInset)
    view.layoutParams=view.layoutParams.apply { height=baseKeyboardHeight+bottomInset }
    insets
   }
   post { requestApplyInsets() }
  }
  suggestions=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL
   gravity=Gravity.CENTER_VERTICAL
   setPadding(dp(5),0,dp(5),dp(4))
  }
  root.addView(suggestions,LinearLayout.LayoutParams(-1,dp(38)))
  buildKeys()
  refreshFromCursor()
  return root
 }

 private fun dp(value:Int)=(value*resources.displayMetrics.density+0.5f).toInt()

 private fun buildKeys() {
  while(root.childCount>1) root.removeViewAt(1)
  if(!symbols) {
   // Dedicated number row, like a full-size QWERTY keyboard.
   addRow(listOf("1","2","3","4","5","6","7","8","9","0"),0.0f,0.0f,true)
   val letters=listOf("q","w","e","r","t","y","u","i","o","p")
   addRow(if(caps) letters.map{it.uppercase()} else letters)
   val middle=listOf("a","s","d","f","g","h","j","k","l")
   addRow(if(caps) middle.map{it.uppercase()} else middle,0.5f,0.5f)
   val lower=listOf("z","x","c","v","b","n","m")
   val lowerRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
   addKey(lowerRow,"⇧",1.25f,specialColor) { caps=!caps; buildKeys() }
   lower.forEach { letter ->
    val shown=if(caps) letter.uppercase() else letter
    addKey(lowerRow,shown,1f,keyColor) { typeLetter(letter) }
   }
   addKey(lowerRow,"⌫",1.25f,specialColor) {
    currentInputConnection?.let {
     if(!it.deleteSurroundingText(1,0)) it.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL))
     refreshFromCursor()
    }
   }
   root.addView(lowerRow,LinearLayout.LayoutParams(-1,0,1f))
  } else {
   addRow(listOf("1","2","3","4","5","6","7","8","9","0"))
   addRow(listOf("@","#","$","%","&","-","+","(",")","/"))
   addRow(listOf("*","\"","'",":",";","!","?","ABC","⌫"))
  }
  val bottom=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
  addKey(bottom,if(symbols) "ABC" else "?123",1.25f,specialColor) { symbols=!symbols; buildKeys() }
  addKey(bottom,",",0.75f,specialColor) { type(",") }
  addKey(bottom,"ESPAÇO",4.5f,keyColor) { type(" ") }
  addKey(bottom,".",0.75f,specialColor) { type(".") }
  addKey(bottom,"↵",1.25f,cyan) { enter() }
  root.addView(bottom,LinearLayout.LayoutParams(-1,0,1f))
 }

 private fun addRow(labels:List<String>,left:Float=0f,right:Float=0f,numberRow:Boolean=false) {
  val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
  if(left>0f) row.addView(Space(this),LinearLayout.LayoutParams(0,1,left))
  labels.forEach { label ->
   addKey(row,label,1f,if(numberRow) specialColor else keyColor) {
    if(label=="ABC") { symbols=false; buildKeys() }
    else if(label=="⌫") {
     currentInputConnection?.let {
      if(!it.deleteSurroundingText(1,0)) it.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL))
      refreshFromCursor()
     }
    } else if(label.length==1 && label[0].isDigit()) type(label)
    else if(label.length==1) type(label)
   }
  }
  if(right>0f) row.addView(Space(this),LinearLayout.LayoutParams(0,1,right))
  root.addView(row,LinearLayout.LayoutParams(-1,0,1f))
 }

 private fun typeLetter(letter:String) {
  val lower=letter.lowercase()
  val choices=when(lower) {
   "a" -> listOf("a","á","à","â","ã")
   "e" -> listOf("e","é","ê")
   "i" -> listOf("i","í")
   "o" -> listOf("o","ó","ô","õ")
   "u" -> listOf("u","ú")
   "c" -> listOf("c","ç")
   else -> emptyList()
  }
  if(choices.isEmpty()) type(if(caps) lower.uppercase() else lower)
  else showAccentPopup(choices,if(caps) lower.uppercase() else lower)
 }

 private fun showAccentPopup(options:List<String>,base:String) {
  val content=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL
   setPadding(dp(5),dp(5),dp(5),dp(5))
   setBackgroundColor(Color.rgb(37,43,53))
  }
  options.forEach { option ->
   val shown=if(caps) option.uppercase() else option
   val item=TextView(this).apply {
    text=shown
    textSize=22f
    gravity=Gravity.CENTER
    setTextColor(Color.WHITE)
    background=keyBackground(cyan)
    isClickable=true
    setOnClickListener { type(shown); (content.parent as? PopupWindow)?.dismiss() }
   }
   content.addView(item,LinearLayout.LayoutParams(dp(48),dp(52)).apply { setMargins(dp(2),0,dp(2),0) })
  }
  val popup=PopupWindow(content,ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT,true)
  popup.isOutsideTouchable=true
  popup.setBackgroundDrawable(GradientDrawable().apply { setColor(Color.TRANSPARENT) })
  popup.elevation=dp(8).toFloat()
  // Place above the keyboard's suggestion strip, safely inside the IME window.
  popup.showAtLocation(root,Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,0,dp(190))
 }

 private fun keyBackground(color:Int):GradientDrawable=GradientDrawable().apply {
  setColor(color); cornerRadius=dp(8).toFloat()
 }

 private fun addKey(row:LinearLayout,label:String,weight:Float,color:Int,action:()->Unit) {
  val key=TextView(this).apply {
   text=label
   textSize=if(label=="ESPAÇO") 12f else 19f
   gravity=Gravity.CENTER
   setTextColor(Color.WHITE)
   val mask=keyBackground(Color.WHITE)
   background=if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP)
    RippleDrawable(ColorStateList.valueOf(Color.argb(95,255,255,255)),keyBackground(color),mask)
    else keyBackground(color)
   isClickable=true
   isFocusable=true
   setOnClickListener { action() }
   if(label=="ESPAÇO") contentDescription="Espaço"
  }
  row.addView(key,LinearLayout.LayoutParams(0,-1,weight).apply {
   setMargins(dp(2),dp(2),dp(2),dp(2))
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
    textSize=14f; gravity=Gravity.CENTER_VERTICAL; setTextColor(Color.WHITE)
    setPadding(dp(13),0,dp(13),0)
    background=keyBackground(Color.rgb(45,75,88))
    isClickable=true; setOnClickListener { expand(match) }
   }
   suggestions.addView(suggestion,LinearLayout.LayoutParams(-1,-1))
  } else {
   suggestions.addView(TextView(this).apply {
    this.text="KeyFast   •   "+if(token.isBlank()) "Digite seu atalho" else "Nenhum atalho correspondente"
    setTextColor(Color.rgb(190,198,208)); textSize=13f; gravity=Gravity.CENTER_VERTICAL
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