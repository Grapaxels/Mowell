plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.grapaxels.mowell"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.grapaxels.mowell"
        minSdk = 24
        targetSdk = 35
        versionCode = 21
        versionName = "1.5.5"
        buildConfigField("boolean", "SELF_UPDATE", "true")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // R8 removes unused code. Resource shrinking is disabled because the
            // Android Gradle zip transformer is unreliable on Windows/JDK zipfs.
            isShrinkResources = false
            isDebuggable = false
            buildConfigField("boolean", "SELF_UPDATE", "false")
            val releaseStore = System.getenv("MOWELL_KEYSTORE")
            if (!releaseStore.isNullOrBlank()) {
                signingConfig = signingConfigs.create("mowellRelease") {
                    storeFile = file(releaseStore)
                    storePassword = System.getenv("MOWELL_STORE_PASSWORD")
                    keyAlias = System.getenv("MOWELL_KEY_ALIAS") ?: "mowell-upload"
                    keyPassword = System.getenv("MOWELL_KEY_PASSWORD")
                }
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("direct") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            // Sideloaded packages must not silently become app installers.
            // Updates are handled by Google Play once the app is published.
            buildConfigField("boolean", "SELF_UPDATE", "false")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

// This all-Kotlin app does not require javac. Disabling the empty task also makes
// builds deterministic in restricted Windows environments where javac's ZIP FS
// cannot resolve Android platform jars while closing them.
tasks.withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
    enabled = false
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
}
