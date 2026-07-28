package com.cdnhunter.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

// SavedConfig is defined in AppScreen.kt (same package)
// Use full package path if AppScreen.kt hasn't exported it yet

/**
 * Helper to split configs into Main (manual) and Imported (subscription)
 */
fun List<SavedConfig>.separateBySource(): Pair<List<SavedConfig>, Map<String?, List<SavedConfig>>> {
    val main = filter { !it.isImported }
    // For now, subscriptions are loaded separately
    return main to emptyMap()
}

/**
 * Dialog to add a new subscription
 */
@Composable
fun AddSubscriptionDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onAdd: (url: String, name: String) -> Unit
) {
    if (!visible) return
    
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(AnanasCard)
                .padding(20.dp)
                .widthIn(max = 350.dp)
        ) {
            Text(
                "Add Subscription",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = AnanasTextHi
            )
            
            Spacer(Modifier.height(16.dp))
            
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Subscription Name") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. My VPN") },
                enabled = !isLoading
            )
            
            Spacer(Modifier.height(10.dp))
            
            TextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Subscription URL") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://example.com/sub") },
                enabled = !isLoading
            )
            
            Spacer(Modifier.height(16.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.End)
            ) {
                Button(
                    onClick = onDismiss,
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AnanasCard2
                    )
                ) {
                    Text("Cancel", color = AnanasText)
                }
                Button(
                    onClick = {
                        if (url.isNotBlank() && name.isNotBlank()) {
                            isLoading = true
                            onAdd(url, name)
                            // onDismiss will be called by parent after completion
                        }
                    },
                    enabled = !isLoading && url.isNotBlank() && name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AnanasAccent
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Add")
                    }
                }
            }
        }
    }
}

/**
 * Subscription header with refresh/delete buttons
 */
@Composable
fun SubscriptionHeader(
    sub: Subscription,
    onRefresh: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                sub.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AnanasTextHi
            )
            Text(
                "${sub.configs.size} servers",
                fontSize = 10.sp,
                color = AnanasMuted
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Rounded.Refresh,
                    null,
                    tint = AnanasAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Section header for locations screen
 */
@Composable
fun LocationSectionHeader(
    icon: String,
    title: String,
    subtitle: String? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$icon $title",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = AnanasAccent
        )
        if (subtitle != null) {
            Text(
                subtitle,
                fontSize = 10.sp,
                color = AnanasMuted
            )
        }
    }
}

/**
 * Single server list item component
 */
@Composable
fun ServerListItem(
    cfg: SavedConfig,
    activeId: String,
    connected: Boolean,
    onConnect: (SavedConfig) -> Unit,
    onDelete: (SavedConfig) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onConnect(cfg) }
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            // Country flag badge (placeholder - use existing CountryFlagBadge from AppScreen)
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AnanasCard2),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    cfg.countryCode,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = AnanasAccent
                )
            }
            
            Column {
                Text(
                    cfg.displayName,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = AnanasText,
                    letterSpacing = (-0.1).sp
                )
                Text(
                    when {
                        cfg.id == activeId && connected -> "Connected"
                        cfg.id == activeId -> "Selected"
                        cfg.pingMs >= 0 -> "${cfg.pingMs} ms"
                        else -> "Tap to select"
                    },
                    fontSize = 11.5.sp,
                    color = AnanasMuted,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (cfg.id == activeId && connected) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(AnanasAccent)
                )
            } else if (cfg.id == activeId) {
                Icon(
                    Icons.Rounded.Check,
                    null,
                    tint = AnanasAccent,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            Icon(
                Icons.Rounded.DeleteOutline,
                null,
                tint = Color(0xFF8B8C99),
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onDelete(cfg) }
            )
        }
    }
    Divider(color = Color(0xFF252529), thickness = 1.dp)
}
