package com.cdnhunter.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdnhunter.app.vpn.AppSettings

/**
 * Enhanced Settings Screen with new features:
 * - MTU Settings with presets and testing
 * - Ad Blocker (R.O.B.E.R.T style)
 * - Theme & Appearance customization
 * - Connection notifications & sounds
 * - Server management (favorites, custom names)
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Settings Header
        Text(
            "⚙️ SETTINGS",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )

        // Tabs
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text("VPN", modifier = Modifier.padding(16.dp))
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Text("Network", modifier = Modifier.padding(16.dp))
            }
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                Text("UI", modifier = Modifier.padding(16.dp))
            }
            Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }) {
                Text("Advanced", modifier = Modifier.padding(16.dp))
            }
        }

        // Content
        when (selectedTab) {
            0 -> VpnSettingsTab(context)
            1 -> NetworkSettingsTab(context)
            2 -> AppearanceSettingsTab(context)
            3 -> AdvancedSettingsTab(context)
        }
    }
}

@Composable
private fun VpnSettingsTab(context: Context) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // DoH Toggle
        SettingToggleItem(
            title = "🔐 DNS over HTTPS (DoH)",
            subtitle = "Encrypt DNS requests",
            value = AppSettings.useDoh(context),
            onValueChange = { AppSettings.setUseDoh(context, it) }
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Kill Switch
        SettingToggleItem(
            title = "⚡ Kill Switch",
            subtitle = "Block all traffic if VPN disconnects",
            value = AppSettings.killSwitchEnabled(context),
            onValueChange = { AppSettings.setKillSwitchEnabled(context, it) }
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Auto-Reconnect
        SettingToggleItem(
            title = "🔄 Auto-Reconnect",
            subtitle = "Automatically reconnect on disconnect",
            value = AppSettings.autoReconnectEnabled(context),
            onValueChange = { AppSettings.setAutoReconnectEnabled(context, it) }
        )

        if (AppSettings.autoReconnectEnabled(context)) {
            var retries by remember { mutableStateOf(AppSettings.maxRetryAttempts(context).toFloat()) }
            Column(modifier = Modifier.padding(start = 32.dp, top = 8.dp)) {
                Text("Max Retries: ${retries.toInt()}", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = retries,
                    onValueChange = {
                        retries = it
                        AppSettings.setMaxRetryAttempts(context, it.toInt())
                    },
                    valueRange = 1f..5f,
                    steps = 3
                )
            }
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Ad Blocker
        SettingToggleItem(
            title = "🚫 Ad Blocker (R.O.B.E.R.T)",
            subtitle = "Block ads, trackers, and malware",
            value = AppSettings.adBlockerEnabled(context),
            onValueChange = { AppSettings.setAdBlockerEnabled(context, it) }
        )

        if (AppSettings.adBlockerEnabled(context)) {
            Column(modifier = Modifier.padding(start = 32.dp, top = 8.dp)) {
                SettingToggleItem(
                    title = "Block Ads",
                    value = AppSettings.blockAds(context),
                    onValueChange = { AppSettings.setBlockAds(context, it) }
                )
                SettingToggleItem(
                    title = "Block Trackers",
                    value = AppSettings.blockTrackers(context),
                    onValueChange = { AppSettings.setBlockTrackers(context, it) }
                )
                SettingToggleItem(
                    title = "Block Malware",
                    value = AppSettings.blockMalware(context),
                    onValueChange = { AppSettings.setBlockMalware(context, it) }
                )
            }
        }
    }
}

@Composable
private fun NetworkSettingsTab(context: Context) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Allow LAN
        SettingToggleItem(
            title = "🌐 Allow LAN",
            subtitle = "Allow access to local network",
            value = AppSettings.allowLan(context),
            onValueChange = { AppSettings.setAllowLan(context, it) }
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // IPv6
        SettingToggleItem(
            title = "📡 IPv6",
            subtitle = "Enable IPv6 support",
            value = AppSettings.ipv6Enabled(context),
            onValueChange = { AppSettings.setIpv6Enabled(context, it) }
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // MTU Settings - MAIN FEATURE
        Text(
            "📏 MTU SIZE",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        var mtuValue by remember { mutableStateOf(AppSettings.mtu(context).toFloat()) }
        var mtuPreset by remember { mutableStateOf(AppSettings.mtuPreset(context)) }

        // MTU Slider
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Current: ${mtuValue.toInt()} bytes")
                Text("Preset: $mtuPreset")
            }

            Slider(
                value = mtuValue,
                onValueChange = { mtuValue = it },
                valueRange = 1100f..1500f,
                steps = 20,
                modifier = Modifier.fillMaxWidth()
            )

            // MTU Presets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MtuPresetButton(
                    label = "Default\n(1500)",
                    mtu = 1500,
                    preset = "default",
                    selected = mtuPreset == "default",
                    onClick = {
                        mtuValue = 1500f
                        mtuPreset = "default"
                        AppSettings.setMtu(context, 1500)
                        AppSettings.setMtuPreset(context, "default")
                    }
                )

                MtuPresetButton(
                    label = "Safe\n(1432)",
                    mtu = 1432,
                    preset = "safe",
                    selected = mtuPreset == "safe",
                    onClick = {
                        mtuValue = 1432f
                        mtuPreset = "safe"
                        AppSettings.setMtu(context, 1432)
                        AppSettings.setMtuPreset(context, "safe")
                    }
                )

                MtuPresetButton(
                    label = "VPN\n(1280)",
                    mtu = 1280,
                    preset = "vpn_optimized",
                    selected = mtuPreset == "vpn_optimized",
                    onClick = {
                        mtuValue = 1280f
                        mtuPreset = "vpn_optimized"
                        AppSettings.setMtu(context, 1280)
                        AppSettings.setMtuPreset(context, "vpn_optimized")
                    }
                )

                MtuPresetButton(
                    label = "Iran\n(1280)",
                    mtu = 1280,
                    preset = "iran_isp",
                    selected = mtuPreset == "iran_isp",
                    onClick = {
                        mtuValue = 1280f
                        mtuPreset = "iran_isp"
                        AppSettings.setMtu(context, 1280)
                        AppSettings.setMtuPreset(context, "iran_isp")
                    }
                )
            }

            Text(
                "ℹ️ Lower MTU = less filtering but more packets",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )

            Button(
                onClick = { /* TODO: Implement MTU test */ },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 12.dp)
            ) {
                Text("🧪 Test MTU")
            }
        }
    }
}

