package br.com.keyfast

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class RouteAddress(
    val id:String=UUID.randomUUID().toString(),
    val street:String,
    val number:String,
    val segment:Int=1,
    val direction:String="CRESCENTE",
    val delivered:Boolean=false,
    val cep:String="",
    val objectCode:String="",
    val parity:String="AMBOS",
    val objectCodes:List<String> = emptyList()
)
data class RouteDefinition(val id:String=UUID.randomUUID().toString(),val name:String,val addresses:List<RouteAddress>)

data class NumberRule(val street:String,val number:String,val lowAnchor:String,val highAnchor:String)

class RouteStore(context:android.content.Context) {
    private val prefs=context.getSharedPreferences("keyfast_routes",android.content.Context.MODE_PRIVATE)
    fun routes():List<RouteDefinition> = runCatching {
        val a=JSONArray(prefs.getString("routes","[]"))
        (0 until a.length()).map { i ->
            val o=a.getJSONObject(i); val aa=o.getJSONArray("addresses")
            RouteDefinition(o.optString("id",UUID.randomUUID().toString()),o.optString("name","Roteiro"),(0 until aa.length()).map { j ->
                val x=aa.getJSONObject(j)
                RouteAddress(x.optString("id",UUID.randomUUID().toString()),x.optString("street",""),x.optString("number",""),x.optInt("segment",1),x.optString("direction","CRESCENTE"),x.optBoolean("delivered",false),x.optString("cep",""),x.optString("objectCode",""),x.optString("parity","AMBOS"),(x.optJSONArray("objectCodes")?.let { codes -> (0 until codes.length()).map { codes.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()))
            })
        }
    }.getOrDefault(emptyList())
    fun save(route:RouteDefinition) { saveAll(routes().filterNot{it.id==route.id}+route) }
    fun delete(id:String) { saveAll(routes().filterNot{it.id==id}) }
    private fun saveAll(items:List<RouteDefinition>) {
        val a=JSONArray()
        items.forEach { r ->
            val aa=JSONArray()
            r.addresses.forEach { x -> aa.put(JSONObject().put("id",x.id).put("street",x.street).put("number",x.number).put("segment",x.segment).put("direction",x.direction).put("delivered",x.delivered).put("cep",x.cep).put("objectCode",x.objectCode).put("parity",x.parity).put("objectCodes",JSONArray((x.objectCodes.ifEmpty { listOfNotNull(x.objectCode.takeIf { it.isNotBlank() }) })))) }
            a.put(JSONObject().put("id",r.id).put("name",r.name).put("addresses",aa))
        }
        val serialized=a.toString()
        check(runCatching { JSONArray(serialized) }.isSuccess) { "Não foi possível validar os roteiros." }
        check(prefs.edit().putString("routes",serialized).commit()) { "Não foi possível gravar os roteiros no armazenamento." }
    }
    fun saveDailyDeliveries(routeId:String,items:List<RouteAddress>) {
        val a=JSONArray()
        items.forEach { x -> a.put(JSONObject().put("id",x.id).put("street",x.street).put("number",x.number).put("segment",x.segment).put("direction",x.direction).put("delivered",x.delivered).put("cep",x.cep).put("objectCode",x.objectCode).put("parity",x.parity).put("objectCodes",JSONArray((x.objectCodes.ifEmpty { listOfNotNull(x.objectCode.takeIf { it.isNotBlank() }) })))) }
        val payload=JSONObject().put("routeId",routeId).put("items",a).toString()
        check(prefs.edit().putString("daily_deliveries",payload).commit()) { "Não foi possível salvar a lista diária." }
    }
    fun dailyDeliveries(routeId:String):List<RouteAddress> = runCatching {
        val o=JSONObject(prefs.getString("daily_deliveries","{}"))
        if(o.optString("routeId")!=routeId) return@runCatching emptyList()
        val a=o.optJSONArray("items")?:JSONArray()
        (0 until a.length()).map { i -> val x=a.getJSONObject(i); RouteAddress(x.optString("id",UUID.randomUUID().toString()),x.optString("street",""),x.optString("number",""),x.optInt("segment",1),x.optString("direction","CRESCENTE"),x.optBoolean("delivered",false),x.optString("cep",""),x.optString("objectCode",""),x.optString("parity","AMBOS"),(x.optJSONArray("objectCodes")?.let { codes -> (0 until codes.length()).map { codes.optString(it) }.filter { it.isNotBlank() } } ?: emptyList())) }
    }.getOrDefault(emptyList())
    private fun normalizeAddressPart(value:String)=value.lowercase().replace(Regex("[^\\p{L}\\p{N}]"),"")
    private fun codesOf(item:RouteAddress):List<String> =
        (item.objectCodes.ifEmpty { listOfNotNull(item.objectCode.takeIf { it.isNotBlank() }) })
            .map { it.replace(Regex("[^A-Za-z0-9]"),"").uppercase() }.filter { it.matches(Regex("[A-Z]{2}\\d{9}[A-Z]{2}")) }.distinct()

    fun matchDailyDeliveries(route:RouteDefinition, imported:List<RouteAddress>):Pair<List<RouteAddress>,Int> {
        val ordered=applyRules(route).addresses
        val grouped=imported.groupBy {
            "${normalizeAddressPart(it.street)}|${normalizeAddressPart(it.number)}|${it.cep.filter { c -> c.isDigit() }}"
        }.values.map { group ->
            val first=group.first()
            val allCodes=group.flatMap { codesOf(it) }.distinct()
            first.copy(objectCode=allCodes.firstOrNull().orEmpty(),objectCodes=allCodes,delivered=false)
        }
        val matched=grouped.mapNotNull { item ->
            val streetKey=normalizeAddressPart(item.street)
            val numberKey=normalizeAddressPart(item.number)
            val cepKey=item.cep.filter { it.isDigit() }
            if(streetKey.isBlank()) return@mapNotNull null
            val candidates=ordered.withIndex().filter { (_,saved) ->
                val streetOk=streetKey==normalizeAddressPart(saved.street)
                val numberOk=if(numberKey.isBlank() || numberKey=="0") true else numberKey==normalizeAddressPart(saved.number)
                val savedCep=saved.cep.filter { it.isDigit() }
                val cepOk=cepKey.isBlank() || savedCep.isBlank() || cepKey==savedCep
                streetOk && numberOk && cepOk
            }
            val match=if(numberKey.isBlank() || numberKey=="0") candidates.singleOrNull() else candidates.firstOrNull()
            match ?: return@mapNotNull null
            match.index to item.copy(
                street=match.value.street,number=match.value.number,
                segment=match.value.segment,direction=match.value.direction,
                cep=match.value.cep.ifBlank { item.cep },delivered=false
            )
        }.sortedBy { it.first }.map { it.second }
        return matched to (grouped.size-matched.size)
    }
    fun updateDailyAddress(routeId:String,addressId:String) {
        val items=dailyDeliveries(routeId).map { if(it.id==addressId) it.copy(delivered=true) else it }
        saveDailyDeliveries(routeId,items)
    }
    fun activeId():String?=prefs.getString("active_route",null)
    fun setActive(id:String?) { prefs.edit().putString("active_route",id).apply() }
    fun rules():List<NumberRule> = runCatching {
        val a=JSONArray(prefs.getString("global_number_rules","[]"))
        (0 until a.length()).map { i -> val o=a.getJSONObject(i); NumberRule(o.getString("street"),o.getString("number"),o.getString("low"),o.getString("high")) }
    }.getOrDefault(emptyList())
    fun saveRule(rule:NumberRule) {
        val all=rules().filterNot{it.street.equals(rule.street,true)&&it.number==rule.number}
        val a=JSONArray()
        (all+rule).forEach { a.put(JSONObject().put("street",it.street).put("number",it.number).put("low",it.lowAnchor).put("high",it.highAnchor)) }
        prefs.edit().putString("global_number_rules",a.toString()).apply()
    }
    fun applyRules(route:RouteDefinition):RouteDefinition {
        var list=route.addresses.toMutableList()
        rules().forEach { rule ->
            val special=list.firstOrNull{it.street.equals(rule.street,true)&&it.number==rule.number} ?: return@forEach
            val low=list.indexOfFirst{it.street.equals(rule.street,true)&&it.number==rule.lowAnchor}
            val high=list.indexOfFirst{it.street.equals(rule.street,true)&&it.number==rule.highAnchor}
            if(low>=0&&high>=0&&low!=high) {
                list.removeAll{it.id==special.id}
                val a=list.indexOfFirst{it.street.equals(rule.street,true)&&it.number==rule.lowAnchor}
                val b=list.indexOfFirst{it.street.equals(rule.street,true)&&it.number==rule.highAnchor}
                // Keep the special address immediately after the lower anchor in ascending order;
                // when the route is reversed, this naturally places it immediately before that anchor.
                val insert=if(a<b)a+1 else a
                list.add(insert,special)
            }
        }
        return route.copy(addresses=list)
    }
    fun updateAddress(routeId:String,addressId:String,transform:(RouteAddress)->RouteAddress) {
        val r=routes().firstOrNull{it.id==routeId}?:return
        save(r.copy(addresses=r.addresses.map{if(it.id==addressId)transform(it)else it}))
    }
    fun exportJson(route:RouteDefinition):String {
        val a=JSONArray()
        route.addresses.forEach { x -> a.put(JSONObject().put("street",x.street).put("number",x.number).put("segment",x.segment).put("direction",x.direction).put("cep",x.cep).put("objectCode",x.objectCode).put("parity",x.parity)) }
        val rules=JSONArray()
        rules().filter{rule->route.addresses.any{it.street.equals(rule.street,true)}}.forEach{rules.put(JSONObject().put("street",it.street).put("number",it.number).put("low",it.lowAnchor).put("high",it.highAnchor))}
        return JSONObject().put("format","keyfast-route").put("version",1).put("name",route.name).put("addresses",a).put("numberRules",rules).toString(2)
    }
    fun importJson(raw:String):RouteDefinition {
        val o=JSONObject(raw); require(o.getString("format")=="keyfast-route"&&o.getInt("version")==1)
        val a=o.getJSONArray("addresses")
        val addresses=(0 until a.length()).map{i->val x=a.getJSONObject(i);RouteAddress(street=x.getString("street"),number=x.getString("number"),segment=x.optInt("segment",1),direction=x.optString("direction","CRESCENTE"),cep=x.optString("cep",""),objectCode=x.optString("objectCode",""),parity=x.optString("parity","AMBOS"))}
        val route=RouteDefinition(name=o.getString("name"),addresses=addresses)
        val rr=o.optJSONArray("numberRules")?:JSONArray()
        for(i in 0 until rr.length()){val x=rr.getJSONObject(i);saveRule(NumberRule(x.getString("street"),x.getString("number"),x.getString("low"),x.getString("high")))}
        save(route);return route
    }
}
