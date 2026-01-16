package com.healthtracker.presentation.planning

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.healthtracker.domain.model.DietPlan
import com.healthtracker.domain.model.Exercise
import com.healthtracker.domain.model.HydrationSummary
import com.healthtracker.domain.model.MealSuggestion
import com.healthtracker.domain.model.MealType
import com.healthtracker.domain.model.WaterIntake
import com.healthtracker.domain.model.WorkoutPlan
import com.healthtracker.domain.model.WorkoutType
import com.healthtracker.presentation.theme.CyberGreen
import com.healthtracker.presentation.theme.ElectricBlue
import com.healthtracker.presentation.theme.GlassSurface
import com.healthtracker.presentation.theme.GlowWhite
import com.healthtracker.presentation.theme.HealthTrackerTheme
import com.healthtracker.presentation.theme.NeonPurple
import com.healthtracker.presentation.theme.WaterColor

/**
 * Planning screen with workout, diet, and hydration tabs.
 */
@Composable
fun PlanningScreen(
    viewModel: PlanningViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    
    PlanningContent(
        uiState = uiState,
        onTabSelected = viewModel::selectTab,
        onWorkoutTypeSelected = viewModel::selectWorkoutType,
        onLogWater = viewModel::logWater,
        onRegeneratePlans = viewModel::regeneratePlans,
        modifier = modifier
    )
}

@Composable
private fun PlanningContent(
    uiState: PlanningUiState,
    onTabSelected: (PlanningTab) -> Unit,
    onWorkoutTypeSelected: (WorkoutType) -> Unit,
    onLogWater: (Int) -> Unit,
    onRegeneratePlans: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gradientBackground = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0D0D1A),
            Color(0xFF1A1A2E),
            Color(0xFF16213E)
        )
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradientBackground)
    ) {
        // Floating orbs
        FloatingPlanningOrbs()
        
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            PlanningHeader(onRegeneratePlans = onRegeneratePlans)
            
            // Tab selector
            PlanningTabRow(
                selectedTab = uiState.selectedTab,
                onTabSelected = onTabSelected
            )
            
            // Content
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = ElectricBlue)
                    }
                }
                else -> {
                    when (uiState.selectedTab) {
                        PlanningTab.WORKOUT -> WorkoutContent(
                            homePlan = uiState.homePlan,
                            gymPlan = uiState.gymPlan,
                            selectedType = uiState.selectedWorkoutType,
                            onTypeSelected = onWorkoutTypeSelected
                        )
                        PlanningTab.DIET -> DietContent(
                            dietPlan = uiState.dietPlan
                        )
                        else -> WorkoutContent(
                            homePlan = uiState.homePlan,
                            gymPlan = uiState.gymPlan,
                            selectedType = uiState.selectedWorkoutType,
                            onTypeSelected = onWorkoutTypeSelected
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingPlanningOrbs() {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .blur(70.dp)
                .background(CyberGreen.copy(alpha = 0.15f), CircleShape)
                .align(Alignment.TopEnd)
        )
        Box(
            modifier = Modifier
                .size(140.dp)
                .blur(60.dp)
                .background(WaterColor.copy(alpha = 0.12f), CircleShape)
                .align(Alignment.BottomStart)
        )
    }
}

@Composable
private fun PlanningHeader(onRegeneratePlans: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Your Plans",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        IconButton(
            onClick = onRegeneratePlans,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(GlassSurface)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Regenerate",
                tint = ElectricBlue
            )
        }
    }
}

