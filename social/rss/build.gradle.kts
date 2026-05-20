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
        namespace = "dev.dimension.flare.social.rss"
        platforms(
            FlarePlatform.ANDROID,
            FlarePlatform.JVM,
            FlarePlatform.IOS,
            FlarePlatform.WEB,
        )
    }

    compilerOptions {
        allWarningsAsErrors.set(false)
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.core.common)
                api(projects.core.model)
                api(projects.foundation.database)
                api(projects.foundation.network)
                api(projects.social.microblog)
                api(projects.modules.account.api)
                api(projects.social.model)
                implementation(projects.social.api)
                implementation(dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.kotlinx.datetime)
                implementation(libs.ksoup)
                implementation(libs.readability)
                implementation(libs.xmlUtil)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
