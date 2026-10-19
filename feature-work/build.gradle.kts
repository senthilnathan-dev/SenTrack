// feature-work: companies, attendance with location, leave calendar and (later) tasks and appreciation.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "in.senthilnathan_dev.sentrack.feature.work"
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
    implementation(project(":core-alerts"))
    implementation(libs.androidx.activity.compose)
    testImplementation(libs.junit)
}
