package com.malikhw.hwgdreqs.network

data class DiscoveredDevice(
    val serviceName: String,
    val host: String,
    val port: Int,
    val login: String,
    val version: String,
)

fun baseUrlFor(host: String, port: Int) = "http://$host:$port"

data class QueueEntry(
    val id: String,
    val name: String,
    val author: String,
    val requester: String,
)
