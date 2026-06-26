import co.touchlab.skie.configuration.DefaultArgumentInterop
import dev.dimension.flare.buildlogic.FlarePlatform
import dev.dimension.flare.buildlogic.flare
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
        namespace = "dev.dimension.flare.apple.shared"
        platforms(
            FlarePlatform.IOS,
            FlarePlatform.MACOS,
        )
    }

    val commonExportedProjects =
        listOf(
            projects.shared,
            projects.social.bluesky,
            projects.social.fanbox,
            projects.social.mastodon,
            projects.social.misskey,
            projects.social.pixiv,
            projects.social.vvo,
            projects.social.xqt,
            projects.feature.loginApi,
            projects.feature.login,
            projects.feature.subscription,
            projects.feature.tab,
            projects.feature.agent,
        )

    listOf("iosArm64", "iosSimulatorArm64", "macosArm64")
        .map { targetName -> targets.getByName(targetName) as KotlinNativeTarget }
        .forEach { appleTarget ->
            appleTarget.binaries.framework {
                baseName = "KotlinSharedUI"
                isStatic = true

                if (appleTarget.name.startsWith("macos")) {
                    linkerOpts.add("-lsqlite3")
                }

                commonExportedProjects.forEach { exportedProject ->
                    export(exportedProject)
                }

                if (appleTarget.name.startsWith("ios")) {
                    export(projects.social.nostr)
                }
            }
        }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.shared)
                api(projects.social.bluesky)
                api(projects.social.fanbox)
                api(projects.social.mastodon)
                api(projects.social.misskey)
                api(projects.social.pixiv)
                api(projects.social.vvo)
                api(projects.social.xqt)
                api(projects.feature.loginApi)
                api(projects.feature.login)
                api(projects.feature.subscription)
                api(projects.feature.tab)
                api(projects.feature.agent)
                implementation(libs.compose.runtime)
// START Non-FOSS component
                implementation(libs.crashkios.crashlytics)
// END Non-FOSS component
                implementation(libs.kotlinx.serialization.protobuf)
                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.koin.annotations)
                implementation(libs.kotlinx.immutable)
            }
        }
        val iosMain by getting {
            dependencies {
                api(projects.social.nostr)
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
