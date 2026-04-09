plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
    explicitApi()
    applyDefaultHierarchyTemplate()
    android {
        compileSdk = libs.versions.compileSdk.get().toInt()
        namespace = "dev.dimension.flare.readability"
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
                implementation(libs.ksoup)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
