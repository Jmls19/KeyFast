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
    val parity:String="AMBOS"
)
data class RouteDefinition(val id:String=UUID.randomUUID().toString(),val name:String,val addresses:List<RouteAddress>)

data class NumberRule(val street:String,val number:String,val lowAnchor:String,val highAnchor:String)

class RouteStore(context:android.content.Context) {
    private val prefs=context.getSharedPreferences("keyfast_routes",android.content.Context.MODE_PRIVATE)
    fun routes():List<RouteDefinition> = runCatching {
        val a=JSONArray(prefs.getString("routes","[]"))
        (0 until a.length()).map { i ->
            val o=a.getJSONObject(i); val aa=o.getJSONArray("addresses")
            RouteDefinition(o.getString("id"),o.getString("name"),(0 until aa.length()).map { j ->
                val x=aa.getJSONObject(j)
                RouteAddress(x.getString("id"),x.getString("street"),x.getString("number"),x.optInt("segment",1),x.optString("direction","CRESCENTE"),x.optBoolean("delivered",false),x.optString("cep",""),x.optString("objectCode",""),x.optString("parity","AMBOS"))
            })
        }
    }.getOrDefault(emptyList())
    fun save(route:RouteDefinition) { saveAll(routes().filterNot{it.id==route.id}+route) }
    fun delete(id:String) { saveAll(routes().filterNot{it.id==id}) }
    private fun saveAll(items:List<RouteDefinition>) {
        val a=JSONArray()
        items.forEach { r ->
            val aa=JSONArray()
            r.addresses.forEach { x -> aa.put(JSONObject().put("id",x.id).put("street",x.street).put("number",x.number).put("segment",x.segment).put("direction",x.direction).put("delivered",x.delivered).put("cep",x.cep).put("objectCode",x.objectCode).put("parity",x.parity)) }
            a.put(JSONObject().put("id",r.id).put("name",r.name).put("addresses",aa))
        }
        prefs.edit().putString("routes",a.toString()).apply()
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
