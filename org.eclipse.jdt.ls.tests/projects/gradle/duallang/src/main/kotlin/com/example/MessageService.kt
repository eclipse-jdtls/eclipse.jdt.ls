package com.example

class MessageService(private val prefix: String = "Hello") {

    fun formatMsg(name: String): String = "$prefix, $name!"

    fun getPrefix(): String = prefix

}
