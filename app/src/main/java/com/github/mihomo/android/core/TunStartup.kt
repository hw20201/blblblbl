package com.github.mihomo.android.core

import com.github.mihomo.android.data.TunStack

/** Checks the app/native contract before a borrowed VPN descriptor reaches JNI. */
internal object TunStartup {
    private const val REQUIRED_CONTRACT = 1

    fun start(
        stack: String,
        contract: () -> Int,
        nativeStart: (String) -> Int
    ): TunStack {
        val selected = requireNotNull(TunStack.fromKey(stack)) { "Unsupported TUN stack: $stack" }
        check(contract() == REQUIRED_CONTRACT) {
            "Incompatible native TUN bridge; install a matching core and bridge"
        }
        val status = nativeStart(selected.key)
        check(status == 0) {
            "Native ${selected.displayName} TUN startup failed (code $status); see core logs"
        }
        return selected
    }
}
