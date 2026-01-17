package com.healthtracker.presentation.social

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.healthtracker.presentation.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

// Data classes
data class CircleMemberData(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val avatarEmoji: String = listOf("👤", "👨", "👩", "🧑", "👦", "👧", "🧔", "👱").random(),
    val endorsementCount: Int = 0,
    val joinedAt: Long = System.currentTimeMillis(),
    val isCurrentUser: Boolean = false
)

data class CircleData(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val code: String = generateCircleCode(),
    val type: String = "Friends",
    val members: List<CircleMemberData> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

data class EndorsementData(
    val id: String = UUID.randomUUID().toString(),
    val fromUserId: String,
    val toUserId: String,
    val circleId: String,
    val month: String, // Format: "2026-01"
    val timestamp: Long = System.currentTimeMillis()
)

private fun generateCircleCode(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    return (1..8).map { chars.random() }.joinToString("")
}

// SharedPreferences helper
private fun getPrefs(context: Context) = context.getSharedPreferences("social_circles", Context.MODE_PRIVATE)
private val gson = Gson()

private fun saveCircles(context: Context, circles: List<CircleData>) {
    getPrefs(context).edit().putString("circles", gson.toJson(circles)).apply()
}

private fun loadCircles(context: Context): List<CircleData> {
    val json = getPrefs(context).getString("circles", null) ?: return emptyList()
    return try {
        gson.fromJson(json, object : TypeToken<List<CircleData>>() {}.type)
    } catch (e: Exception) { emptyList() }
}

private fun saveEndorsements(context: Context, endorsements: List<EndorsementData>) {
    getPrefs(context).edit().putString("endorsements", gson.toJson(endorsements)).apply()
}

private fun loadEndorsements(context: Context): List<EndorsementData> {
    val json = getPrefs(context).getString("endorsements", null) ?: return emptyList()
    return try {
        gson.fromJson(json, object : TypeToken<List<EndorsementData>>() {}.type)
    } catch (e: Exception) { emptyList() }
}

private fun getCurrentUserId(context: Context): String {
    val prefs = getPrefs(context)
    var userId = prefs.getString("current_user_id", null)
    if (userId == null) {
        userId = UUID.randomUUID().toString()
        prefs.edit().putString("current_user_id", userId).apply()
    }
    return userId
}

private fun getCurrentUserName(context: Context): String {
    return getPrefs(context).getString("current_user_name", null) ?: "You"
}

private fun setCurrentUserName(context: Context, name: String) {
    getPrefs(context).edit().putString("current_user_name", name).apply()
}

private fun getCurrentMonth(): String {
    return YearMonth.now().toString()
}

// Firebase Database Reference
private fun getFirebaseDb() = Firebase.database.reference

// Firebase helper functions
private fun saveCircleToFirebase(circle: CircleData) {
    getFirebaseDb().child("circles").child(circle.id).setValue(circle)
    // Also save by code for easy lookup
    getFirebaseDb().child("circle_codes").child(circle.code).setValue(circle.id)
}

private fun saveEndorsementToFirebase(endorsement: EndorsementData) {
    getFirebaseDb().child("endorsements").child(endorsement.id).setValue(endorsement)
}

private fun updateCircleMembersInFirebase(circleId: String, members: List<CircleMemberData>) {
    getFirebaseDb().child("circles").child(circleId).child("members").setValue(members)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToCircleDetail: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var circles by remember { mutableStateOf<List<CircleData>>(emptyList()) }
    var endorsements by remember { mutableStateOf<List<EndorsementData>>(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showProfileSetup by remember { mutableStateOf(getCurrentUserName(context) == "You") }
    var selectedCircle by remember { mutableStateOf<CircleData?>(null) }
    var joinError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    val currentUserId = remember { getCurrentUserId(context) }
    val currentMonth = remember { getCurrentMonth() }
    
    // Listen to Firebase for circles where current user is a member
    DisposableEffect(currentUserId) {
        val circlesRef = getFirebaseDb().child("circles")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val allCircles = mutableListOf<CircleData>()
                for (circleSnapshot in snapshot.children) {
                    try {
                        val id = circleSnapshot.child("id").getValue(String::class.java) ?: continue
                        val name = circleSnapshot.child("name").getValue(String::class.java) ?: continue
                        val code = circleSnapshot.child("code").getValue(String::class.java) ?: continue
                        val type = circleSnapshot.child("type").getValue(String::class.java) ?: "Friends"
                        val createdAt = circleSnapshot.child("createdAt").getValue(Long::class.java) ?: 0L
                        
                        val members = mutableListOf<CircleMemberData>()
                        for (memberSnapshot in circleSnapshot.child("members").children) {
                            val memberId = memberSnapshot.child("id").getValue(String::class.java) ?: continue
                            val memberName = memberSnapshot.child("name").getValue(String::class.java) ?: "Unknown"
                            val avatarEmoji = memberSnapshot.child("avatarEmoji").getValue(String::class.java) ?: "👤"
                            val joinedAt = memberSnapshot.child("joinedAt").getValue(Long::class.java) ?: 0L
                            members.add(CircleMemberData(
                                id = memberId,
                                name = memberName,
                                avatarEmoji = avatarEmoji,
                                joinedAt = joinedAt,
                                isCurrentUser = memberId == currentUserId
                            ))
                        }
                        
                        // Only add if current user is a member
                        if (members.any { it.id == currentUserId }) {
                            allCircles.add(CircleData(id, name, code, type, members, createdAt))
                        }
                    } catch (e: Exception) { }
                }
                circles = allCircles
                isLoading = false
            }
            override fun onCancelled(error: DatabaseError) { isLoading = false }
        }
        circlesRef.addValueEventListener(listener)
        
        // Timeout - if Firebase doesn't respond in 5 seconds, stop loading
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (isLoading) {
                isLoading = false
            }
        }, 5000)
        
        onDispose { circlesRef.removeEventListener(listener) }
    }
    
    // Listen to endorsements
    DisposableEffect(Unit) {
        val endorsementsRef = getFirebaseDb().child("endorsements")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val allEndorsements = mutableListOf<EndorsementData>()
                for (endorsementSnapshot in snapshot.children) {
                    try {
                        val id = endorsementSnapshot.child("id").getValue(String::class.java) ?: continue
                        val fromUserId = endorsementSnapshot.child("fromUserId").getValue(String::class.java) ?: continue
                        val toUserId = endorsementSnapshot.child("toUserId").getValue(String::class.java) ?: continue
                        val circleId = endorsementSnapshot.child("circleId").getValue(String::class.java) ?: continue
                        val month = endorsementSnapshot.child("month").getValue(String::class.java) ?: continue
                        val timestamp = endorsementSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                        allEndorsements.add(EndorsementData(id, fromUserId, toUserId, circleId, month, timestamp))
                    } catch (e: Exception) { }
                }
                endorsements = allEndorsements
            }
            override fun onCancelled(error: DatabaseError) { }
        }
        endorsementsRef.addValueEventListener(listener)
        onDispose { endorsementsRef.removeEventListener(listener) }
    }
    
    // Calculate total endorsements received by current user
    val myEndorsements = endorsements.count { it.toUserId == currentUserId }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Health Circles", fontWeight = FontWeight.Bold, color = Color.White) },
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
                .background(Brush.verticalGradient(listOf(Color(0xFF0D0D1A), Color(0xFF1A1A2E), Color(0xFF16213E))))
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = NeonPurple
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // My Profile Card with Endorsements
                    item {
                        MyProfileCard(
                            userName = getCurrentUserName(context),
                            visibleUserId = currentUserId.take(8),
                            endorsementCount = myEndorsements,
                            onEditName = { showProfileSetup = true }
                        )
                    }
                    
                    // My Circles
                    item {
                        Text("👥 My Circles", style = MaterialTheme.typography.titleMedium,
                            color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    
                    if (circles.isEmpty()) {
                        item {
                            EmptyCirclesCard(
                                onCreateClick = { showCreateDialog = true },
                                onJoinClick = { showJoinDialog = true }
                            )
                        }
                    } else {
                        items(circles) { circle ->
                            CircleCard(
                                circle = circle,
                                endorsements = endorsements,
                                currentUserId = currentUserId,
                                onClick = { selectedCircle = circle }
                            )
                        }
                    }
                    
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    // Dialogs
    if (showProfileSetup) {
        ProfileSetupDialog(
            currentName = getCurrentUserName(context),
            onDismiss = { showProfileSetup = false },
            onSave = { name ->
                setCurrentUserName(context, name)
                showProfileSetup = false
            }
        )
    }
    
    if (showCreateDialog) {
        CreateCircleDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, type ->
                val currentUser = CircleMemberData(
                    id = currentUserId,
                    name = getCurrentUserName(context),
                    isCurrentUser = true
                )
                val newCircle = CircleData(
                    name = name,
                    type = type,
                    members = listOf(currentUser)
                )
                // Save to Firebase
                saveCircleToFirebase(newCircle)
                showCreateDialog = false
            }
        )
    }
    
    if (showJoinDialog) {
        JoinCircleDialog(
            errorMessage = joinError,
            onDismiss = { showJoinDialog = false; joinError = null },
            onJoin = { code ->
                // Look up circle by code in Firebase
                getFirebaseDb().child("circle_codes").child(code.uppercase())
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val circleId = snapshot.getValue(String::class.java)
                        if (circleId != null) {
                            // Get the circle and add member
                            getFirebaseDb().child("circles").child(circleId)
                                .get()
                                .addOnSuccessListener { circleSnapshot ->
                                    val existingMembers = mutableListOf<CircleMemberData>()
                                    for (memberSnapshot in circleSnapshot.child("members").children) {
                                        val memberId = memberSnapshot.child("id").getValue(String::class.java) ?: continue
                                        val memberName = memberSnapshot.child("name").getValue(String::class.java) ?: "Unknown"
                                        val avatarEmoji = memberSnapshot.child("avatarEmoji").getValue(String::class.java) ?: "👤"
                                        val joinedAt = memberSnapshot.child("joinedAt").getValue(Long::class.java) ?: 0L
                                        existingMembers.add(CircleMemberData(memberId, memberName, avatarEmoji, 0, joinedAt, memberId == currentUserId))
                                    }
                                    
                                    // Check if already member
                                    if (existingMembers.none { it.id == currentUserId }) {
                                        val newMember = CircleMemberData(
                                            id = currentUserId,
                                            name = getCurrentUserName(context),
                                            isCurrentUser = true
                                        )
                                        val updatedMembers = existingMembers + newMember
                                        updateCircleMembersInFirebase(circleId, updatedMembers)
                                        showJoinDialog = false
                                        joinError = null
                                    } else {
                                        joinError = "You're already in this circle!"
                                    }
                                }
                        } else {
                            joinError = "Invalid code! Circle not found."
                        }
                    }
                    .addOnFailureListener {
                        joinError = "Error joining circle. Try again."
                    }
            }
        )
    }
    
    // Circle Detail Dialog
    if (selectedCircle != null) {
        // Refresh selected circle from list
        val updatedCircle = circles.find { it.id == selectedCircle!!.id } ?: selectedCircle!!
        
        CircleDetailDialog(
            circle = updatedCircle,
            endorsements = endorsements,
            currentUserId = currentUserId,
            currentMonth = currentMonth,
            onDismiss = { selectedCircle = null },
            onEndorse = { memberId ->
                // Check if already endorsed this month
                val alreadyEndorsed = endorsements.any { 
                    it.fromUserId == currentUserId && it.month == currentMonth 
                }
                if (!alreadyEndorsed && memberId != currentUserId) {
                    val newEndorsement = EndorsementData(
                        fromUserId = currentUserId,
                        toUserId = memberId,
                        circleId = updatedCircle.id,
                        month = currentMonth
                    )
                    saveEndorsementToFirebase(newEndorsement)
                }
            },
            onLeaveCircle = {
                val updatedMembers = updatedCircle.members.filter { it.id != currentUserId }
                if (updatedMembers.isEmpty()) {
                    // Delete circle if no members left
                    getFirebaseDb().child("circles").child(updatedCircle.id).removeValue()
                    getFirebaseDb().child("circle_codes").child(updatedCircle.code).removeValue()
                } else {
                    updateCircleMembersInFirebase(updatedCircle.id, updatedMembers)
                }
                selectedCircle = null
            }
        )
    }
}

