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
        namespace = "dev.dimension.flare.social.mastodon"
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
                api(projects.presentation.model)
                api(projects.network)
                api(projects.social.api)
                api(projects.social.microblog)
                implementation(projects.capability.account)
                implementation(projects.social.nodeinfo)
                api(libs.ktorfit.converters.response)
                implementation(dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.ksoup)
                implementation(libs.twitter.parser)
                implementation(libs.kotlinx.datetime)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
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