@Composable
private fun AppearanceSettingsTab(context: Context) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Theme Selection
        Text(
            "🎨 THEME",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        var theme by remember { mutableStateOf(AppSettings.theme(context)) }

        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("light", "dark", "auto").forEach { option ->
                Button(
                    onClick = {
                        theme = option
                        AppSettings.setTheme(context, option)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (theme == option)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(option.uppercase())
                }
            }
        }

        Divider(modifier = Modifier.padding(vertical = 12.dp))

        // AMOLED Mode
        SettingToggleItem(
            title = "⚫ AMOLED Mode",
            subtitle = "Pure black background (saves battery)",
            value = AppSettings.amoledMode(context),
            onValueChange = { AppSettings.setAmoledMode(context, it) }
        )
    }
}

@Composable
private fun AdvancedSettingsTab(context: Context) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "🔧 ADVANCED SETTINGS",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Connection Notifications
        SettingToggleItem(
            title = "🔔 Connection Alerts",
            subtitle = "Notify on connect/disconnect",
            value = AppSettings.alertsEnabled(context),
            onValueChange = { AppSettings.setAlertsEnabled(context, it) }
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Language (Farsi)
        Text(
            "🌐 LANGUAGE",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        var lang by remember { mutableStateOf(AppSettings.language(context)) }

        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("fa", "en").forEach { option ->
                Button(
                    onClick = {
                        lang = option
                        AppSettings.setLanguage(context, option)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (lang == option)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(if (option == "fa") "🇮🇷 فارسی" else "🇬🇧 English")
                }
            }
        }

        Divider(modifier = Modifier.padding(vertical = 12.dp))

        // Clear Data
        Button(
            onClick = { /* TODO: Clear cache */ },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Text("🗑️ Clear Cache & History")
        }
    }
}

@Composable
private fun SettingToggleItem(
    title: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    subtitle: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = value, onCheckedChange = onValueChange)
    }
}

@Composable
private fun MtuPresetButton(
    label: String,
    mtu: Int,
    preset: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .height(60.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(lineHeight = 1.2.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
