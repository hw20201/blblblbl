package com.github.mihomo.android.core

import com.github.mihomo.android.data.TunStack

/** Checks the app/native contract before a borrowed VPN descriptor reaches JNI. */
internal object TunStartup {
    /** Strict stack selection, borrowed fd and checked startup status. */
    const val CONTRACT_BASE = 1

    /** Adds MTU and MIPS congestion-controller options to the TUN start call. */
    const val CONTRACT_TUN_OPTIONS = 2

    fun start(
        stack: String,
        contract: () -> Int,
        nativeStart: (TunStack, Int) -> Int
    ): TunStack {
        val selected = requireNotNull(TunStack.fromKey(stack)) { "Unsupported TUN stack: $stack" }
        val version = contract()
        check(version == CONTRACT_BASE || version == CONTRACT_TUN_OPTIONS) {
            "Incompatible native TUN bridge; install a matching core and bridge"
        }
        val status = nativeStart(selected, version)
        check(status == 0) {
            "Native ${selected.displayName} TUN startup failed (code $status); see core logs"
        }
        return selected
    }
}
