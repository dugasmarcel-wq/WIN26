plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "rocks.gorjan.gokixp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.win26.launcher"
        minSdk = 29
        targetSdk = 36
        versionCode = System.getenv("WIN26_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("WIN26_VERSION_NAME") ?: "0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val signingPath = System.getenv("WIN26_KEYSTORE_PATH")
    val signingStorePassword = System.getenv("WIN26_KEYSTORE_PASSWORD")
    val signingAlias = System.getenv("WIN26_KEY_ALIAS")
    val signingKeyPassword = System.getenv("WIN26_KEY_PASSWORD")
    val hasReleaseSigning =
        !signingPath.isNullOrBlank() &&
        !signingStorePassword.isNullOrBlank() &&
        !signingAlias.isNullOrBlank() &&
        !signingKeyPassword.isNullOrBlank()

    signingConfigs {
        if (hasReleaseSigning) {
            create("win26Release") {
                storeFile = file(signingPath!!)
                storePassword = signingStorePassword
                keyAlias = signingAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("win26Release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // Kotlin jvmTarget follows compileOptions.targetCompatibility (AGP built-in Kotlin)
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("pl.droidsonroids.gif:android-gif-drawable:1.2.29")

    // WindowManager for foldable device detection
    implementation("androidx.window:window:1.3.0")

    // OSMDroid for OpenStreetMap

    // PDF rendering with PdfBox
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
