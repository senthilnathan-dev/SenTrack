plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "in.senthilnathan_dev.sentrack"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "in.senthilnathan_dev.sentrack"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
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
    implementation(project(":core-security"))
    implementation(project(":core-data"))
    implementation(project(":core-sync"))
    implementation(project(":core-ui"))
    implementation(project(":feature-finance"))
    implementation(project(":core-alerts"))
    implementation(project(":feature-reminders"))
    implementation(project(":feature-personal"))
    implementation(project(":feature-work"))
    implementation(project(":feature-career"))
    implementation(project(":core-ai"))
    implementation(libs.play.services.auth)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
