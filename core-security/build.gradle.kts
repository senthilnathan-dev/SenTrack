// core-security: vault, key management and cryptography. No UI code lives here.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "in.senthilnathan_dev.sentrack.core.security"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 36
        // Consumer rules keep BouncyCastle's Argon2 intact when the app is shrunk by R8.
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Argon2id is not in the JDK/Android; BouncyCastle's lightweight API provides it.
    implementation(libs.bouncycastle)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    testImplementation(libs.junit)
}
