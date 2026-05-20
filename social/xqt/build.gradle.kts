import dev.dimension.flare.buildlogic.FlarePlatform
import dev.dimension.flare.buildlogic.flare

plugins {
    id("dev.dimension.flare.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktorfit)
}

kotlin {
    flare {
        namespace = "dev.dimension.flare.social.xqt"
        platforms(
            FlarePlatform.ANDROID,
            FlarePlatform.JVM,
            FlarePlatform.IOS,
            FlarePlatform.WEB,
        )
        ksp(
            libs.ktorfit.ksp,
        )
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
                api(projects.modules.account.api)
                api(projects.social.model)
                api(projects.foundation.network)
                api(projects.social.api)
                api(projects.social.microblog)
                api(libs.ktorfit.converters.response)
                implementation(projects.foundation.database)
                implementation(dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.cryptography.provider.optimal)
                implementation(libs.kotlin.codepoints.deluxe)
                implementation(libs.kotlinx.datetime)
                implementation(libs.twitter.parser)
            }
        }
    }
}

ktorfit {
    compilerPluginVersion.set("2.3.3")
}
