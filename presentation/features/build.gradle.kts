import dev.dimension.flare.buildlogic.FlarePlatform
import dev.dimension.flare.buildlogic.flare

plugins {
    id("dev.dimension.flare.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktorfit)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.room)
}

kotlin {
    flare {
        namespace = "dev.dimension.flare.presentation.features"
        platforms(
            FlarePlatform.ANDROID,
            FlarePlatform.JVM,
            FlarePlatform.IOS,
            FlarePlatform.WEB,
        )
        ksp(
            libs.ktorfit.ksp,
            libs.room.compiler,
        )
    }
    android {
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            execution = "HOST"
        }
    }

    compilerOptions {
        allWarningsAsErrors.set(false)
    }

    sourceSets {
        all {
            languageSettings {
                optIn("kotlin.uuid.ExperimentalUuidApi")
            }
        }
        val commonMain by getting {
            dependencies {
                api(projects.core.common)
                api(projects.core.model)
                api(projects.presentation.model)
                implementation(projects.core.deeplink)
                implementation(projects.capability.ai)
                implementation(projects.capability.account)
                implementation(projects.foundation.database)
                api(projects.capability.settings)
                api(projects.capability.draft)
                implementation(projects.capability.local)
                implementation(projects.capability.translation)
                implementation(projects.foundation.network)
                api(projects.social.api)
                implementation(projects.social.bluesky)
                implementation(projects.social.mastodon)
                implementation(projects.social.misskey)
                api(projects.social.microblog)
                implementation(projects.social.nodeinfo)
                implementation(projects.social.rss)
                implementation(projects.social.vvo)
                implementation(projects.social.xqt)
                api(projects.presentation.runtime)
                implementation(dependencies.platform(libs.compose.bom))
                implementation(libs.compose.runtime)
                implementation(libs.bundles.kotlinx)
                implementation(dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                api(libs.paging.common)
                api(libs.paging.compose)
                implementation(libs.bundles.ktorfit)
                implementation(libs.bundles.ktor)
                implementation(libs.okio)
                implementation(libs.kotlin.codepoints.deluxe)
                implementation(libs.ksoup)
                implementation(libs.mfm.multiplatform)
                implementation(libs.twitter.parser)
                implementation(libs.molecule.runtime)
                api(libs.bluesky)
                api(libs.bluesky.oauth)
                implementation(libs.room.runtime)
                implementation(libs.room.paging)
                implementation(libs.kotlinx.serialization.protobuf)
                implementation(libs.xmlUtil)
                implementation(libs.ktor.client.resources)
                implementation(libs.cryptography.provider.optimal)
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
        val nonWebMain by getting {
            dependencies {
                implementation(projects.social.nostr)
            }
        }
        val androidJvmMain by getting {
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.core.ktx)
                implementation(libs.koin.android)
                implementation(libs.koin.compose)
                implementation(libs.activity.compose)
            }
        }
        val androidDeviceTest by getting {
            dependencies {
                implementation(libs.junit)
                implementation(libs.robolectric)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.commons.lang3)
                implementation(libs.prettytime)
                implementation(libs.jna)
            }
        }
        val appleMain by getting {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

ktorfit {
    compilerPluginVersion.set("2.3.3")
}
