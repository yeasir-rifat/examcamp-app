plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Firebase is enabled only when google-services.json is present, so the app
// (UI, theme, navigation) still builds and runs before Firebase is wired up.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.examcamp.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.examcamp.app"
        minSdk = 24
        targetSdk = 35
        // Play Store needs versionCode to grow with every upload. The release workflow passes
        // the GitHub run number; local builds use 1.
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.0.0"
        // The app is English + Bengali only. Drops translations bundled by libraries.
        resourceConfigurations += listOf("en", "bn")
    }

    signingConfigs {
        // Fixed debug keystore: the SHA-1 stays the same on every GitHub Actions run,
        // which Google Sign-In needs. Debug only; the release keystore comes later.
        getByName("debug") {
            storeFile = file("examcamp-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Release keystore comes from environment variables (set from GitHub Secrets by
        // .github/workflows/release.yml, or exported locally). Nothing secret lives in the repo.
        val releaseStore = System.getenv("KEYSTORE_FILE")
        if (releaseStore != null && file(releaseStore).exists()) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Real release key when provided; otherwise debug key so a local
            // `assembleRelease` still works for smoke-testing R8. Never upload that one.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // Material3/Compose APIs this app uses that are still marked experimental (ModalBottomSheet,
        // SwipeToDismissBox, FlowRow, combinedClickable ...). Opted in once here instead of per file.
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
        )
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    buildFeatures { compose = true }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        resources.excludes += "/META-INF/*.version"
        resources.excludes += "/DebugProbesKt.bin"
    }
    // Play serves each phone only its own screen density / language / CPU from the AAB.
    bundle {
        language { enableSplit = false } // keep both en + bn always available (in-app language toggle)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Firebase Auth + Google sign-in through Credential Manager
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Local unit tests: gradle :app:testDebugUnitTest
    testImplementation("junit:junit:4.13.2")
}
