// core-ai: optional AI providers (Gemini, OpenAI, Claude) called with the user's own key. No UI code lives here.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "in.senthilnathan_dev.sentrack.core.ai"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 36
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.litertlm.android)   // on-device language models (LiteRT-LM)
    testImplementation(libs.junit)
    testImplementation(libs.json)   // org.json is part of Android at run time but not on the plain JVM used by unit tests
}
