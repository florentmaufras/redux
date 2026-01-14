package com.florentmaufras.reduxdemo.universities.data

data class University(
    val country: String,
    val webPages: ArrayList<String>?,
    val stateProvince: String?,
    val alphaTwoCode: String,
    val domains: ArrayList<String>,
    val name: String
)
