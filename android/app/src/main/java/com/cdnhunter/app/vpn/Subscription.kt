package com.cdnhunter.app.vpn

import com.cdnhunter.app.ui.AppScreen

/**
 * Represents a subscription link (Hiddify, V2RayN, etc)
 */
data class Subscription(
    val id: String,                    // UUID
    val name: String,                  // User-given name (e.g. "My VPN")
    val url: String,                   // Subscription URL
    val configs: List<AppScreen.SavedConfig>,    // Parsed server configs
    val lastUpdated: Long,             // Milliseconds since epoch
    val nextUpdateSchedule: Long,      // When to refresh next
    val updateInterval: Long = 3600000, // 1 hour default (ms)
    val enabled: Boolean = true,
)
