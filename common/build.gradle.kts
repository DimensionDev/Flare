plugins {
    kotlin("multiplatform")
    kotlin("native.cocoapods")
    id("com.android.library")
}

kotlin {
    targetHierarchy.default()
    androidTarget()
    ios()
    cocoapods {
        version = "1.0.0"
        summary = "Shared Module for Flare"
        // homepage = "Link to the Shared Module homepage"
        ios.deploymentTarget = "14.1"
        // podfile = project.file("../iosApp/Podfile")
        framework {
            baseName = "flare-common"
            isStatic = true
        }
        extraSpecAttributes["resources"] =
            "['src/commonMain/resources/**', 'src/iosMain/resources/**']"
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
            }
        }
        val androidMain by getting {
            dependencies {
            }
        }
        val iosMain by getting {
            dependencies {
            }
        }
    }
}

android {
    compileSdk = 34
    namespace = "dev.dimension.flare.common"

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        minSdk = 21
    }
    kotlin.jvmToolchain(17)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}