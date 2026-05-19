import dev.dimension.flare.buildlogic.FlarePlatform
import dev.dimension.flare.buildlogic.flare

plugins {
    id("dev.dimension.flare.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    flare {
        namespace = "dev.dimension.flare.social.nostr"
        platforms(
            FlarePlatform.ANDROID,
            FlarePlatform.JVM,
            FlarePlatform.IOS,
        )
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.core.common)
                api(projects.core.model)
                api(projects.presentation.model)
                api(projects.storage.database)
                api(projects.social.api)
                api(projects.social.microblog)
                implementation(projects.network)
                implementation(libs.bundles.ktor)
                implementation(libs.cryptography.provider.optimal)
                implementation(libs.nostr.sdk.kmp)
                implementation(dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
            }
        }
    }
}
