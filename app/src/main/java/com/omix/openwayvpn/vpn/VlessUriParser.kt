package com.omix.openwayvpn.vpn

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object VlessUriParser {
    fun parse(raw: String): Result<VlessProfile> = runCatching {
        val uriText = raw.trim()
        require(uriText.startsWith("vless://", ignoreCase = true)) { "Not a vless URI" }

        val remarkPart = uriText.substringAfter('#', "")
        val name = decode(remarkPart).ifBlank { null }
        val noFragment = uriText.substringBefore('#')
        val uri = URI(noFragment)

        val uuid = uri.userInfo?.trim().orEmpty()
        require(uuid.isNotBlank()) { "Missing UUID in vless URI" }
        val host = uri.host?.trim().orEmpty()
        require(host.isNotBlank()) { "Missing host in vless URI" }
        val port = uri.port
        require(port in 1..65535) { "Invalid port in vless URI" }

        val params = parseQuery(uri.rawQuery.orEmpty())
        val network = params["type"]?.lowercase().orEmpty().ifBlank { "tcp" }
        val security = params["security"]?.lowercase().orEmpty()
        val alpn = params["alpn"]
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

        VlessProfile(
            rawUri = uriText,
            name = name,
            host = host,
            port = port,
            uuid = uuid,
            network = network,
            security = security,
            sni = params["sni"],
            fingerprint = params["fp"],
            publicKey = params["pbk"],
            shortId = params["sid"],
            spiderX = params["spx"],
            flow = params["flow"],
            wsHost = params["host"],
            wsPath = params["path"],
            grpcServiceName = params["serviceName"] ?: params["service_name"],
            alpn = alpn,
            packetEncoding = params["packetEncoding"] ?: params["packetencoding"],
        )
    }

    private fun parseQuery(rawQuery: String): Map<String, String> {
        if (rawQuery.isBlank()) return emptyMap()
        return rawQuery
            .split('&')
            .mapNotNull { token ->
                if (token.isBlank()) return@mapNotNull null
                val key = decode(token.substringBefore('=')).trim()
                val value = decode(token.substringAfter('=', "")).trim()
                if (key.isEmpty()) null else key to value
            }
            .toMap()
    }

    private fun decode(value: String): String {
        return URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }
}
