plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.veripaytransactionmonitor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.veripaytransactionmonitor"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    kotlinOptions {
        jvmTarget = "11"
    }

    // ✅ Optional (if you use vector icons like check/error)
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // ✅ Firebase BoM (manages versions for all Firebase libs)
    implementation(platform("com.google.firebase:firebase-bom:33.3.0"))

    // ✅ Firebase services
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")

    // ✅ AndroidX and Google libraries
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // ✅ Material Design components (needed for dialogs)
    implementation("com.google.android.material:material:1.12.0")

    // ✅ Navigation Component (for findNavController and safe navigation)
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.2")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.2")

    // ✅ Networking (Volley)
    implementation("com.android.volley:volley:1.2.1")

    // ✅ Optional (for success/failure animations)
    implementation("com.airbnb.android:lottie:6.5.2")

    // ✅ Testing libraries
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
