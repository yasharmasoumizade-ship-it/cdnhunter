package com.cdnhunter.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Formatting helper: convert bytes to appropriate unit ──────────────────────────
fun formatTrafficBytes(bytes: Long): Pair<String, String> {
    return when {
        bytes < 1024 -> "%.0f".format(bytes.toDouble()) to "B"
        bytes < 1024 * 1024 -> "%.1f".format(bytes / 1024.0) to "KB"
        bytes < 1024 * 1024 * 1024 -> "%.1f".format(bytes / 1024.0 / 1024.0) to "MB"
        else -> "%.2f".format(bytes / 1024.0 / 1024.0 / 1024.0) to "GB"
    }
}

// ── Speed data point ──────────────────────────────────────────────────────────────
data class SpeedDataPoint(
    val timestamp: Long,
    val speedKbps: Float,
    val label: String = ""
)

// ── Traffic chart: displays upload/download in KB→MB→GB scale ─────────────────────
@Composable
fun TrafficChartCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    dataPoints: List<Float>,
    currentValue: Float,
    totalBytes: Long,
    accentColor: Color = Color(0xFF4ADE9C),
    isDownload: Boolean = true,
    modifier: Modifier = Modifier
) {
    val (totalValue, totalUnit) = formatTrafficBytes(totalBytes)
    
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0B0B0D))
            .border(1.5.dp, Color(0xFF1E1F24), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header with icon and title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = accentColor, modifier = Modifier.size(14.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor.copy(0.8f),
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        "$totalValue $totalUnit",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFAFAFA)
                    )
                }
            }

            // Current speed indicator
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(0.05f))
                    .padding(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier
                            .size(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(accentColor)
                    )
                    Text(
                        if (currentValue > 1024) "%.1f MB/s".format(currentValue / 1024f) else "%.0f KB/s".format(currentValue),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor
                    )
                }
            }

            // Historical graph
            if (dataPoints.isNotEmpty()) {
                Canvas(Modifier.fillMaxWidth().height(40.dp)) {
                    val width = size.width
                    val height = size.height
                    val maxValue = dataPoints.maxOrNull() ?: 1f
                    val range = maxValue.coerceAtLeast(1f)
                    
                    // Draw area under curve
                    val path = Path()
                    dataPoints.forEachIndexed { index, value ->
                        val x = (index.toFloat() / dataPoints.size) * width
                        val y = height - (value / range) * (height * 0.9f)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.lineTo(width, height)
                    path.lineTo(0f, height)
                    path.close()
                    
                    drawPath(
                        path,
                        brush = Brush.verticalGradient(
                            colors = listOf(accentColor.copy(0.3f), accentColor.copy(0.05f))
                        )
                    )
                    
                    // Draw line
                    val linePath = Path()
                    dataPoints.forEachIndexed { index, value ->
                        val x = (index.toFloat() / dataPoints.size) * width
                        val y = height - (value / range) * (height * 0.9f)
                        if (index == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                    }
                    drawPath(linePath, color = accentColor, style = Stroke(width = 2f))
                }
            }
        }
    }
}

// ── Horizontal carousel for traffic breakdown ──────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailedTrafficBreakdown(
    downloadBytes: Long,
    uploadBytes: Long,
    downloadHistory: List<Float>,
    uploadHistory: List<Float>,
    currentDownloadKbps: Float = 0f,
    currentUploadKbps: Float = 0f,
    modifier: Modifier = Modifier
) {
    val downloadColor = Color(0xFF64D2FF)
    val uploadColor = Color(0xFFFFD60A)
    
    val pagerState = rememberPagerState(pageCount = { 3 })
    
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Traffic Statistics",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFFAFAFA)
        )
        
        // Horizontal Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            pageSpacing = 12.dp
        ) { page ->
            when (page) {
                0 -> TrafficChartCard(
                    title = "DOWNLOAD",
                    icon = Icons.Rounded.ArrowDownward,
                    dataPoints = downloadHistory,
                    currentValue = currentDownloadKbps,
                    totalBytes = downloadBytes,
                    accentColor = downloadColor,
                    isDownload = true
                )
                1 -> TrafficChartCard(
                    title = "UPLOAD",
                    icon = Icons.Rounded.ArrowUpward,
                    dataPoints = uploadHistory,
                    currentValue = currentUploadKbps,
                    totalBytes = uploadBytes,
                    accentColor = uploadColor,
                    isDownload = false
                )
                2 -> TrafficScaleReference()
            }
        }
        
        // Indicator dots
        Row(
            Modifier
                .fillMaxWidth()
                .height(28.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { index ->
                val isSelected = pagerState.currentPage == index
                val width by animateDpAsState(
                    targetValue = if (isSelected) 24.dp else 8.dp,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                    label = "indicator_width_$index"
                )
                val alpha by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0.4f,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                    label = "indicator_alpha_$index"
                )
                
                Box(
                    Modifier
                        .width(width)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            when (index) {
                                0 -> Color(0xFF64D2FF)
                                1 -> Color(0xFFFFD60A)
                                else -> Color(0xFF4ADE9C)
                            }.copy(alpha = alpha)
                        )
                )
                
                if (index < 2) Spacer(Modifier.width(8.dp))
            }
        }
    }
}

// ── Scale reference: shows KB→MB→GB progression ────────────────────────────────
@Composable
fun TrafficScaleReference(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0B0B0D))
            .border(1.5.dp, Color(0xFF1E1F24), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF4ADE9C).copy(0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Info,
                        null,
                        tint = Color(0xFF4ADE9C),
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    "Scale Reference",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFAFAFA)
                )
            }
            
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ScaleItem("1 KB", "1,024 B", Color(0xFF4ADE9C))
                ScaleItem("1 MB", "1,024 KB", Color(0xFF64D2FF))
                ScaleItem("1 GB", "1,024 MB", Color(0xFFFFD60A))
            }
        }
    }
}

@Composable
private fun ScaleItem(value: String, subtext: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                value,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFAFAFA)
            )
            Text(subtext, fontSize = 9.sp, color = Color(0xFF6E7078))
        }
    }
}

// ── Traffic progress bar ───────────────────────────────────────────────────────────
@Composable
fun TrafficProgressBar(bytes: Long, modifier: Modifier = Modifier) {
    val (value, unit) = formatTrafficBytes(bytes)
    
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$value $unit",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFAFAFA),
                modifier = Modifier.weight(1f)
            )
            Text("Used", fontSize = 9.sp, color = Color(0xFF6E7078))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF232328))
        ) {
            val progress = when {
                bytes < 1024 * 1024 * 1024 -> (bytes.toFloat() / (1024 * 1024 * 1024).toFloat())
                    .coerceIn(0f, 1f)
                else -> 1f
            }
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        when {
                            bytes < 256 * 1024 * 1024 -> Color(0xFF4ADE9C)
                            bytes < 1024 * 1024 * 1024 -> Color(0xFF64D2FF)
                            else -> Color(0xFFFFD60A)
                        }
                    )
            )
        }
    }
}
