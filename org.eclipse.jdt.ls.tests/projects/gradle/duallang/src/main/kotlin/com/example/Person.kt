package com.example

data class Person(val name: String, val age: Int) {
    fun isAdult(): Boolean = age >= 18
}
