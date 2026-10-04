import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.mahamart.essae"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.mahamart.essae"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "1.1.1"

        val supabaseProps = Properties().apply {
            val sharedFile = rootProject.file("supabase.properties")
            if (sharedFile.exists()) sharedFile.inputStream().use { load(it) }
            val localFile = rootProject.file("local.properties")
            if (localFile.exists()) localFile.inputStream().use { load(it) }
        }
        val supabaseUrl = supabaseProps.getProperty("SUPABASE_URL", "").trim()
        val supabaseKey = supabaseProps.getProperty("SUPABASE_PUBLISHABLE_KEY", "").trim()
        require(supabaseUrl.startsWith("https://") && supabaseKey.isNotBlank()) {
            "Supabase configuration missing. Check supabase.properties or local.properties."
        }
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$supabaseKey\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.10" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_1_8; targetCompatibility = JavaVersion.VERSION_1_8 }
    kotlinOptions { jvmTarget = "1.8" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.1")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