@Composable
private fun PlanningTabRow(
    selectedTab: PlanningTab,
    onTabSelected: (PlanningTab) -> Unit
) {
    // Only show Workout and Diet tabs (removed Hydration)
    val tabs = listOf(PlanningTab.WORKOUT, PlanningTab.DIET)
    
    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        TabRow(
            selectedTabIndex = if (selectedTab == PlanningTab.HYDRATION) 0 else tabs.indexOf(selectedTab).coerceAtLeast(0),
            containerColor = Color.Transparent,
            contentColor = ElectricBlue,
            indicator = {},
            divider = {}
        ) {
            tabs.forEach { tab ->
                val selected = tab == selectedTab
                val (icon, color) = when (tab) {
                    PlanningTab.WORKOUT -> Icons.Default.FitnessCenter to CyberGreen
                    PlanningTab.DIET -> Icons.Default.LocalDining to NeonPurple
                    else -> Icons.Default.FitnessCenter to CyberGreen
                }
                
                Tab(
                    selected = selected,
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier
                        .padding(4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) color.copy(alpha = 0.2f)
                            else Color.Transparent
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = tab.name,
                            tint = if (selected) color else GlowWhite,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = tab.name.lowercase().replaceFirstChar { it.uppercase() },
                            color = if (selected) color else GlowWhite,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    glowColor: Color = ElectricBlue,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = glowColor.copy(alpha = 0.3f)
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
        colors = CardDefaults.cardColors(containerColor = GlassSurface)
    ) {
        content()
    }
}

// ============================================
// WORKOUT CONTENT
// ============================================

@Composable
private fun WorkoutContent(
    homePlan: WorkoutPlan?,
    gymPlan: WorkoutPlan?,
    selectedType: WorkoutType,
    onTypeSelected: (WorkoutType) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Type selector
        item {
            WorkoutTypeSelector(
                selectedType = selectedType,
                onTypeSelected = onTypeSelected
            )
        }
        
        // Current plan
        val currentPlan = if (selectedType == WorkoutType.HOME) homePlan else gymPlan
        
        if (currentPlan != null) {
            item {
                WorkoutSummaryCard(plan = currentPlan)
            }
            
            items(currentPlan.exercises) { exercise ->
                ExerciseCard(exercise = exercise)
            }
        } else {
            item {
                EmptyPlanCard(
                    message = "No ${selectedType.name.lowercase()} workout plan yet",
                    icon = if (selectedType == WorkoutType.HOME) Icons.Default.Home else Icons.Default.FitnessCenter
                )
            }
        }
    }
}

@Composable
private fun WorkoutTypeSelector(
    selectedType: WorkoutType,
    onTypeSelected: (WorkoutType) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        WorkoutType.entries.forEach { type ->
            val selected = type == selectedType
            val (icon, label) = when (type) {
                WorkoutType.HOME -> Icons.Default.Home to "Home"
                WorkoutType.GYM -> Icons.Default.FitnessCenter to "Gym"
            }
            
            GlassmorphicCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTypeSelected(type) },
                glowColor = if (selected) CyberGreen else GlowWhite
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (selected) CyberGreen.copy(alpha = 0.2f)
                            else Color.Transparent
                        )
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (selected) CyberGreen else GlowWhite
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        color = if (selected) CyberGreen else GlowWhite,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutSummaryCard(plan: WorkoutPlan) {
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = CyberGreen
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "${plan.goal.name.replace("_", " ")} Workout",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(label = "Duration", value = "${plan.durationMinutes} min", color = CyberGreen)
                StatItem(label = "Exercises", value = "${plan.exercises.size}", color = ElectricBlue)
                StatItem(label = "Calories", value = "~${plan.caloriesBurnEstimate}", color = NeonPurple)
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = GlowWhite
        )
    }
}

@Composable
private fun ExerciseCard(exercise: Exercise) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Text(
                    text = exercise.category.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberGreen
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Sets/Reps or Duration
            val setsInfo = buildString {
                append("${exercise.sets} sets")
                exercise.reps?.let { append(" × $it reps") }
                exercise.durationSeconds?.let { append(" × ${it}s") }
            }
            
            Text(
                text = setsInfo,
                style = MaterialTheme.typography.bodyMedium,
                color = ElectricBlue
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = exercise.instructions,
                style = MaterialTheme.typography.bodySmall,
                color = GlowWhite.copy(alpha = 0.8f)
            )
            
            if (exercise.equipmentRequired.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Equipment: ${exercise.equipmentRequired.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlowWhite.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// ============================================
// DIET CONTENT
// ============================================

@Composable
private fun DietContent(dietPlan: DietPlan?) {
    if (dietPlan == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            EmptyPlanCard(
                message = "No diet plan yet",
                icon = Icons.Default.LocalDining
            )
        }
        return
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary card
        item {
            DietSummaryCard(plan = dietPlan)
        }
        
        // Macro targets
        item {
            MacroTargetsCard(macros = dietPlan.macroTargets, calories = dietPlan.dailyCalorieTarget)
        }
        
        // Meals by type
        val mealsByType = dietPlan.mealSuggestions.groupBy { it.mealType }
        
        MealType.entries.forEach { mealType ->
            val meals = mealsByType[mealType] ?: emptyList()
            if (meals.isNotEmpty()) {
                item {
                    Text(
                        text = mealType.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NeonPurple,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                items(meals) { meal ->
                    MealCard(meal = meal)
                }
            }
        }
    }
}

@Composable
private fun DietSummaryCard(plan: DietPlan) {
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = NeonPurple
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${plan.goal.name.replace("_", " ")} Diet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Text(
                    text = plan.preference.name.replace("_", "-"),
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonPurple
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(label = "Daily Cal", value = "${plan.dailyCalorieTarget}", color = NeonPurple)
                StatItem(label = "Meals", value = "${plan.mealSuggestions.size}", color = ElectricBlue)
            }
        }
    }
}

@Composable
private fun MacroTargetsCard(macros: com.healthtracker.domain.model.MacroTargets, calories: Int) {
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = ElectricBlue
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Daily Macro Targets",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Macro bars
            MacroBar(label = "Protein", value = macros.proteinGrams, unit = "g", color = CyberGreen, maxValue = 200)
            Spacer(modifier = Modifier.height(8.dp))
            MacroBar(label = "Carbs", value = macros.carbsGrams, unit = "g", color = ElectricBlue, maxValue = 300)
            Spacer(modifier = Modifier.height(8.dp))
            MacroBar(label = "Fat", value = macros.fatGrams, unit = "g", color = NeonPurple, maxValue = 100)
            Spacer(modifier = Modifier.height(8.dp))
            MacroBar(label = "Fiber", value = macros.fiberGrams, unit = "g", color = WaterColor, maxValue = 50)
        }
    }
}

