package com.audiora.feature.welcome

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiora.ui.theme.LocalDarkTheme
import com.audiora.domain.repository.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun SwirlingBackground(modifier: Modifier = Modifier) {
    val isDark = LocalDarkTheme.current
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        
        // Soft gradient across background mirroring welcome_page.png
        drawRect(
            brush = Brush.verticalGradient(
                colors = if (isDark) {
                    listOf(
                        Color(0xFF0A0712),  // Rich cosmic back-canvas
                        Color(0xFF110B22),  // Deep space violet
                        Color(0xFF160E2A)   // Ambient dark slate
                    )
                } else {
                    listOf(
                        Color(0xFFF9F5FE),  // Very soft lavender white top
                        Color(0xFFF1E6FC),  // Dreamy soft lilac middle
                        Color(0xFFECE0FA)   // Rich violet hint at bottom
                    )
                }
            ),
            size = size
        )
        
        // 3 layers of elegant swirling waves on left & middle
        val swirlPath1 = Path().apply {
            moveTo(-w * 0.2f, h * 0.1f)
            cubicTo(
                w * 0.7f, h * 0.18f,
                w * 0.5f, h * 0.65f,
                -w * 0.2f, h * 0.85f
            )
        }
        
        val swirlPath2 = Path().apply {
            moveTo(-w * 0.1f, h * 0.18f)
            cubicTo(
                w * 0.55f, h * 0.24f,
                w * 0.42f, h * 0.58f,
                -w * 0.1f, h * 0.76f
            )
        }
        
        val swirlPath3 = Path().apply {
            moveTo(-w * 0.15f, h * 0.26f)
            cubicTo(
                w * 0.38f, h * 0.3f,
                w * 0.28f, h * 0.52f,
                -w * 0.15f, h * 0.66f
            )
        }
        
        // Draw the outer soft glow swirl
        drawPath(
            swirlPath1,
            brush = Brush.linearGradient(
                colors = if (isDark) {
                    listOf(Color(0xFF8B5CF6).copy(alpha = 0.08f), Color(0xFFEC4899).copy(alpha = 0.04f))
                } else {
                    listOf(Color(0xFFD8B4FE).copy(alpha = 0.25f), Color(0xFFF472B6).copy(alpha = 0.12f))
                }
            ),
            style = Stroke(width = w * 0.32f, cap = StrokeCap.Round)
        )
        
        // Draw the middle main swirl
        drawPath(
            swirlPath2,
            brush = Brush.linearGradient(
                colors = if (isDark) {
                    listOf(Color(0xFF7C3AED).copy(alpha = 0.14f), Color(0xFF4C1D95).copy(alpha = 0.18f))
                } else {
                    listOf(Color(0xFFC084FC).copy(alpha = 0.45f), Color(0xFFE9D5FF).copy(alpha = 0.6f))
                }
            ),
            style = Stroke(width = w * 0.15f, cap = StrokeCap.Round)
        )
        
        // Draw the inner accent swirl
        drawPath(
            swirlPath3,
            brush = Brush.linearGradient(
                colors = if (isDark) {
                    listOf(Color(0xFF8B5CF6).copy(alpha = 0.20f), Color(0xFF1E1B29).copy(alpha = 0.25f))
                } else {
                    listOf(Color(0xFFA855F7).copy(alpha = 0.65f), Color(0xFFE9D5FF).copy(alpha = 0.85f))
                }
            ),
            style = Stroke(width = w * 0.05f, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun AudioraLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        
        val centerX = w * 0.5f
        val bookTopY = h * 0.52f
        val bookBottomY = h * 0.82f
        val bookCenterY = h * 0.72f
        
        // Ambient purple glowing soft drop-shadow
        drawCircle(
            color = Color(0xFF7C3AED).copy(alpha = 0.14f),
            radius = w * 0.42f,
            center = androidx.compose.ui.geometry.Offset(centerX, bookCenterY)
        )
        
        // Left page of the book
        val leftPage = Path().apply {
            moveTo(centerX, bookCenterY)
            cubicTo(
                w * 0.35f, bookTopY - h * 0.09f,
                w * 0.14f, bookTopY - h * 0.06f,
                w * 0.06f, bookTopY + h * 0.04f
            )
            lineTo(w * 0.06f, bookBottomY)
            cubicTo(
                w * 0.14f, bookBottomY - h * 0.06f,
                w * 0.35f, bookBottomY - h * 0.09f,
                centerX, bookBottomY + h * 0.04f
            )
            close()
        }
        
        // Right page of the book
        val rightPage = Path().apply {
            moveTo(centerX, bookCenterY)
            cubicTo(
                w * 0.65f, bookTopY - h * 0.09f,
                w * 0.86f, bookTopY - h * 0.06f,
                w * 0.94f, bookTopY + h * 0.04f
            )
            lineTo(w * 0.94f, bookBottomY)
            cubicTo(
                w * 0.86f, bookBottomY - h * 0.06f,
                w * 0.65f, bookBottomY - h * 0.09f,
                centerX, bookBottomY + h * 0.04f
            )
            close()
        }
        
        // Rich high-fidelity gradients matching logo.png exactly
        val pageBrushLeft = Brush.linearGradient(
            colors = listOf(Color(0xFFD8B4FE), Color(0xFF7C3AED)),
            start = androidx.compose.ui.geometry.Offset(0f, h * 0.4f),
            end = androidx.compose.ui.geometry.Offset(centerX, h * 0.9f)
        )
        val pageBrushRight = Brush.linearGradient(
            colors = listOf(Color(0xFF7C3AED), Color(0xFFD8B4FE)),
            start = androidx.compose.ui.geometry.Offset(centerX, h * 0.4f),
            end = androidx.compose.ui.geometry.Offset(w, h * 0.9f)
        )
        
        drawPath(leftPage, brush = pageBrushLeft)
        drawPath(rightPage, brush = pageBrushRight)
        
        // Left overlay edge accent for 3D page curl feel
        val leftEdgeAccent = Path().apply {
            moveTo(w * 0.06f, bookTopY + h * 0.04f)
            lineTo(w * 0.06f, bookBottomY)
        }
        drawPath(
            leftEdgeAccent,
            color = Color.White.copy(alpha = 0.4f),
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
        
        // Center spine white highlight
        val spineHighlight = Path().apply {
            moveTo(centerX, bookCenterY - 2f)
            lineTo(centerX, bookBottomY + h * 0.04f - 2f)
        }
        drawPath(
            spineHighlight,
            color = Color.White.copy(alpha = 0.7f),
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )
        
        // 5 Vertical Soundwave bars rising from book split
        val barCount = 5
        val barWidth = w * 0.045f
        val barSpacing = w * 0.075f
        
        // Progressive amplitude heights mimicking logo.png (0.15f to 0.42f)
        val heights = floatArrayOf(0.18f, 0.32f, 0.42f, 0.28f, 0.14f)
        val barColors = listOf(
            Color(0xFFC084FC),
            Color(0xFFA855F7),
            Color(0xFF7C3AED), // center prominent bar
            Color(0xFF9333EA),
            Color(0xFFC084FC)
        )
        
        val startX = centerX - ((barCount - 1) * barSpacing) / 2f
        for (i in 0 until barCount) {
            val x = startX + i * barSpacing
            val barAmp = h * 0.4f * heights[i]
            val barTopY = bookTopY - barAmp - h * 0.04f
            val barBottomY = bookTopY - h * 0.04f
            
            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(barColors[i], barColors[i].copy(alpha = 0.4f))
                ),
                start = androidx.compose.ui.geometry.Offset(x, barTopY),
                end = androidx.compose.ui.geometry.Offset(x, barBottomY),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun WelcomeScreen(
    settingsRepository: SettingsRepository,
    onNavigateToMain: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    
    // Scale animations for delightful entry transitions
    val logoScale = remember { Animatable(0.7f) }
    val contentAlpha = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        logoScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }
    
    LaunchedEffect(Unit) {
        delay(150)
        contentAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 650, easing = EaseOutQuad)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("welcome_screen")
    ) {
        // 1. Swirling Swirls Gradient canvas Background
        SwirlingBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Margin Spacer
            Spacer(modifier = Modifier.height(30.dp))

            // 2. Centered Logo and Brand Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                // Book Soundwave Logo container
                AudioraLogo(
                    modifier = Modifier
                        .size(170.dp)
                        .scale(logoScale.value)
                        .padding(bottom = 12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Typography pairings matching logo.png and welcome_page.png: "Audio" in rich purple, "ora" in glowing light purple gradient
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.scale(contentAlpha.value)
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(color = Color(0xFF5B21B6), fontWeight = FontWeight.Bold)) {
                                append("Audi")
                            }
                            withStyle(style = SpanStyle(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFF7C3AED), Color(0xFFC084FC))
                                ),
                                fontWeight = FontWeight.SemiBold
                            )) {
                                append("ora")
                            }
                        },
                        fontSize = 44.sp,
                        letterSpacing = (-1.5).sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // First subtle Slogan Text "Listen. Edit. Create."
                Text(
                    text = "Listen. Edit. Create.",
                    color = Color(0xFF1E1B29),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.scale(contentAlpha.value)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Subtitle: "Your complete audiobook studio"
                Text(
                    text = "Your complete\naudiobook studio",
                    color = Color(0xFF1C1A24).copy(alpha = 0.8f),
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .scale(contentAlpha.value)
                )
            }

            // 3. Elegant Bottom Capsule Button "Get Started" with arrow pointing right
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 54.dp)
                    .scale(contentAlpha.value)
            ) {
                val buttonInteractionSource = remember { MutableInteractionSource() }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .height(58.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = CircleShape,
                            clip = false,
                            ambientColor = Color(0xFF7C3AED).copy(alpha = 0.35f),
                            spotColor = Color(0xFF7C3AED)
                        )
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFF7C3AED), Color(0xFFA855F7))
                            ),
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = buttonInteractionSource,
                            indication = ripple(color = Color.White),
                            onClick = {
                                onNavigateToMain()
                            }
                        )
                        .testTag("get_started_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Spacer to push text to center
                        Spacer(modifier = Modifier.weight(1f))
                        
                        Text(
                            text = "Get Started",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "Get Started Arrow",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SplashScreen(
    settingsRepository: SettingsRepository,
    onSplashCompleted: (isFirstTime: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val logoScale = remember { Animatable(0.6f) }
    val textAlpha = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        // Animate the logo scale cleanly
        logoScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    LaunchedEffect(Unit) {
        // Fade in the logo text shortly after launch
        delay(200)
        textAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = EaseOutQuad)
        )
    }

    LaunchedEffect(Unit) {
        // Wait 2200ms total loop for premium splash persistence, then resolve destination
        delay(2200)
        
        // Read onboarding complete state from settings
        val onboardingCompleted = settingsRepository.isOnboardingCompleted().first()
        onSplashCompleted(!onboardingCompleted)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("splash_screen")
    ) {
        // Reuses the identical beautiful visual Swirling curves background
        SwirlingBackground(modifier = Modifier.fillMaxSize())

        // Content
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AudioraLogo(
                modifier = Modifier
                    .size(160.dp)
                    .scale(logoScale.value)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.scale(textAlpha.value)
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(color = Color(0xFF5B21B6), fontWeight = FontWeight.Bold)) {
                            append("Audi")
                        }
                        withStyle(style = SpanStyle(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF7C3AED), Color(0xFFC084FC))
                            ),
                            fontWeight = FontWeight.SemiBold
                        )) {
                            append("ora")
                        }
                    },
                    fontSize = 42.sp,
                    letterSpacing = (-1.5).sp,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Listen. Edit. Create.",
                color = Color(0xFF1C1A24).copy(alpha = 0.6f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.scale(textAlpha.value)
            )
        }
    }
}
