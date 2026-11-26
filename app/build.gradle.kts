import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Release signing: keystore.properties (local) ya environment variables (CI) se padhta hai.
val ksProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun sign(key: String, env: String): String? = ksProps.getProperty(key) ?: System.getenv(env)

android {
    namespace = "com.wyrix.bookgenerator"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wyrix.bookgenerator"
        minSdk = 29
        targetSdk = 36          // Play Store: 31 Aug 2026 se API 36 zaroori hai
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            val path = sign("storeFile", "WYRIX_STORE_FILE")
            if (path != null) {
                storeFile = rootProject.file(path)
                storePassword = sign("storePassword", "WYRIX_STORE_PASSWORD")
                keyAlias = sign("keyAlias", "WYRIX_KEY_ALIAS")
                keyPassword = sign("keyPassword", "WYRIX_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (sign("storeFile", "WYRIX_STORE_FILE") != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    bundle { language { enableSplit = false } }   // Hindi/English dono hamesha available
    lint { abortOnError = false }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")   // API keys encrypted store
}
