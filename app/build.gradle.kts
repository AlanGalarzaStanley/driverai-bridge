plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val gitSha: String = (System.getenv("GITHUB_SHA") ?: "local").take(7)

android {
    namespace = "ar.driverai.bridge"
    compileSdk = 35

    defaultConfig {
        applicationId = "ar.driverai.bridge"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1.0-$gitSha"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // arm64 (Samsung S10 y la mayoría), armv7 (equipos viejos), x86_64 (emulador de CI)
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        buildConfigField("String", "BACKEND_BASE_URL", "\"https://bzjvfexqqxeccmbxwipe.supabase.co/functions/v1/\"")
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
    }

    buildTypes {
        debug {
            // Firma de depuración estándar de Android. No hay claves de producción en el repo.
            buildConfigField("boolean", "SIMULATOR_WATCH", "true")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("boolean", "SIMULATOR_WATCH", "false")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = false
        disable += setOf("GradleDependency", "NewerVersionAvailable", "OldTargetApi", "AndroidGradlePluginVersion")
        xmlReport = true
        htmlReport = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.kotlin_module")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // OCR local (modelo incluido en el APK, sin envío de imágenes a servidores)
    implementation("com.google.mlkit:text-recognition:16.0.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
