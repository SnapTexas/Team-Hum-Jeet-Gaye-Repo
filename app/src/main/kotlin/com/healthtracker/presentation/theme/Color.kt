package com.healthtracker.presentation.theme

import androidx.compose.ui.graphics.Color

// ============================================
// PREMIUM FUTURISTIC COLOR PALETTE
// ============================================

// Primary Colors - Electric Blue (Main accent)
val ElectricBlue = Color(0xFF00D4FF)
val ElectricBlueDark = Color(0xFF0099CC)
val ElectricBlueLight = Color(0xFF66E5FF)
val Primary = ElectricBlue
val PrimaryVariant = ElectricBlueDark
val OnPrimary = Color(0xFFFFFFFF)

// Secondary Colors - Neon Purple (Highlights)
val NeonPurple = Color(0xFF8B5CF6)
val NeonPurpleDark = Color(0xFF6D28D9)
val NeonPurpleLight = Color(0xFFA78BFA)
val Secondary = NeonPurple
val SecondaryVariant = NeonPurpleDark
val OnSecondary = Color(0xFFFFFFFF)

// Tertiary Colors - Cyber Green (Success/Positive)
val CyberGreen = Color(0xFF10B981)
val CyberGreenDark = Color(0xFF059669)
val CyberGreenLight = Color(0xFF34D399)
val Tertiary = CyberGreen
val TertiaryVariant = CyberGreenDark
val OnTertiary = Color(0xFFFFFFFF)

// ============================================
// GLASSMORPHISM & DEPTH COLORS
// ============================================

// Glass Effect Colors (for frosted glass UI)
val GlassWhite = Color(0x33FFFFFF)
val GlassWhiteStrong = Color(0x66FFFFFF)
val GlassDark = Color(0x33000000)
val GlassDarkStrong = Color(0x66000000)
val GlassBorder = Color(0x33FFFFFF)
val GlassBorderDark = Color(0x1AFFFFFF)
val GlassSurface = Color(0x1A1E293B)

// Neumorphism Colors
val NeuroLight = Color(0xFFE8EDF5)
val NeuroShadowLight = Color(0xFFB8C4D9)
val NeuroDark = Color(0xFF1A1F2E)
val NeuroShadowDark = Color(0xFF0D1117)

// ============================================
// BACKGROUND COLORS
// ============================================

// Light Theme Backgrounds
val Background = Color(0xFFFFFFFF)
val Surface = Color(0xFFF8FAFC)
val SurfaceVariant = Color(0xFFF1F5F9)
val OnBackground = Color(0xFF1E293B)
val OnSurface = Color(0xFF334155)
val OnSurfaceVariant = Color(0xFF64748B)

// Dark Theme Backgrounds (Premium Dark)
val BackgroundDark = Color(0xFF0D0D1A)
val BackgroundDarkSecondary = Color(0xFF1A1A2E)
val BackgroundDarkTertiary = Color(0xFF16213E)
val SurfaceDark = Color(0xFF1E293B)
val SurfaceVariantDark = Color(0xFF334155)
val OnBackgroundDark = Color(0xFFF8FAFC)
val OnSurfaceDark = Color(0xFFE2E8F0)
val OnSurfaceVariantDark = Color(0xFF94A3B8)

// ============================================
// GLOW & NEON EFFECTS
// ============================================

// Glow Colors (20% opacity for subtle glow)
val GlowPrimary = Color(0x3300D4FF)
val GlowSecondary = Color(0x338B5CF6)
val GlowTertiary = Color(0x3310B981)
val GlowWhite = Color(0x33FFFFFF)

// Neon Glow (stronger, for highlights)
val NeonGlowBlue = Color(0x6600D4FF)
val NeonGlowPurple = Color(0x668B5CF6)
val NeonGlowGreen = Color(0x6610B981)
val NeonGlowPink = Color(0x66EC4899)

// ============================================
// GRADIENT COLORS
// ============================================

// Primary Gradient (Purple → Blue → Cyan)
val GradientStart = Color(0xFF8B5CF6)
val GradientMiddle = Color(0xFF00D4FF)
val GradientEnd = Color(0xFF10B981)

// Aurora Gradient (for backgrounds)
val AuroraStart = Color(0xFF667EEA)
val AuroraMid1 = Color(0xFF764BA2)
val AuroraMid2 = Color(0xFFF093FB)
val AuroraEnd = Color(0xFFF5576C)

// Holographic Gradient
val HoloStart = Color(0xFF00D4FF)
val HoloMid = Color(0xFF8B5CF6)
val HoloEnd = Color(0xFFEC4899)

// ============================================
// CARD & SURFACE COLORS
// ============================================

val CardBackground = Color(0xFFFFFFFF)
val CardBackgroundDark = Color(0xFF1E293B)
val CardBackgroundGlass = Color(0x1AFFFFFF)
val CardBorder = Color(0xFFE2E8F0)
val CardBorderDark = Color(0xFF334155)
val CardBorderGlow = Color(0x3300D4FF)

// Elevated Card (with depth)
val CardElevated = Color(0xFFFAFAFC)
val CardElevatedDark = Color(0xFF252B3B)

// ============================================
// STATUS COLORS
// ============================================

// Error Colors
val Error = Color(0xFFEF4444)
val ErrorContainer = Color(0xFFFEE2E2)
val OnError = Color(0xFFFFFFFF)
val OnErrorContainer = Color(0xFF7F1D1D)

// Success Colors
val Success = Color(0xFF22C55E)
val SuccessContainer = Color(0xFFDCFCE7)
val OnSuccess = Color(0xFFFFFFFF)
val OnSuccessContainer = Color(0xFF14532D)

// Warning Colors
val Warning = Color(0xFFF59E0B)
val WarningContainer = Color(0xFFFEF3C7)
val OnWarning = Color(0xFFFFFFFF)
val OnWarningContainer = Color(0xFF78350F)

// ============================================
// OUTLINE COLORS
// ============================================

val Outline = Color(0xFFCBD5E1)
val OutlineVariant = Color(0xFFE2E8F0)
val OutlineDark = Color(0xFF475569)
val OutlineVariantDark = Color(0xFF334155)

// ============================================
// HEALTH METRIC COLORS (Vibrant)
// ============================================

val StepsColor = Color(0xFF3B82F6)
val CaloriesColor = Color(0xFFEF4444)
val SleepColor = Color(0xFF8B5CF6)
val HeartRateColor = Color(0xFFEC4899)
val DistanceColor = Color(0xFF10B981)
val ScreenTimeColor = Color(0xFFF59E0B)
val WaterColor = Color(0xFF06B6D4)
val MoodColor = Color(0xFFF97316)
val StressColor = Color(0xFFDC2626)
val HRVColor = Color(0xFF7C3AED)

// ============================================
// BADGE RARITY COLORS (Gamification)
// ============================================

val BadgeCommon = Color(0xFF9CA3AF)
val BadgeUncommon = Color(0xFF22C55E)
val BadgeRare = Color(0xFF3B82F6)
val BadgeEpic = Color(0xFF8B5CF6)
val BadgeLegendary = Color(0xFFF59E0B)

// ============================================
// SHIMMER & ANIMATION COLORS
// ============================================

val ShimmerBase = Color(0xFFE2E8F0)
val ShimmerHighlight = Color(0xFFF8FAFC)
val ShimmerBaseDark = Color(0xFF334155)
val ShimmerHighlightDark = Color(0xFF475569)
