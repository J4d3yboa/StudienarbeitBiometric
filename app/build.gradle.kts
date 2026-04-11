plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.studienarbeitbiometric"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.studienarbeitbiometric"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    useLibrary("wear-sdk")
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.play.services.wearable)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.compose.material)
    implementation(libs.compose.foundation)
    implementation(libs.wear.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.core.splashscreen)
    implementation(libs.navigation.compose)
    implementation(libs.navigation.common.ktx)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
    //new dependencies
    // Grundlegende Wear OS Bibliotheken
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("androidx.compose.material:material-icons-extended")

    // Jetpack Compose für Wear OS (Die wichtigste UI-Bibliothek)
    // Hinweis: Prüfen Sie auf die aktuellste stabile Version
    val wearComposeVersion = "1.4.0"
    implementation("androidx.wear.compose:compose-material:$wearComposeVersion")
    implementation("androidx.wear.compose:compose-foundation:$wearComposeVersion")
    implementation("androidx.wear.compose:compose-navigation:$wearComposeVersion")

    // Integration mit Activities
    implementation("androidx.activity:activity-compose:1.9.0")
    // Health Services Client
    implementation("androidx.health:health-services-client:1.1.0-alpha05") // oder stabilste Version

    // Coroutines für asynchrone Aufrufe (Guava ListenableFuture support)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.9.0")

    // Lifecycle Service für Foreground Management
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.compose.material3:material3")
    val horologistVersion = "0.6.17"
    implementation("com.google.android.horologist:horologist-composables:${horologistVersion}")
// 2. Erweiterte Layouts (z.B. AppScaffold, ScreenScaffold mit Scroll-Support)
    implementation("com.google.android.horologist:horologist-compose-layout:${horologistVersion}")
// 3. Wenn du eine Media-App baust (Player, Lautstärke)
    implementation("com.google.android.horologist:horologist-media-ui:${horologistVersion}")
// 4. Für Kommunikation zwischen Handy und Uhr (DataLayer)
    implementation("com.google.android.horologist:horologist-datalayer:${horologistVersion}")
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")
}