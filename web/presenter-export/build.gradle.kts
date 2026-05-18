import dev.dimension.flare.buildlogic.FlarePlatform
import dev.dimension.flare.buildlogic.flare

plugins {
    id("dev.dimension.flare.multiplatform-library")
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    flare {
        namespace = "dev.dimension.flare.web.presenter.export"
        platforms(FlarePlatform.WEB)
    }

    sourceSets {
        val wasmJsMain by getting {
            dependencies {
                implementation(projects.presenter.runtime)
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}
