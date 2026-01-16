package com.healthtracker.presentation.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthtracker.presentation.theme.*
import kotlin.math.cos
import kotlin.math.sin

// ============================================
// PREMIUM LINE CHART
// ============================================

/**
 * Premium animated line chart with glow effects.
 */
@Composable
fun PremiumLineChart(
    data: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = ElectricBlue,
    fillGradient: Boolean = true,
    showDataPoints: Boolean = true,
    animated: Boolean = true,
    onPointSelected: ((Int, Float) -> Unit)? = null
) {
    if (data.isEmpty()) return
    
    var animationProgress by remember { mutableFloatStateOf(if (animated) 0f else 1f) }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    
    val animatedProgress by animateFloatAsState(
        targetValue = animationProgress,
        animationSpec = tween(1500, easing = EaseOutCubic),
        label = "lineChartProgress"
    )
    
    LaunchedEffect(data) {
        animationProgress = 1f
    }
    
    val minValue = data.minOrNull() ?: 0f
    val maxValue = data.maxOrNull() ?: 1f
    val valueRange = if (maxValue - minValue > 0) maxValue - minValue else 1f
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(data) {
                detectTapGestures { offset ->
                    val pointWidth = size.width.toFloat() / (data.size - 1).coerceAtLeast(1)
                    val index = (offset.x / pointWidth).toInt().coerceIn(0, data.size - 1)
                    selectedIndex = index
                    onPointSelected?.invoke(index, data[index])
                }
            }
    ) {
        val width = size.width
        val height = size.height
        val padding = 20f
        val chartWidth = width - padding * 2
        val chartHeight = height - padding * 2
        
        // Draw grid
        drawGrid(padding, chartWidth, chartHeight, height)
        
        // Calculate points
        val points = data.mapIndexed { index, value ->
            val x = padding + (chartWidth / (data.size - 1).coerceAtLeast(1)) * index
            val normalizedValue = (value - minValue) / valueRange
            val y = padding + chartHeight - (chartHeight * normalizedValue)
            Offset(x, y)
        }
        
        // Draw gradient fill
        if (fillGradient) {
            drawGradientFill(points, padding, height, chartHeight, lineColor, animatedProgress)
        }
        
        // Draw glow line
        drawGlowLine(points, lineColor, animatedProgress)
        
        // Draw main line
        drawMainLine(points, lineColor, animatedProgress)
        
        // Draw data points
        if (showDataPoints) {
            drawDataPoints(points, lineColor, animatedProgress, selectedIndex)
        }
    }
}

private fun DrawScope.drawGrid(
    padding: Float,
    chartWidth: Float,
    chartHeight: Float,
    height: Float
) {
    val gridColor = Color.White.copy(alpha = 0.08f)
    
    // Horizontal lines
    for (i in 0..4) {
        val y = padding + (chartHeight / 4) * i
        drawLine(
            color = gridColor,
            start = Offset(padding, y),
            end = Offset(padding + chartWidth, y),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
        )
    }
}

private fun DrawScope.drawGradientFill(
    points: List<Offset>,
    padding: Float,
    height: Float,
    chartHeight: Float,
    color: Color,
    progress: Float
) {
    val fillPath = Path().apply {
        moveTo(points.first().x, height - padding)
        points.forEachIndexed { index, point ->
            val animatedX = points.first().x + (point.x - points.first().x) * progress
            val animatedY = height - padding + (point.y - (height - padding)) * progress
            lineTo(animatedX, animatedY)
        }
        val lastAnimatedX = points.first().x + (points.last().x - points.first().x) * progress
        lineTo(lastAnimatedX, height - padding)
        close()
    }
    
    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                color.copy(alpha = 0.4f),
                color.copy(alpha = 0.1f),
                Color.Transparent
            )
        )
    )
}

