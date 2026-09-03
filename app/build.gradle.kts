plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.videogenerator"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.videogenerator"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signing config can be added via environment variables
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Video processing
    implementation("com.arthenica:mobile-ffmpeg-full:4.4.LTS")
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("androidx.media3:media3-ui:1.2.0")
    implementation("androidx.media3:media3-common:1.2.0")
    
    // File I/O
    implementation("commons-io:commons-io:2.15.1")
    implementation("com.github.kittinunf.fuel:fuel:2.3.1")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

// Add task to verify assets exist
tasks.register("verifyAssets") {
    doLast {
        val assetsDir = file("src/main/assets")
        if (!assetsDir.exists()) {
            println("Warning: assets directory doesn't exist, creating it...")
            assetsDir.mkdirs()
        }
        
        val fontFile = file("src/main/assets/font.ttf")
        if (!fontFile.exists()) {
            println("Warning: font.ttf not found in assets!")
        }
        
        val audioFile = file("src/main/assets/bg.mp3")
        if (!audioFile.exists()) {
            println("Warning: bg.mp3 not found in assets!")
        }
    }
}

tasks.whenTaskAdded {
    if (name == "preBuild") {
        dependsOn("verifyAssets")
    }
}
