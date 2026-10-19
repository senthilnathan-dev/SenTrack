// core-ui: SenTrack's shared Compose UI kit (screens, sections, rows, buttons). No business logic.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "in.senthilnathan_dev.sentrack.core.ui"
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
    // `api`: feature modules building on this kit get Compose and Material 3 automatically.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material3)
}
