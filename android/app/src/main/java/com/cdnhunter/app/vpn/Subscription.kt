package com.cdnhunter.app.vpn

/**
 * Represents a subscription link (Hiddify, V2RayN, etc)
 * SavedConfig is defined in AppScreen.kt - uses late binding
 */
data class Subscription(
    val id: String,                    // UUID
    val name: String,                  // User-given name (e.g. "My VPN")
    val url: String,                   // Subscription URL
    val configs: List<Any>,            // List<SavedConfig> - imported from ui.AppScreen
    val lastUpdated: Long,             // Milliseconds since epoch
    val nextUpdateSchedule: Long,      // When to refresh next
    val updateInterval: Long = 3600000, // 1 hour default (ms)
    val enabled: Boolean = true,
)
