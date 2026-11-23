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
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Xml
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class RouteManagerActivity:Activity() {
    private lateinit var store:RouteStore
    private lateinit var list:LinearLayout
    private val openFile=4101
    private val saveFile=4102
    private var exporting:RouteDefinition?=null
    private val navy=Color.rgb(7,23,50)
    private val blue=Color.rgb(18,99,217)
    private val pale=Color.rgb(225,237,255)
    private val ink=Color.rgb(12,31,65)
    private fun surface(color:Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(15).toFloat();setStroke(dp(1),Color.rgb(205,222,249))}
    private fun action(label:String,primary:Boolean,onClick:()->Unit)=TextView(this).apply{text=label;gravity=Gravity.CENTER;textSize=12f;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);setTextColor(if(primary)Color.WHITE else ink);background=surface(if(primary)blue else pale);isClickable=true;isFocusable=true;setPadding(dp(8),dp(10),dp(8),dp(10));setOnClickListener{onClick()}}
    private fun styleDialog(dialog:AlertDialog){dialog.window?.setBackgroundDrawable(surface(Color.rgb(250,252,255)));dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(blue);dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(ink)}
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState);store=RouteStore(this);render() }
    private fun dp(v:Int)=(v*resources.displayMetrics.density+0.5f).toInt()
    private fun render() {
        window.statusBarColor=navy;window.navigationBarColor=navy
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(20),dp(18),dp(16));background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(navy,Color.rgb(5,17,39)))}
        root.addView(TextView(this).apply{text="ROTEIROS";textSize=25f;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);setTextColor(Color.WHITE);setPadding(dp(2),0,0,dp(14))})
        root.addView(action("▣  IMPORTAR ROTEIRO POR ARQUIVO",true){val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="*/*";putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("text/csv","text/plain","application/json","application/octet-stream"))};startActivityForResult(i,openFile)},LinearLayout.LayoutParams(-1,dp(50)).apply{bottomMargin=dp(9)})
        root.addView(action("☷  IMPORTAR LISTA DE ENTREGAS (SRO)",false){pasteDeliveryList()},LinearLayout.LayoutParams(-1,dp(50)).apply{bottomMargin=dp(9)})
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
            val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            actions.addView(action("ABRIR",true){startRoute(route)})
            actions.addView(action("EDITAR",false){editRoute(route)})
            actions.addView(action("COMPARTILHAR",false){exportRoute(route)})
            actions.addView(action("EXCLUIR",false){AlertDialog.Builder(this@RouteManagerActivity).setTitle("Excluir roteiro?").setMessage("Excluir ${route.name}?").setNegativeButton("CANCELAR",null).setPositiveButton("EXCLUIR"){_,_->store.delete(route.id);refresh()}.show().also{styleDialog(it)}})
            card.addView(actions);list.addView(card,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)})
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
            override fun getView(position:Int,convertView:View?,parent:ViewGroup?):View {
                val view=super.getView(position,convertView,parent) as TextView
                view.setTextColor(ink);view.textSize=14f;view.setPadding(dp(10),dp(9),dp(10),dp(9))
                return view
            }
            override fun getDropDownView(position:Int,convertView:View?,parent:ViewGroup?):View {
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

    private fun addSegment(container:LinearLayout, existing:List<RouteAddress> = emptyList()):SegmentFields {
        val first=existing.firstOrNull()
        val nums=existing.mapNotNull{it.number.toIntOrNull()}
        val descending=first?.direction?.contains("DECRESC",true)==true
        val card=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(10),dp(8),dp(10),dp(10))
            background=surface(Color.rgb(250,252,255))
        }
        val heading=TextView(this).apply {
            text="TRECHO ${container.childCount+1}"
            textSize=12f
            setTextColor(blue)
            setPadding(0,0,0,dp(4))
        }
        val cep=field("CEP",first?.cep.orEmpty())
        val street=field("Logradouro",first?.street.orEmpty())
        val initial=field("N. Inicial",if(nums.isEmpty())"" else if(descending)nums.max().toString() else nums.min().toString(),true)
        val final=field("N. Final",if(nums.isEmpty())"" else if(descending)nums.min().toString() else nums.max().toString(),true)
        val direction=choiceSpinner(listOf("Crescente","Decrescente"),if(descending)"Decrescente" else "Crescente")
        val parityValue=first?.parity?.uppercase()?.takeIf{it in listOf("PARES","IMPARES","AMBOS")}?:"AMBOS"
        val parity=choiceSpinner(listOf("Ambos","Pares","Ímpares"),when(parityValue){"PARES"->"Pares";"IMPARES"->"Ímpares";else->"Ambos"})
        card.addView(heading)
        card.addView(cep,LinearLayout.LayoutParams(-1,-2))
        card.addView(street,LinearLayout.LayoutParams(-1,-2))
        val numbers=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        numbers.addView(initial,LinearLayout.LayoutParams(0,-2,1f).apply{rightMargin=dp(5)})
        numbers.addView(final,LinearLayout.LayoutParams(0,-2,1f).apply{leftMargin=dp(5)})
        card.addView(numbers)
        val directionLabel=TextView(this).apply{text="Sentido";textSize=12f;setTextColor(ink);setPadding(dp(2),dp(4),0,0)}
        card.addView(directionLabel)
        card.addView(direction,LinearLayout.LayoutParams(-1,-2))
        val parityLabel=TextView(this).apply{text="Numeração";textSize=12f;setTextColor(ink);setPadding(dp(2),dp(7),0,0)}
        card.addView(parityLabel)
        card.addView(parity,LinearLayout.LayoutParams(-1,-2))
        container.addView(card,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)})
        return SegmentFields(cep,street,initial,final,direction,parity,card)
    }

    private fun editRoute(existing:RouteDefinition?) {
        val name=field("Nome do roteiro",existing?.name.orEmpty())
        val segments=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val grouped=existing?.addresses?.groupBy{it.segment}?.toSortedMap()?.values?.toList().orEmpty()
        if(grouped.isEmpty()) addSegment(segments) else grouped.forEach{addSegment(segments,it)}
        val add=action("+  ADICIONAR TRECHO",false){addSegment(segments)}
        val content=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(8),0,dp(8),0)
            addView(name)
            addView(TextView(this@RouteManagerActivity).apply{text="Dados de cada trecho";textSize=14f;setTextColor(ink);setPadding(0,dp(8),0,dp(6))})
            addView(segments)
            addView(add)
        }
        val scroll=ScrollView(this).apply{addView(content)}
        AlertDialog.Builder(this)
            .setTitle(if(existing==null)"Novo roteiro" else "Editar roteiro")
            .setView(scroll)
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Salvar"){_,_->
                runCatching {
                    require(name.text.isNotBlank())
                    val parsed=mutableListOf<RouteAddress>()
                    segmentsViews(segments).forEachIndexed{idx,s->
                        val street=s.street.text.toString().trim()
                        val first=s.initial.text.toString().toInt()
                        val last=s.final.text.toString().toInt()
                        val cepValue=s.cep.text.toString().trim()
                        require(street.isNotBlank() && first>=0 && last>=0)
                        val descending=s.direction.selectedItem?.toString()=="Decrescente"
                        val parity=when(s.parity.selectedItem?.toString()){"Pares"->"PARES";"Ímpares"->"IMPARES";else->"AMBOS"}
                        val step=if(descending)-1 else 1
                        var n=if(descending)maxOf(first,last) else minOf(first,last)
                        val stop=if(descending)minOf(first,last) else maxOf(first,last)
                        val before=parsed.size
                        while(true){
                            val selected=when(parity){"PARES"->n%2==0;"IMPARES"->n%2!=0;else->true}
                            if(selected)parsed.add(RouteAddress(street=street,number=n.toString(),segment=idx+1,direction=if(descending)"DECRESCENTE" else "CRESCENTE",cep=cepValue,parity=parity))
                            if(n==stop)break
                            n+=step
                        }
                        require(parsed.size>before){"O intervalo não contém números da paridade selecionada."}
                    }
                    require(parsed.isNotEmpty())
                    store.save(RouteDefinition(existing?.id?:java.util.UUID.randomUUID().toString(),name.text.toString().trim(),parsed))
                    refresh()
                }.onFailure{Toast.makeText(this,"Confira o nome e os campos dos trechos.",Toast.LENGTH_LONG).show()}
            }.show().also{styleDialog(it)}
    }

    private fun segmentsViews(container:LinearLayout):List<SegmentFields> {
        // The five input widgets are kept in each segment card in a fixed order.
        return (0 until container.childCount).mapNotNull { index ->
            val card=container.getChildAt(index) as? LinearLayout ?: return@mapNotNull null
            val cep=card.getChildAt(1) as? EditText ?: return@mapNotNull null
            val street=card.getChildAt(2) as? EditText ?: return@mapNotNull null
            val numbers=card.getChildAt(3) as? LinearLayout ?: return@mapNotNull null
            val initial=numbers.getChildAt(0) as? EditText ?: return@mapNotNull null
            val final=numbers.getChildAt(1) as? EditText ?: return@mapNotNull null
            val direction=card.getChildAt(5) as? Spinner ?: return@mapNotNull null
            SegmentFields(cep,street,initial,final,direction,card)
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
            val addresses=parseDeliveryList(raw)
            require(addresses.isNotEmpty()) { "Nenhuma linha de entrega reconhecida." }
            val name=EditText(this).apply{setText(suggestedName);hint="Nome do roteiro";setSingleLine()}
            AlertDialog.Builder(this).setTitle("Nomear roteiro").setView(name)
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Salvar"){_,_->
                    val title=name.text.toString().trim().ifBlank{suggestedName}
                    store.save(RouteDefinition(name=title,addresses=addresses))
                    refresh()
                    Toast.makeText(this,"\${addresses.size} entregas importadas.",Toast.LENGTH_LONG).show()
                }.show().also{styleDialog(it)}
        }.onFailure { Toast.makeText(this,it.message?: "Não foi possível reconhecer a lista.",Toast.LENGTH_LONG).show() }
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
            val descending=direction.contains("DECRESC",true) || (p.size<4&&end<start)
            val step=if(descending)-1 else 1
            var n=if(descending)maxOf(start,end) else minOf(start,end)
            val last=if(descending)minOf(start,end) else maxOf(start,end)
            while(true){
                val selected=when(parity){"PARES"->n%2==0;"IMPARES"->n%2!=0;else->true}\n                if(selected)result.add(RouteAddress(street=street,number=n.toString(),segment=segment,direction=if(descending)"DECRESCENTE" else "CRESCENTE",cep=cep,parity=parity))
                if(n==last)break
                n+=step
            }
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
    private fun exportRoute(route:RouteDefinition) {
        exporting=route
        val i=Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/json";putExtra(Intent.EXTRA_TITLE,route.name.replace(Regex("[^A-Za-z0-9_-]"),"_")+".keyfast")}
        startActivityForResult(i,saveFile)
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK||data?.data==null)return
        val uri=data.data!!
        when(requestCode){
            openFile->runCatching{
                val bytes=contentResolver.openInputStream(uri)!!.use{it.readBytes()}
                val raw=if(bytes.size>=2&&bytes[0]==0x50.toByte()&&bytes[1]==0x4b.toByte())parseXlsx(bytes) else String(bytes,Charsets.UTF_8)
                if(raw.trimStart().startsWith("{"))store.importJson(raw)
                else if(raw.lineSequence().any{line->line.split("\t").firstOrNull()?.trim()?.let{isTrackingCode(it)}==true || line.split(";").firstOrNull()?.trim()?.let{isTrackingCode(it)}==true}) {
                    importDeliveryText(raw,uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')?.substringBeforeLast('.')?: "Lista SRO")
                } else {
                    val title=uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')?.substringBeforeLast('.')?.ifBlank{"Roteiro importado"}?:"Roteiro importado"
                    store.save(RouteDefinition(name=title,addresses=parseCsv(raw)))
                }
                refresh();Toast.makeText(this,"Roteiro importado.",Toast.LENGTH_SHORT).show()
            }.onFailure{Toast.makeText(this,"Não foi possível importar. Use CSV ou arquivo .keyfast.",Toast.LENGTH_LONG).show()}
            saveFile->runCatching{
                val route=exporting?:return
                contentResolver.openOutputStream(uri)!!.bufferedWriter().use{it.write(store.exportJson(route))}
                val share=Intent(Intent.ACTION_SEND).apply{type="application/json";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}
                startActivity(Intent.createChooser(share,"Compartilhar roteiro KeyFast"))
            }.onFailure{Toast.makeText(this,"Falha ao exportar roteiro.",Toast.LENGTH_LONG).show()}
        }
    }
}
