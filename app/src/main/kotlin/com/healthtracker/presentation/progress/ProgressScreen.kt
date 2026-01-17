package com.healthtracker.presentation.progress

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.healthtracker.presentation.theme.*
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    viewModel: ProgressViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    
    val backgroundColor = Color(0xFF0D0D1A)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "My Progress",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor
                ),
                actions = {
                    IconButton(onClick = { viewModel.togglePublicVisibility() }) {
                        Icon(
                            imageVector = if (uiState.isPublic) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle visibility",
                            tint = if (uiState.isPublic) CyberGreen else GlowWhite
                        )
                    }
                }
            )
        },
        containerColor = backgroundColor
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Period selector
            item {
                PeriodSelector(
                    selectedPeriod = uiState.selectedPeriod,
                    onPeriodSelected = { viewModel.selectPeriod(it) }
                )
            }
            
            // Today's Overview - Radial Progress
            item {
                TodayOverviewCard(
                    steps = uiState.todaySteps,
                    calories = uiState.todayCalories,
                    distance = uiState.todayDistance,
                    stepGoal = uiState.stepGoal,
                    calorieGoal = uiState.calorieGoal,
                    distanceGoal = uiState.distanceGoal
                )
            }
            
            // Steps - Bar Chart
            item {
                BarChartCard(
                    title = "Steps History",
                    icon = Icons.Default.DirectionsWalk,
                    color = ElectricBlue,
                    data = uiState.stepsData,
                    goal = uiState.stepGoal.toFloat(),
                    unit = "steps"
                )
            }
            
            // Calories - Area Chart
            item {
                AreaChartCard(
                    title = "Calories Burned",
                    icon = Icons.Default.LocalFireDepartment,
                    color = Color(0xFFFF6B6B),
                    data = uiState.caloriesData,
                    unit = "kcal"
                )
            }
            
            // Distance - Line Chart with Gradient
            item {
                GradientLineChartCard(
                    title = "Distance Covered",
                    icon = Icons.Default.LocationOn,
                    color = Color(0xFF06B6D4),
                    data = uiState.distanceData,
                    unit = "km"
                )
            }
            
            // Weekly Comparison - Grouped Bar Chart
            item {
                WeeklyComparisonCard(
                    stepsData = uiState.stepsData,
                    caloriesData = uiState.caloriesData,
                    stepGoal = uiState.stepGoal
                )
            }
            
            // Activity Distribution - Donut Chart
            item {
                ActivityDistributionCard(
                    steps = uiState.todaySteps,
                    calories = uiState.todayCalories,
                    distance = uiState.todayDistance,
                    stepGoal = uiState.stepGoal,
                    calorieGoal = uiState.calorieGoal,
                    distanceGoal = uiState.distanceGoal
                )
            }
            
            // Summary Statistics
            item {
                SummaryStatsCard(
                    avgSteps = uiState.avgSteps,
                    avgCalories = uiState.avgCalories,
                    totalDistance = uiState.totalDistance,
                    activeDays = uiState.activeDays,
                    bestDay = uiState.bestDaySteps,
                    period = uiState.selectedPeriod
                )
            }
            
            // Streak & Achievements
            item {
                StreakCard(
                    activeDays = uiState.activeDays,
                    bestDay = uiState.bestDaySteps,
                    totalSteps = uiState.stepsData.sumOf { it.value.toInt() }
                )
            }
        }
    }
}

