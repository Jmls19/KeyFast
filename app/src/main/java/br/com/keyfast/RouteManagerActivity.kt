package br.com.keyfast

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import android.util.Xml
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class RouteManagerActivity:Activity() {
    private lateinit var store:RouteStore
    private lateinit var list:LinearLayout
    private val openFile=4101
    private val saveFile=4102
    private var exporting:RouteDefinition?=null
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState);store=RouteStore(this);render() }
    private fun dp(v:Int)=(v*resources.displayMetrics.density+0.5f).toInt()
    private fun render() {
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(18),dp(16),dp(16));setBackgroundColor(0xff101827.toInt())}
        root.addView(TextView(this).apply{text="ROTEIROS";textSize=24f;setTextColor(-1);setPadding(0,0,0,dp(12))})
        root.addView(Button(this).apply{text="IMPORTAR ROTEIRO POR ARQUIVO";setOnClickListener{val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="*/*";putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("text/csv","text/plain","application/json","application/octet-stream"))};startActivityForResult(i,openFile)}})
        root.addView(Button(this).apply{text="+ CRIAR ROTEIRO";setOnClickListener{editRoute(null)}})
        val scroll=ScrollView(this);list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};scroll.addView(list);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root);refresh()
    }
    private fun refresh() {
        list.removeAllViews()
        store.routes().forEach { route ->
            val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(10));setBackgroundColor(0xff263449.toInt())}
            card.addView(TextView(this).apply{text=route.name;textSize=18f;setTextColor(-1)})
            card.addView(TextView(this).apply{text="${route.addresses.size} endereços • ${route.addresses.count{it.delivered}} entregues";textSize=12f;setTextColor(0xffcbd5e1.toInt())})
            val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            actions.addView(Button(this).apply{text="ABRIR";setOnClickListener{startRoute(route)}})
            actions.addView(Button(this).apply{text="EDITAR";setOnClickListener{editRoute(route)}})
            actions.addView(Button(this).apply{text="COMPARTILHAR";setOnClickListener{exportRoute(route)}})
            actions.addView(Button(this).apply{text="EXCLUIR";setOnClickListener{AlertDialog.Builder(this@RouteManagerActivity).setMessage("Excluir ${route.name}?").setNegativeButton("Cancelar",null).setPositiveButton("Excluir"){_,_->store.delete(route.id);refresh()}.show()}})
            card.addView(actions);list.addView(card,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)})
        }
        if(store.routes().isEmpty())list.addView(TextView(this).apply{text="Nenhum roteiro cadastrado.";setTextColor(0xffcbd5e1.toInt());setPadding(0,dp(20),0,0)})
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
    private fun editRoute(existing:RouteDefinition?) {
        val name=EditText(this).apply{hint="Nome do roteiro";setSingleLine();setText(existing?.name?:"")}
        val content=EditText(this).apply{hint="Logradouro;Inicial;Final;Sentido\nRua A;1;110;crescente";minLines=6;gravity=Gravity.TOP;setText(existing?.addresses?.let{addressesToCsv(it)}?:"")}
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(8),0,dp(8),0);addView(name);addView(content)}
        AlertDialog.Builder(this).setTitle(if(existing==null)"Novo roteiro" else "Editar roteiro").setView(box).setNegativeButton("Cancelar",null).setPositiveButton("Salvar"){_,_->
            runCatching {
                val parsed=parseCsv(content.text.toString())
                require(name.text.isNotBlank()&&parsed.isNotEmpty())
                store.save(RouteDefinition(existing?.id?:java.util.UUID.randomUUID().toString(),name.text.toString().trim(),parsed))
                refresh()
            }.onFailure{Toast.makeText(this,"Confira o nome e o formato dos trechos.",Toast.LENGTH_LONG).show()}
        }.show()
    }
    private fun addressesToCsv(items:List<RouteAddress>):String = items.joinToString("\n"){"${it.street};${it.number};${it.number};${it.direction}"}
    private fun parseCsv(raw:String):List<RouteAddress> {
        val result=mutableListOf<RouteAddress>()
        raw.lineSequence().map{it.trim()}.filter{it.isNotBlank()}.forEachIndexed{idx,line->
            val p=line.split(';',',','\t').map{it.trim().trim('"')}
            if(idx==0&&p.any{it.contains("logradouro",true)||it.contains("trecho",true)})return@forEachIndexed
            val segment:Int
            val street:String
            val start:Int
            val end:Int
            val direction:String
            if(p.size>=5) {
                segment=p[0].toIntOrNull()?:idx+1
                street=p[1];start=p[2].toInt();end=p[3].toInt();direction=p[4].uppercase()
            } else {
                require(p.size>=3)
                segment=idx+1;street=p[0];start=p[1].toInt();end=p[2].toInt()
                direction=p.getOrNull(3)?.uppercase()?:if(end>=start)"CRESCENTE" else "DECRESCENTE"
            }
            val step=if(end>=start)1 else -1
            var n=start
            while(true){result.add(RouteAddress(street=street,number=n.toString(),segment=segment,direction=direction));if(n==end)break;n+=step}
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
                else {
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
