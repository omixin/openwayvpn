package com.omix.openwayvpn.vpn

data class VlessProfile(
    val rawUri: String,
    val name: String?,
    val host: String,
    val port: Int,
    val uuid: String,
    val network: String,
    val security: String,
    val sni: String?,
    val fingerprint: String?,
    val publicKey: String?,
    val shortId: String?,
    val spiderX: String?,
    val flow: String?,
    val wsHost: String?,
    val wsPath: String?,
    val grpcServiceName: String?,
    val alpn: List<String>,
    val packetEncoding: String?,
)