@Composable
private fun PeriodSelector(
    selectedPeriod: ProgressPeriod,
    onPeriodSelected: (ProgressPeriod) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ProgressPeriod.entries.forEach { period ->
                val isSelected = period == selectedPeriod
                TextButton(
                    onClick = { onPeriodSelected(period) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) ElectricBlue.copy(alpha = 0.2f)
                            else Color.Transparent
                        )
                ) {
                    Text(
                        text = period.label,
                        color = if (isSelected) ElectricBlue else GlowWhite,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * Today's Overview with Triple Radial Progress
 */
@Composable
private fun TodayOverviewCard(
    steps: Int,
    calories: Int,
    distance: Double,
    stepGoal: Int,
    calorieGoal: Int,
    distanceGoal: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Today's Progress",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Triple Ring Progress
            Box(
                modifier = Modifier.size(200.dp),
                contentAlignment = Alignment.Center
            ) {
                TripleRingProgress(
                    stepsProgress = (steps.toFloat() / stepGoal).coerceIn(0f, 1f),
                    caloriesProgress = (calories.toFloat() / calorieGoal).coerceIn(0f, 1f),
                    distanceProgress = (distance.toFloat() / distanceGoal).coerceIn(0f, 1f)
                )
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "%,d".format(steps),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = ElectricBlue
                    )
                    Text(
                        text = "steps",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlowWhite
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LegendItem(color = ElectricBlue, label = "Steps", value = "${(steps * 100 / stepGoal.coerceAtLeast(1))}%")
                LegendItem(color = Color(0xFFFF6B6B), label = "Calories", value = "${(calories * 100 / calorieGoal.coerceAtLeast(1))}%")
                LegendItem(color = Color(0xFF06B6D4), label = "Distance", value = "${(distance * 100 / distanceGoal.coerceAtLeast(1)).toInt()}%")
            }
        }
    }
}

@Composable
private fun TripleRingProgress(
    stepsProgress: Float,
    caloriesProgress: Float,
    distanceProgress: Float
) {
    val animatedSteps by animateFloatAsState(stepsProgress, tween(1000), label = "steps")
    val animatedCalories by animateFloatAsState(caloriesProgress, tween(1200), label = "cal")
    val animatedDistance by animateFloatAsState(distanceProgress, tween(1400), label = "dist")
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 16.dp.toPx()
        val gap = 20.dp.toPx()
        
        // Outer ring - Steps (Blue)
        val outerRadius = (size.minDimension - strokeWidth) / 2
        drawArc(
            color = ElectricBlue.copy(alpha = 0.2f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(strokeWidth, cap = StrokeCap.Round),
            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
            size = Size(outerRadius * 2, outerRadius * 2)
        )
        drawArc(
            color = ElectricBlue,
            startAngle = -90f,
            sweepAngle = 360f * animatedSteps,
            useCenter = false,
            style = Stroke(strokeWidth, cap = StrokeCap.Round),
            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
            size = Size(outerRadius * 2, outerRadius * 2)
        )
        
        // Middle ring - Calories (Red)
        val middleRadius = outerRadius - gap
        val middleOffset = (size.minDimension - middleRadius * 2) / 2
        drawArc(
            color = Color(0xFFFF6B6B).copy(alpha = 0.2f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(strokeWidth, cap = StrokeCap.Round),
            topLeft = Offset(middleOffset, middleOffset),
            size = Size(middleRadius * 2, middleRadius * 2)
        )
        drawArc(
            color = Color(0xFFFF6B6B),
            startAngle = -90f,
            sweepAngle = 360f * animatedCalories,
            useCenter = false,
            style = Stroke(strokeWidth, cap = StrokeCap.Round),
            topLeft = Offset(middleOffset, middleOffset),
            size = Size(middleRadius * 2, middleRadius * 2)
        )
        
        // Inner ring - Distance (Cyan)
        val innerRadius = middleRadius - gap
        val innerOffset = (size.minDimension - innerRadius * 2) / 2
        drawArc(
            color = Color(0xFF06B6D4).copy(alpha = 0.2f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(strokeWidth, cap = StrokeCap.Round),
            topLeft = Offset(innerOffset, innerOffset),
            size = Size(innerRadius * 2, innerRadius * 2)
        )
        drawArc(
            color = Color(0xFF06B6D4),
            startAngle = -90f,
            sweepAngle = 360f * animatedDistance,
            useCenter = false,
            style = Stroke(strokeWidth, cap = StrokeCap.Round),
            topLeft = Offset(innerOffset, innerOffset),
            size = Size(innerRadius * 2, innerRadius * 2)
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = GlowWhite)
            Text(text = value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

/**
 * Bar Chart Card
 */
@Composable
private fun BarChartCard(
    title: String,
    icon: ImageVector,
    color: Color,
    data: List<ChartDataPoint>,
    goal: Float,
    unit: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (data.isNotEmpty()) {
                AnimatedBarChart(
                    data = data,
                    color = color,
                    goalLine = goal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // X-axis labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    data.takeLast(7).forEach { point ->
                        Text(
                            text = point.label.take(3),
                            style = MaterialTheme.typography.labelSmall,
                            color = GlowWhite.copy(alpha = 0.7f),
                            fontSize = 9.sp
                        )
                    }
                }
            } else {
                EmptyChartPlaceholder()
            }
        }
    }
}

@Composable
private fun AnimatedBarChart(
    data: List<ChartDataPoint>,
    color: Color,
    goalLine: Float,
    modifier: Modifier = Modifier
) {
    var animationPlayed by remember { mutableStateOf(false) }
    val animatedProgress by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(1000),
        label = "barAnim"
    )
    
    LaunchedEffect(Unit) {
        animationPlayed = true
    }
    
    val maxValue = (data.maxOfOrNull { it.value } ?: 1f).coerceAtLeast(goalLine)
    
    Canvas(modifier = modifier) {
        val barWidth = size.width / (data.size * 2)
        val spacing = barWidth
        
        // Draw goal line
        val goalY = size.height * (1 - goalLine / maxValue)
        drawLine(
            color = Color(0xFFF59E0B).copy(alpha = 0.5f),
            start = Offset(0f, goalY),
            end = Offset(size.width, goalY),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )
        
        // Draw bars
        data.forEachIndexed { index, point ->
            val barHeight = (point.value / maxValue) * size.height * animatedProgress
            val x = index * (barWidth + spacing) + spacing / 2
            
            // Bar gradient
            val gradient = Brush.verticalGradient(
                colors = listOf(color, color.copy(alpha = 0.5f)),
                startY = size.height - barHeight,
                endY = size.height
            )
            
            drawRoundRect(
                brush = gradient,
                topLeft = Offset(x, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
        }
    }
}


/**
 * Area Chart Card - Filled area under line
 */
@Composable
private fun AreaChartCard(
    title: String,
    icon: ImageVector,
    color: Color,
    data: List<ChartDataPoint>,
    unit: String
) {
    val total = data.sumOf { it.value.toInt() }
    val avg = if (data.isNotEmpty()) total / data.size else 0
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("%,d".format(total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
                    Text("total $unit", style = MaterialTheme.typography.labelSmall, color = GlowWhite)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (data.isNotEmpty()) {
                AreaChart(
                    data = data,
                    color = color,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Avg: %,d $unit/day".format(avg), style = MaterialTheme.typography.labelSmall, color = GlowWhite)
                    Text("Peak: %,d $unit".format(data.maxOfOrNull { it.value.toInt() } ?: 0), style = MaterialTheme.typography.labelSmall, color = color)
                }
            } else {
                EmptyChartPlaceholder()
            }
        }
    }
}

@Composable
private fun AreaChart(
    data: List<ChartDataPoint>,
    color: Color,
    modifier: Modifier = Modifier
) {
    var animationPlayed by remember { mutableStateOf(false) }
    val animatedProgress by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(1200),
        label = "areaAnim"
    )
    
    LaunchedEffect(Unit) { animationPlayed = true }
    
    val maxValue = data.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val pointSpacing = width / (data.size - 1).coerceAtLeast(1)
        
        // Create path for area
        val areaPath = Path()
        val linePath = Path()
        
        data.forEachIndexed { index, point ->
            val x = index * pointSpacing
            val y = height * (1 - (point.value / maxValue) * animatedProgress)
            
            if (index == 0) {
                areaPath.moveTo(x, height)
                areaPath.lineTo(x, y)
                linePath.moveTo(x, y)
            } else {
                areaPath.lineTo(x, y)
                linePath.lineTo(x, y)
            }
        }
        
        // Close area path
        areaPath.lineTo(width, height)
        areaPath.close()
        
        // Draw filled area with gradient
        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.4f), color.copy(alpha = 0.05f))
            )
        )
        
        // Draw line
        drawPath(
            path = linePath,
            color = color,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
        
        // Draw points
        data.forEachIndexed { index, point ->
            val x = index * pointSpacing
            val y = height * (1 - (point.value / maxValue) * animatedProgress)
            
            drawCircle(color = color, radius = 5.dp.toPx(), center = Offset(x, y))
            drawCircle(color = Color(0xFF1A1A2E), radius = 2.dp.toPx(), center = Offset(x, y))
        }
    }
}

