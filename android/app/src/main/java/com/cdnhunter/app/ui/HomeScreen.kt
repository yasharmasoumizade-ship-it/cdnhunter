package com.cdnhunter.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdnhunter.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: AppUiState,
    onConnectClick: () -> Unit,
    onServerSelect: (Any) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onMenuClick: () -> Unit,
    onFavoriteToggle: ((Any) -> Unit)? = null
) {
    val isConnected = uiState.isConnected
    val isConnecting = uiState.isConnecting

    // Dynamic button palette matching connection states
    val buttonColors = when {
        isConnected -> listOf(Color(0xFF10B981), Color(0xFF059669))
        isConnecting -> listOf(Color(0xFFF59E0B), Color(0xFFD97706))
        else -> listOf(Color(0xFF162032), Color(0xFF0F172A))
    }

    val buttonBorderColor = when {
        isConnected -> Color(0xFF34D399)
        isConnecting -> Color(0xFFFBBF24)
        else -> Color(0xFF2563EB).copy(alpha = 0.8f)
    }

    val buttonText = when {
        isConnected -> "CONNECTED • TAP TO STOP"
        isConnecting -> "CONNECTING..."
        else -> "TAP TO CONNECT"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080B11))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top App Bar Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Menu action button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF121826).copy(alpha = 0.7f))
                        .clickable { onMenuClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_lucide_sliders),
                        contentDescription = "Menu",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = uiState.selectedServer?.country ?: "Germany",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Status indicator badge
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF121826).copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) Color(0xFF10B981) else Color(0xFF64748B))
                    )
                }
            }

            // Redesigned Vivid Hero Flag Card with rounded corners and high legibility shadow scrim
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .height(145.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.Black,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                shadowElevation = 10.dp
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Crisp and vivid flag image contained inside the rounded card
                    Image(
                        painter = painterResource(id = R.drawable.hero_flag_de),
                        contentDescription = "Country Flag",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp)),
                        contentScale = ContentScale.Crop
                    )

                    // Smooth shadow scrim: keeps flag crystal clear while darkening the text area
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.15f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.45f),
                                        Color.Black.copy(alpha = 0.90f)
                                    )
                                )
                            )
                    )

                    // Country title, auto node text, and ping chip overlay
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            // Country name with text shadow for crisp readability
                            Text(
                                text = uiState.selectedServer?.country ?: "Germany",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                style = TextStyle(
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.9f),
                                        offset = Offset(2f, 2f),
                                        blurRadius = 8f
                                    )
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = uiState.selectedServer?.name ?: "Frankfurt Auto Node",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFCBD5E1),
                                style = TextStyle(
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.8f),
                                        offset = Offset(1f, 1f),
                                        blurRadius = 5f
                                    )
                                )
                            )
                        }

                        // Glassmorphism ping indicator badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "26ms",
                                    color = Color(0xFF10B981),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Server bottom sheet container
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = Color(0xFF0C111D),
                border = BorderStroke(1.dp, Color(0xFF2563EB).copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    // Search bar
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = {
                            Text(
                                "Search location or server...",
                                color = Color(0xFF64748B),
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_lucide_globe),
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF161E2E),
                            unfocusedContainerColor = Color(0xFF161E2E),
                            focusedBorderColor = Color(0xFF2563EB),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.06f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Server list with bottom padding to avoid overlapping the action button
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.servers) { server ->
                            ServerRowItem(
                                server = server,
                                isSelected = server == uiState.selectedServer,
                                onClick = { onServerSelect(server) }
                            )
                        }
                    }
                }
            }
        }

        // Bottom backdrop container with top shadow / gradient fade
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x99080B11),
                            Color(0xFF080B11),
                            Color(0xFF080B11)
                        )
                    )
                )
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 14.dp)
        ) {
            Button(
                onClick = onConnectClick,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp),
                border = BorderStroke(1.5.dp, buttonBorderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .shadow(
                        elevation = if (isConnected) 16.dp else 6.dp,
                        shape = RoundedCornerShape(18.dp),
                        ambientColor = buttonColors.first(),
                        spotColor = buttonColors.first()
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.horizontalGradient(buttonColors)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_connect_bolt),
                            contentDescription = "Bolt",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = buttonText,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ServerRowItem(
    server: Any,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val serverName = server.toString()

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) Color(0xFF1E293B).copy(alpha = 0.6f) else Color(0xFF131927).copy(alpha = 0.5f),
        border = BorderStroke(
            1.dp,
            if (isSelected) Color(0xFF38BDF8).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.04f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Active indicator line
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF38BDF8))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_lucide_globe),
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = serverName,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "VLESS Auto Node",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
            }

            Text(
                text = "28ms",
                color = Color(0xFF10B981),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
