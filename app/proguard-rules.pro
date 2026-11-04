# ---------------------------------------------------------------------------
# Native bridges (libclash.so / libbridge.so / libnowhere_android.so) -- entry
# points are looked up by JNI name, so they must survive shrinking/obfuscation.
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
-keep,includedescriptorclasses class com.github.arca.android.**$$serializer { *; }
-keepclassmembers class com.github.arca.android.** {
    *** Companion;
}
-keepclasseswithmembers class com.github.arca.android.** {
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
-keeppackagenames org.yaml.snakeyaml.**
-dontwarn com.google.gson.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.slf4j.**

# Manifest components and reflectively accessed persisted data.
-keep class com.github.arca.android.MihomoApplication { *; }
-keep class com.github.arca.android.ui.MainActivity { *; }
-keep class com.github.arca.android.service.MihomoVpnService { *; }
-keep class com.github.arca.android.service.NowhereSidecarService { *; }
-keep class com.github.arca.android.service.NowhereNative { *; }
-keep class com.github.arca.android.service.MihomoTileService { *; }
-keep class com.github.arca.android.receiver.BootReceiver { *; }
-keep class com.github.arca.android.data.** { *; }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepattributes Signature,MethodParameters,Exceptions,EnclosingMethod
