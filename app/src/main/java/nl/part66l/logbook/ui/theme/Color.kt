package nl.part66l.logbook.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Brand
// ---------------------------------------------------------------------------

/**
 * The one brand blue, sampled from the app icon. Deliberately the same value in
 * light and dark: it is the top bar on every screen and the tint on every icon,
 * and an app that changes its own brand colour between themes reads as two apps.
 * White sits on it at 5.2:1 in both themes.
 */
val Part66Blue = Color(0xFF176FC1)

val Part66BlueContainer = Color(0xFFD3E4FF)
val Part66OnBlueContainer = Color(0xFF001C3B)
val Part66BlueContainerDark = Color(0xFF00497D)
val Part66OnBlueContainerDark = Color(0xFFD3E4FF)

/** The brand blue lightened for dark grounds: inverse surfaces, and blue text (see linkBlue). */
val Part66BlueLight = Color(0xFF9FCAFF)

/** Save / confirm actions only (per explicit instruction) — not part of the Material role palette. */
val Part66ConfirmGreen = Color(0xFF5E9918)
val Part66ConfirmGreenDark = Color(0xFF8AC33E)

/** "Running out soon" — licence expiry and recency lapse warnings. Red is the theme's own error colour. */
val Part66WarningAmber = Color(0xFFB86E00)
val Part66WarningAmberDark = Color(0xFFFFB95C)

// ---------------------------------------------------------------------------
// Neutrals
//
// Material's baseline neutrals are purple-biased (#FFFBFE, #E7E0EC, #49454F).
// These are the same ladder biased ~210° instead, so greys sit under the brand
// blue rather than fighting it. Every step is declared because Card, chips and
// the drawer all reach for roles the old theme never named, and anything left
// undeclared falls back to purple.
// ---------------------------------------------------------------------------

val NeutralWhite = Color(0xFFFFFFFF)
val Neutral99 = Color(0xFFFCFDFE)
val Neutral97 = Color(0xFFF6F8FA)
val Neutral95 = Color(0xFFF0F3F6)
val Neutral93 = Color(0xFFEAEEF2)
val Neutral90 = Color(0xFFE3E9EF)
val Neutral87 = Color(0xFFDCE2E8)
val Neutral80 = Color(0xFFC7D0DA)
val Neutral55 = Color(0xFF7A8795)
val Neutral35 = Color(0xFF4A5561)
/** Light mode's inverse surface and dark mode's card tone are the same step of the ladder. */
val Neutral22 = Color(0xFF2A323A)
val Neutral10 = Color(0xFF121A21)

val NeutralDark06 = Color(0xFF0C1013)
val NeutralDark09 = Color(0xFF12161A)
val NeutralDark12 = Color(0xFF171C22)
val NeutralDark14 = Color(0xFF1B2128)
val NeutralDark18 = Color(0xFF252C34)
val NeutralDark24 = Color(0xFF38404A)
val NeutralDark30 = Color(0xFF414B55)
val NeutralDark60 = Color(0xFF8A96A3)
val NeutralDark78 = Color(0xFFB6C2CE)
val NeutralDark91 = Color(0xFFE2E7EC)

// ---------------------------------------------------------------------------
// Supporting roles — kept inside the blue family so nothing decorative drifts
// off-brand. Secondary carries selected chips and drawer items; tertiary is the
// steel tone used where a third accent is unavoidable.
// ---------------------------------------------------------------------------

val Secondary = Color(0xFF45617F)
val SecondaryContainer = Color(0xFFD6E3F2)
val OnSecondaryContainer = Color(0xFF0B1D2E)
val SecondaryDark = Color(0xFFACC7E4)
val OnSecondaryDark = Color(0xFF163348)
val SecondaryContainerDark = Color(0xFF2D4A63)

val Tertiary = Color(0xFF3E6470)
val TertiaryContainer = Color(0xFFC2E9F7)
val OnTertiaryContainer = Color(0xFF001F27)
val TertiaryDark = Color(0xFFA6CDDB)
val OnTertiaryDark = Color(0xFF073541)
val TertiaryContainerDark = Color(0xFF254C58)

// ---------------------------------------------------------------------------
// Error — Material's own red, which is already hue-neutral enough to sit beside
// the blue without clashing.
// ---------------------------------------------------------------------------

val ErrorRed = Color(0xFFB3261E)
val ErrorContainer = Color(0xFFF9DEDC)
val OnErrorContainer = Color(0xFF410E0B)
val ErrorRedDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)
