package br.com.keyfast
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Offline AES-GCM vault. The encryption key is held by Android Keystore. */
class ShortcutStore(context:Context) {
 private val prefs=context.getSharedPreferences("keyfast_vault",Context.MODE_PRIVATE)
 private val alias="keyfast.shortcuts.aes.v1"
 @Synchronized fun all():List<RecipientShortcut> = runCatching {
  val packed=prefs.getString("payload",null) ?: return emptyList()
  val parts=packed.split(":"); require(parts.size==2)
  val cipher=Cipher.getInstance("AES/GCM/NoPadding")
  cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)))
  val arr=JSONArray(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)).toString(Charsets.UTF_8))
  (0 until arr.length()).map { i -> val o=arr.getJSONObject(i); RecipientShortcut(o.getString("id"),o.getString("trigger"),o.getString("name"),o.getString("document"),o.optLong("updatedAt"),o.optInt("useCount")) }
 }.getOrDefault(emptyList())
 @Synchronized fun save(item:RecipientShortcut) { persist(all().filterNot { it.id==item.id || it.trigger.equals(item.trigger,true) }+item) }
 @Synchronized fun delete(id:String) { persist(all().filterNot { it.id==id }) }
 @Synchronized fun markUsed(id:String) { persist(all().map { if(it.id==id) it.copy(useCount=it.useCount+1,updatedAt=System.currentTimeMillis()) else it }) }
 private fun persist(items:List<RecipientShortcut>) {
  val arr=JSONArray()
  items.forEach { arr.put(JSONObject().put("id",it.id).put("trigger",it.trigger).put("name",it.recipientName).put("document",it.document).put("updatedAt",it.updatedAt).put("useCount",it.useCount)) }
  val c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,key())
  val iv=Base64.encodeToString(c.iv,Base64.NO_WRAP); val data=Base64.encodeToString(c.doFinal(arr.toString().toByteArray()),Base64.NO_WRAP)
  check(prefs.edit().putString("payload","$iv:$data").commit())
 }
 private fun key():SecretKey {
  val ks=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  (ks.getKey(alias,null) as? SecretKey)?.let { return it }
  val gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
  gen.init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build())
  return gen.generateKey()
 }
}
