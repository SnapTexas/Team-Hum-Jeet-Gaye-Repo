package com.healthtracker.presentation.avatar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import android.os.Build
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.healthtracker.domain.model.AvatarResponse
import com.healthtracker.domain.model.AvatarState
import com.healthtracker.presentation.theme.CyberGreen
import com.healthtracker.presentation.theme.ElectricBlue
import com.healthtracker.presentation.theme.GlassSurface
import com.healthtracker.presentation.theme.GlowWhite
import com.healthtracker.presentation.theme.HealthTrackerTheme
import com.healthtracker.presentation.theme.NeonPurple

/**
 * Main Avatar screen with full chat interface.
 */
@Composable
fun AvatarScreen(
    viewModel: AvatarViewModel = hiltViewModel(),
    onDismiss: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    
    AvatarContent(
        uiState = uiState,
        onQuerySubmit = viewModel::submitQuery,
        onSuggestionClick = viewModel::submitQuery,
        onVoiceInput = viewModel::startVoiceInput,
        onDismiss = onDismiss
    )
}

@Composable
private fun AvatarContent(
    uiState: AvatarUiState,
    onQuerySubmit: (String) -> Unit,
    onSuggestionClick: (String) -> Unit,
    onVoiceInput: () -> Unit,
    onDismiss: () -> Unit
) {
    val gradientBackground = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0D0D1A).copy(alpha = 0.95f),
            Color(0xFF1A1A2E).copy(alpha = 0.95f),
            Color(0xFF16213E).copy(alpha = 0.95f)
        )
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBackground)
    ) {
        // Floating orb effects
        FloatingAvatarOrbs()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header with close button
            AvatarHeader(onDismiss = onDismiss)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Avatar visualization
            AvatarVisualization(
                state = uiState.avatarState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.3f)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Chat messages
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.5f),
                reverseLayout = true,
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Current response
                uiState.currentResponse?.let { response ->
                    item {
                        AvatarMessageBubble(response = response)
                    }
                }
                
                // Conversation history
                items(uiState.conversationHistory.reversed()) { response ->
                    AvatarMessageBubble(response = response)
                }
            }
            
            // Suggestions
            if (uiState.suggestions.isNotEmpty()) {
                SuggestionsRow(
                    suggestions = uiState.suggestions,
                    onSuggestionClick = onSuggestionClick
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Input area
            AvatarInputArea(
                query = uiState.currentQuery,
                isProcessing = uiState.avatarState == AvatarState.PROCESSING,
                onQueryChange = { /* handled by viewModel */ },
                onQuerySubmit = onQuerySubmit,
                onVoiceInput = onVoiceInput
            )
        }
    }
}

@Composable
private fun FloatingAvatarOrbs() {
    Box(modifier = Modifier.fillMaxSize()) {
        // Pulsing electric blue orb
        val infiniteTransition = rememberInfiniteTransition(label = "orb")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
        
        Box(
            modifier = Modifier
                .size(180.dp)
                .scale(scale)
                .blur(70.dp)
                .background(
                    ElectricBlue.copy(alpha = 0.2f),
                    CircleShape
                )
                .align(Alignment.TopCenter)
        )
        
        Box(
            modifier = Modifier
                .size(120.dp)
                .blur(50.dp)
                .background(
                    NeonPurple.copy(alpha = 0.15f),
                    CircleShape
                )
                .align(Alignment.BottomEnd)
        )
    }
}

@Composable
private fun AvatarHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Health Assistant",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(GlassSurface)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = GlowWhite
            )
        }
    }
}

@Composable
private fun AvatarVisualization(
    state: AvatarState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Glow effect behind avatar
        val glowAlpha by animateFloatAsState(
            targetValue = when (state) {
                AvatarState.PROCESSING -> 0.6f
                AvatarState.RESPONDING -> 0.5f
                AvatarState.LISTENING -> 0.7f
                else -> 0.3f
            },
            animationSpec = tween(500),
            label = "glow"
        )
        
        Box(
            modifier = Modifier
                .size(160.dp)
                .blur(40.dp)
                .background(
                    ElectricBlue.copy(alpha = glowAlpha),
                    CircleShape
                )
        )
        
        // Avatar container with glassmorphism
        GlassmorphicAvatarCard(
            modifier = Modifier.size(140.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // State indicator
                when (state) {
                    AvatarState.PROCESSING -> {
                        CircularProgressIndicator(
                            color = ElectricBlue,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(60.dp)
                        )
                    }
                    AvatarState.LISTENING -> {
                        PulsingMicIcon()
                    }
                    else -> {
                        // Animated GIF Avatar
                        val context = LocalContext.current
                        val imageLoader = remember {
                            ImageLoader.Builder(context)
                                .components {
                                    if (Build.VERSION.SDK_INT >= 28) {
                                        add(ImageDecoderDecoder.Factory())
                                    } else {
                                        add(GifDecoder.Factory())
                                    }
                                }
                                .build()
                        }
                        
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(com.healthtracker.R.drawable.avatar_animation)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Avatar Animation",
                            imageLoader = imageLoader,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                        )
                    }
                }
            }
        }
        
        // State label
        Text(
            text = when (state) {
                AvatarState.MINIMIZED -> "Tap to expand"
                AvatarState.EXPANDED -> "Ask me anything!"
                AvatarState.LISTENING -> "Listening..."
                AvatarState.PROCESSING -> "Thinking..."
                AvatarState.RESPONDING -> "Here's what I found"
                AvatarState.IDLE -> "Ready to help"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = GlowWhite,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(top = 8.dp)
        )
    }
}

