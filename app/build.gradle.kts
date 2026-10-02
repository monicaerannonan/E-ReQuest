plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.request"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.request"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility =
            JavaVersion.VERSION_11

        targetCompatibility =
            JavaVersion.VERSION_11
    }
}

dependencies {

    // =====================================================
    // ANDROIDX
    // =====================================================

    implementation(
        libs.androidx.activity.ktx
    )

    implementation(
        libs.androidx.appcompat
    )

    implementation(
        libs.androidx.constraintlayout
    )

    implementation(
        libs.androidx.core.ktx
    )

    implementation(
        libs.material
    )


    // =====================================================
    // ROOM DATABASE
    // LOCAL / OFFLINE DATABASE
    // =====================================================

    implementation(
        libs.androidx.room.runtime
    )

    implementation(
        libs.androidx.room.ktx
    )

    ksp(
        libs.androidx.room.compiler
    )


    // =====================================================
    // FIREBASE BOM
    // =====================================================

    implementation(
        platform(
            "com.google.firebase:firebase-bom:34.9.0"
        )
    )


    // =====================================================
    // CLOUD FIRESTORE
    // ONLINE DATABASE
    // =====================================================

    implementation(
        "com.google.firebase:firebase-firestore"
    )


    // =====================================================
    // FIREBASE AUTHENTICATION
    // =====================================================

    implementation(
        "com.google.firebase:firebase-auth"
    )


    // =====================================================
    // FIREBASE CLOUD MESSAGING
    // PUSH NOTIFICATIONS
    // =====================================================

    implementation(
        "com.google.firebase:firebase-messaging"
    )


    // =====================================================
    // TESTING
    // =====================================================

    testImplementation(
        libs.junit
    )

    androidTestImplementation(
        libs.androidx.espresso.core
    )

    androidTestImplementation(
        libs.androidx.junit
    )
}