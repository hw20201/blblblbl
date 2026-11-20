package com.github.kr328.clash.core.bridge

/** Loaded only in the permissionless subscription validation process. No nativeInit or ApplyConfig. */
object SubscriptionInspection {
    init {
        runCatching { android.system.Os.setenv("GODEBUG", "cpu.all=off", true) }
        System.loadLibrary("clash")
        System.loadLibrary("bridge")
    }

    external fun nativeInspect(text: String, mode: String, assets: android.content.res.AssetManager): String
}
