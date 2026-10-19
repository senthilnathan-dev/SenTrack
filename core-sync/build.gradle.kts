// core-sync: encrypted backup snapshots, schedules and (later) the Drive client. No UI code lives here.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "in.senthilnathan_dev.sentrack.core.sync"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 36
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core-security"))
    testImplementation(libs.junit)
}
