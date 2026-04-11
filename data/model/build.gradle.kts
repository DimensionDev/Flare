
import dev.dimension.flare.gradle.FlarePlatform

plugins {
    id("flare.kmp")
    id("flare.koin")
}

flare {
    platforms(
        "dev.dimension.flare.data.model",
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
                implementation(projects.core.common)
                implementation(projects.core.humanizer)
                implementation(dependencies.platform(libs.compose.bom))
                implementation(libs.compose.runtime)
                implementation(libs.kotlin.codepoints.deluxe)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.immutable)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.serialization.protobuf)
                implementation(libs.okio)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
