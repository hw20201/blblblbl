import java.util.Properties
import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.gitlab.arturbosch.detekt")
}


abstract class VerifyNativeLibrariesTask : DefaultTask() {
    @get:InputFile
    abstract val checksumFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val libraries: ConfigurableFileCollection

    @get:Input
    abstract val libraryPaths: ListProperty<String>

    @TaskAction
    fun verify() {
        val expected = checksumFile.get().asFile.readLines()
            .filter { it.isNotBlank() }
            .associate { line ->
                val parts = line.trim().split(Regex("\\s+"), limit = 2)
                check(parts.size == 2) { "Malformed native/SHA256SUMS" }
                parts[1] to parts[0]
            }
        val paths = libraryPaths.get()
        check(expected.keys == paths.toSet()) { "Native library pair manifest is incomplete" }

        val librariesByName = libraries.files.associateBy { it.name }
        check(paths.map { it.substringAfterLast('/') }.toSet() == librariesByName.keys) {
            "Configured native library files do not match the expected pair"
        }
        for (path in paths) {
            val library = librariesByName.getValue(path.substringAfterLast('/'))
            val actual = MessageDigest.getInstance("SHA-256").digest(library.readBytes())
                .joinToString("") { "%02x".format(it) }
            check(actual == expected[path]) { "Mismatched native library $path; rebuild the matching pair" }
        }
    }
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
val r8Enabled = providers.gradleProperty("nexus.r8").orNull?.toBoolean() ?: true

val nativeLibraryPaths = listOf(
    "app/src/main/jniLibs/arm64-v8a/libclash.so",
    "app/src/main/jniLibs/arm64-v8a/libbridge.so",
)
val verifyNativeLibraries = tasks.register<VerifyNativeLibrariesTask>("verifyNativeLibraries") {
    checksumFile.set(rootProject.layout.projectDirectory.file("native/SHA256SUMS"))
    libraryPaths.set(nativeLibraryPaths)
    libraries.from(nativeLibraryPaths.map { rootProject.file(it) })
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
        versionCode = 62
        versionName = "1.0.40-arca-gray-selection"

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
            versionNameSuffix = "-draft-glass-candidate12-reference-paced"
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
        versionNameSuffix = "-draft-glass-candidate12-reference-paced-performance"
        signingConfig = signingConfigs.getByName("debug")
        matchingFallbacks += listOf("release")
    }
    sourceSets.getByName("performance") {
        java.srcDir("src/debug/java")
        res.srcDir("src/debug/res")
        manifest.srcFile("src/debug/AndroidManifest.xml")
    }

    // Explicitly opt-in, side-by-side R8 experiment. This is not the production release:
    // it has its own package id and a disposable debug signature so it can be installed
    // alongside both the owner's app and the performance candidate for device diagnosis.
    // Build only with :app:assembleR8Test; never promote without device validation.
    buildTypes.create("r8Test") {
        initWith(buildTypes.getByName("release"))
        isDebuggable = false
        isMinifyEnabled = true
        isShrinkResources = true
        applicationIdSuffix = ".r8test"
        versionNameSuffix = "-r8-experiment"
        signingConfig = signingConfigs.getByName("debug")
        matchingFallbacks += listOf("release")
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
