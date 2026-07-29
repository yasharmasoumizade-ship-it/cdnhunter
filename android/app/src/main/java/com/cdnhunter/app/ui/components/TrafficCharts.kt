package com.cdnhunter.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import kotlin.math.log10

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
    dataPoints: List<Float>,  // KB/s values
    currentValue: Float,       // KB/s
    totalBytes: Long,          // Cumulative bytes
    accentColor: Color = Color(0xFF4ADE9C),
    isDownload: Boolean = true,
    modifier: Modifier = Modifier
) {
    val (totalValue, totalUnit) = formatTrafficBytes(totalBytes)
    val currentFormatted = if (currentValue >= 1024f) {
        "%.1f MB/s".format(currentValue / 1024f)
    } else {
        "%.0f KB/s".format(currentValue)
    }
    
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF131316))
            .border(1.5.dp, Color(0xFF1E1F24), RoundedCornerShape(16.dp))
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header with icon and title
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(icon, null, tint = accentColor, modifier = Modifier.size(18.dp))
                    Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF6E7078))
                }
            }
            
            // Current speed display
            Column {
                Text(currentFormatted, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFAFAFA))
                Text("Current speed", fontSize = 11.sp, color = Color(0xFF6E7078), modifier = Modifier.padding(top = 2.dp))
            }
            
            // Sparkline chart
            if (dataPoints.isNotEmpty()) {
                SparklineChart(
                    dataPoints = dataPoints,
                    color = accentColor,
                    height = 60.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
            
            // Total bytes row
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E1F24))
                    .border(1.5.dp, Color(0xFF2F2F34), RoundedCornerShape(10.dp))
                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(10.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Total ${if (isDownload) "Downloaded" else "Uploaded"}",
                    fontSize = 11.sp,
                    color = Color(0xFF6E7078)
                )
                Text(
                    "$totalValue $totalUnit",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFAFAFA)
                )
            }
        }
    }
}

// ── Sparkline: compact chart showing historical speed data ─────────────────────────
@Composable
fun SparklineChart(
    dataPoints: List<Float>,
    color: Color,
    height: Dp = 50.dp,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 2f
) {
    if (dataPoints.isEmpty() || dataPoints.size < 2) {
        return
    }
    
    Canvas(modifier.height(height).fillMaxWidth()) {
        val width = size.width
        val maxHeight = size.height
        
        // Find min and max values for scaling
        val maxValue = dataPoints.maxOrNull() ?: 1f
        val minValue = dataPoints.minOrNull() ?: 0f
        val range = if (maxValue > minValue) (maxValue - minValue) else maxValue.coerceAtLeast(1f)
        
        // Draw background grid
        drawLine(
            color = color.copy(alpha = 0.1f),
            start = androidx.compose.ui.geometry.Offset(0f, maxHeight / 2),
            end = androidx.compose.ui.geometry.Offset(width, maxHeight / 2),
            strokeWidth = 1f
        )
        
        // Draw the line chart
        val path = Path()
        val points = dataPoints.mapIndexed { index, value ->
            val x = (index.toFloat() / (dataPoints.size - 1)) * width
            val normalizedValue = if (range > 0) (value - minValue) / range else 0.5f
            val y = maxHeight - (normalizedValue * maxHeight * 0.8f + maxHeight * 0.1f)
            androidx.compose.ui.geometry.Offset(x, y)
        }
        
        // Build path
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            // Smooth curve using quadratic bezier
            val control = androidx.compose.ui.geometry.Offset(
                (points[i - 1].x + points[i].x) / 2,
                (points[i - 1].y + points[i].y) / 2
            )
            path.quadraticBezierTo(control.x, control.y, points[i].x, points[i].y)
        }
        
        // Fill area under curve
        val fillPath = Path()
        fillPath.addPath(path)
        fillPath.lineTo(points.last().x, maxHeight)
        fillPath.lineTo(points.first().x, maxHeight)
        fillPath.close()
        
        drawPath(
            fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.2f), Color.Transparent),
                startY = 0f,
                endY = maxHeight
            )
        )
        
        // Draw line stroke
        drawPath(path, color = color, style = Stroke(width = strokeWidth))
        
        // Draw data point circles at key positions
        listOf(0, dataPoints.size / 2, dataPoints.size - 1).forEach { idx ->
            if (idx in dataPoints.indices) {
                drawCircle(
                    color = color,
                    radius = strokeWidth + 1f,
                    center = points[idx]
                )
            }
        }
    }
}

