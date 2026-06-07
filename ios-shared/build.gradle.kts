import dev.dimension.flare.buildlogic.FlarePlatform
import dev.dimension.flare.buildlogic.flare
import co.touchlab.skie.configuration.DefaultArgumentInterop
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("dev.dimension.flare.multiplatform-library")
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.skie)
}

kotlin {
    flare {
        namespace = "dev.dimension.flare.ios.shared"
        platforms(
            FlarePlatform.IOS,
        )
    }

    listOf("iosArm64", "iosSimulatorArm64")
        .map { targetName -> targets.getByName(targetName) as KotlinNativeTarget }
        .forEach { appleTarget ->
            appleTarget.binaries.framework {
                baseName = "KotlinSharedUI"
                isStatic = true
                export(projects.shared)
                export(projects.social.bluesky)
                export(projects.social.mastodon)
                export(projects.social.misskey)
                export(projects.social.nostr)
                export(projects.social.pixiv)
                export(projects.social.vvo)
                export(projects.social.xqt)
                export(projects.feature.loginApi)
                export(projects.feature.agent)
                export(projects.feature.login)
                export(projects.feature.subscription)
                export(projects.feature.tab)
            }
        }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.shared)
                api(projects.social.bluesky)
                api(projects.social.mastodon)
                api(projects.social.misskey)
                api(projects.social.nostr)
                api(projects.social.pixiv)
                api(projects.social.vvo)
                api(projects.social.xqt)
                api(projects.feature.agent)
                api(projects.feature.login)
                api(projects.feature.subscription)
                api(projects.feature.tab)
                implementation(libs.compose.runtime)
                implementation(libs.kotlinx.serialization.protobuf)
                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.koin.annotations)
                implementation(libs.kotlinx.immutable)
            }
        }
    }
}

skie {
    analytics {
        disableUpload.set(true)
        enabled.set(false)
    }
    features {
        group {
            DefaultArgumentInterop.Enabled(true)
        }
        enableFlowCombineConvertorPreview = true
    }
}
