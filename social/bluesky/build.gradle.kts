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
        namespace = "dev.dimension.flare.social.bluesky"
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
                api(projects.core.model)
                api(projects.modules.account.api)
                api(projects.social.model)
                api(projects.foundation.network)
                api(projects.social.api)
                api(projects.social.microblog)
                api(libs.bluesky)
                api(libs.bluesky.oauth)
                implementation(dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.cryptography.provider.optimal)
                implementation(libs.twitter.parser)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
            }
        }
    }
}
