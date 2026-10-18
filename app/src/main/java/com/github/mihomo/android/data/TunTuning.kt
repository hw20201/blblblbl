package com.github.mihomo.android.data

/**
 * Limits shared by the VPN service, the settings UI and the native bridge.
 * The congestion controllers are the ones mipstack registers; cubic is its default.
 */
object TunTuning {
    const val DEFAULT_MTU = 9000
    const val MIN_MTU = 1280
    const val MAX_MTU = 65535

    const val DEFAULT_CONGESTION_CONTROLLER = "cubic"
    val CONGESTION_CONTROLLERS = listOf("cubic", "reno", "bbr", "bbr3")

    fun normalizeMtu(value: Int): Int = value.coerceIn(MIN_MTU, MAX_MTU)

    fun normalizeCongestionController(value: String?): String =
        value?.trim()?.lowercase()?.takeIf { it in CONGESTION_CONTROLLERS } ?: DEFAULT_CONGESTION_CONTROLLER
}
