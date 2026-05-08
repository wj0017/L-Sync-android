package com.lsync.app.ui.theme

import androidx.compose.ui.graphics.Color

// Surfaces (premium dark — single theme, no light variant)
val BgPrimary    = Color(0xFF0A0A0A)
val BgSecondary  = Color(0xFF141414)
val BgCard       = Color(0xFF161616)
val BgElevated   = Color(0xFF202020)
val Divider      = Color(0xFF1F1F1F)

// Text (4-level hierarchy)
val FgPrimary    = Color(0xFFF5F5F5)   // titles, primary content (softened from #FFF)
val FgSecondary  = Color(0xFF8A8A8A)   // meta, sub-labels
val FgTertiary   = Color(0xFF5C5C5C)   // year label, uppercase meta
val FgDisabled   = Color(0xFF3A3A3A)   // placeholders, off-month dates

// Accents (used sparingly)
val AccentBlue    = Color(0xFF4F7EFF)
val AccentBlue20  = Color(0x334F7EFF)  // nav indicator pill
val AccentGreen   = Color(0xFF43A047)
val AccentRed     = Color(0xFFE53935)
val AccentRed80   = Color(0xCCE53935)  // Sunday, warning meta

// Card hairline overlay (simulated in Compose via border)
val HairlineWhite = Color(0x09FFFFFF)  // rgba(255,255,255,.035)
