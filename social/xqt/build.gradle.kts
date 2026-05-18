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
        )
        ksp(
            libs.ktorfit.ksp,
        )
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.core.common)
                api(projects.core.model)
                api(projects.ui.model)
                api(projects.data.network)
                api(projects.social.api)
                api(projects.social.microblog)
                api(libs.ktorfit.converters.response)
                implementation(projects.data.repository)
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

afterEvaluate {
    tasks {
        configureEach {
            if (this.name != "kspCommonMainKotlinMetadata" && this.name.startsWith("ksp")) {
                this.dependsOn("kspCommonMainKotlinMetadata")
            }
        }
    }
}
