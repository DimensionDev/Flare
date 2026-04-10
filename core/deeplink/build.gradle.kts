import dev.dimension.flare.gradle.FlarePlatform.Android
import dev.dimension.flare.gradle.FlarePlatform.Desktop
import dev.dimension.flare.gradle.FlarePlatform.Ios
import dev.dimension.flare.gradle.FlarePlatform.WasmJs

plugins {
    id("flare.kmp")
}

flare {
    namespace = "dev.dimension.flare.core.deeplink"
    platforms(Android, Desktop, Ios, WasmJs)
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.ktor.http)
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
