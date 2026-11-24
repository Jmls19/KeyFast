package br.com.keyfast

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Xml
import android.provider.OpenableColumns
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import java.io.File
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class RouteManagerActivity:Activity() {
    private lateinit var store:RouteStore
    private lateinit var list:LinearLayout
    private val openFile=4101
    private val saveFile=4102
    private val openDailyFile=4103
    private var exporting:RouteDefinition?=null
    private var expandedSegmentDetails:LinearLayout?=null
    private val textRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val navy=Color.rgb(7,23,50)
    private val blue=Color.rgb(18,99,217)
    private val pale=Color.rgb(225,237,255)
    private val ink=Color.rgb(12,31,65)
    private fun surface(color:Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(15).toFloat();setStroke(dp(1),Color.rgb(205,222,249))}
    private fun action(label:String,primary:Boolean,onClick:()->Unit)=TextView(this).apply{text=label;gravity=Gravity.CENTER;textSize=12f;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);setTextColor(if(primary)Color.WHITE else ink);background=surface(if(primary)blue else pale);isClickable=true;isFocusable=true;setPadding(dp(8),dp(10),dp(8),dp(10));setOnClickListener{onClick()}}
    private fun styleDialog(dialog:AlertDialog){dialog.window?.setBackgroundDrawable(surface(Color.rgb(250,252,255)));dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(blue);dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(ink)}
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState);store=RouteStore(this);render() }
    override fun onResume() { super.onResume(); if(::list.isInitialized) refresh() }
    private fun dp(v:Int)=(v*resources.displayMetrics.density+0.5f).toInt()
    private fun render() {
        window.statusBarColor=navy;window.navigationBarColor=navy
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(20),dp(18),dp(16));background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(navy,Color.rgb(5,17,39)))}
        root.addView(TextView(this).apply{text="ROTEIROS";textSize=25f;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);setTextColor(Color.WHITE);setPadding(dp(2),0,0,dp(14))})
        root.addView(action("▣  IMPORTAR ROTEIRO POR ARQUIVO",true){val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="*/*";putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("text/csv","text/plain","application/json","application/octet-stream","application/pdf","image/*","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))};startActivityForResult(i,openFile)},LinearLayout.LayoutParams(-1,dp(50)).apply{bottomMargin=dp(9)})
        root.addView(action("☷  IMPORTAR LISTA DE ENTREGAS",false){openDailyFilePicker()},LinearLayout.LayoutParams(-1,dp(50)).apply{bottomMargin=dp(9)})
        root.addView(action("▤  COLAR LISTA DO SRO",false){pasteDeliveryList()},LinearLayout.LayoutParams(-1,dp(44)).apply{bottomMargin=dp(9)})
        root.addView(action("+  CRIAR ROTEIRO",true){editRoute(null)},LinearLayout.LayoutParams(-1,dp(50)).apply{bottomMargin=dp(16)})
        val scroll=ScrollView(this);list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};scroll.addView(list);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root);refresh()
    }
    private fun refresh() {
        list.removeAllViews()
        store.routes().forEach { route ->
            val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(12),dp(14),dp(12));background=surface(Color.rgb(250,252,255));elevation=dp(3).toFloat()}
            card.addView(TextView(this).apply{text=route.name;textSize=18f;setTextColor(ink)})
            card.addView(TextView(this).apply{text="${route.addresses.size} endereços • ${route.addresses.count{it.delivered}} entregues";textSize=12f;setTextColor(Color.rgb(92,110,138))})
            val editButton=action("EDITAR ROTEIRO",true){editRoute(route)}
            card.addView(editButton,LinearLayout.LayoutParams(-1,dp(42)).apply{topMargin=dp(8)})
            val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            val open=action("ABRIR",false){startRoute(route)}
            val share=action("COMPARTILHAR",false){exportRoute(route)}
            val delete=action("EXCLUIR",false){AlertDialog.Builder(this@RouteManagerActivity).setTitle("Excluir roteiro?").setMessage("Excluir ${route.name}?").setNegativeButton("CANCELAR",null).setPositiveButton("EXCLUIR"){_,_->store.delete(route.id);refresh()}.show().also{styleDialog(it)}}
            actions.addView(open,LinearLayout.LayoutParams(0,dp(40),1f).apply{rightMargin=dp(4)})
            actions.addView(share,LinearLayout.LayoutParams(0,dp(40),1f).apply{leftMargin=dp(4);rightMargin=dp(4)})
            actions.addView(delete,LinearLayout.LayoutParams(0,dp(40),1f).apply{leftMargin=dp(4)})
            card.addView(actions,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(6)})
            list.addView(card,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)})
        }
        if(store.routes().isEmpty())list.addView(TextView(this).apply{text="Nenhum roteiro cadastrado.";setTextColor(Color.rgb(220,232,250));setPadding(0,dp(20),0,0)})
    }
    private fun startRoute(route:RouteDefinition) {
        store.setActive(route.id)
        if(!android.provider.Settings.canDrawOverlays(this)) {
            startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))
            Toast.makeText(this,"Autorize a janela sobre outros apps e toque em ABRIR novamente.",Toast.LENGTH_LONG).show()
            return
        }
        startService(Intent(this,RouteWidgetService::class.java).setAction(RouteWidgetService.ACTION_SHOW))
        finish()
    }
    private data class SegmentFields(val cep:EditText,val street:EditText,val initial:EditText,val final:EditText,val direction:Spinner,val parity:Spinner,val card:LinearLayout)

    private fun field(label:String, value:String="", numeric:Boolean=false):EditText =
        EditText(this).apply {
            hint=label
            setSingleLine(true)
            setText(value)
            textSize=14f
            setPadding(dp(10),dp(8),dp(10),dp(8))
            if(numeric) inputType=android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(ink);setHintTextColor(Color.rgb(125,140,164));background=surface(Color.rgb(250,252,255))
        }

    private fun choiceSpinner(options:List<String>, selected:String):Spinner {
        val spinner=Spinner(this)
        val adapter=object:ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,options) {
            override fun getView(position:Int,convertView:View?,parent:ViewGroup):View {
                val view=super.getView(position,convertView,parent) as TextView
                view.setTextColor(ink);view.textSize=14f;view.setPadding(dp(10),dp(9),dp(10),dp(9))
                return view
            }
            override fun getDropDownView(position:Int,convertView:View?,parent:ViewGroup):View {
                val view=super.getDropDownView(position,convertView,parent) as TextView
                view.setTextColor(ink);view.textSize=14f;view.setPadding(dp(14),dp(12),dp(14),dp(12))
                view.setBackgroundColor(Color.rgb(250,252,255))
                return view
            }
        }
        spinner.adapter=adapter
        spinner.background=surface(pale)
        spinner.setSelection(options.indexOf(selected).takeIf{it>=0}?:0)
        return spinner
    }

    private fun renumberSegments(container:LinearLayout) {
        for(i in 0 until container.childCount) {
            val card=container.getChildAt(i) as? LinearLayout ?: continue
            val header=card.getChildAt(0) as? LinearLayout ?: continue
            (header.getChildAt(0) as? TextView)?.text="TRECHO ${i+1}"
        }
    }

    private fun moveSegment(container:LinearLayout,card:LinearLayout,delta:Int) {
        val from=container.indexOfChild(card)
        val to=from+delta
        if(from<0||to !in 0 until container.childCount) return
        val params=card.layoutParams
        container.removeViewAt(from)
        container.addView(card,to,params)
        renumberSegments(container)
    }

    private fun addSegment(container:LinearLayout, existing:List<RouteAddress> = emptyList(), expanded:Boolean=false):SegmentFields {
        if(expanded) expandedSegmentDetails?.visibility=View.GONE
        val first=existing.firstOrNull()
        val nums=existing.mapNotNull{it.number.toIntOrNull()}
        val descending=first?.direction?.contains("DECRESC",true)==true
        val card=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(10),dp(8),dp(10),dp(10))
            background=surface(Color.rgb(250,252,255))
        }
        val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        val heading=TextView(this).apply {
            text="TRECHO ${container.childCount+1}"
            textSize=13f
            typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
            setTextColor(blue)
            setPadding(dp(2),dp(8),dp(4),dp(8))
            isClickable=true
            setOnClickListener {
                val details=card.getChildAt(1)
                if(details.visibility==View.VISIBLE) {
                    details.visibility=View.GONE
                    if(expandedSegmentDetails===details) expandedSegmentDetails=null
                } else {
                    expandedSegmentDetails?.visibility=View.GONE
                    details.visibility=View.VISIBLE
                    expandedSegmentDetails=details as LinearLayout
                }
            }
        }
        val up=action("↑",false){moveSegment(container,card,-1)}
        val down=action("↓",false){moveSegment(container,card,1)}
        header.addView(heading,LinearLayout.LayoutParams(0,dp(38),1f))
        header.addView(up,LinearLayout.LayoutParams(dp(42),dp(36)).apply{rightMargin=dp(4)})
        header.addView(down,LinearLayout.LayoutParams(dp(42),dp(36)))
        val details=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;visibility=if(expanded)View.VISIBLE else View.GONE}
        val cep=field("CEP",first?.cep.orEmpty())
        val street=field("Logradouro",first?.street.orEmpty())
        val initial=field("N. Inicial",if(nums.isEmpty())"" else if(descending)nums.max().toString() else nums.min().toString(),true)
        val final=field("N. Final",if(nums.isEmpty())"" else if(descending)nums.min().toString() else nums.max().toString(),true)
        val direction=choiceSpinner(listOf("Crescente","Decrescente"),if(descending)"Decrescente" else "Crescente")
        val parityValue=first?.parity?.uppercase()?.takeIf{it in listOf("PARES","IMPARES","AMBOS")}?:"AMBOS"
        val parity=choiceSpinner(listOf("Ambos","Pares","Ímpares"),when(parityValue){"PARES"->"Pares";"IMPARES"->"Ímpares";else->"Ambos"})
        details.addView(cep,LinearLayout.LayoutParams(-1,-2))
        details.addView(street,LinearLayout.LayoutParams(-1,-2))
        val numbers=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        numbers.addView(initial,LinearLayout.LayoutParams(0,-2,1f).apply{rightMargin=dp(5)})
        numbers.addView(final,LinearLayout.LayoutParams(0,-2,1f).apply{leftMargin=dp(5)})
        details.addView(numbers)
        details.addView(TextView(this).apply{text="Sentido";textSize=12f;setTextColor(ink);setPadding(dp(2),dp(4),0,0)})
        details.addView(direction,LinearLayout.LayoutParams(-1,-2))
        details.addView(TextView(this).apply{text="Numeração";textSize=12f;setTextColor(ink);setPadding(dp(2),dp(7),0,0)})
        details.addView(parity,LinearLayout.LayoutParams(-1,-2))
        card.addView(header)
        card.addView(details)
        container.addView(card,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)})
        if(expanded) expandedSegmentDetails=details
        return SegmentFields(cep,street,initial,final,direction,parity,card)
    }

    private fun editRoute(existing:RouteDefinition?) {
        if(existing!=null && existing.addresses.any{it.objectCode.isNotBlank()}) {
            val routeName=field("Nome do roteiro",existing.name)
            AlertDialog.Builder(this).setTitle("Lista de objetos importada")
                .setMessage("Esta lista contém objetos importados. A edição por trechos não se aplica a ela; você pode alterar o nome sem perder os códigos e a ordem.")
                .setView(routeName).setNegativeButton("CANCELAR",null)
                .setPositiveButton("SALVAR") { _,_ ->
                    runCatching { store.save(existing.copy(name=routeName.text.toString().trim().ifBlank{existing.name}));refresh() }
                        .onFailure { Toast.makeText(this,it.message?: "Não foi possível salvar o roteiro.",Toast.LENGTH_LONG).show() }
                }.show().also { styleDialog(it) }
            return
        }
        expandedSegmentDetails=null
        val name=field("Nome do roteiro",existing?.name.orEmpty())
        val segments=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val grouped=existing?.addresses?.groupBy{it.segment}?.toSortedMap()?.values?.toList().orEmpty()
        if(grouped.isEmpty()) addSegment(segments,expanded=true) else grouped.forEachIndexed{index,items->addSegment(segments,items,expanded=index==0)}
        val add=action("+  ADICIONAR TRECHO",false){addSegment(segments,expanded=true)}
        val content=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(8),0,dp(8),0)
            addView(name)
            addView(TextView(this@RouteManagerActivity).apply{text="Trechos do roteiro — toque para editar e use as setas para mudar a ordem";textSize=13f;setTextColor(ink);setPadding(0,dp(8),0,dp(6))})
            addView(segments)
            addView(add)
        }
        val scroll=ScrollView(this).apply{addView(content)}
        val dialog=AlertDialog.Builder(this)
            .setTitle(if(existing==null)"Novo roteiro" else "Editar roteiro")
            .setView(scroll)
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Salvar",null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                runCatching {
                    require(name.text.toString().isNotBlank()) { "Informe o nome do roteiro." }
                    val views=segmentsViews(segments)
                    require(views.isNotEmpty() && views.size==segments.childCount) { "Confira os trechos adicionados." }
                    val parsed=mutableListOf<RouteAddress>()
                    views.forEachIndexed{idx,s->
                        val street=s.street.text.toString().trim()
                        val first=s.initial.text.toString().toIntOrNull()
                            ?: throw IllegalArgumentException("Informe o número inicial do trecho ${idx+1}.")
                        val last=s.final.text.toString().toIntOrNull()
                            ?: throw IllegalArgumentException("Informe o número final do trecho ${idx+1}.")
                        val cepValue=s.cep.text.toString().trim()
                        require(street.isNotBlank()) { "Informe o logradouro do trecho ${idx+1}." }
                        require(first>=0 && last>=0) { "Os números dos trechos não podem ser negativos." }
                        val descending=s.direction.selectedItem?.toString()=="Decrescente"
                        val parity=when(s.parity.selectedItem?.toString()){"Pares"->"PARES";"Ímpares"->"IMPARES";else->"AMBOS"}
                        val step=if(descending)-1 else 1
                        var n=if(descending)maxOf(first,last) else minOf(first,last)
                        val stop=if(descending)minOf(first,last) else maxOf(first,last)
                        val before=parsed.size
                        while(true){
                            val selected=when(parity){"PARES"->n%2==0;"IMPARES"->n%2!=0;else->true}
                            val savedRandomReference=store.rules().any{it.street.equals(street,true)&&it.number==n.toString()}
                            if(selected||savedRandomReference)parsed.add(RouteAddress(street=street,number=n.toString(),segment=idx+1,direction=if(descending)"DECRESCENTE" else "CRESCENTE",cep=cepValue,parity=parity))
                            if(n==stop)break
                            n+=step
                        }
                        require(parsed.size>before) { "O intervalo do trecho ${idx+1} não contém números da paridade selecionada." }
                    }
                    require(parsed.isNotEmpty()) { "Adicione ao menos um trecho válido." }
                    val route=RouteDefinition(existing?.id?:java.util.UUID.randomUUID().toString(),name.text.toString().trim(),parsed)
                    store.save(route)
                    val persisted=store.routes().firstOrNull{it.id==route.id}
                    check(persisted!=null && persisted.addresses.size==route.addresses.size) {
                        "O roteiro não foi confirmado no armazenamento. Tente salvar novamente."
                    }
                    refresh()
                    Toast.makeText(this,"Roteiro salvo: ${route.name} (${route.addresses.size} endereços).",Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                }.onFailure {
                    Toast.makeText(this,it.message?: "Não foi possível salvar o roteiro.",Toast.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
        styleDialog(dialog)
    }

    private fun segmentsViews(container:LinearLayout):List<SegmentFields> {
        return (0 until container.childCount).mapNotNull { index ->
            val card=container.getChildAt(index) as? LinearLayout ?: return@mapNotNull null
            val details=card.getChildAt(1) as? LinearLayout ?: return@mapNotNull null
            val cep=details.getChildAt(0) as? EditText ?: return@mapNotNull null
            val street=details.getChildAt(1) as? EditText ?: return@mapNotNull null
            val numbers=details.getChildAt(2) as? LinearLayout ?: return@mapNotNull null
            val initial=numbers.getChildAt(0) as? EditText ?: return@mapNotNull null
            val final=numbers.getChildAt(1) as? EditText ?: return@mapNotNull null
            val direction=details.getChildAt(4) as? Spinner ?: return@mapNotNull null
            val parity=details.getChildAt(6) as? Spinner ?: return@mapNotNull null
            SegmentFields(cep,street,initial,final,direction,parity,card)
        }
    }

    private fun openDailyFilePicker() {
        val types=arrayOf(
            "application/pdf","text/plain","text/csv","text/tab-separated-values",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel","application/rtf","text/rtf",
            "application/vnd.oasis.opendocument.text","application/vnd.oasis.opendocument.spreadsheet","image/*"
        )
        val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type="*/*"
            putExtra(Intent.EXTRA_MIME_TYPES,types)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { startActivityForResult(intent,openDailyFile) }
        catch(_:android.content.ActivityNotFoundException) {
            Toast.makeText(this,"Nenhum gerenciador de arquivos disponível para abrir documentos.",Toast.LENGTH_LONG).show()
        }
    }

    private fun pasteDeliveryList() {
        val input=EditText(this).apply { hint="Cole aqui as linhas copiadas do SRO Web"; minLines=8; gravity=Gravity.TOP }
        val scroll=ScrollView(this).apply{addView(input)}
        AlertDialog.Builder(this).setTitle("Colar lista de entregas")
            .setMessage("Cole as colunas Código, Ordem, Endereço e CEP. As demais serão ignoradas.")
            .setView(scroll).setNegativeButton("Cancelar",null)
            .setPositiveButton("Importar"){_,_->importDeliveryText(input.text.toString(),"Lista SRO")}
            .show().also{styleDialog(it)}
    }
    private fun isTrackingCode(value:String)=Regex("^[A-Z]{2}\\d{9}[A-Z]{2}$").matches(value.trim().uppercase())
    private fun importDeliveryText(raw:String, suggestedName:String) {
        runCatching {
            val addresses=parseDeliveryList(raw).ifEmpty { parseRecognizedObjects(raw) }
            require(addresses.isNotEmpty()) { "Nenhum objeto no padrão AA123456789BB foi reconhecido." }
            importDailyList(addresses,suggestedName)
        }.onFailure { Toast.makeText(this,it.message?: "Não foi possível reconhecer a lista.",Toast.LENGTH_LONG).show() }
    }
    private fun importDailyList(imported:List<RouteAddress>,sourceName:String) {
        val routes=store.routes()
        require(routes.isNotEmpty()) { "Cadastre primeiro o roteiro permanente antes de importar a lista diária." }
        val active=store.activeId()
        val initial=routes.indexOfFirst{it.id==active}.let{if(it<0)routes.lastIndex else it}
        val picker=choiceSpinner(routes.map{it.name},routes[initial].name)
        val examples=imported.take(3).joinToString("\n") { it.objectCode }
        val grouped=imported.groupBy { "${it.street}|${it.number}|${it.cep}" }
        val addressSummary=grouped.entries.take(3).joinToString("\n") { entry ->
            val parts=entry.key.split("|")
            "${parts.getOrElse(0){""}}, nº ${parts.getOrElse(1){""}} • CEP ${parts.getOrElse(2){""}} • ${entry.value.size} objetos"
        }
        val panel=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(18),dp(4),dp(18),0)
            addView(TextView(this@RouteManagerActivity).apply {
                text="${imported.size} objetos identificados\nEndereços extraídos:\n$addressSummary\n\nExemplos de códigos:\n$examples\n\nSelecione o roteiro permanente para organizar as entregas."
                textSize=13f;setTextColor(ink)
            })
            addView(picker,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10)})
        }
        AlertDialog.Builder(this).setTitle("Lista diária • $sourceName").setView(panel)
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("CRUZAR COM ROTEIRO") { _,_ ->
                val route=routes[picker.selectedItemPosition.coerceIn(0,routes.lastIndex)]
                runCatching {
                    val (matched,unmatched)=store.matchDailyDeliveries(route,imported)
                    require(matched.isNotEmpty()) { "Nenhum endereço da lista corresponde ao roteiro selecionado. O roteiro permanente não foi alterado." }
                    store.saveDailyDeliveries(route.id,matched)
                    store.setActive(route.id)
                    refresh()
                    Toast.makeText(this,"${matched.size} entregas organizadas no roteiro. $unmatched sem correspondência.",Toast.LENGTH_LONG).show()
                }.onFailure { Toast.makeText(this,it.message?: "Não foi possível montar a sequência diária.",Toast.LENGTH_LONG).show() }
            }.show().also{styleDialog(it)}
    }
    private fun parseDeliveryList(raw:String):List<RouteAddress> {
        val result=mutableListOf<Pair<Int,RouteAddress>>()
        raw.lineSequence().forEach { line ->
            if(line.isBlank()) return@forEach
            val cells=(if(line.contains("\t")) line.split("\t") else line.split(";")).map{it.trim().trim('"')}
            if(cells.size<4) return@forEach
            val code=cells[0].uppercase()
            if(!isTrackingCode(code)) return@forEach
            val order=cells[1].toIntOrNull() ?: (result.size+1)
            val fullAddress=cells[2]
            val cep=cells.firstOrNull{Regex("\\d{5}-?\\d{3}").matches(it)}.orEmpty()
            if(fullAddress.isBlank()) return@forEach
            val match=Regex("^(.*?)(?:\\s+(\\d+[A-Za-z]?))$").matchEntire(fullAddress)
            val street=match?.groupValues?.get(1)?.trim().orEmpty().ifBlank{fullAddress}
            val house=match?.groupValues?.get(2).orEmpty()
            result.add(order to RouteAddress(street=street,number=house,segment=1,direction="LISTA SRO",cep=cep,objectCode=code))
        }
        return result.sortedBy{it.first}.map{it.second}
    }

    private fun parseCsv(raw:String):List<RouteAddress> {
        val result=mutableListOf<RouteAddress>()
        val lines=raw.lineSequence().map{it.trim()}.filter{it.isNotBlank()}.toList()
        val header=lines.firstOrNull().orEmpty()
        val hasHeader=header.contains("logradouro",true)||header.contains("trecho",true)||header.contains("cep",true)
        val newFormat=header.contains("cep",true)
        lines.drop(if(hasHeader)1 else 0).forEachIndexed{idx,line->
            val p=line.split(';',',','\t').map{it.trim().trim('"')}
            val cep:String
            val segment:Int
            val street:String
            val start:Int
            val end:Int
            val direction:String
            if(newFormat || (!hasHeader && p.size>=5 && p[0].filter{it.isDigit()}.length>=7 && p[2].toIntOrNull()!=null && p[3].toIntOrNull()!=null)) {
                cep=p.getOrNull(0).orEmpty()
                segment=idx+1
                street=p.getOrNull(1).orEmpty()
                start=p.getOrNull(2)?.toInt()?:throw IllegalArgumentException("Número inicial inválido")
                end=p.getOrNull(3)?.toInt()?:throw IllegalArgumentException("Número final inválido")
                direction=p.getOrNull(4)?.uppercase()?:if(end>=start)"CRESCENTE" else "DECRESCENTE"
            } else if(p.size>=5) {
                cep=""
                segment=p[0].toIntOrNull()?:idx+1
                street=p[1];start=p[2].toInt();end=p[3].toInt();direction=p[4].uppercase()
            } else {
                require(p.size>=3)
                cep=""
                segment=idx+1;street=p[0];start=p[1].toInt();end=p[2].toInt()
                direction=p.getOrNull(3)?.uppercase()?:if(end>=start)"CRESCENTE" else "DECRESCENTE"
            }
            val parity=when(p.getOrNull(5)?.uppercase()) {
                "PAR","PARES","EVEN"->"PARES"
                "IMPAR","ÍMPAR","IMPARES","ÍMPARES","ODD"->"IMPARES"
                else->"AMBOS"
            }
            val descending=direction.contains("DECRESC",true) || (p.size<4&&end<start)
            val step=if(descending)-1 else 1
            var n=if(descending)maxOf(start,end) else minOf(start,end)
            val last=if(descending)minOf(start,end) else maxOf(start,end)
            val before=result.size
            while(true) {
                val selected=when(parity){"PARES"->n%2==0;"IMPARES"->n%2!=0;else->true}
                val savedRandomReference=store.rules().any{it.street.equals(street,true)&&it.number==n.toString()}
                if(selected||savedRandomReference)result.add(RouteAddress(street=street,number=n.toString(),segment=segment,direction=if(descending)"DECRESCENTE" else "CRESCENTE",cep=cep,parity=parity))
                if(n==last)break
                n+=step
            }
            require(result.size>before){"O intervalo não contém números da paridade selecionada."}
        }
        return result
    }
    private fun parseXlsx(bytes:ByteArray):String {
        val files=mutableMapOf<String,ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while(true){val entry=zip.nextEntry?:break;files[entry.name]=zip.readBytes();zip.closeEntry()}
        }
        val shared=mutableListOf<String>()
        files["xl/sharedStrings.xml"]?.let { xml ->
            val p=Xml.newPullParser();p.setInput(ByteArrayInputStream(xml),"UTF-8")
            var inSi=false;var inText=false;val value=StringBuilder()
            while(p.eventType!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT){
                when(p.eventType){
                    org.xmlpull.v1.XmlPullParser.START_TAG->{if(p.name=="si"){inSi=true;value.setLength(0)};if(p.name=="t"&&inSi)inText=true}
                    org.xmlpull.v1.XmlPullParser.TEXT->{if(inText)value.append(p.text)}
                    org.xmlpull.v1.XmlPullParser.END_TAG->{if(p.name=="t")inText=false;if(p.name=="si"){shared.add(value.toString());inSi=false}}
                };p.next()
            }
        }
        val sheet=files["xl/worksheets/sheet1.xml"]?:throw IllegalArgumentException("Planilha sem primeira aba.")
        val p=Xml.newPullParser();p.setInput(ByteArrayInputStream(sheet),"UTF-8")
        val rows=mutableListOf<Map<Int,String>>();var row=mutableMapOf<Int,String>()
        var inCell=false;var inValue=false;var cellType="";var cellRef="";val value=StringBuilder()
        while(p.eventType!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT){
            when(p.eventType){
                org.xmlpull.v1.XmlPullParser.START_TAG->when(p.name){
                    "row"->{row=mutableMapOf()}
                    "c"->{inCell=true;cellRef=p.getAttributeValue(null,"r")?:"";cellType=p.getAttributeValue(null,"t")?:"";value.setLength(0)}
                    "v"->{if(inCell)inValue=true}
                    "t"->{if(inCell&&cellType=="inlineStr")inValue=true}
                }
                org.xmlpull.v1.XmlPullParser.TEXT->{if(inCell&&inValue)value.append(p.text)}
                org.xmlpull.v1.XmlPullParser.END_TAG->when(p.name){
                    "v","t"->{inValue=false}
                    "c"->{
                        var col=0
                        cellRef.takeWhile{it.isLetter()}.uppercase().forEach{col=col*26+(it-'A'+1)}
                        val raw=value.toString()
                        val resolved=if(cellType=="s")shared.getOrNull(raw.toIntOrNull()?:-1)?:raw else raw
                        if(col>0)row[col-1]=resolved
                        inCell=false;inValue=false
                    }
                    "row"->{if(row.isNotEmpty())rows.add(row)}
                }
            };p.next()
        }
        return rows.joinToString("\n"){r->(0..(r.keys.maxOrNull()?:0)).joinToString(";"){i->r[i].orEmpty().replace(";"," ")}}
    }
    private fun parseDocx(bytes:ByteArray):String {
        val document=ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var found:ByteArray?=null
            while(true) {
                val entry=zip.nextEntry?:break
                if(entry.name=="word/document.xml") found=zip.readBytes()
                zip.closeEntry()
                if(found!=null) break
            }
            found
        } ?: throw IllegalArgumentException("Documento Word inválido ou sem texto.")
        return String(document,Charsets.UTF_8)
            .replace(Regex("<w:tab[^>]*/>"),"\t")
            .replace(Regex("</w:p>"),"\n")
            .replace(Regex("<[^>]+>")," ")
            .replace("&amp;","&").replace("&lt;","<").replace("&gt;",">")
            .replace(Regex("[ \\t]+")," ").replace(Regex("\\n{2,}"),"\\n").trim()
    }

    private fun normalizeDocumentText(raw:String,extension:String):String = when(extension) {
        "rtf" -> raw.replace(Regex("\\\\[a-zA-Z]+-?\\d* ?")," ")
            .replace(Regex("[{}]")," ").replace(Regex("\\s+")," ")
        "html","htm" -> raw.replace(Regex("(?is)<script[^>]*>.*?</script>")," ")
            .replace(Regex("(?is)<style[^>]*>.*?</style>")," ")
            .replace(Regex("<[^>]+>")," ").replace("&nbsp;"," ").replace("&amp;","&")
            .replace(Regex("\\s+")," ")
        else -> raw
    }

    private fun recognizeImage(uri:Uri,name:String) {
        val progress=AlertDialog.Builder(this).setTitle("Reconhecendo lista").setMessage("Lendo o texto da imagem...").setCancelable(false).create()
        progress.show()
        runCatching { InputImage.fromFilePath(this,uri) }.onSuccess { image ->
            textRecognizer.process(image)
                .addOnSuccessListener { result -> progress.dismiss(); showOcrReview(result.text,name) }
                .addOnFailureListener { error -> progress.dismiss(); Toast.makeText(this,"Falha no reconhecimento: \${error.localizedMessage}",Toast.LENGTH_LONG).show() }
        }.onFailure { progress.dismiss();Toast.makeText(this,"Não foi possível abrir a imagem.",Toast.LENGTH_LONG).show() }
    }

    private fun recognizePdf(uri:Uri,name:String) {
        val progress=AlertDialog.Builder(this).setTitle("Reconhecendo PDF").setMessage("Lendo as páginas do documento...").setCancelable(false).create()
        progress.show()
        runCatching {
            val file=File(cacheDir,"keyfast_ocr_\${System.currentTimeMillis()}.pdf")
            contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
            file
        }.onSuccess { file ->
            runCatching {
                val descriptor=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)
                PdfRenderer(descriptor)
            }.onSuccess { renderer ->
                val output=StringBuilder()
                fun nextPage(index:Int) {
                    if(index>=renderer.pageCount) {
                        renderer.close();progress.dismiss();file.delete()
                        showOcrReview(output.toString(),name);return
                    }
                    val page=renderer.openPage(index)
                    val scale=minOf(2.0,2000.0/maxOf(page.width,page.height))
                    val bitmap=Bitmap.createBitmap((page.width*scale).toInt().coerceAtLeast(1),(page.height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
                    page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);page.close()
                    textRecognizer.process(InputImage.fromBitmap(bitmap,0))
                        .addOnSuccessListener { result -> output.append("\n").append(result.text);bitmap.recycle();nextPage(index+1) }
                        .addOnFailureListener { bitmap.recycle();nextPage(index+1) }
                }
                nextPage(0)
            }.onFailure { progress.dismiss();file.delete();Toast.makeText(this,"Não foi possível abrir o PDF.",Toast.LENGTH_LONG).show() }
        }.onFailure { progress.dismiss();Toast.makeText(this,"Falha ao ler o arquivo PDF.",Toast.LENGTH_LONG).show() }
    }

    private fun showOcrReview(raw:String,name:String) {
        if(raw.isBlank()) { Toast.makeText(this,"Nenhum texto foi reconhecido no documento.",Toast.LENGTH_LONG).show();return }
        importRecognizedText(raw,name)
    }

    private fun importRecognizedText(raw:String,name:String) {
        runCatching {
            val addresses=parseRecognizedObjects(raw)
            require(addresses.isNotEmpty()) { "Nenhum código de objeto dos Correios foi reconhecido." }
            importDailyList(addresses,name)
        }.onFailure { Toast.makeText(this,it.message?: "Não foi possível reconhecer os objetos.",Toast.LENGTH_LONG).show() }
    }

    private fun parseRecognizedObjects(raw:String):List<RouteAddress> {
        val lines=raw.lines().map { it.trim() }.filter { it.isNotBlank() }
        val codeRegex=Regex("(?i)\\b[A-Z]{2}\\d{9}[A-Z]{2}\\b")
        val hits=mutableListOf<Pair<Int,String>>()
        lines.forEachIndexed { index,line -> codeRegex.findAll(line).forEach { hits.add(index to it.value.uppercase()) } }
        val result=mutableListOf<RouteAddress>()
        hits.forEachIndexed { hitIndex,hit ->
            val nextLine=hits.getOrNull(hitIndex+1)?.first ?: lines.size
            val chunk=lines.subList(hit.first,nextLine).joinToString(" ")
            val code=hit.second
            val cleaned=chunk.replace(codeRegex," ").replace(Regex("(?i)\\b(CEP|OBJETO|ORDEM|ENDEREÇO)\\b")," ")
            val cep=Regex("\\b\\d{5}-?\\d{3}\\b").find(cleaned)?.value.orEmpty()
            val withoutCep=cleaned.replace(Regex("\\b\\d{5}-?\\d{3}\\b")," ")
            val normalized=withoutCep.replace(Regex("[|;•]+")," ").replace(Regex("\\s+")," ").trim(' ', '-', ',', ':')
            val numberMatch=Regex("(?:,|\\s)(\\d+[A-Za-z]?)$").find(normalized)
            val house=numberMatch?.groupValues?.get(1) ?: "0"
            val street=if(numberMatch!=null)normalized.removeRange(numberMatch.range).trim(' ', '-', ',', ':') else normalized
            result.add(RouteAddress(street=street.ifBlank { "Endereço a conferir" },number=house,segment=1,direction="LISTA IMPORTADA",cep=cep,objectCode=code))
        }
        return result.distinctBy { it.objectCode }
    }

    private fun exportRoute(route:RouteDefinition) {
        exporting=route
        val i=Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/json";putExtra(Intent.EXTRA_TITLE,route.name.replace(Regex("[^A-Za-z0-9_-]"),"_")+".keyfast")}
        startActivityForResult(i,saveFile)
    }
    private fun processDailyFile(uri:Uri) {
        val mime=contentResolver.getType(uri).orEmpty().lowercase()
        val path=uri.lastPathSegment.orEmpty()
        val displayName=runCatching {
            contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
                if(it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull().orEmpty().ifBlank { path.substringAfterLast(':').substringAfterLast('/') }
        val extension=displayName.substringAfterLast('.', "").lowercase().ifBlank {
            when(mime) {
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
                "application/rtf","text/rtf" -> "rtf"
                "text/html" -> "html"
                else -> ""
            }
        }
        val filename=displayName.substringBeforeLast('.').ifBlank { "Lista de entregas" }
        when {
            mime=="application/pdf" || extension=="pdf" -> recognizePdf(uri,filename)
            mime.startsWith("image/") || extension in listOf("jpg","jpeg","png","webp","heic") -> recognizeImage(uri,filename)
            extension=="doc" || mime=="application/msword" ->
                Toast.makeText(this,"Formato Word antigo (.doc) não é compatível. Salve o documento como .docx e tente novamente.",Toast.LENGTH_LONG).show()
            extension=="xls" || mime=="application/vnd.ms-excel" ->
                Toast.makeText(this,"Formato Excel antigo (.xls) não é compatível. Salve a planilha como .xlsx e tente novamente.",Toast.LENGTH_LONG).show()
            else -> runCatching {
                val bytes=contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalArgumentException("Não foi possível abrir o arquivo selecionado.")
                val raw=when {
                    extension=="docx" -> parseDocx(bytes)
                    extension=="xlsx" -> parseXlsx(bytes)
                    bytes.size>=2 && bytes[0]==0x50.toByte() && bytes[1]==0x4b.toByte() ->
                        throw IllegalArgumentException("Arquivo compactado não reconhecido. Use PDF, TXT, CSV, DOCX ou XLSX.")
                    else -> normalizeDocumentText(String(bytes,Charsets.UTF_8),extension)
                }
                require(raw.isNotBlank()) { "O arquivo está vazio ou não contém texto legível." }
                importDeliveryText(raw,filename)
            }.onFailure {
                Toast.makeText(this,it.message?: "Não foi possível ler a lista selecionada.",Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK||data?.data==null)return
        val uri=data.data!!
        when(requestCode){
            openDailyFile->{processDailyFile(uri)}
            openFile->{
                val mime=contentResolver.getType(uri).orEmpty().lowercase()
                val filename=uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')?.substringBeforeLast('.')?.ifBlank{"Lista importada"}?:"Lista importada"
                when {
                    mime=="application/pdf" || uri.lastPathSegment?.endsWith(".pdf",true)==true->recognizePdf(uri,filename)
                    mime.startsWith("image/")->recognizeImage(uri,filename)
                    else->runCatching{
                        val bytes=contentResolver.openInputStream(uri)!!.use{it.readBytes()}
                        val raw=if(bytes.size>=2&&bytes[0]==0x50.toByte()&&bytes[1]==0x4b.toByte())parseXlsx(bytes) else String(bytes,Charsets.UTF_8)
                        if(raw.trimStart().startsWith("{")) {
                            val importedRoute=store.importJson(raw)
                            refresh()
                            Toast.makeText(this,"Roteiro \"${importedRoute.name}\" salvo com ${importedRoute.addresses.size} endereços.",Toast.LENGTH_LONG).show()
                        }
                        else if(Regex("(?i)[A-Z]{2}\\d{9}[A-Z]{2}").containsMatchIn(raw)) importDeliveryText(raw,filename)
                        else {
                            val addresses=parseCsv(raw)
                            require(addresses.isNotEmpty()){"Nenhum trecho ou objeto foi reconhecido no arquivo."}
                            store.save(RouteDefinition(name=filename,addresses=addresses))
                            refresh();Toast.makeText(this,"Roteiro importado.",Toast.LENGTH_SHORT).show()
                        }
                    }.onFailure{Toast.makeText(this,it.message?: "Não foi possível importar o arquivo.",Toast.LENGTH_LONG).show()}
                }
            }
            saveFile->runCatching{
                val route=exporting?:return
                contentResolver.openOutputStream(uri)!!.bufferedWriter().use{it.write(store.exportJson(route))}
                val share=Intent(Intent.ACTION_SEND).apply{type="application/json";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}
                startActivity(Intent.createChooser(share,"Compartilhar roteiro KeyFast"))
            }.onFailure{Toast.makeText(this,"Falha ao exportar roteiro.",Toast.LENGTH_LONG).show()}
        }
    }
}
