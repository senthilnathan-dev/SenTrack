// core-data: encrypted Room database. Opens only while the vault is unlocked.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "in.senthilnathan_dev.sentrack.core.data"
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

// Exported schemas are committed so every migration can be reviewed and tested.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core-security"))
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // SQLCipher: AES-256 encryption of the whole database file (data, indexes, journal).
    implementation(libs.sqlcipher.android)
}
