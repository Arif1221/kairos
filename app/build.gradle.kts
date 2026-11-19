plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.skarif.kairos"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.skarif.kairos"
        minSdk = 28 // DynamicsProcessing (10-band EQ) needs Android 9
        targetSdk = 35
        versionCode = 13
        versionName = "0.6.3"
    }

    signingConfigs {
        // One fixed key, so every CI build installs over the previous one. A fresh debug key
        // per CI run made Android refuse updates. Sideload-only; use a private upload key for Play.
        create("kairos") {
            storeFile = file("kairos-release.jks")
            storePassword = "REMOVED"
            keyAlias = "kairos"
            keyPassword = "REMOVED"
        }
    }

    // "play": offline-only build for the Play Store (no internet permission, no YouTube code).
    // "online": adds YouTube Music search and streaming; shipped on GitHub Releases only.
    flavorDimensions += "distribution"
    productFlavors {
        create("play") { dimension = "distribution" }
        create("online") {
            dimension = "distribution"
            applicationIdSuffix = ".online"
            versionNameSuffix = "-online"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("kairos")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // NewPipeExtractor uses java.nio APIs that need desugaring below API 33.
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.common)
    implementation(libs.media3.datasource)
    implementation(libs.kotlinx.coroutines.guava)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.palette.ktx)

    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)

    "onlineImplementation"(libs.newpipe.extractor)
    "onlineImplementation"(libs.okhttp)

    testImplementation(libs.junit)
}
