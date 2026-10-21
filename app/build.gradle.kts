import java.util.Properties
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.gitlab.arturbosch.detekt")
}

// Release signing is never committed to the repo. Provide it via local.properties
// (releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
// or the RELEASE_* environment variables (e.g. CI secrets). Without them,
// release builds remain unsigned and must never fall back to the debug key.
val signingProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) localFile.inputStream().use { load(it) }
}

fun signingValue(propertyKey: String, envKey: String): String? =
    (signingProperties.getProperty(propertyKey) ?: System.getenv(envKey))?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("releaseStoreFile", "RELEASE_STORE_FILE")?.let { file(it) }
val releaseKeyAlias = signingValue("releaseKeyAlias", "RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingValue("releaseKeyPassword", "RELEASE_KEY_PASSWORD")
val releaseStorePassword = signingValue("releaseStorePassword", "RELEASE_STORE_PASSWORD")
val hasReleaseSigning = releaseStoreFile != null && releaseStoreFile.exists() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank()

// R8 remains experimental and off for signed draft packages.
val r8Enabled = providers.gradleProperty("nexus.r8").orNull?.toBoolean() ?: false

val nativeChecksums = rootProject.file("native/SHA256SUMS")
val nativeLibraries = listOf("libclash.so", "libbridge.so").associate { name ->
    val path = "app/src/main/jniLibs/arm64-v8a/$name"
    path to rootProject.file(path)
}
val verifyNativeLibraries by tasks.registering {
    inputs.file(nativeChecksums)
    inputs.files(nativeLibraries.values)
    doLast {
        val expected = nativeChecksums.readLines().filter { it.isNotBlank() }.associate { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            check(parts.size == 2) { "Malformed native/SHA256SUMS" }
            parts[1] to parts[0]
        }
        check(expected.keys == nativeLibraries.keys) { "Native library pair manifest is incomplete" }
        for ((path, library) in nativeLibraries) {
            val actual = MessageDigest.getInstance("SHA-256").digest(library.readBytes())
                .joinToString("") { "%02x".format(it) }
            check(actual == expected[path]) { "Mismatched native library $path; rebuild the matching pair" }
        }
    }
}
tasks.named("preBuild") { dependsOn(verifyNativeLibraries) }

android {
    namespace = "com.github.mihomo.android"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.github.mihomo.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 37
        versionName = "1.0.32-with-suspended"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // Enable only for separately device-tested builds with -Pnexus.r8=true.
            isMinifyEnabled = r8Enabled
            isShrinkResources = r8Enabled
            isDebuggable = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            // Isolated disposable UI draft; never replaces the owner's installed production app.
            applicationIdSuffix = ".draftglass"
            versionNameSuffix = "-draft-glass-candidate5"
        }
    }

    // Same disposable identity/signature and local diagnostics, without debuggable ART
    // or Compose inspection dependencies. No R8/reflection/native changes in this candidate.
    buildTypes.create("performance") {
        initWith(buildTypes.getByName("release"))
        isDebuggable = false
        isMinifyEnabled = false
        isShrinkResources = false
        applicationIdSuffix = ".draftglass"
        versionNameSuffix = "-draft-glass-candidate5-performance"
        signingConfig = signingConfigs.getByName("debug")
        matchingFallbacks += listOf("release")
    }
    sourceSets.getByName("performance") {
        java.srcDir("src/debug/java")
        res.srcDir("src/debug/res")
        manifest.srcFile("src/debug/AndroidManifest.xml")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        // Only the single existing TileService issue expressly authorized for this branch.
        baseline = file("lint-baseline.xml")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
            // The guard's NDK build must not strip/rewrite pinned core binaries.
            keepDebugSymbols += setOf("**/libclash.so", "**/libbridge.so")
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    
    // Compose
    implementation("androidx.compose.ui:ui:1.7.5")
    implementation("androidx.compose.ui:ui-graphics:1.7.5")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.5")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.5")
    
    // Coroutines & Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Network & Config
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.yaml:snakeyaml:2.2")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.mozilla:rhino:1.7.15")
    implementation("io.github.taoweiji.quickjs:quickjs-android:1.4.6")

    debugImplementation("androidx.compose.ui:ui-tooling:1.7.5")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.5")

    // Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.compose.ui:ui-test-junit4:1.7.5")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    ignoreFailures = false
}