private fun DrawScope.drawGlowLine(
    points: List<Offset>,
    color: Color,
    progress: Float
) {
    val linePath = createLinePath(points, progress, size.height - 20f)
    
    drawPath(
        path = linePath,
        color = color.copy(alpha = 0.4f),
        style = Stroke(width = 10f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawMainLine(
    points: List<Offset>,
    color: Color,
    progress: Float
) {
    val linePath = createLinePath(points, progress, size.height - 20f)
    
    drawPath(
        path = linePath,
        brush = Brush.horizontalGradient(
            colors = listOf(color, color.copy(alpha = 0.8f))
        ),
        style = Stroke(width = 3f, cap = StrokeCap.Round)
    )
}

private fun createLinePath(points: List<Offset>, progress: Float, bottomY: Float): Path {
    return Path().apply {
        points.forEachIndexed { index, point ->
            val animatedX = points.first().x + (point.x - points.first().x) * progress
            val animatedY = bottomY + (point.y - bottomY) * progress
            if (index == 0) {
                moveTo(animatedX, animatedY)
            } else {
                lineTo(animatedX, animatedY)
            }
        }
    }
}

private fun DrawScope.drawDataPoints(
    points: List<Offset>,
    color: Color,
    progress: Float,
    selectedIndex: Int
) {
    val bottomY = size.height - 20f
    
    points.forEachIndexed { index, point ->
        val animatedX = points.first().x + (point.x - points.first().x) * progress
        val animatedY = bottomY + (point.y - bottomY) * progress
        
        val isSelected = index == selectedIndex
        val pointRadius = if (isSelected) 10f else 6f
        
        // Glow
        drawCircle(
            color = color.copy(alpha = if (isSelected) 0.6f else 0.4f),
            radius = pointRadius + 4f,
            center = Offset(animatedX, animatedY)
        )
        
        // Point
        drawCircle(
            color = color,
            radius = pointRadius,
            center = Offset(animatedX, animatedY)
        )
        
        // Inner
        drawCircle(
            color = Color.White,
            radius = pointRadius - 3f,
            center = Offset(animatedX, animatedY)
        )
    }
}

// ============================================
// PREMIUM BAR CHART
// ============================================

/**
 * Premium 3D bar chart with depth effect.
 */
@Composable
fun PremiumBarChart(
    data: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
    barColor: Color = ElectricBlue,
    animated: Boolean = true
) {
    if (data.isEmpty()) return
    
    val maxValue = data.maxOfOrNull { it.second } ?: 1f
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEachIndexed { index, (label, value) ->
            PremiumBar(
                value = value,
                maxValue = maxValue,
                label = label,
                color = barColor,
                animated = animated,
                delay = index * 100
            )
        }
    }
}

@Composable
private fun PremiumBar(
    value: Float,
    maxValue: Float,
    label: String,
    color: Color,
    animated: Boolean,
    delay: Int
) {
    val targetHeight = (value / maxValue).coerceIn(0f, 1f)
    
    var animatedHeight by remember { mutableFloatStateOf(if (animated) 0f else targetHeight) }
    
    val height by animateFloatAsState(
        targetValue = animatedHeight,
        animationSpec = tween(800, delayMillis = delay, easing = EaseOutCubic),
        label = "barHeight"
    )
    
    LaunchedEffect(value) {
        animatedHeight = targetHeight
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(40.dp)
    ) {
        // Bar
        Box(
            modifier = Modifier
                .width(32.dp)
                .fillMaxHeight(height.coerceAtLeast(0.02f))
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                    ambientColor = color.copy(alpha = 0.3f),
                    spotColor = color.copy(alpha = 0.4f)
                )
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            color,
                            color.copy(alpha = 0.7f)
                        )
                    )
                )
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Label
        Text(
            text = label,
            color = GlowWhite.copy(alpha = 0.7f),
            fontSize = 10.sp
        )
    }
}

// ============================================
// PREMIUM DONUT CHART
// ============================================

/**
 * Premium animated donut chart.
 */
