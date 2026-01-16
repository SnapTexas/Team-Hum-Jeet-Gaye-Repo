package com.healthtracker.presentation.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.healthtracker.domain.model.DataPoint
import com.healthtracker.domain.model.MetricType
import com.healthtracker.domain.model.TrendAnalysis
import com.healthtracker.domain.model.TrendDirection
import com.healthtracker.presentation.theme.CyberGreen
import com.healthtracker.presentation.theme.ElectricBlue
import com.healthtracker.presentation.theme.GlassSurface
import com.healthtracker.presentation.theme.GlowWhite
import com.healthtracker.presentation.theme.NeonPurple
import java.time.format.DateTimeFormatter

/**
 * Premium trend chart with glassmorphism and glow effects.
 */
@Composable
fun TrendChart(
    trendAnalysis: TrendAnalysis,
    modifier: Modifier = Modifier,
    chartColor: Color = ElectricBlue
) {
    var animationProgress by remember { mutableFloatStateOf(0f) }
    var selectedPoint by remember { mutableStateOf<DataPoint?>(null) }
    
    val animatedProgress by animateFloatAsState(
        targetValue = animationProgress,
        animationSpec = tween(1500),
        label = "chartAnimation"
    )
    
    LaunchedEffect(trendAnalysis) {
        animationProgress = 1f
    }
    
    GlassmorphicChartCard(
        modifier = modifier,
        glowColor = chartColor
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = trendAnalysis.metricType.name.lowercase()
                        .replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                TrendIndicator(
                    direction = trendAnalysis.direction,
                    percentageChange = trendAnalysis.percentageChange
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Chart
            if (trendAnalysis.dataPoints.isNotEmpty()) {
                LineChart(
                    dataPoints = trendAnalysis.dataPoints,
                    color = chartColor,
                    animationProgress = animatedProgress,
                    selectedPoint = selectedPoint,
                    onPointSelected = { selectedPoint = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                )
                
                // Selected point info
                selectedPoint?.let { point ->
                    Spacer(modifier = Modifier.height(8.dp))
                    SelectedPointInfo(point, trendAnalysis.metricType, chartColor)
                }
            }
        }
    }
}


@Composable
private fun GlassmorphicChartCard(
    modifier: Modifier = Modifier,
    glowColor: Color = ElectricBlue,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = glowColor.copy(alpha = 0.3f),
                spotColor = glowColor.copy(alpha = 0.3f)
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.5f),
                        glowColor.copy(alpha = 0.1f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = GlassSurface
        )
    ) {
        content()
    }
}

