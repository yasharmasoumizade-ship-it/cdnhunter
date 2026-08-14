package com.cdnhunter.app.vpn

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.URLDecoder
import java.util.Base64
import java.util.UUID

/**
 * Parses subscription URLs (Hiddify, V2RayN, raw list formats)
 */
object SubscriptionParser {
    private const val TAG = "SubscriptionParser"

    /**
     * Fetch and parse subscription URL
     * Supports:
     * - Base64-encoded list (V2RayN format)
     * - Raw newline-separated list
     * - JSON format (future)
     */
    suspend fun parseSubscriptionUrl(
        url: String,
        name: String
    ): Subscription? = withContext(Dispatchers.IO) {
        try {
            // The URL is a credential — a subscription link is all anyone needs to fetch
            // the whole server list — so a release build logs only the user's own name for
            // it, never the address.
            if (com.cdnhunter.app.BuildConfig.DEBUG) {
                Log.d(TAG, "Fetching subscription: $name from $url")
            } else {
                Log.d(TAG, "Fetching subscription: $name")
            }

            
            // Fetch
            val response = try {
                URL(url).readText(Charsets.UTF_8)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch subscription URL", e)
                return@withContext null
            }
            
            if (response.isBlank()) {
                Log.e(TAG, "Empty response from subscription URL")
                return@withContext null
            }
            
            // Try Base64 decode (V2RayN/Clash format)
            val decoded = try {
                String(Base64.getDecoder().decode(response), Charsets.UTF_8)
            } catch (e: Exception) {
                // Not base64, use as-is
                response
            }
            
            // Parse lines
            val configs = decoded.split("\n", "\r\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() && (it.startsWith("vless://") || 
                                               it.startsWith("trojan://") || 
                                               it.startsWith("vmess://") ||
                                               it.startsWith("ss://") ||
                                               it.startsWith("http://") ||
                                               it.startsWith("https://") ||
                                               it.startsWith("socks5://")) }
                .mapNotNull { line ->
                    parseConfig(line)?.let { cfg ->
                        cfg + mapOf(
                            "isImported" to true,
                            "subscriptionId" to UUID.randomUUID().toString(),
                            "subscriptionName" to name
                        )
                    }
                }
            
            if (configs.isEmpty()) {
                Log.e(TAG, "No valid configs found in subscription")
                return@withContext null
            }
            
            Log.d(TAG, "Parsed ${configs.size} configs from subscription")
            
            Subscription(
                id = UUID.randomUUID().toString(),
                name = name,
                url = url,
                configs = configs,
                lastUpdated = System.currentTimeMillis(),
                nextUpdateSchedule = System.currentTimeMillis() + 3600000 // 1 hour
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse subscription", e)
            null
        }
    }

    /**
     * Parse a single config line (vless://, trojan://, etc)
     * Returns a generic map that can be converted to SavedConfig later
     */
    private fun parseConfig(line: String): Map<String, Any?>? {
        return try {
            // Use existing ConfigUriParser if available, or manual parsing
            val uri = java.net.URI(line)
            val scheme = uri.scheme
            val host = uri.host ?: return null
            val port = if (uri.port == -1) {
                when (scheme) {
                    "vless", "trojan" -> 443
                    "vmess", "ss" -> 8388
                    else -> 443
                }
            } else {
                uri.port
            }
            
            // Extract name from fragment (#name)
            val displayName = try {
                val fragment = uri.rawFragment
                if (!fragment.isNullOrEmpty()) {
                    URLDecoder.decode(fragment, "UTF-8")
                } else {
                    host.take(20)
                }
            } catch (e: Exception) {
                host.take(20)
            }
            
            mapOf(
                "id" to line.hashCode().toString(),
                "uri" to line,
                "displayName" to displayName,
                "proto" to (scheme ?: "?"),
                "address" to host,
                "port" to port,
                "network" to "tcp",
                "sni" to host,
                "isImported" to false,
                "subscriptionId" to null,
                "subscriptionName" to null
            )
        } catch (e: Exception) {
            // A config line carries the server address plus its UUID/password, so the
            // line itself only ever reaches the log in a debug build.
            if (com.cdnhunter.app.BuildConfig.DEBUG) {
                Log.w(TAG, "Failed to parse config: $line", e)
            } else {
                Log.w(TAG, "Failed to parse a config line from subscription", e)
            }
            null
        }
    }
}