@Composable
private fun MyProfileCard(userName: String, visibleUserId: String, endorsementCount: Int, onEditName: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar - Fixed size
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(NeonPurple.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👤", fontSize = 30.sp)
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Name and ID
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = userName, 
                            fontWeight = FontWeight.Bold, 
                            color = Color.White, 
                            fontSize = 20.sp,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onEditName, 
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit, 
                                null, 
                                tint = Color.White.copy(alpha = 0.5f), 
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = "ID: $visibleUserId...", 
                        color = Color.White.copy(alpha = 0.4f), 
                        fontSize = 12.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Endorsement Badge
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (endorsementCount > 0) CyberGreen.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("⭐", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "$endorsementCount Endorsements",
                            fontWeight = FontWeight.Bold,
                            color = if (endorsementCount > 0) CyberGreen else Color.White,
                            fontSize = 18.sp
                        )
                        Text(
                            if (endorsementCount > 0) "People trust your health journey!" 
                            else "Get endorsed by circle members",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCirclesCard(onCreateClick: () -> Unit, onJoinClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("👥", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text("No Circles Yet", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Create or join a circle to connect with friends!", 
                color = Color.White.copy(alpha = 0.6f), 
                textAlign = TextAlign.Center, 
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            
            // Buttons in a proper row with spacing
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onJoinClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricBlue),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Join Circle")
                }
                
                Button(
                    onClick = onCreateClick,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create")
                }
            }
        }
    }
}

