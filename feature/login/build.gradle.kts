import dev.dimension.flare.buildlogic.FlarePlatform
import dev.dimension.flare.buildlogic.flare

plugins {
    id("dev.dimension.flare.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    flare {
        namespace = "dev.dimension.flare.feature.login"
        platforms(
            FlarePlatform.ANDROID,
            FlarePlatform.JVM,
            FlarePlatform.IOS,
        )
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.shared)
                api(projects.social.bluesky)
                api(projects.social.nostr)
                implementation(projects.social.mastodon)
                implementation(projects.social.misskey)
                implementation(dependencies.platform(libs.compose.bom))
                implementation(libs.compose.runtime)
                api(dependencies.platform(libs.koin.bom))
                api(libs.koin.core)
                implementation(libs.bundles.kotlinx)
            }
        }
    }
}
