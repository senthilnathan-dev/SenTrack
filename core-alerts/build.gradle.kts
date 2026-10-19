// core-alerts: the shared reminders engine (alarms, notifications, boot recovery). No screens live here.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "in.senthilnathan_dev.sentrack.core.alerts"
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
    api(project(":core-sync"))      // ScheduleSpec / CronSchedule describe repeating reminders
    implementation(project(":core-data"))
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
