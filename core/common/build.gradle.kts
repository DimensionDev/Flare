import dev.dimension.flare.gradle.FlarePlatform.Android
import dev.dimension.flare.gradle.FlarePlatform.Desktop
import dev.dimension.flare.gradle.FlarePlatform.Ios
import dev.dimension.flare.gradle.FlarePlatform.WasmJs

plugins {
    id("flare.kmp")
}

flare {
    namespace = "dev.dimension.flare.core.common"
    platforms(Android, Desktop, Ios, WasmJs)
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(dependencies.platform(libs.compose.bom))
                implementation(libs.compose.runtime)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.immutable)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.serialization.protobuf)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.commons.lang3)
            }
        }
    }
}
