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
        namespace = "dev.dimension.flare.data.translation"
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
                api(projects.data.ai)
                api(projects.data.database)
                api(projects.data.datastore)
                implementation(projects.data.network)
                api(projects.ui.model)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
