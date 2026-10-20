import java.util.Properties

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
        versionCode = 500   // major * 10000 + minor * 100 + patch (tools/bump-version.sh keeps this rule)
        versionName = "0.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The privacy policy, terms, user guide and changelog are plain text files in docs/legal, shared by the app (as
    // assets) and the published web pages (tools/make-legal-pages.sh), so they cannot drift apart.
    sourceSets {
        getByName("main").assets.srcDir(rootProject.file("docs/legal"))
    }

    // Release signing. The keystore and its passwords live OUTSIDE the repository, in keystore.properties (git-ignored):
    //   storeFile=C:/keys/sentrack-upload.jks
    //   storePassword=...
    //   keyAlias=upload
    //   keyPassword=...
    // Without that file the release build is simply left unsigned, so nothing breaks for people without the key.
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (keystoreProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
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
