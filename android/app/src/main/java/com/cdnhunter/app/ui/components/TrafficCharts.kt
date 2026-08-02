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
import androidx.compose.ui.geometry.Offset
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
            .padding(4.dp)
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

            // Historical graph — smooth curve (not jagged straight segments), a couple
            // of faint reference gridlines, the peak value labeled, and the latest
            // point highlighted with a small dot so the chart reads as precise data
            // rather than a rough sketch.
            if (dataPoints.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().height(64.dp)) {
                    val points = dataPoints.toList()
                    val maxValue = (points.maxOrNull() ?: 1f).coerceAtLeast(1f)
                    Text(
                        text = if (maxValue > 1024) "%.1f MB/s".format(maxValue / 1024f) else "%.0f KB/s".format(maxValue),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = accentColor.copy(alpha = 0.55f),
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                    Canvas(Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val count = points.size
                        if (count > 0 && width > 0f && height > 0f) {
                            // Faint horizontal reference lines (25% / 50% / 75% of peak) —
                            // gives the eye a scale to read the curve against.
                            val gridColor = Color.White.copy(alpha = 0.05f)
                            for (frac in listOf(0.25f, 0.5f, 0.75f)) {
                                val y = height * (1f - frac)
                                drawLine(gridColor, Offset(0f, y), Offset(width, y), strokeWidth = 1f)
                            }

                            fun yFor(v: Float) = (height - (v / maxValue) * (height * 0.88f)).coerceIn(0f, height)

                            val plotted = points.mapIndexed { index, value ->
                                val x = if (count == 1) width else (index.toFloat() / (count - 1)) * width
                                Offset(x, yFor(value))
                            }

                            // Smooth path through the points via quadratic mid-point
                            // interpolation instead of straight lineTo segments — the
                            // straight-segment version looked jagged/rough at typical
                            // sample rates.
                            val linePath = Path().apply {
                                moveTo(plotted.first().x, plotted.first().y)
                                for (i in 1 until plotted.size) {
                                    val p0 = plotted[i - 1]
                                    val p1 = plotted[i]
                                    val midX = (p0.x + p1.x) / 2f
                                    val midY = (p0.y + p1.y) / 2f
                                    quadraticBezierTo(p0.x, p0.y, midX, midY)
                                }
                                lineTo(plotted.last().x, plotted.last().y)
                            }

                            val fillPath = Path().apply {
                                addPath(linePath)
                                lineTo(plotted.last().x, height)
                                lineTo(plotted.first().x, height)
                                close()
                            }

                            drawPath(
                                fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(accentColor.copy(alpha = 0.30f), accentColor.copy(alpha = 0.0f)),
                                    startY = 0f, endY = height
                                )
                            )
                            drawPath(
                                linePath,
                                color = accentColor.copy(alpha = 0.95f),
                                style = Stroke(width = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )

                            // Highlight the most recent sample.
                            val last = plotted.last()
                            drawCircle(accentColor.copy(alpha = 0.25f), radius = 6f, center = last)
                            drawCircle(accentColor, radius = 2.6f, center = last)
                        }
                    }
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

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Horizontal Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
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
            .padding(4.dp)
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