@Composable
private fun TrendIndicator(
    direction: TrendDirection,
    percentageChange: Double
) {
    val (color, arrow) = when (direction) {
        TrendDirection.INCREASING -> Pair(CyberGreen, "↑")
        TrendDirection.DECREASING -> Pair(Color(0xFFFF6B6B), "↓")
        TrendDirection.STABLE -> Pair(GlowWhite, "→")
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                color.copy(alpha = 0.2f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = arrow,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "${String.format("%.1f", kotlin.math.abs(percentageChange))}%",
            color = color,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LineChart(
    dataPoints: List<DataPoint>,
    color: Color,
    animationProgress: Float,
    selectedPoint: DataPoint?,
    onPointSelected: (DataPoint?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (dataPoints.isEmpty()) return
    
    val minValue = dataPoints.minOf { it.value }
    val maxValue = dataPoints.maxOf { it.value }
    val valueRange = if (maxValue - minValue > 0) maxValue - minValue else 1.0
    
    Canvas(
        modifier = modifier
            .pointerInput(dataPoints) {
                detectTapGestures { offset ->
                    val pointWidth = size.width.toFloat() / (dataPoints.size - 1).coerceAtLeast(1)
                    val index = (offset.x / pointWidth).toInt().coerceIn(0, dataPoints.size - 1)
                    onPointSelected(dataPoints[index])
                }
            }
    ) {
        val width = size.width
        val height = size.height
        val padding = 20f
        val chartWidth = width - padding * 2
        val chartHeight = height - padding * 2
        
        // Draw grid lines
        val gridColor = Color.White.copy(alpha = 0.1f)
        for (i in 0..4) {
            val y = padding + (chartHeight / 4) * i
            drawLine(
                color = gridColor,
                start = Offset(padding, y),
                end = Offset(width - padding, y),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
            )
        }
        
        // Calculate points
        val points = dataPoints.mapIndexed { index, dataPoint ->
            val x = padding + (chartWidth / (dataPoints.size - 1).coerceAtLeast(1)) * index
            val normalizedValue = (dataPoint.value - minValue) / valueRange
            val y = padding + chartHeight - (chartHeight * normalizedValue).toFloat()
            Offset(x, y)
        }
        
        // Draw gradient fill under the line
        val fillPath = Path().apply {
            moveTo(points.first().x, height - padding)
            points.forEachIndexed { index, point ->
                val animatedX = points.first().x + (point.x - points.first().x) * animationProgress
                val animatedY = height - padding + (point.y - (height - padding)) * animationProgress
                if (index == 0) {
                    lineTo(animatedX, animatedY)
                } else {
                    lineTo(animatedX, animatedY)
                }
            }
            val lastAnimatedX = points.first().x + (points.last().x - points.first().x) * animationProgress
            lineTo(lastAnimatedX, height - padding)
            close()
        }
        
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    color.copy(alpha = 0.3f),
                    color.copy(alpha = 0.0f)
                )
            )
        )
        
        // Draw line
        val linePath = Path().apply {
            points.forEachIndexed { index, point ->
                val animatedX = points.first().x + (point.x - points.first().x) * animationProgress
                val animatedY = height - padding + (point.y - (height - padding)) * animationProgress
                if (index == 0) {
                    moveTo(animatedX, animatedY)
                } else {
                    lineTo(animatedX, animatedY)
                }
            }
        }
        
        // Glow effect
        drawPath(
            path = linePath,
            color = color.copy(alpha = 0.5f),
            style = Stroke(
                width = 8f,
                cap = StrokeCap.Round
            )
        )
        
        // Main line
        drawPath(
            path = linePath,
            color = color,
            style = Stroke(
                width = 3f,
                cap = StrokeCap.Round
            )
        )
        
        // Draw data points
        points.forEachIndexed { index, point ->
            val animatedX = points.first().x + (point.x - points.first().x) * animationProgress
            val animatedY = height - padding + (point.y - (height - padding)) * animationProgress
            
            val isSelected = selectedPoint == dataPoints[index]
            val pointRadius = if (isSelected) 10f else 6f
            
            // Glow
            drawCircle(
                color = color.copy(alpha = 0.5f),
                radius = pointRadius + 4f,
                center = Offset(animatedX, animatedY)
            )
            
            // Point
            drawCircle(
                color = color,
                radius = pointRadius,
                center = Offset(animatedX, animatedY)
            )
            
            // Inner point
            drawCircle(
                color = Color.White,
                radius = pointRadius - 3f,
                center = Offset(animatedX, animatedY)
            )
        }
    }
}

@Composable
private fun SelectedPointInfo(
    point: DataPoint,
    metricType: MetricType,
    color: Color
) {
    val formatter = DateTimeFormatter.ofPattern("MMM d")
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color.copy(alpha = 0.1f),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = point.date.format(formatter),
            style = MaterialTheme.typography.bodyMedium,
            color = GlowWhite
        )
        
        Text(
            text = formatValue(point.value, metricType),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

private fun formatValue(value: Double, metricType: MetricType): String {
    return when (metricType) {
        MetricType.STEPS -> "${value.toInt()} steps"
        MetricType.DISTANCE -> "${String.format("%.1f", value / 1000)} km"
        MetricType.CALORIES -> "${value.toInt()} kcal"
        MetricType.SLEEP -> "${(value / 60).toInt()}h ${(value % 60).toInt()}m"
        MetricType.SCREEN_TIME -> "${(value / 60).toInt()}h ${(value % 60).toInt()}m"
        MetricType.HEART_RATE -> "${value.toInt()} bpm"
        MetricType.HRV -> "${value.toInt()} ms"
        MetricType.MOOD -> value.toInt().toString()
    }
}

/**
 * Multi-metric comparison chart.
 */
@Composable
fun MultiMetricChart(
    trends: Map<MetricType, TrendAnalysis>,
    modifier: Modifier = Modifier
) {
    val colors = mapOf(
        MetricType.STEPS to ElectricBlue,
        MetricType.CALORIES to Color(0xFFFF6B6B),
        MetricType.SLEEP to NeonPurple,
        MetricType.HEART_RATE to CyberGreen
    )
    
    GlassmorphicChartCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Trends Overview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                trends.keys.take(4).forEach { metricType ->
                    val color = colors[metricType] ?: ElectricBlue
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = metricType.name.take(4),
                            style = MaterialTheme.typography.labelSmall,
                            color = GlowWhite
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Mini charts for each metric
            trends.entries.take(4).forEach { (metricType, trend) ->
                val color = colors[metricType] ?: ElectricBlue
                MiniTrendBar(
                    label = metricType.name.lowercase().replaceFirstChar { it.uppercase() },
                    direction = trend.direction,
                    percentageChange = trend.percentageChange,
                    color = color
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun MiniTrendBar(
    label: String,
    direction: TrendDirection,
    percentageChange: Double,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = GlowWhite,
            modifier = Modifier.width(80.dp)
        )
        
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color.copy(alpha = 0.2f))
        ) {
            val progress = (kotlin.math.abs(percentageChange).toFloat() / 100f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        TrendIndicator(direction, percentageChange)
    }
}