@Composable
private fun PulsingMicIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "mic")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micScale"
    )
    
    Icon(
        imageVector = Icons.Default.Mic,
        contentDescription = "Listening",
        tint = ElectricBlue,
        modifier = Modifier
            .size(60.dp)
            .scale(scale)
    )
}

@Composable
private fun GlassmorphicAvatarCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .shadow(
                elevation = 16.dp,
                shape = CircleShape,
                ambientColor = ElectricBlue.copy(alpha = 0.4f),
                spotColor = ElectricBlue.copy(alpha = 0.4f)
            )
            .border(
                width = 2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        ElectricBlue.copy(alpha = 0.6f),
                        NeonPurple.copy(alpha = 0.3f)
                    )
                ),
                shape = CircleShape
            ),
        shape = CircleShape,
        colors = CardDefaults.cardColors(
            containerColor = GlassSurface
        )
    ) {
        content()
    }
}

@Composable
private fun AvatarMessageBubble(response: AvatarResponse) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = ElectricBlue.copy(alpha = 0.2f)
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        ElectricBlue.copy(alpha = 0.4f),
                        ElectricBlue.copy(alpha = 0.1f)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = GlassSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = response.text,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                lineHeight = 22.sp
            )
            
            // Show metrics if available
            response.metrics?.let { metrics ->
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(metrics) { metric ->
                        MetricChip(
                            label = metric.type.name.lowercase().replaceFirstChar { it.uppercase() },
                            value = metric.formattedValue
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricChip(
    label: String,
    value: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(ElectricBlue.copy(alpha = 0.2f))
            .border(
                width = 1.dp,
                color = ElectricBlue.copy(alpha = 0.5f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = ElectricBlue
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun SuggestionsRow(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(suggestions) { suggestion ->
            SuggestionChip(
                text = suggestion,
                onClick = { onSuggestionClick(suggestion) }
            )
        }
    }
}

@Composable
private fun SuggestionChip(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(NeonPurple.copy(alpha = 0.2f))
            .border(
                width = 1.dp,
                color = NeonPurple.copy(alpha = 0.5f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = GlowWhite
        )
    }
}


@Composable
private fun AvatarInputArea(
    query: String,
    isProcessing: Boolean,
    onQueryChange: (String) -> Unit,
    onQuerySubmit: (String) -> Unit,
    onVoiceInput: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = ElectricBlue.copy(alpha = 0.3f)
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        ElectricBlue.copy(alpha = 0.5f),
                        NeonPurple.copy(alpha = 0.3f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = GlassSurface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Voice input button
            IconButton(
                onClick = onVoiceInput,
                enabled = !isProcessing,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(NeonPurple.copy(alpha = 0.2f))
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice input",
                    tint = if (isProcessing) GlowWhite.copy(alpha = 0.5f) else NeonPurple
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Text input
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = "Ask about your health...",
                        color = GlowWhite.copy(alpha = 0.5f)
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = ElectricBlue,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputText.isNotBlank() && !isProcessing) {
                            onQuerySubmit(inputText)
                            inputText = ""
                        }
                    }
                ),
                singleLine = true,
                enabled = !isProcessing
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Send button
            IconButton(
                onClick = {
                    if (inputText.isNotBlank() && !isProcessing) {
                        onQuerySubmit(inputText)
                        inputText = ""
                    }
                },
                enabled = inputText.isNotBlank() && !isProcessing,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (inputText.isNotBlank() && !isProcessing)
                            ElectricBlue.copy(alpha = 0.3f)
                        else
                            GlowWhite.copy(alpha = 0.1f)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = if (inputText.isNotBlank() && !isProcessing)
                        ElectricBlue
                    else
                        GlowWhite.copy(alpha = 0.5f)
                )
            }
        }
    }
}

/**
 * Floating avatar button for overlay mode.
 */
@Composable
fun FloatingAvatarButton(
    state: AvatarState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "float")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )
    
    Box(
        modifier = modifier
            .size(64.dp)
            .shadow(
                elevation = 12.dp,
                shape = CircleShape,
                ambientColor = ElectricBlue.copy(alpha = 0.5f),
                spotColor = ElectricBlue.copy(alpha = 0.5f)
            )
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E)
                    )
                )
            )
            .border(
                width = 2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        ElectricBlue.copy(alpha = 0.7f),
                        NeonPurple.copy(alpha = 0.5f)
                    )
                ),
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Glow effect
        Box(
            modifier = Modifier
                .size(48.dp)
                .blur(16.dp)
                .background(
                    ElectricBlue.copy(alpha = 0.4f),
                    CircleShape
                )
        )
        
        // Animated GIF Avatar
        val imageLoader = remember {
            ImageLoader.Builder(context)
                .components {
                    if (Build.VERSION.SDK_INT >= 28) {
                        add(ImageDecoderDecoder.Factory())
                    } else {
                        add(GifDecoder.Factory())
                    }
                }
                .build()
        }
        
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(com.healthtracker.R.drawable.avatar_animation)
                .crossfade(true)
                .build(),
            contentDescription = "Avatar",
            imageLoader = imageLoader,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D1A)
@Composable
private fun AvatarScreenPreview() {
    HealthTrackerTheme {
        AvatarContent(
            uiState = AvatarUiState(),
            onQuerySubmit = {},
            onSuggestionClick = {},
            onVoiceInput = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D1A)
@Composable
private fun FloatingAvatarButtonPreview() {
    HealthTrackerTheme {
        FloatingAvatarButton(
            state = AvatarState.MINIMIZED,
            onClick = {}
        )
    }
}