@Composable
fun PremiumDonutChart(
    data: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(ElectricBlue, NeonPurple, CyberGreen, Color(0xFFFF6B6B)),
    strokeWidth: Dp = 24.dp,
    animated: Boolean = true
) {
    if (data.isEmpty()) return
    
    val total = data.sumOf { it.second.toDouble() }.toFloat()
    
    var animationProgress by remember { mutableFloatStateOf(if (animated) 0f else 1f) }
    
    val progress by animateFloatAsState(
        targetValue = animationProgress,
        animationSpec = tween(1500, easing = EaseOutCubic),
        label = "donutProgress"
    )
    
    LaunchedEffect(data) {
        animationProgress = 1f
    }
    
    Box(
        modifier = modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = strokeWidth.toPx()
            val radius = (size.minDimension - strokeWidthPx) / 2
            val center = Offset(size.width / 2, size.height / 2)
            
            var startAngle = -90f
            
            data.forEachIndexed { index, (_, value) ->
                val sweepAngle = (value / total) * 360f * progress
                val color = colors[index % colors.size]
                
                // Glow
                drawArc(
                    color = color.copy(alpha = 0.3f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidthPx + 8.dp.toPx(), cap = StrokeCap.Round)
                )
                
                // Segment
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )
                
                startAngle += sweepAngle
            }
        }
        
        // Center content
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${(progress * 100).toInt()}%",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Complete",
                color = GlowWhite.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }
    }
}

// ============================================
// PREMIUM RADAR CHART
// ============================================

/**
 * Premium radar/spider chart for wellness overview.
 */
@Composable
fun PremiumRadarChart(
    data: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
    color: Color = ElectricBlue,
    animated: Boolean = true
) {
    if (data.isEmpty()) return
    
    var animationProgress by remember { mutableFloatStateOf(if (animated) 0f else 1f) }
    
    val progress by animateFloatAsState(
        targetValue = animationProgress,
        animationSpec = tween(1500, easing = EaseOutCubic),
        label = "radarProgress"
    )
    
    LaunchedEffect(data) {
        animationProgress = 1f
    }
    
    Canvas(
        modifier = modifier
            .size(200.dp)
    ) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2 - 20f
        val angleStep = 360f / data.size
        
        // Draw grid circles
        for (i in 1..4) {
            val gridRadius = radius * (i / 4f)
            drawCircle(
                color = Color.White.copy(alpha = 0.1f),
                radius = gridRadius,
                center = center,
                style = Stroke(width = 1f)
            )
        }
        
        // Draw axis lines
        data.forEachIndexed { index, _ ->
            val angle = Math.toRadians((angleStep * index - 90).toDouble())
            val endX = center.x + radius * cos(angle).toFloat()
            val endY = center.y + radius * sin(angle).toFloat()
            
            drawLine(
                color = Color.White.copy(alpha = 0.1f),
                start = center,
                end = Offset(endX, endY),
                strokeWidth = 1f
            )
        }
        
        // Draw data polygon
        val path = Path()
        data.forEachIndexed { index, (_, value) ->
            val angle = Math.toRadians((angleStep * index - 90).toDouble())
            val valueRadius = radius * value.coerceIn(0f, 1f) * progress
            val x = center.x + valueRadius * cos(angle).toFloat()
            val y = center.y + valueRadius * sin(angle).toFloat()
            
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
        
        // Fill
        drawPath(
            path = path,
            color = color.copy(alpha = 0.3f)
        )
        
        // Stroke with glow
        drawPath(
            path = path,
            color = color.copy(alpha = 0.5f),
            style = Stroke(width = 6f)
        )
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2f)
        )
        
        // Draw data points
        data.forEachIndexed { index, (_, value) ->
            val angle = Math.toRadians((angleStep * index - 90).toDouble())
            val valueRadius = radius * value.coerceIn(0f, 1f) * progress
            val x = center.x + valueRadius * cos(angle).toFloat()
            val y = center.y + valueRadius * sin(angle).toFloat()
            
            drawCircle(
                color = color.copy(alpha = 0.5f),
                radius = 8f,
                center = Offset(x, y)
            )
            drawCircle(
                color = color,
                radius = 5f,
                center = Offset(x, y)
            )
        }
    }
}

// ============================================
// UTILITY EASING
// ============================================

private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