/**
 * Gradient Line Chart Card
 */
@Composable
private fun GradientLineChartCard(
    title: String,
    icon: ImageVector,
    color: Color,
    data: List<ChartDataPoint>,
    unit: String
) {
    val total = data.sumOf { it.value.toDouble() }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("%.1f".format(total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
                    Text("total $unit", style = MaterialTheme.typography.labelSmall, color = GlowWhite)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (data.isNotEmpty()) {
                GradientLineChart(
                    data = data,
                    color = color,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
            } else {
                EmptyChartPlaceholder()
            }
        }
    }
}

@Composable
private fun GradientLineChart(
    data: List<ChartDataPoint>,
    color: Color,
    modifier: Modifier = Modifier
) {
    var animationPlayed by remember { mutableStateOf(false) }
    val animatedProgress by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(1500),
        label = "lineAnim"
    )
    
    LaunchedEffect(Unit) { animationPlayed = true }
    
    val maxValue = data.maxOfOrNull { it.value }?.coerceAtLeast(0.1f) ?: 0.1f
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val pointSpacing = width / (data.size - 1).coerceAtLeast(1)
        
        // Draw grid
        for (i in 0..4) {
            val y = height * i / 4
            drawLine(
                color = Color.White.copy(alpha = 0.05f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx()
            )
        }
        
        // Create smooth curve path using cubic bezier
        val path = Path()
        val points = data.mapIndexed { index, point ->
            val x = index * pointSpacing
            val y = height * (1 - (point.value / maxValue) * animatedProgress)
            Offset(x, y)
        }
        
        if (points.isNotEmpty()) {
            path.moveTo(points[0].x, points[0].y)
            
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val controlX = (prev.x + curr.x) / 2
                path.cubicTo(controlX, prev.y, controlX, curr.y, curr.x, curr.y)
            }
        }
        
        // Draw gradient stroke
        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                colors = listOf(color.copy(alpha = 0.5f), color, color.copy(alpha = 0.8f))
            ),
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
        
        // Draw glow points
        points.forEach { point ->
            // Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.5f), Color.Transparent),
                    center = point,
                    radius = 12.dp.toPx()
                ),
                radius = 12.dp.toPx(),
                center = point
            )
            // Point
            drawCircle(color = color, radius = 4.dp.toPx(), center = point)
        }
    }
}

