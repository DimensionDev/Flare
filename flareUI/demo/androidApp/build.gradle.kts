import org.gradle.api.JavaVersion

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.dimension.flare.flareui.demo.android"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = 0
        }
    }
    defaultConfig {
        applicationId = "dev.dimension.flare.flareui.demo"
        minSdk {
            version = release(libs.versions.minSdk.get().toInt())
        }
        targetSdk {
            version = release(libs.versions.compileSdk.get().toInt())
        }
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }
}

dependencies {
    implementation(project(":demo:shared"))
    implementation(libs.androidx.activity)
    testImplementation(libs.junit)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