@Composable
private fun CircleCard(
    circle: CircleData,
    endorsements: List<EndorsementData>,
    currentUserId: String,
    onClick: () -> Unit
) {
    val typeColor = when (circle.type) {
        "Friends" -> CyberGreen
        "Family" -> Color(0xFFE91E63)
        "Corporate" -> ElectricBlue
        else -> NeonPurple
    }
    val typeEmoji = when (circle.type) {
        "Friends" -> "👫"
        "Family" -> "👨‍👩‍👧‍👦"
        "Corporate" -> "🏢"
        else -> "👥"
    }
    
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = typeColor.copy(alpha = 0.2f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(typeEmoji, fontSize = 24.sp)
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(circle.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                    Text("${circle.members.size} members • ${circle.type}", 
                        color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                }
                
                Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.5f))
            }
            
            // Member avatars preview
            if (circle.members.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    items(circle.members.take(5)) { member ->
                        val memberEndorsements = endorsements.count { it.toUserId == member.id }
                        Box {
                            Surface(
                                shape = CircleShape,
                                color = if (member.isCurrentUser) NeonPurple else Color(0xFF374151),
                                modifier = Modifier.size(36.dp).border(2.dp, Color(0xFF1E293B), CircleShape)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(member.avatarEmoji, fontSize = 18.sp)
                                }
                            }
                            if (memberEndorsements > 0) {
                                Surface(
                                    shape = CircleShape,
                                    color = CyberGreen,
                                    modifier = Modifier.size(16.dp).align(Alignment.BottomEnd)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Text("$memberEndorsements", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    if (circle.members.size > 5) {
                        item {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF374151),
                                modifier = Modifier.size(36.dp).border(2.dp, Color(0xFF1E293B), CircleShape)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text("+${circle.members.size - 5}", fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CircleDetailDialog(
    circle: CircleData,
    endorsements: List<EndorsementData>,
    currentUserId: String,
    currentMonth: String,
    onDismiss: () -> Unit,
    onEndorse: (String) -> Unit,
    onLeaveCircle: () -> Unit
) {
    val hasEndorsedThisMonth = endorsements.any { 
        it.fromUserId == currentUserId && it.month == currentMonth 
    }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NeonPurple.copy(alpha = 0.2f))
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(circle.name, style = MaterialTheme.typography.headlineSmall,
                                color = Color.White, fontWeight = FontWeight.Bold)
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, "Close", tint = Color.White)
                            }
                        }
                        
                        Text("${circle.members.size} members • ${circle.type}", 
                            color = Color.White.copy(alpha = 0.7f))
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Join Code
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Key, null, tint = CyberGreen, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Join Code: ", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                                Text(circle.code, color = CyberGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                        
                        // Endorsement status
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (hasEndorsedThisMonth) Color(0xFFFFA500).copy(alpha = 0.2f) else CyberGreen.copy(alpha = 0.2f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (hasEndorsedThisMonth) "⏳" else "⭐", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (hasEndorsedThisMonth) "You've used your monthly endorsement"
                                    else "You can endorse 1 member this month!",
                                    color = if (hasEndorsedThisMonth) Color(0xFFFFA500) else CyberGreen,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Members List
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text("👥 Members", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                    }
                    
                    items(circle.members.sortedByDescending { 
                        endorsements.count { e -> e.toUserId == it.id }
                    }) { member ->
                        val memberEndorsements = endorsements.count { it.toUserId == member.id }
                        val canEndorse = !hasEndorsedThisMonth && member.id != currentUserId
                        
                        MemberCard(
                            member = member,
                            endorsementCount = memberEndorsements,
                            canEndorse = canEndorse,
                            isCurrentUser = member.id == currentUserId,
                            onEndorse = { onEndorse(member.id) }
                        )
                    }
                }
                
                // Leave button
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE53935).copy(alpha = 0.1f)
                ) {
                    TextButton(
                        onClick = { showLeaveConfirm = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ExitToApp, null, tint = Color(0xFFE53935))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Leave Circle", color = Color(0xFFE53935))
                    }
                }
            }
        }
    }
    
    // Leave confirmation
    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Leave Circle?") },
            text = { Text("Are you sure you want to leave ${circle.name}?") },
            confirmButton = {
                Button(
                    onClick = { onLeaveCircle(); showLeaveConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) { Text("Leave") }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MemberCard(
    member: CircleMemberData,
    endorsementCount: Int,
    canEndorse: Boolean,
    isCurrentUser: Boolean,
    onEndorse: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentUser) NeonPurple.copy(alpha = 0.15f) else Color(0xFF374151)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with endorsement badge
            Box {
                Surface(
                    shape = CircleShape,
                    color = if (isCurrentUser) NeonPurple.copy(alpha = 0.3f) else Color(0xFF4B5563),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(member.avatarEmoji, fontSize = 24.sp)
                    }
                }
                if (endorsementCount > 0) {
                    Surface(
                        shape = CircleShape,
                        color = CyberGreen,
                        modifier = Modifier.size(20.dp).align(Alignment.BottomEnd)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text("$endorsementCount", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(member.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                    if (isCurrentUser) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(4.dp), color = NeonPurple) {
                            Text("You", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp, color = Color.White)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⭐", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("$endorsementCount endorsements", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                }
            }
            
            // Endorse button
            if (canEndorse) {
                Button(
                    onClick = onEndorse,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberGreen),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Endorse", fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSetupDialog(currentName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(if (currentName == "You") "" else currentName) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Your Name") },
        text = {
            Column {
                Text("Enter your name to be shown in circles", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onSave(name) },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateCircleDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("Friends") }
    var expanded by remember { mutableStateOf(false) }
    val types = listOf("Friends", "Family", "Corporate")
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Circle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Circle Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        types.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = { selectedType = type; expanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onCreate(name, selectedType) },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun JoinCircleDialog(errorMessage: String? = null, onDismiss: () -> Unit, onJoin: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join Circle") },
        text = {
            Column {
                Text("Enter the 8-character code shared by circle owner", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 8) code = it.uppercase() },
                    label = { Text("Join Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    isError = errorMessage != null
                )
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorMessage, color = Color(0xFFE53935), fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onJoin(code) },
                enabled = code.length == 8
            ) { Text("Join") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
