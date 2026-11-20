package br.com.keyfast

import android.inputmethodservice.InputMethodService
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.textservice.TextInfo
import android.view.textservice.SuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.TextServicesManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Build
import android.view.Gravity
import android.widget.*
import java.util.Locale

class KeyFastImeService:InputMethodService() {
 private lateinit var root:LinearLayout
 private lateinit var suggestions:LinearLayout
 private lateinit var store:ShortcutStore
 private var caps=false
 private var capsLock=false
 private var lastShiftTap=0L
 private var symbols=false
 private var formMode=false
 private var editingId:String?=null
 private var formField=0
 private val values= mutableListOf("","","")
 private val valueViews= mutableListOf<TextView>()
 private var formPopup:PopupWindow?=null
 private var spellSession:SpellCheckerSession?=null
 private var requestedToken=""
 private var spellRequestToken=""
 private var shownSuggestions=emptyList<String>()
 private val panel=Color.rgb(24,28,35)
 private val keyColor=Color.rgb(60,66,76)
 private val specialColor=Color.rgb(55,63,75)
 private val cyan=Color.rgb(32,199,217)

 override fun onCreate(){ super.onCreate(); store=ShortcutStore(this) }
 private fun dp(v:Int)=(v*resources.displayMetrics.density+0.5f).toInt()

