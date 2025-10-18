plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
    explicitApi()
    applyDefaultHierarchyTemplate()
    androidLibrary {
        compileSdk = libs.versions.compileSdk.get().toInt()
        namespace = "dev.dimension.flare.shared.api"
        minSdk = libs.versions.minSdk.get().toInt()
    }
    jvm()
    macosArm64()
    linuxX64()
    mingwX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.ktor.resources)
            }
        }
        val commonTest by getting {
            dependencies {
            }
        }
    }
}