// ── Detailed traffic breakdown card (shows KB, MB, GB breakdown) ──────────────────
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
    val downloadColor = Color(0xFF64D2FF)  // Teal
    val uploadColor = Color(0xFFFFD60A)    // Yellow
    
    Column(modifier.fillMaxWidth().then(modifier), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Traffic Statistics", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFAFAFA))
        
        TrafficChartCard(
            title = "DOWNLOAD",
            icon = Icons.Rounded.ArrowDownward,
            dataPoints = downloadHistory,
            currentValue = currentDownloadKbps,
            totalBytes = downloadBytes,
            accentColor = downloadColor,
            isDownload = true
        )
        
        TrafficChartCard(
            title = "UPLOAD",
            icon = Icons.Rounded.ArrowUpward,
            dataPoints = uploadHistory,
            currentValue = currentUploadKbps,
            totalBytes = uploadBytes,
            accentColor = uploadColor,
            isDownload = false
        )
        
        // Scale reference card
        TrafficScaleReference()
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
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Traffic Scale", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF6E7078))
            
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScaleItem(Modifier.weight(1f), "1 KB", "1,024 B", Color(0xFF4ADE9C))
                ScaleItem(Modifier.weight(1f), "1 MB", "1,024 KB", Color(0xFF64D2FF))
                ScaleItem(Modifier.weight(1f), "1 GB", "1,024 MB", Color(0xFFFFD60A))
            }
            
            // Progress bar showing typical session
            Text("Session Example", fontSize = 10.sp, color = Color(0xFF6E7078), modifier = Modifier.padding(top = 4.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E1F24))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TrafficProgressBar("Light browsing", 128L * 1024 * 1024)     // 128 MB
                TrafficProgressBar("Video streaming (1hr)", 500L * 1024 * 1024)  // 500 MB
                TrafficProgressBar("Gaming (1hr)", 1024L * 1024 * 1024)     // 1 GB
                TrafficProgressBar("Full day usage", 3L * 1024 * 1024 * 1024)  // 3 GB
            }
        }
    }
}

@Composable
private fun ScaleItem(modifier: Modifier = Modifier, label: String, description: String, color: Color) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
        Text(description, fontSize = 8.sp, color = Color(0xFF6E7078))
    }
}

@Composable
private fun TrafficProgressBar(label: String, bytes: Long) {
    val (value, unit) = formatTrafficBytes(bytes)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 10.sp, color = Color(0xFFF0F0F2))
            Text("$value $unit", fontSize = 9.sp, color = Color(0xFF6E7078))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF232328))
        ) {
            val progress = when {
                bytes < 1024 * 1024 * 1024 -> (bytes.toFloat() / (1024 * 1024 * 1024).toFloat()).coerceIn(0f, 1f)
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

// ── Real-time stats panel (for home screen) ───────────────────────────────────────
@Composable
fun RealTimeTrafficStats(
    downloadKbps: Float,
    uploadKbps: Float,
    downloadHistory: List<Float>,
    uploadHistory: List<Float>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF131316))
            .border(1.5.dp, Color(0xFF1E1F24), RoundedCornerShape(14.dp))
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Download stat
        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(Icons.Rounded.ArrowDownward, null, tint = Color(0xFF6E7078), modifier = Modifier.size(11.dp))
                Text("DOWNLOAD", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF6E7078))
            }
            Text(
                if (downloadKbps >= 1024f) "%.1f MB/s".format(downloadKbps / 1024f) else "%.0f KB/s".format(downloadKbps),
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFAFAFA)
            )
            if (downloadHistory.isNotEmpty()) {
                Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                    val width = size.width
                    val height = size.height
                    val maxValue = downloadHistory.maxOrNull() ?: 1f
                    val range = maxValue.coerceAtLeast(1f)
                    
                    val path = Path()
                    downloadHistory.forEachIndexed { index, value ->
                        val x = (index.toFloat() / downloadHistory.size) * width
                        val y = height - (value / range) * (height * 0.8f)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    
                    drawPath(path, color = Color(0xFF64D2FF), style = Stroke(width = 1.5f))
                }
            }
        }
        
        // Divider
        Box(Modifier.width(1.dp).height(60.dp).background(Color(0xFF1E1F24)))
        
        // Upload stat
        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(Icons.Rounded.ArrowUpward, null, tint = Color(0xFF6E7078), modifier = Modifier.size(11.dp))
                Text("UPLOAD", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF6E7078))
            }
            Text(
                if (uploadKbps >= 1024f) "%.1f MB/s".format(uploadKbps / 1024f) else "%.0f KB/s".format(uploadKbps),
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFAFAFA)
            )
            if (uploadHistory.isNotEmpty()) {
                Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                    val width = size.width
                    val height = size.height
                    val maxValue = uploadHistory.maxOrNull() ?: 1f
                    val range = maxValue.coerceAtLeast(1f)
                    
                    val path = Path()
                    uploadHistory.forEachIndexed { index, value ->
                        val x = (index.toFloat() / uploadHistory.size) * width
                        val y = height - (value / range) * (height * 0.8f)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    
                    drawPath(path, color = Color(0xFFFFD60A), style = Stroke(width = 1.5f))
                }
            }
        }
    }
}
