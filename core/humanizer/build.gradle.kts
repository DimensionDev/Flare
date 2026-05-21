import dev.dimension.flare.buildlogic.FlarePlatform
import dev.dimension.flare.buildlogic.flare

plugins {
    id("dev.dimension.flare.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    flare {
        namespace = "dev.dimension.flare.core.humanizer"
        platforms(
            FlarePlatform.ANDROID,
            FlarePlatform.JVM,
            FlarePlatform.IOS,
            FlarePlatform.WEB,
        )
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(dependencies.platform(libs.koin.bom))
                api(libs.koin.core)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.kotlinx.datetime)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.kotlinx.datetime)
                implementation(libs.prettytime)
            }
        }
        val appleMain by getting {
            dependencies {
                implementation(libs.kotlinx.datetime)
            }
        }
    }
}
