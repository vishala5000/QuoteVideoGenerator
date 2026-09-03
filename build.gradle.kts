// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}

// Add build script configuration for GitHub Actions
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
val commitHash = System.getenv("GITHUB_SHA")?.take(7) ?: "local"

subprojects {
    afterEvaluate {
        if (project.hasProperty("android")) {
            android {
                defaultConfig {
                    versionCode = 1 + buildNumber
                    versionName = "1.0.$buildNumber"
                }
            }
        }
    }
}
