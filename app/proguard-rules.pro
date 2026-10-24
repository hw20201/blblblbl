# ---------------------------------------------------------------------------
# Native bridge (libclash.so / libbridge.so) -- entry points are looked up by
# JNI name, so they must survive shrinking and obfuscation.
# NOTE: shrinking is currently disabled (isMinifyEnabled = false) because the
# minified build previously crashed on startup. R8 is experimental and opt-in.
# ---------------------------------------------------------------------------
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class com.github.kr328.clash.core.** { *; }
-keep interface com.github.kr328.clash.core.** { *; }
# JNI_OnLoad in libbridge.so resolves kotlin/Unit and its INSTANCE field by fixed names.
-keep class kotlin.Unit { public static final kotlin.Unit INSTANCE; }
# JNI_OnLoad also resolves CompletableDeferred.complete/completeExceptionally via GetMethodID.
-keep interface kotlinx.coroutines.CompletableDeferred { *; }
-keep class * implements com.github.kr328.clash.core.bridge.TunInterface { *; }
-keep class * implements com.github.kr328.clash.core.bridge.FetchCallback { *; }
-keep class * implements com.github.kr328.clash.core.bridge.LogcatInterface { *; }
-keepattributes *Annotation*

# ---------------------------------------------------------------------------
# kotlinx.serialization -- generated serializers are referenced reflectively.
# ---------------------------------------------------------------------------
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.github.mihomo.android.**$$serializer { *; }
-keepclassmembers class com.github.mihomo.android.** {
    *** Companion;
}
-keepclasseswithmembers class com.github.mihomo.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------------------------------------------------------------------------
# Third-party engines
# ---------------------------------------------------------------------------
# QuickJS is driven through JNI.
-keep class com.quickjs.** { *; }
# Rhino is invoked reflectively by the script engine.
-dontwarn org.mozilla.javascript.**
-keep class org.mozilla.javascript.** { *; }

# SnakeYAML / Gson / OkHttp ship their own (or need no) keep rules; silence the
# optional-dependency warnings they emit.
-dontwarn org.yaml.snakeyaml.**
-dontwarn com.google.gson.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.slf4j.**

# Manifest components and reflectively accessed persisted data.
-keep class com.github.mihomo.android.MihomoApplication { *; }
-keep class com.github.mihomo.android.ui.MainActivity { *; }
-keep class com.github.mihomo.android.service.MihomoVpnService { *; }
-keep class com.github.mihomo.android.service.MihomoTileService { *; }
-keep class com.github.mihomo.android.receiver.BootReceiver { *; }
-keep class com.github.mihomo.android.data.** { *; }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepattributes Signature,MethodParameters,Exceptions,EnclosingMethod
