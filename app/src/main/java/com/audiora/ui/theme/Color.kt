package com.audiora.ui.theme

import androidx.compose.ui.graphics.Color

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.material3.MaterialTheme

// Premium Audiora Color Palette
val PrimaryPurple: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.primary

val AccentPurple = Color(0xFF651FFF)         // Accent Purple
val LightPurple = Color(0xFFEEEEFF)          // Frosted background hint
val CosmicSlate = Color(0xFF1E1B29)          // Dark Cosmic Theme card background
val GlassBorderLight = Color(0x99FFFFFF)     // 1dp translucent border rgba(255, 255, 255, 0.6)
val GlassBorderDark = Color(0x14FFFFFF)      // 1dp translucent border rgba(255, 255, 255, 0.08)

// Brand Gradient Colors
val BrandGradientStart = Color(0xFFA855F7)
val BrandGradientEnd = Color(0xFF7C3AED)

// Material 3 Color scheme tokens (Light Theme)
val PrimaryLight = Color(0xFF8B5CF6)         // Audiora Purple #8B5CF6
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFE8DFFF)
val OnPrimaryContainerLight = Color(0xFF22005D)

val SecondaryLight = Color(0xFF651FFF)
val OnSecondaryLight = Color(0xFFFFFFFF)

val BackgroundLight = Color(0xFFF8F6FB)      // soft off-white background #F8F6FB
val SurfaceLight = Color(0xFAFFFFFF)
val OnBackgroundLight = Color(0xFF1C1A24)    // Soft dark grey text (no pure black)
val OnSurfaceLight = Color(0xFF1C1A24)

// Material 3 Color scheme tokens (Dark Theme)
val PrimaryDark = Color(0xFF8B5CF6)          // Audiora Purple #8B5CF6
val OnPrimaryDark = Color(0xFF100E17)
val PrimaryContainerDark = Color(0xFF4F378B)
val OnPrimaryContainerDark = Color(0xFFE8DFFF)

val SecondaryDark = Color(0xFFCCC2DC)
val OnSecondaryDark = Color(0xFF332D41)

val BackgroundDark = Color(0xFF08080B)       // deep dark background #08080B
val SurfaceDark = Color(0xFF0D0D11)          // subtle depth #0D0D11
val OnBackgroundDark = Color(0xFFE6E1E9)     // Soft light grey text (no pure white)
val OnSurfaceDark = Color(0xFFE6E1E9)