/**
 * Weekly Comparison - Grouped bars
 */
@Composable
private fun WeeklyComparisonCard(
    stepsData: List<ChartDataPoint>,
    caloriesData: List<ChartDataPoint>,
    stepGoal: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Compare, null, tint = NeonPurple, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Steps vs Goal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (stepsData.isNotEmpty()) {
                ComparisonBarChart(
                    data = stepsData.takeLast(7),
                    goal = stepGoal.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    LegendItem(color = ElectricBlue, label = "Actual", value = "")
                    Spacer(modifier = Modifier.width(24.dp))
                    LegendItem(color = Color(0xFFF59E0B), label = "Goal", value = "")
                }
            } else {
                EmptyChartPlaceholder()
            }
        }
    }
}

@Composable
private fun ComparisonBarChart(
    data: List<ChartDataPoint>,
    goal: Float,
    modifier: Modifier = Modifier
) {
    var animationPlayed by remember { mutableStateOf(false) }
    val animatedProgress by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(1000),
        label = "compAnim"
    )
    
    LaunchedEffect(Unit) { animationPlayed = true }
    
    val maxValue = maxOf(data.maxOfOrNull { it.value } ?: goal, goal)
    
    Canvas(modifier = modifier) {
        val barWidth = size.width / (data.size * 3)
        val groupSpacing = barWidth
        
        data.forEachIndexed { index, point ->
            val groupX = index * (barWidth * 2 + groupSpacing) + groupSpacing / 2
            
            // Goal bar (background)
            val goalHeight = (goal / maxValue) * size.height
            drawRoundRect(
                color = Color(0xFFF59E0B).copy(alpha = 0.3f),
                topLeft = Offset(groupX, size.height - goalHeight),
                size = Size(barWidth, goalHeight),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
            
            // Actual bar
            val actualHeight = (point.value / maxValue) * size.height * animatedProgress
            val barColor = if (point.value >= goal) CyberGreen else ElectricBlue
            drawRoundRect(
                color = barColor,
                topLeft = Offset(groupX + barWidth, size.height - actualHeight),
                size = Size(barWidth, actualHeight),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
        }
    }
}


/**
 * Activity Distribution - Donut Chart
 */
@Composable
private fun ActivityDistributionCard(
    steps: Int,
    calories: Int,
    distance: Double,
    stepGoal: Int,
    calorieGoal: Int,
    distanceGoal: Int
) {
    val stepsPercent = (steps.toFloat() / stepGoal).coerceIn(0f, 1f)
    val caloriesPercent = (calories.toFloat() / calorieGoal).coerceIn(0f, 1f)
    val distancePercent = (distance.toFloat() / distanceGoal).coerceIn(0f, 1f)
    val total = stepsPercent + caloriesPercent + distancePercent
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PieChart, null, tint = CyberGreen, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Goal Distribution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Donut Chart
                Box(
                    modifier = Modifier.size(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    DonutChart(
                        values = listOf(
                            stepsPercent / total.coerceAtLeast(0.01f),
                            caloriesPercent / total.coerceAtLeast(0.01f),
                            distancePercent / total.coerceAtLeast(0.01f)
                        ),
                        colors = listOf(ElectricBlue, Color(0xFFFF6B6B), Color(0xFF06B6D4))
                    )
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${((stepsPercent + caloriesPercent + distancePercent) * 100 / 3).toInt()}%",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text("Overall", style = MaterialTheme.typography.labelSmall, color = GlowWhite)
                    }
                }
                
                // Legend
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DonutLegendItem(
                        color = ElectricBlue,
                        label = "Steps",
                        value = "%,d".format(steps),
                        percent = (stepsPercent * 100).toInt()
                    )
                    DonutLegendItem(
                        color = Color(0xFFFF6B6B),
                        label = "Calories",
                        value = "%,d kcal".format(calories),
                        percent = (caloriesPercent * 100).toInt()
                    )
                    DonutLegendItem(
                        color = Color(0xFF06B6D4),
                        label = "Distance",
                        value = "%.1f km".format(distance / 1000),
                        percent = (distancePercent * 100).toInt()
                    )
                }
            }
        }
    }
}

