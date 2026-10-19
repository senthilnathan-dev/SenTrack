// feature-personal: notes, habits, plans, achievements, important dates and the timeline.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "in.senthilnathan_dev.sentrack.feature.personal"
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
    implementation(project(":core-data"))
    implementation(project(":core-attachments"))
    implementation(project(":core-security"))
    implementation(libs.androidx.activity.compose)
    testImplementation(libs.junit)
}