 override fun onCreateInputView():View {
  val h=(resources.displayMetrics.heightPixels*0.34f).toInt()
  root=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL; setPadding(dp(4),dp(5),dp(4),dp(5)); setBackgroundColor(panel)
   minimumHeight=h; layoutParams=ViewGroup.LayoutParams(-1,h)
   setOnApplyWindowInsetsListener { view,insets ->
    val b=if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R) insets.getInsets(WindowInsets.Type.navigationBars()).bottom else insets.systemWindowInsetBottom
    view.setPadding(dp(4),dp(5),dp(4),dp(5)+b); view.layoutParams=view.layoutParams.apply{height=h+b}; insets
   }
   post{requestApplyInsets()}
  }
  val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(5),0,dp(2),dp(4))}
  suggestions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
  bar.addView(suggestions,LinearLayout.LayoutParams(0,-1,1f))
  val manage=TextView(this).apply{
   text="⚙";textSize=24f;gravity=Gravity.CENTER;setTextColor(Color.WHITE)
   background=keyBg(Color.rgb(35,91,105));isClickable=true;setPadding(dp(5),0,dp(5),0)
   contentDescription="Gerenciar atalhos";setOnClickListener{showManageMenu(this)}
  }
  bar.addView(manage,LinearLayout.LayoutParams(dp(44),dp(34)))
  root.addView(bar,LinearLayout.LayoutParams(-1,dp(38)))
  buildKeys();refreshFromCursor();return root
 }

 override fun onDestroy(){
  spellSession?.close(); spellSession=null
  formPopup?.dismiss(); formPopup=null
  super.onDestroy()
 }

 private fun buildKeys(){
  while(root.childCount>1)root.removeViewAt(1)
  if(!symbols){
   addRow(listOf("1","2","3","4","5","6","7","8","9","0"),number=true)
   val a=listOf("q","w","e","r","t","y","u","i","o","p");addRow(if(caps)a.map{it.uppercase()}else a)
   val b=listOf("a","s","d","f","g","h","j","k","l");addRow(if(caps)b.map{it.uppercase()}else b,0.5f,0.5f)
   val c=listOf("z","x","c","v","b","n","m");val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
   addKey(row,"⇧",1.25f,if(capsLock)cyan else specialColor){handleShift()}
   c.forEach{ch->val shown=if(caps)ch.uppercase()else ch;addKey(row,shown,1f,keyColor,{showAccentOptions(ch)}){type(shown)}}
   addKey(row,"⌫",1.25f,specialColor){backspace()}
   root.addView(row,LinearLayout.LayoutParams(-1,0,1f))
  }else{
   addRow(listOf("1","2","3","4","5","6","7","8","9","0"))
   addRow(listOf("@","#","$","%","&","-","+","(",")","/"))
   addRow(listOf("*","\"","'",":",";","!","?","ABC","⌫"))
  }
  val bottom=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  addKey(bottom,if(symbols)"ABC" else "?123",1.25f,specialColor){symbols=!symbols;buildKeys()}
  addKey(bottom,",",0.75f,specialColor){type(",")}
  addKey(bottom,"ESPAÇO",4.5f,keyColor){type(" ")}
  addKey(bottom,".",0.75f,specialColor){type(".")}
  addKey(bottom,"↵",1.25f,cyan){enter()}
  root.addView(bottom,LinearLayout.LayoutParams(-1,0,1f))
 }

 private fun handleShift(){
  val now=System.currentTimeMillis()
  if(capsLock){capsLock=false;caps=false;lastShiftTap=0L}
  else if(caps && now-lastShiftTap<=450L){capsLock=true;caps=true;lastShiftTap=0L}
  else if(caps){caps=false;lastShiftTap=0L}
  else{caps=true;lastShiftTap=now}
  buildKeys()
 }

 private fun updateShiftAfterCharacter(value:String){
  if(caps && !capsLock && value.any{it.isLetter()}){
   caps=false;lastShiftTap=0L;buildKeys()
  }
 }

 private fun showForm(item:RecipientShortcut?){
  editingId=item?.id
  values[0]=item?.trigger?:"";values[1]=item?.recipientName?:"";values[2]=item?.document?:""
  formField=0;formMode=true;symbols=false
  buildKeys()
  valueViews.clear()
  val outer=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(14),dp(16),dp(14));background=keyBg(Color.rgb(32,42,56))
  }
  outer.addView(TextView(this).apply{
   text=if(item==null)"NOVO ATALHO" else "EDITAR ATALHO"
   textSize=16f;setTextColor(cyan);gravity=Gravity.CENTER;setPadding(0,0,0,dp(10))
  })
  val labels=listOf("ATALHO","NOME / DESTINATÁRIO","DOCUMENTO")
  for(i in 0..2){
   val card=LinearLayout(this).apply{
    orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(6),dp(10),dp(6))
    background=keyBg(if(i==formField)Color.rgb(35,91,105)else Color.rgb(38,52,73))
    isClickable=true;setOnClickListener{formField=i;updateForm()}
   }
   card.addView(TextView(this).apply{text=labels[i];textSize=9f;setTextColor(cyan)})
   val value=TextView(this).apply{
    text=values[i].ifBlank{"Toque aqui e digite no teclado"}
    textSize=14f;setTextColor(Color.WHITE);gravity=Gravity.CENTER_VERTICAL;maxLines=1
    ellipsize=android.text.TextUtils.TruncateAt.END
   }
   valueViews.add(value);card.addView(value,LinearLayout.LayoutParams(-1,dp(29)))
   outer.addView(card,LinearLayout.LayoutParams(-1,dp(57)).apply{setMargins(0,dp(3),0,dp(3))})
  }
  val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.END}
  actions.addView(actionText("CANCELAR",specialColor){closeForm()},LinearLayout.LayoutParams(dp(100),dp(38)).apply{setMargins(0,dp(10),dp(8),0)})
  actions.addView(actionText("SALVAR",cyan){saveForm()},LinearLayout.LayoutParams(dp(92),dp(38)).apply{setMargins(0,dp(10),0,0)})
  outer.addView(actions)
  formPopup?.dismiss()
  formPopup=PopupWindow(outer,dp(320),ViewGroup.LayoutParams.WRAP_CONTENT,true).apply{
   isOutsideTouchable=false;isFocusable=true;setBackgroundDrawable(keyBg(Color.TRANSPARENT))
   elevation=dp(18).toFloat()
   setOnDismissListener{if(formMode){formMode=false;editingId=null;valueViews.clear();refreshFromCursor()}}
  }
  formPopup?.showAtLocation(root,Gravity.CENTER,0,-dp(36))
 }

 private fun actionText(label:String,color:Int,action:()->Unit)=TextView(this).apply{
  text=label;textSize=11f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);background=keyBg(color);isClickable=true;setOnClickListener{action()}
 }
 private fun updateForm(){
  valueViews.forEachIndexed{i,v->
   v.text=values[i].ifBlank{"Toque aqui e digite no teclado"}
   (v.parent as? View)?.background=keyBg(if(i==formField)Color.rgb(35,91,105)else Color.rgb(38,52,73))
  }
 }
 private fun closeForm(){
  formMode=false;editingId=null;values.indices.forEach{values[it]=""}
  valueViews.clear();formPopup?.setOnDismissListener(null);formPopup?.dismiss();formPopup=null
  buildKeys();refreshFromCursor()
 }
 private fun saveForm(){
  val t=values[0].trim().uppercase();val n=values[1].trim();val d=values[2].trim()
  if(t.isBlank()||n.isBlank()||d.isBlank()){Toast.makeText(this,"Preencha atalho, nome e documento.",Toast.LENGTH_SHORT).show();return}
  if(store.all().any{it.trigger.equals(t,true)&&it.id!=editingId}){Toast.makeText(this,"Esse atalho já está cadastrado.",Toast.LENGTH_SHORT).show();return}
  store.save(RecipientShortcut(editingId?:java.util.UUID.randomUUID().toString(),t,n,d))
  Toast.makeText(this,"Atalho salvo.",Toast.LENGTH_SHORT).show();closeForm()
 }

 private fun showManageMenu(anchor:View){
  val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(5),dp(5),dp(5),dp(5));background=keyBg(Color.rgb(32,42,56))}
  lateinit var popup:PopupWindow
  listOf("NOVO","EDITAR","EXCLUIR").forEach{choice->
   box.addView(TextView(this).apply{
    text=choice;textSize=14f;setTextColor(Color.WHITE);gravity=Gravity.CENTER_VERTICAL;setPadding(dp(18),0,dp(18),0);isClickable=true
    setBackgroundResource(android.R.drawable.list_selector_background)
    setOnClickListener{popup.dismiss();when(choice){"NOVO"->showForm(null);"EDITAR"->showList(true);else->showList(false)}}
   },LinearLayout.LayoutParams(dp(150),dp(42)))
  }
  popup=PopupWindow(box,dp(160),ViewGroup.LayoutParams.WRAP_CONTENT,true);popup.isOutsideTouchable=true
  popup.setBackgroundDrawable(keyBg(Color.rgb(32,42,56)));popup.elevation=dp(10).toFloat();popup.showAsDropDown(anchor,0,dp(3),Gravity.END)
 }
 private fun showList(edit:Boolean){
  val items=store.all().sortedBy{it.trigger.lowercase()}
  if(items.isEmpty()){Toast.makeText(this,"Nenhum atalho cadastrado.",Toast.LENGTH_SHORT).show();return}
  val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(6),dp(6),dp(6),dp(6));background=keyBg(Color.rgb(32,42,56))}
  box.addView(TextView(this).apply{text=if(edit)"SELECIONE PARA EDITAR"else"SELECIONE PARA EXCLUIR";textSize=11f;setTextColor(cyan);setPadding(dp(8),dp(7),dp(8),dp(7))})
  lateinit var popup:PopupWindow
  items.forEach{item->box.addView(TextView(this).apply{
   text=item.trigger+"  •  "+item.recipientName;textSize=13f;setTextColor(Color.WHITE);gravity=Gravity.CENTER_VERTICAL;setPadding(dp(10),0,dp(10),0);isClickable=true
   setBackgroundResource(android.R.drawable.list_selector_background)
   setOnClickListener{popup.dismiss();if(edit)showForm(item)else confirmDelete(item)}
  },LinearLayout.LayoutParams(dp(260),dp(42)))}
  popup=PopupWindow(box,dp(272),ViewGroup.LayoutParams.WRAP_CONTENT,true);popup.isOutsideTouchable=true
  popup.setBackgroundDrawable(keyBg(Color.rgb(32,42,56)));popup.elevation=dp(10).toFloat()
  popup.showAtLocation(root,Gravity.TOP or Gravity.CENTER_HORIZONTAL,0,dp(42))
 }
 private fun confirmDelete(item:RecipientShortcut){
  val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(12),dp(14),dp(10));background=keyBg(Color.rgb(32,42,56))}
  box.addView(TextView(this).apply{text="Excluir o atalho "+item.trigger+"?";textSize=14f;setTextColor(Color.WHITE)})
  val buttons=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.END}
  lateinit var popup:PopupWindow
  buttons.addView(actionText("CANCELAR",specialColor){popup.dismiss()},LinearLayout.LayoutParams(dp(92),dp(34)).apply{setMargins(0,dp(12),dp(6),0)})
  buttons.addView(actionText("EXCLUIR",Color.rgb(170,65,65)){popup.dismiss();store.delete(item.id);Toast.makeText(this,"Atalho excluído.",Toast.LENGTH_SHORT).show()},LinearLayout.LayoutParams(dp(82),dp(34)).apply{setMargins(0,dp(12),0,0)})
  box.addView(buttons);popup=PopupWindow(box,dp(290),ViewGroup.LayoutParams.WRAP_CONTENT,true);popup.isOutsideTouchable=true
  popup.setBackgroundDrawable(keyBg(Color.rgb(32,42,56)));popup.elevation=dp(10).toFloat();popup.showAtLocation(root,Gravity.CENTER,0,0)
 }

 private fun addRow(labels:List<String>,left:Float=0f,right:Float=0f,number:Boolean=false){
  val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  if(left>0)row.addView(Space(this),LinearLayout.LayoutParams(0,1,left))
  labels.forEach{label->
   val accent=!number&&label.lowercase() in setOf("a","e","i","o","u","c")
   addKey(row,label,1f,if(number)specialColor else keyColor,{if(accent)showAccentOptions(label)}) {
    when(label) {
     "ABC" -> { symbols=false; buildKeys() }
     "⌫" -> backspace()
     else -> if(label.length==1) type(label)
    }
   }
  }
  if(right>0)row.addView(Space(this),LinearLayout.LayoutParams(0,1,right))
  root.addView(row,LinearLayout.LayoutParams(-1,0,1f))
 }
 private fun showAccentOptions(letter:String){
  val choices=when(letter.lowercase()){"a"->listOf("á","à","â","ã");"e"->listOf("é","ê");"i"->listOf("í");"o"->listOf("ó","ô","õ");"u"->listOf("ú");"c"->listOf("ç");else->emptyList()}
  if(choices.isEmpty())return
  val box=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(dp(5),dp(5),dp(5),dp(5));setBackgroundColor(Color.rgb(37,43,53))}
  lateinit var popup:PopupWindow
  choices.forEach{ch->val shown=if(caps)ch.uppercase()else ch;box.addView(TextView(this).apply{
   text=shown;textSize=22f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);background=keyBg(cyan);isClickable=true;setOnClickListener{type(shown);popup.dismiss()}
  },LinearLayout.LayoutParams(dp(48),dp(52)).apply{setMargins(dp(2),0,dp(2),0)})}
  popup=PopupWindow(box,ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT,true);popup.isOutsideTouchable=true
  popup.setBackgroundDrawable(GradientDrawable().apply{setColor(Color.TRANSPARENT)});popup.elevation=dp(8).toFloat()
  popup.showAtLocation(root,Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,0,dp(190))
 }
 private fun keyBg(color:Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(8).toFloat()}
 private fun addKey(row:LinearLayout,label:String,weight:Float,color:Int,longPress:(()->Unit)?=null,action:()->Unit){
  val key=TextView(this).apply{
   text=label;textSize=if(label=="ESPAÇO")12f else 19f;gravity=Gravity.CENTER;setTextColor(Color.WHITE)
   val mask=keyBg(Color.WHITE)
   background=if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP)RippleDrawable(ColorStateList.valueOf(Color.argb(95,255,255,255)),keyBg(color),mask)else keyBg(color)
   isClickable=true;isFocusable=true;setOnClickListener{action()};if(longPress!=null)setOnLongClickListener{longPress();true}
   if(label=="ESPAÇO")contentDescription="Espaço"
  }
  row.addView(key,LinearLayout.LayoutParams(0,-1,weight).apply{setMargins(dp(2),dp(2),dp(2),dp(2))})
 }

 private fun type(s:String){
  if(formMode){
   values[formField]+=s;updateForm();updateShiftAfterCharacter(s);return
  }
  currentInputConnection?.commitText(s,1)
  updateShiftAfterCharacter(s)
  refreshFromCursor()
 }
 private fun backspace(){
  if(formMode){if(values[formField].isNotEmpty())values[formField]=values[formField].dropLast(1);updateForm();return}
  currentInputConnection?.let{if(!it.deleteSurroundingText(1,0))it.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL));refreshFromCursor()}
 }
 private fun enter(){
  if(formMode){if(formField<2){formField++;updateForm()}else saveForm();return}
  val ic=currentInputConnection?:return
  val action=currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)?:EditorInfo.IME_ACTION_NONE
  if(action!=EditorInfo.IME_ACTION_NONE&&action!=EditorInfo.IME_ACTION_UNSPECIFIED)ic.performEditorAction(action)else ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER))
 }

 private fun ensureSpellChecker(){
  if(spellSession!=null)return
  runCatching{
   val manager=getSystemService(TextServicesManager::class.java)
   spellSession=manager?.newSpellCheckerSession(null,Locale("pt","BR"),object:SpellCheckerSession.SpellCheckerSessionListener{
    override fun onGetSuggestions(results:Array<SuggestionsInfo>){
     val responseToken=spellRequestToken
     if(results.isEmpty() || responseToken.isBlank() || responseToken!=requestedToken)return
     val candidates=(0 until results[0].suggestionsCount).mapNotNull{results[0].getSuggestionAt(it)}
      .filter{it.isNotBlank()&&!it.equals(responseToken,true)}.distinct().take(4)
     shownSuggestions=candidates
     runOnUiThread{if(!formMode)renderWordSuggestions(responseToken,candidates)}
    }
    override fun onGetSentenceSuggestions(results:Array<android.view.textservice.SentenceSuggestionsInfo>){}
   },true)
  }
 }
 private fun refreshFromCursor(){
  if(formMode||!::suggestions.isInitialized)return
  val before=currentInputConnection?.getTextBeforeCursor(64,0)?.toString().orEmpty()
  val token=before.takeLastWhile{!it.isWhitespace()}.uppercase()
  refreshSuggestions(token)
 }
 private fun refreshSuggestions(token:String){
  if(!::suggestions.isInitialized||formMode)return
  suggestions.removeAllViews()
  if(token.isBlank()){requestedToken="";shownSuggestions=emptyList();return}
  val match=store.all().firstOrNull{it.trigger.equals(token,true)}
  if(match!=null){
   requestedToken=token;shownSuggestions=emptyList()
   suggestions.addView(TextView(this).apply{
    text=match.trigger+"   ›   "+match.recipientName;textSize=14f;gravity=Gravity.CENTER_VERTICAL;setTextColor(Color.WHITE)
    setPadding(dp(13),0,dp(13),0);background=keyBg(Color.rgb(45,75,88));isClickable=true;setOnClickListener{expand(match)}
   },LinearLayout.LayoutParams(-1,-1))
   return
  }
  if(token!=requestedToken){requestedToken=token;shownSuggestions=emptyList()}
  ensureSpellChecker()
  renderWordSuggestions(token,shownSuggestions)
  spellRequestToken=token
  runCatching{spellSession?.getSuggestions(TextInfo(token.lowercase()),4,false)}
 }
 private fun renderWordSuggestions(token:String,words:List<String>){
  if(formMode||token!=requestedToken)return
  suggestions.removeAllViews()
  words.forEach{word->
   val item=TextView(this).apply{
    text=word;textSize=14f;gravity=Gravity.CENTER;setTextColor(Color.WHITE)
    setPadding(dp(10),0,dp(10),0);background=keyBg(Color.rgb(45,75,88));isClickable=true
    setOnClickListener{
     val ic=currentInputConnection?:return@setOnClickListener
     ic.deleteSurroundingText(token.length,0);ic.commitText(word,1);refreshFromCursor()
    }
   }
   suggestions.addView(item,LinearLayout.LayoutParams(0,-1,1f).apply{setMargins(dp(2),0,dp(2),0)})
  }
 }
 private fun expand(item:RecipientShortcut){
  val ic=currentInputConnection?:return
  ic.deleteSurroundingText(item.trigger.length,0);ic.commitText(item.recipientName,1)
  val moved=ic.performEditorAction(EditorInfo.IME_ACTION_NEXT)
  if(!moved){ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_TAB));ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_TAB))}
  currentInputConnection?.commitText(item.document,1);store.markUsed(item.id);refreshFromCursor()
 }
}