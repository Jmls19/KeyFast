package br.com.keyfast
data class RecipientShortcut(val id:String,val trigger:String,val recipientName:String,val document:String,val updatedAt:Long=System.currentTimeMillis(),val useCount:Int=0)
