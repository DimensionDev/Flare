import dev.dimension.flare.buildlogic.FlarePlatform
import dev.dimension.flare.buildlogic.flare

plugins {
    id("dev.dimension.flare.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    flare {
        namespace = "dev.dimension.flare.feature.subscription"
        platforms(
            FlarePlatform.ANDROID,
            FlarePlatform.JVM,
            FlarePlatform.IOS,
            FlarePlatform.WEB,
            FlarePlatform.MACOS,
        )
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.shared)
                implementation(dependencies.platform(libs.compose.bom))
                implementation(libs.compose.runtime)
                implementation(libs.bundles.kotlinx)
                implementation(dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.bundles.ktor)
                implementation(libs.ktor.client.resources)
                implementation(libs.ksoup)
                implementation(libs.readability)
                implementation(libs.xmlUtil)
                implementation(libs.molecule.runtime)
                implementation(libs.paging.common)
                implementation(libs.paging.compose)
                implementation(libs.kotlinx.immutable)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.paging.testing)
                implementation(libs.ktor.client.mock)
            }
        }
        val androidJvmMain by getting {
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }
        val appleMain by getting {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
    }
}
