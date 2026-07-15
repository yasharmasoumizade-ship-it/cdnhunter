package com.cdnhunter.app.engine

import com.cdnhunter.app.data.ProxyConfig
import com.cdnhunter.app.data.ProxyType
import java.util.UUID

object ConfigGenerator {

    fun generate(config: ProxyConfig): String {
        val uuid = if (config.uuid == "auto") UUID.randomUUID().toString() else config.uuid
        return when (config.type) {
            ProxyType.VLESS -> generateVless(config, uuid)
            ProxyType.VMESS -> generateVmess(config, uuid)
            ProxyType.TROJAN -> generateTrojan(config, uuid)
            ProxyType.SHADOWSOCKS -> generateShadowsocks(config, uuid)
            ProxyType.SINGBOX -> generateSingBox(config, uuid)
            ProxyType.CLASH -> generateClash(config, uuid)
        }
    }

    private fun generateVless(c: ProxyConfig, uuid: String) = buildString {
        appendLine("vless://$uuid@${c.ip}:${c.port}?encryption=none&security=${c.security}&sni=${c.sni.ifBlank { c.ip }}&type=ws&host=${c.host.ifBlank { c.sni }}&path=${c.path}#CDNHunter-${c.ip}")
        appendLine()
        appendLine("// VLESS Config")
        appendLine("// IP: ${c.ip}")
        appendLine("// SNI: ${c.sni}")
        appendLine("// Host: ${c.host}")
    }

    private fun generateVmess(c: ProxyConfig, uuid: String): String {
        val json = """{"v":"2","ps":"CDNHunter-${c.ip}","add":"${c.ip}","port":"${c.port}","id":"$uuid","aid":"0","scy":"auto","net":"ws","type":"none","host":"${c.host.ifBlank { c.sni }}","path":"${c.path}","tls":"${c.security}","sni":"${c.sni.ifBlank { c.ip }}"}"""
        val encoded = android.util.Base64.encodeToString(json.toByteArray(), android.util.Base64.NO_WRAP)
        return "vmess://$encoded"
    }

    private fun generateTrojan(c: ProxyConfig, uuid: String) = buildString {
        appendLine("trojan://$uuid@${c.ip}:${c.port}?security=${c.security}&sni=${c.sni.ifBlank { c.ip }}&type=ws&host=${c.host.ifBlank { c.sni }}&path=${c.path}#CDNHunter-${c.ip}")
    }

    private fun generateShadowsocks(c: ProxyConfig, uuid: String): String {
        val method = "chacha20-ietf-poly1305"
        val userInfo = "$method:$uuid"
        val encoded = android.util.Base64.encodeToString(
            userInfo.toByteArray(), android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE
        ).trimEnd('=')
        return "ss://$encoded@${c.ip}:${c.port}#CDNHunter-${c.ip}"
    }

    private fun generateSingBox(c: ProxyConfig, uuid: String) = buildString {
        appendLine("""{
  "type": "vless",
  "tag": "CDNHunter-${c.ip}",
  "server": "${c.ip}",
  "server_port": ${c.port},
  "uuid": "$uuid",
  "tls": {
    "enabled": true,
    "server_name": "${c.sni.ifBlank { c.ip }}",
    "insecure": true
  },
  "transport": {
    "type": "ws",
    "path": "${c.path}",
    "headers": { "Host": "${c.host.ifBlank { c.sni }}" }
  }
}""")
    }

    private fun generateClash(c: ProxyConfig, uuid: String) = buildString {
        appendLine("""proxies:
  - name: CDNHunter-${c.ip}
    type: vless
    server: ${c.ip}
    port: ${c.port}
    uuid: $uuid
    udp: true
    tls: true
    servername: ${c.sni.ifBlank { c.ip }}
    network: ws
    ws-opts:
      path: ${c.path}
      headers:
        Host: ${c.host.ifBlank { c.sni }}""")
    }
}
