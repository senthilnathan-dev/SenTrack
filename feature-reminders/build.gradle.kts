// feature-reminders: the Reminders screens (list, editor, permissions). The engine itself is in core-alerts.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "in.senthilnathan_dev.sentrack.feature.reminders"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 36
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core-ui"))
    implementation(project(":core-alerts"))
    implementation(project(":core-data"))
    implementation(project(":core-sync"))
    implementation(libs.androidx.activity.compose)
}
