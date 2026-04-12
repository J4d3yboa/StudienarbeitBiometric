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
    val wearComposeVersion = "1.4.0"
    val horologistVersion = "0.6.17"

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

    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.wear.compose:compose-material:$wearComposeVersion")
    implementation("androidx.wear.compose:compose-foundation:$wearComposeVersion")
    implementation("androidx.wear.compose:compose-navigation:$wearComposeVersion")

    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.health:health-services-client:1.1.0-alpha05")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.9.0")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")

    implementation("androidx.compose.material3:material3")

    implementation("com.google.android.horologist:horologist-composables:${horologistVersion}")
    implementation("com.google.android.horologist:horologist-compose-layout:${horologistVersion}")
    implementation("com.google.android.horologist:horologist-media-ui:${horologistVersion}")
    implementation("com.google.android.horologist:horologist-datalayer:${horologistVersion}")

    implementation("com.google.accompanist:accompanist-permissions:0.36.0")
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")
    implementation("androidx.wear:wear-ongoing:1.0.0")
}