package com.github.arca.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Single home for semantic colours that used to be hard-coded per screen.
 * Values are unchanged from the previous literals, so the visual result stays identical;
 * future theme work only needs to touch this file.
 */
object AppPalette {
    // Status
    val success = Color(0xFF34C759)
    val warning = Color(0xFFFF9500)
    val warningAmber = Color(0xFFF59E0B)
    val danger = Color(0xFFFF3B30)
    val dangerSoftBg = Color(0xFFFFEBEE)
    val info = Color(0xFF007AFF)

    // Latency thresholds (ms) and colours shared by dashboard and proxy pages
    const val DELAY_GOOD_MS = 250
    const val DELAY_FAIR_MS = 600
    val delayGood = success
    val delayFair = warning
    val delayPoor = danger

    // Settings / list icon tiles (iOS system hues)
    val iconBlue = Color(0xFF007AFF)
    val iconBrightBlue = Color(0xFF3B82F6)
    val iconSky = Color(0xFF32ADE6)
    val iconTeal = Color(0xFF30B0C7)
    val iconGreen = Color(0xFF34C759)
    val iconOrange = Color(0xFFFF9500)
    val iconAmber = Color(0xFFFF9F0A)
    val iconYellowAmber = Color(0xFFF59E0B)
    val iconRed = Color(0xFFFF3B30)
    val iconPink = Color(0xFFFF2D55)
    val iconPurple = Color(0xFFAF52DE)
    val iconViolet = Color(0xFF8B5CF6)
    val iconLavender = Color(0xFF7B61D1)
    val iconIndigo = Color(0xFF5856D6)
    val iconGray = Color(0xFF8E8E93)

    // Selected node text on the neutral frost
    val selectedOnFrost = Color(0xFF1A1D23)
    val selectedOnFrostMuted = Color(0xB31A1D23)

    // Dashboard light header wash (glass disabled only)
    val dashboardWashTop = Color(0xFFFFF6EE)
    val dashboardWashMid = Color(0xFFF9F9FD)

    fun delayColor(delay: Int, muted: Color): Color = when {
        delay == -2 || delay >= 65535 -> delayPoor
        delay in 1..DELAY_GOOD_MS -> delayGood
        delay in (DELAY_GOOD_MS + 1)..DELAY_FAIR_MS -> delayFair
        delay > DELAY_FAIR_MS -> delayPoor
        else -> muted
    }
}
