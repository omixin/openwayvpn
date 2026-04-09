package com.omix.openwayvpn.vpn

import org.json.JSONArray
import org.json.JSONObject

object XrayConfigFactory {
    fun build(profile: VlessProfile?): String {
        if (profile == null) return buildBootstrapConfig()

        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "info"))
        root.put("inbounds", JSONArray().put(buildSocksInbound()))
        root.put(
            "outbounds",
            JSONArray()
                .put(buildVlessOutbound(profile))
                .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
                .put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
        )
        root.put(
            "routing",
            JSONObject()
                .put("domainStrategy", "AsIs")
                .put(
                    "rules",
                    JSONArray().put(
                        JSONObject()
                            .put("type", "field")
                            .put("inboundTag", JSONArray().put("socks-in"))
                            .put("outboundTag", "proxy")
                    )
                )
        )
        root.put(
            "dns",
            JSONObject().put(
                "servers",
                JSONArray().put("1.1.1.1").put("8.8.8.8")
            )
        )
        return root.toString()
    }

    private fun buildSocksInbound(): JSONObject {
        return JSONObject()
            .put("tag", "socks-in")
            .put("listen", "127.0.0.1")
            .put("port", 10808)
            .put("protocol", "socks")
            .put("settings", JSONObject().put("udp", true))
    }

    private fun buildVlessOutbound(profile: VlessProfile): JSONObject {
        val user = JSONObject()
            .put("id", profile.uuid)
            .put("encryption", "none")
        profile.flow?.takeIf { it.isNotBlank() }?.let { user.put("flow", it) }
        profile.packetEncoding?.takeIf { it.isNotBlank() }?.let { user.put("packetEncoding", it) }

        val vnext = JSONObject()
            .put("address", profile.host)
            .put("port", profile.port)
            .put("users", JSONArray().put(user))

        val settings = JSONObject().put("vnext", JSONArray().put(vnext))

        val streamSettings = JSONObject().put("network", profile.network)
        if (profile.security.isNotBlank()) {
            streamSettings.put("security", profile.security)
        }
        if (profile.network == "ws") {
            val wsHeaders = JSONObject()
            profile.wsHost?.takeIf { it.isNotBlank() }?.let { wsHeaders.put("Host", it) }
            val wsSettings = JSONObject()
                .put("path", profile.wsPath ?: "/")
            if (wsHeaders.length() > 0) wsSettings.put("headers", wsHeaders)
            streamSettings.put("wsSettings", wsSettings)
        } else if (profile.network == "grpc") {
            val grpcSettings = JSONObject()
            profile.grpcServiceName?.takeIf { it.isNotBlank() }?.let { grpcSettings.put("serviceName", it) }
            streamSettings.put("grpcSettings", grpcSettings)
        }

        when (profile.security) {
            "reality" -> {
                val reality = JSONObject()
                profile.sni?.takeIf { it.isNotBlank() }?.let { reality.put("serverName", it) }
                profile.fingerprint?.takeIf { it.isNotBlank() }?.let { reality.put("fingerprint", it) }
                profile.publicKey?.takeIf { it.isNotBlank() }?.let { reality.put("publicKey", it) }
                profile.shortId?.takeIf { it.isNotBlank() }?.let { reality.put("shortId", it) }
                profile.spiderX?.takeIf { it.isNotBlank() }?.let { reality.put("spiderX", it) }
                streamSettings.put("realitySettings", reality)
            }
            "tls", "xtls" -> {
                val tls = JSONObject()
                profile.sni?.takeIf { it.isNotBlank() }?.let { tls.put("serverName", it) }
                profile.fingerprint?.takeIf { it.isNotBlank() }?.let { tls.put("fingerprint", it) }
                if (profile.alpn.isNotEmpty()) {
                    val alpn = JSONArray()
                    profile.alpn.forEach { alpn.put(it) }
                    tls.put("alpn", alpn)
                }
                streamSettings.put("tlsSettings", tls)
            }
        }

        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vless")
            .put("settings", settings)
            .put("streamSettings", streamSettings)
            .put("mux", JSONObject().put("enabled", false))
    }

    private fun buildBootstrapConfig(): String {
        return """
            {
              "log": { "loglevel": "warning" },
              "inbounds": [
                {
                  "tag": "socks-in",
                  "listen": "127.0.0.1",
                  "port": 10808,
                  "protocol": "socks",
                  "settings": { "udp": true }
                }
              ],
              "outbounds": [
                { "tag": "direct", "protocol": "freedom" }
              ],
              "routing": {
                "rules": [
                  {
                    "type": "field",
                    "inboundTag": ["socks-in"],
                    "outboundTag": "direct"
                  }
                ]
              },
              "dns": { "servers": ["1.1.1.1"] }
            }
        """.trimIndent()
    }
}
