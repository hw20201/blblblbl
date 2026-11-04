package com.github.arca.android.service

import androidx.annotation.Keep

/** Native bridge for the official GPL-3.0-only Nowhere Vector runtime. */
@Keep
object NowhereNative {
    init {
        System.loadLibrary("nowhere_android")
    }

    /** Blocks until the service sends SIGTERM or the Vector runtime stops. */
    @JvmStatic
    external fun run(vectorUrl: String): Int
}