@Composable
private fun DonutChart(
    values: List<Float>,
    colors: List<Color>
) {
    var animationPlayed by remember { mutableStateOf(false) }
    val animatedProgress by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(1200),
        label = "donutAnim"
    )
    
    LaunchedEffect(Unit) { animationPlayed = true }
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 24.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2
        val center = Offset(size.width / 2, size.height / 2)
        
        var startAngle = -90f
        
        values.forEachIndexed { index, value ->
            val sweepAngle = 360f * value * animatedProgress
            
            drawArc(
                color = colors.getOrElse(index) { Color.Gray },
                startAngle = startAngle,
                sweepAngle = sweepAngle - 4f, // Gap between segments
                useCenter = false,
                style = Stroke(strokeWidth, cap = StrokeCap.Round),
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2)
            )
            
            startAngle += sweepAngle
        }
    }
}

@Composable
private fun DonutLegendItem(
    color: Color,
    label: String,
    value: String,
    percent: Int
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = GlowWhite)
            Row {
                Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.width(4.dp))
                Text("($percent%)", style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
    }
}

/**
 * Summary Statistics Card
 */
@Composable
private fun SummaryStatsCard(
    avgSteps: Int,
    avgCalories: Int,
    totalDistance: Double,
    activeDays: Int,
    bestDay: Int,
    period: ProgressPeriod
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Analytics, null, tint = NeonPurple, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("${period.label} Statistics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Stats Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatBox(
                    icon = Icons.Default.TrendingUp,
                    value = "%,d".format(avgSteps),
                    label = "Avg Steps",
                    color = ElectricBlue
                )
                StatBox(
                    icon = Icons.Default.LocalFireDepartment,
                    value = "%,d".format(avgCalories),
                    label = "Avg Calories",
                    color = Color(0xFFFF6B6B)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatBox(
                    icon = Icons.Default.LocationOn,
                    value = "%.1f km".format(totalDistance / 1000),
                    label = "Total Distance",
                    color = Color(0xFF06B6D4)
                )
                StatBox(
                    icon = Icons.Default.EmojiEvents,
                    value = "%,d".format(bestDay),
                    label = "Best Day",
                    color = Color(0xFFF59E0B)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Active days progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CalendarMonth, null, tint = CyberGreen, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Active Days: ", style = MaterialTheme.typography.bodyMedium, color = GlowWhite)
                Text("$activeDays / ${period.days}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = CyberGreen)
                Spacer(modifier = Modifier.weight(1f))
                
                // Mini progress bar
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(CyberGreen.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(activeDays.toFloat() / period.days)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(CyberGreen)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatBox(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = GlowWhite)
    }
}

/**
 * Streak & Achievements Card
 */
@Composable
private fun StreakCard(
    activeDays: Int,
    bestDay: Int,
    totalSteps: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2E)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Whatshot, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Achievements", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    AchievementBadge(
                        icon = Icons.Default.Whatshot,
                        title = "Active Streak",
                        value = "$activeDays days",
                        color = Color(0xFFF59E0B),
                        isUnlocked = activeDays >= 3
                    )
                }
                item {
                    AchievementBadge(
                        icon = Icons.Default.EmojiEvents,
                        title = "Best Day",
                        value = "%,d steps".format(bestDay),
                        color = Color(0xFFFFD700),
                        isUnlocked = bestDay >= 10000
                    )
                }
                item {
                    AchievementBadge(
                        icon = Icons.Default.DirectionsWalk,
                        title = "Total Steps",
                        value = "%,d".format(totalSteps),
                        color = ElectricBlue,
                        isUnlocked = totalSteps >= 50000
                    )
                }
                item {
                    AchievementBadge(
                        icon = Icons.Default.Star,
                        title = "Consistency",
                        value = "${(activeDays * 100 / 7).coerceAtMost(100)}%",
                        color = NeonPurple,
                        isUnlocked = activeDays >= 5
                    )
                }
            }
        }
    }
}

