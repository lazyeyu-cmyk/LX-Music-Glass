plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.lxmusic.glass"
    compileSdk = 37  // 修改：从 36 升级到 37

    defaultConfig {
        applicationId = "app.lxmusic.glass"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0-quickjs-runtime"
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // 修改：Compose BOM 升级至 2026.11.01，以兼容 compileSdk 37
    val composeBom = platform("androidx.compose:compose-bom:2026.11.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Miuix Glass stack
    implementation("top.yukonga.miuix.kmp:miuix-glass-android:0.9.5-rc01")
    implementation("top.yukonga.miuix.kmp:miuix-glass-icons-android:0.9.5-rc01")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.5-rc01")

    // Playback foundation
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")

    // QuickJS wrapper
    implementation("wang.harlon.quickjs:wrapper-android:2.4.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
