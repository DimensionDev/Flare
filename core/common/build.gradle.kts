import dev.dimension.flare.gradle.FlarePlatform

plugins {
    id("flare.kmp")
    id("flare.koin")
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
                implementation(libs.bundles.ktorfit)
                implementation(libs.bundles.ktor)
                implementation(libs.paging.common)
                implementation(libs.okio)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.commons.lang3)
                implementation(libs.ktor.client.okhttp)
            }
        }
        val appleMain by getting {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        val webMain by getting {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }
    }
}