@Composable
private fun AchievementBadge(
    icon: ImageVector,
    title: String,
    value: String,
    color: Color,
    isUnlocked: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "badge")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (isUnlocked) 0.6f else 0.3f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "glow"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(90.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isUnlocked) color.copy(alpha = 0.15f)
                else Color.Gray.copy(alpha = 0.1f)
            )
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (isUnlocked)
                        Brush.radialGradient(
                            colors = listOf(color.copy(alpha = glowAlpha), color.copy(alpha = 0.1f))
                        )
                    else
                        Brush.radialGradient(
                            colors = listOf(Color.Gray.copy(alpha = 0.3f), Color.Gray.copy(alpha = 0.1f))
                        )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isUnlocked) icon else Icons.Default.Lock,
                contentDescription = null,
                tint = if (isUnlocked) color else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = if (isUnlocked) Color.White else Color.Gray,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (isUnlocked) color else Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmptyChartPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.ShowChart,
                null,
                tint = GlowWhite.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "No data yet. Start tracking!",
                style = MaterialTheme.typography.bodyMedium,
                color = GlowWhite.copy(alpha = 0.7f)
            )
        }
    }
}

// Data classes
data class ChartDataPoint(
    val label: String,
    val value: Float
)

data class FriendProgress(
    val id: String,
    val name: String,
    val todaySteps: Int,
    val stepGoal: Int,
    val avatarUrl: String? = null
)

enum class ProgressPeriod(val label: String, val days: Int) {
    WEEK("7 Days", 7),
    TWO_WEEKS("14 Days", 14),
    MONTH("30 Days", 30)
}
