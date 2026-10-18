package com.github.mihomo.android.data

/** The only supported TUN stack choices persisted by the app. */
enum class TunStack(val key: String, val displayName: String) {
    SYSTEM("system", "System"),
    MIXED("mixed", "Mixed"),
    GVISOR("gvisor", "gVisor"),
    MIPS("mips", "MIPS");

    companion object {
        val DEFAULT: TunStack = MIPS

        /** Returns null for unrecognized persisted values. */
        fun fromKey(key: String?): TunStack? = entries.firstOrNull { it.key == key }

        /** Backwards-compatible alias for callers that already use value terminology. */
        fun fromValue(value: String?): TunStack? = fromKey(value)
    }
}
