package com.github.arca.android.data

/** Shared opening and closing durations for the app's morphing panels. */
enum class PanelAnimationSpeed(
    val label: String,
    val openDurationMillis: Int,
    val closeDurationMillis: Int
) {
    FAST("快", 240, 180),
    MEDIUM("中", 360, 270),
    SLOW("慢", 540, 405);

    companion object {
        fun fromPreference(value: String?): PanelAnimationSpeed =
            entries.firstOrNull { it.name == value } ?: MEDIUM
    }
}