@Composable
private fun MacroBar(label: String, value: Int, unit: String, color: Color, maxValue: Int) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = GlowWhite
            )
            Text(
                text = "$value$unit",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        LinearProgressIndicator(
            progress = { (value.toFloat() / maxValue).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.2f)
        )
    }
}

@Composable
private fun MealCard(meal: MealSuggestion) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = meal.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                
                Text(
                    text = "${meal.calories} cal",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = NeonPurple
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Macros row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MacroChip(label = "P", value = "${meal.protein.toInt()}g", color = CyberGreen)
                MacroChip(label = "C", value = "${meal.carbs.toInt()}g", color = ElectricBlue)
                MacroChip(label = "F", value = "${meal.fat.toInt()}g", color = NeonPurple)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Ingredients
            Text(
                text = meal.ingredients.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = GlowWhite.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⏱ ${meal.preparationTime} min",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlowWhite.copy(alpha = 0.6f)
                )
                if (meal.isVegetarian) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "🌱 Vegetarian",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun MacroChip(label: String, value: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = GlowWhite
        )
    }
}

// ============================================
// HYDRATION CONTENT
// ============================================

@Composable
private fun HydrationContent(
    summary: HydrationSummary?,
    onLogWater: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Progress card
        item {
            HydrationProgressCard(summary = summary)
        }
        
        // Quick add buttons
        item {
            QuickAddWaterCard(onLogWater = onLogWater)
        }
        
        // Today's logs
        if (summary != null && summary.intakeLogs.isNotEmpty()) {
            item {
                Text(
                    text = "Today's Intake",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WaterColor,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            items(summary.intakeLogs.sortedByDescending { it.loggedAt }) { intake ->
                WaterIntakeLogItem(intake = intake)
            }
        }
    }
}

@Composable
private fun HydrationProgressCard(summary: HydrationSummary?) {
    val target = summary?.targetMl ?: 2000
    val consumed = summary?.consumedMl ?: 0
    val percent = summary?.percentComplete ?: 0f
    
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = WaterColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Daily Hydration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Circular progress
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(160.dp)
            ) {
                CircularProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 12.dp,
                    color = WaterColor,
                    trackColor = WaterColor.copy(alpha = 0.2f)
                )
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${consumed}ml",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = WaterColor
                    )
                    Text(
                        text = "of ${target}ml",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlowWhite
                    )
                    Text(
                        text = "${percent.toInt()}%",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (percent >= 100) CyberGreen else GlowWhite
                    )
                }
            }
            
            if (percent >= 100) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "🎉 Goal reached!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CyberGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun QuickAddWaterCard(onLogWater: (Int) -> Unit) {
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = ElectricBlue
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Quick Add",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val amounts = listOf(100 to "🥤", 200 to "🥛", 250 to "☕", 500 to "🍶")
                items(amounts) { (amount, emoji) ->
                    WaterAmountButton(
                        amount = amount,
                        emoji = emoji,
                        onClick = { onLogWater(amount) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WaterAmountButton(
    amount: Int,
    emoji: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(WaterColor.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Text(
            text = emoji,
            fontSize = 24.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${amount}ml",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = WaterColor
        )
    }
}

@Composable
private fun WaterIntakeLogItem(intake: WaterIntake) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.WaterDrop,
                    contentDescription = null,
                    tint = WaterColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${intake.amountMl}ml",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            Text(
                text = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                    .format(intake.loggedAt.atZone(java.time.ZoneId.systemDefault())),
                style = MaterialTheme.typography.bodySmall,
                color = GlowWhite.copy(alpha = 0.6f)
            )
        }
    }
}

// ============================================
// EMPTY STATE
// ============================================

@Composable
private fun EmptyPlanCard(message: String, icon: ImageVector) {
    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = GlowWhite.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = GlowWhite.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Tap refresh to generate",
                style = MaterialTheme.typography.bodySmall,
                color = GlowWhite.copy(alpha = 0.5f)
            )
        }
    }
}

// ============================================
// PREVIEW
// ============================================

@Preview(showBackground = true, backgroundColor = 0xFF0D0D1A)
@Composable
private fun PlanningScreenPreview() {
    HealthTrackerTheme {
        PlanningContent(
            uiState = PlanningUiState(),
            onTabSelected = {},
            onWorkoutTypeSelected = {},
            onLogWater = {},
            onRegeneratePlans = {}
        )
    }
}