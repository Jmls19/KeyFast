package br.com.keyfast

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import java.io.BufferedReader
import java.io.InputStreamReader

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
                val raw=contentResolver.openInputStream(uri)!!.bufferedReader().use{it.readText()}
                if(raw.trimStart().startsWith("{"))store.importJson(raw)
                else {
                    val lines=raw.lines()
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
