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
}

dependencies {
    implementation(project(":demo:shared"))
}
