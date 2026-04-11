import dev.dimension.flare.gradle.FlarePlatform

plugins {
    id("flare.kmp")
}

flare {
    platforms(
        "dev.dimension.flare.core.common",
        FlarePlatform.Android,
        FlarePlatform.Desktop,
        FlarePlatform.Ios,
        FlarePlatform.WasmJs
    )
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
