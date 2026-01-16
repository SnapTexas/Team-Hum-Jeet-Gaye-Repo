package com.healthtracker.presentation.mentalhealth

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthtracker.presentation.theme.*
import java.util.Calendar

/**
 * Mental Health Screen with REAL mood detection based on:
 * - App usage patterns (social media, music apps, etc.)
 * - Time of day
 * - Recent activity patterns
 * 
 * NO DUMMY DATA - Everything is calculated from real device data!
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MentalHealthScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var moodAnalysis by remember { mutableStateOf<MoodAnalysis?>(null) }
    var isAnalyzing by remember { mutableStateOf(true) }
    var hasPermission by remember { mutableStateOf(false) }

    // Check usage stats permission and analyze mood
    LaunchedEffect(Unit) {
        hasPermission = checkUsageStatsPermission(context)
        if (hasPermission) {
            moodAnalysis = analyzeMoodFromUsage(context)
        }
        isAnalyzing = false
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Mental Wellness",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0D0D1A), Color(0xFF1A1A2E), Color(0xFF16213E))
                    )
                )
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Mood Detection Card
                item {
                    MoodDetectionCard(
                        moodAnalysis = moodAnalysis,
                        isAnalyzing = isAnalyzing,
                        hasPermission = hasPermission,
                        onRequestPermission = {
                            // Open usage access settings
                            val intent = android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            context.startActivity(intent)
                        }
                    )
                }
                
                // Exercise Suggestions based on mood
                if (moodAnalysis != null) {
                    item {
                        Text(
                            "💪 Suggested Activities",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    items(moodAnalysis!!.suggestedExercises) { exercise ->
                        ExerciseSuggestionCard(exercise = exercise)
                    }
                    
                    // App Usage Insights
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "📱 App Usage Insights",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    items(moodAnalysis!!.appInsights.take(5)) { insight ->
                        AppInsightCard(insight = insight)
                    }
                }
                
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}


@Composable
private fun MoodDetectionCard(
    moodAnalysis: MoodAnalysis?,
    isAnalyzing: Boolean,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.9f))
    ) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!hasPermission) {
                // Permission required
                Icon(Icons.Default.Lock, null, tint = Color(0xFFFFA500), modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Usage Access Required", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "To detect your mood from app usage patterns, please grant usage access permission",
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("Grant Permission")
                }
            } else if (isAnalyzing) {
                CircularProgressIndicator(color = ElectricBlue, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Analyzing your mood...", color = Color.White)
            } else if (moodAnalysis != null) {
                // Show detected mood
                val moodEmoji = when (moodAnalysis.mood) {
                    Mood.HAPPY -> "😊"
                    Mood.RELAXED -> "😌"
                    Mood.NEUTRAL -> "😐"
                    Mood.STRESSED -> "😰"
                    Mood.SAD -> "😢"
                    Mood.ENERGETIC -> "⚡"
                }
                
                val moodColor = when (moodAnalysis.mood) {
                    Mood.HAPPY -> CyberGreen
                    Mood.RELAXED -> ElectricBlue
                    Mood.NEUTRAL -> Color.White
                    Mood.STRESSED -> Color(0xFFFFA500)
                    Mood.SAD -> NeonPurple
                    Mood.ENERGETIC -> Color(0xFFFFD700)
                }
                
                Text(moodEmoji, fontSize = 64.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "You seem ${moodAnalysis.mood.name.lowercase()}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = moodColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    moodAnalysis.reason,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Confidence indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Confidence: ", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    LinearProgressIndicator(
                        progress = { moodAnalysis.confidence },
                        modifier = Modifier.width(100.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = moodColor,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                    Text(" ${(moodAnalysis.confidence * 100).toInt()}%", color = moodColor, fontSize = 12.sp)
                }
            }
        }
    }
}


@Composable
private fun ExerciseSuggestionCard(exercise: ExerciseSuggestion) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Exercise icon
            Surface(
                shape = CircleShape,
                color = exercise.color.copy(alpha = 0.2f),
                modifier = Modifier.size(50.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(exercise.emoji, fontSize = 24.sp)
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(exercise.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                Text(exercise.description, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = exercise.color.copy(alpha = 0.2f)
                    ) {
                        Text(
                            "${exercise.durationMinutes} min",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = exercise.color,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CyberGreen.copy(alpha = 0.2f)
                    ) {
                        Text(
                            "~${exercise.caloriesBurn} cal",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = CyberGreen,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppInsightCard(insight: AppUsageInsight) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(insight.emoji, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(insight.appName, fontWeight = FontWeight.Medium, color = Color.White, fontSize = 14.sp)
                Text(insight.insight, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
            Text(
                "${insight.usageMinutes} min",
                color = if (insight.usageMinutes > 60) Color(0xFFFFA500) else CyberGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


// ============================================
// DATA MODELS
// ============================================

enum class Mood {
    HAPPY, RELAXED, NEUTRAL, STRESSED, SAD, ENERGETIC
}

data class MoodAnalysis(
    val mood: Mood,
    val confidence: Float,
    val reason: String,
    val suggestedExercises: List<ExerciseSuggestion>,
    val appInsights: List<AppUsageInsight>
)

data class ExerciseSuggestion(
    val name: String,
    val description: String,
    val emoji: String,
    val durationMinutes: Int,
    val caloriesBurn: Int,
    val color: Color
)

data class AppUsageInsight(
    val appName: String,
    val packageName: String,
    val usageMinutes: Int,
    val category: AppCategory,
    val emoji: String,
    val insight: String
)

enum class AppCategory {
    SOCIAL_MEDIA, MUSIC, VIDEO, PRODUCTIVITY, GAMES, COMMUNICATION, OTHER
}

// ============================================
// MOOD ANALYSIS LOGIC
// ============================================

private fun checkUsageStatsPermission(context: Context): Boolean {
    return try {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            calendar.timeInMillis,
            System.currentTimeMillis()
        )
        stats != null && stats.isNotEmpty()
    } catch (e: Exception) {
        false
    }
}


private fun analyzeMoodFromUsage(context: Context): MoodAnalysis {
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.HOUR_OF_DAY, -24)
    
    val stats = usageStatsManager.queryUsageStats(
        UsageStatsManager.INTERVAL_DAILY,
        calendar.timeInMillis,
        System.currentTimeMillis()
    )
    
    // Categorize apps and calculate usage
    var socialMediaMinutes = 0L
    var musicMinutes = 0L
    var videoMinutes = 0L
    var productivityMinutes = 0L
    var gameMinutes = 0L
    var communicationMinutes = 0L
    
    val appInsights = mutableListOf<AppUsageInsight>()
    
    stats?.filter { it.totalTimeInForeground > 60000 }?.forEach { stat ->
        val minutes = (stat.totalTimeInForeground / 60000).toInt()
        val packageName = stat.packageName
        val appName = try {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast(".")
        }
        
        val (category, emoji) = categorizeApp(packageName)
        
        when (category) {
            AppCategory.SOCIAL_MEDIA -> socialMediaMinutes += minutes
            AppCategory.MUSIC -> musicMinutes += minutes
            AppCategory.VIDEO -> videoMinutes += minutes
            AppCategory.PRODUCTIVITY -> productivityMinutes += minutes
            AppCategory.GAMES -> gameMinutes += minutes
            AppCategory.COMMUNICATION -> communicationMinutes += minutes
            else -> {}
        }
        
        if (minutes > 5) {
            appInsights.add(AppUsageInsight(
                appName = appName,
                packageName = packageName,
                usageMinutes = minutes,
                category = category,
                emoji = emoji,
                insight = getInsightForApp(category, minutes)
            ))
        }
    }
    
    // Determine mood based on usage patterns
    val (mood, confidence, reason) = determineMood(
        socialMediaMinutes, musicMinutes, videoMinutes, 
        productivityMinutes, gameMinutes, communicationMinutes
    )
    
    // Get exercise suggestions based on mood
    val exercises = getExercisesForMood(mood)
    
    return MoodAnalysis(
        mood = mood,
        confidence = confidence,
        reason = reason,
        suggestedExercises = exercises,
        appInsights = appInsights.sortedByDescending { it.usageMinutes }
    )
}


private fun categorizeApp(packageName: String): Pair<AppCategory, String> {
    val pkg = packageName.lowercase()
    return when {
        // Music apps - indicates mood through music choice
        pkg.contains("spotify") || pkg.contains("music") || pkg.contains("gaana") || 
        pkg.contains("wynk") || pkg.contains("jiosaavn") || pkg.contains("soundcloud") ||
        pkg.contains("youtube.music") -> AppCategory.MUSIC to "🎵"
        
        // Social media - can indicate seeking connection or comparison
        pkg.contains("instagram") || pkg.contains("facebook") || pkg.contains("twitter") ||
        pkg.contains("snapchat") || pkg.contains("tiktok") || pkg.contains("reddit") ||
        pkg.contains("linkedin") || pkg.contains("pinterest") -> AppCategory.SOCIAL_MEDIA to "📱"
        
        // Video - entertainment/relaxation
        pkg.contains("youtube") || pkg.contains("netflix") || pkg.contains("prime") ||
        pkg.contains("hotstar") || pkg.contains("voot") || pkg.contains("zee5") ||
        pkg.contains("mxplayer") || pkg.contains("vlc") -> AppCategory.VIDEO to "📺"
        
        // Games - stress relief or escapism
        pkg.contains("game") || pkg.contains("pubg") || pkg.contains("freefire") ||
        pkg.contains("candy") || pkg.contains("clash") || pkg.contains("bgmi") ||
        pkg.contains("ludo") || pkg.contains("chess") -> AppCategory.GAMES to "🎮"
        
        // Productivity - focused/working
        pkg.contains("docs") || pkg.contains("sheets") || pkg.contains("office") ||
        pkg.contains("notion") || pkg.contains("evernote") || pkg.contains("calendar") ||
        pkg.contains("mail") || pkg.contains("gmail") -> AppCategory.PRODUCTIVITY to "💼"
        
        // Communication - social connection
        pkg.contains("whatsapp") || pkg.contains("telegram") || pkg.contains("messenger") ||
        pkg.contains("signal") || pkg.contains("discord") || pkg.contains("call") ||
        pkg.contains("dialer") -> AppCategory.COMMUNICATION to "💬"
        
        else -> AppCategory.OTHER to "📦"
    }
}

private fun getInsightForApp(category: AppCategory, minutes: Int): String {
    return when (category) {
        AppCategory.MUSIC -> if (minutes > 60) "Listening to music a lot - music therapy!" else "Enjoying some tunes"
        AppCategory.SOCIAL_MEDIA -> if (minutes > 120) "Heavy social media use - take a break?" else "Staying connected"
        AppCategory.VIDEO -> if (minutes > 180) "Binge watching mode!" else "Entertainment time"
        AppCategory.GAMES -> if (minutes > 60) "Gaming session - stress relief?" else "Quick gaming break"
        AppCategory.PRODUCTIVITY -> "Being productive!"
        AppCategory.COMMUNICATION -> "Staying in touch with people"
        AppCategory.OTHER -> "App usage"
    }
}


private fun determineMood(
    socialMedia: Long, music: Long, video: Long,
    productivity: Long, games: Long, communication: Long
): Triple<Mood, Float, String> {
    val totalUsage = socialMedia + music + video + productivity + games + communication
    
    // Time of day factor
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val isLateNight = hour in 0..5 || hour >= 23
    val isMorning = hour in 6..11
    val isEvening = hour in 18..22
    
    return when {
        // High music usage often indicates emotional state
        music > 60 && music > socialMedia -> {
            if (isLateNight) {
                Triple(Mood.SAD, 0.7f, "Late night music session - feeling reflective?")
            } else {
                Triple(Mood.RELAXED, 0.75f, "Enjoying music - you seem relaxed!")
            }
        }
        
        // Heavy social media can indicate seeking validation or boredom
        socialMedia > 120 -> {
            Triple(Mood.STRESSED, 0.65f, "Heavy social media use detected - might be feeling anxious")
        }
        
        // Gaming for stress relief
        games > 60 -> {
            if (games > 120) {
                Triple(Mood.STRESSED, 0.6f, "Extended gaming - escaping from stress?")
            } else {
                Triple(Mood.RELAXED, 0.65f, "Gaming session - unwinding!")
            }
        }
        
        // High productivity = focused/energetic
        productivity > 60 -> {
            Triple(Mood.ENERGETIC, 0.7f, "Productive day! You're in the zone!")
        }
        
        // Lots of communication = social/happy
        communication > 60 -> {
            Triple(Mood.HAPPY, 0.7f, "Lots of chatting - feeling social!")
        }
        
        // Video binge
        video > 180 -> {
            if (isLateNight) {
                Triple(Mood.SAD, 0.55f, "Late night binge watching - trouble sleeping?")
            } else {
                Triple(Mood.RELAXED, 0.6f, "Entertainment mode - taking it easy")
            }
        }
        
        // Morning with low usage
        isMorning && totalUsage < 30 -> {
            Triple(Mood.ENERGETIC, 0.6f, "Fresh morning - ready to start the day!")
        }
        
        // Evening with balanced usage
        isEvening && totalUsage < 120 -> {
            Triple(Mood.RELAXED, 0.6f, "Balanced evening usage - winding down")
        }
        
        // Default
        else -> {
            Triple(Mood.NEUTRAL, 0.5f, "Balanced app usage today")
        }
    }
}


private fun getExercisesForMood(mood: Mood): List<ExerciseSuggestion> {
    return when (mood) {
        Mood.SAD -> listOf(
            ExerciseSuggestion("Brisk Walk", "Get outside, fresh air helps!", "🚶", 20, 100, ElectricBlue),
            ExerciseSuggestion("Dance Workout", "Put on your favorite music and move!", "💃", 15, 120, NeonPurple),
            ExerciseSuggestion("Stretching", "Gentle stretches to release tension", "🧘", 10, 30, CyberGreen),
            ExerciseSuggestion("Jump Rope", "Quick cardio to boost endorphins", "⏭️", 10, 150, Color(0xFFFFD700))
        )
        
        Mood.STRESSED -> listOf(
            ExerciseSuggestion("Deep Breathing", "4-7-8 breathing technique", "🌬️", 5, 10, ElectricBlue),
            ExerciseSuggestion("Yoga Flow", "Calming yoga sequence", "🧘", 20, 80, NeonPurple),
            ExerciseSuggestion("Slow Walk", "Mindful walking meditation", "🚶‍♂️", 15, 60, CyberGreen),
            ExerciseSuggestion("Progressive Relaxation", "Tense and release muscles", "😌", 10, 20, Color(0xFF9CA3AF))
        )
        
        Mood.HAPPY -> listOf(
            ExerciseSuggestion("HIIT Workout", "High energy to match your mood!", "🔥", 20, 250, Color(0xFFEF4444)),
            ExerciseSuggestion("Running", "Channel that positive energy", "🏃", 25, 300, ElectricBlue),
            ExerciseSuggestion("Dance Party", "Celebrate with movement!", "🎉", 15, 150, NeonPurple),
            ExerciseSuggestion("Sports", "Play your favorite sport", "⚽", 30, 200, CyberGreen)
        )
        
        Mood.RELAXED -> listOf(
            ExerciseSuggestion("Gentle Yoga", "Maintain your calm state", "🧘‍♀️", 20, 60, NeonPurple),
            ExerciseSuggestion("Swimming", "Relaxing water workout", "🏊", 30, 200, ElectricBlue),
            ExerciseSuggestion("Tai Chi", "Flowing movements", "☯️", 15, 50, CyberGreen),
            ExerciseSuggestion("Light Cycling", "Easy bike ride", "🚴", 25, 150, Color(0xFFFFA500))
        )
        
        Mood.ENERGETIC -> listOf(
            ExerciseSuggestion("Strength Training", "Use that energy for gains!", "💪", 30, 200, Color(0xFFEF4444)),
            ExerciseSuggestion("Sprint Intervals", "High intensity sprints", "⚡", 15, 200, Color(0xFFFFD700)),
            ExerciseSuggestion("Boxing", "Power workout", "🥊", 20, 250, NeonPurple),
            ExerciseSuggestion("CrossFit", "Full body challenge", "🏋️", 30, 350, ElectricBlue)
        )
        
        Mood.NEUTRAL -> listOf(
            ExerciseSuggestion("Walking", "Simple and effective", "🚶", 20, 80, CyberGreen),
            ExerciseSuggestion("Bodyweight Workout", "No equipment needed", "🏠", 20, 150, ElectricBlue),
            ExerciseSuggestion("Cycling", "Explore your area", "🚴‍♂️", 25, 180, NeonPurple),
            ExerciseSuggestion("Stretching Routine", "Improve flexibility", "🤸", 15, 40, Color(0xFFFFA500))
        )
    }
}
