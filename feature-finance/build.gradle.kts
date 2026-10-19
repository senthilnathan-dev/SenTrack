// feature-finance: accounts, transactions, budgets, goals and the dashboard.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "in.senthilnathan_dev.sentrack.feature.finance"
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
    implementation(project(":core-security"))
    implementation(project(":core-attachments"))
    implementation(libs.androidx.activity.compose)
    testImplementation(libs.junit)
}
