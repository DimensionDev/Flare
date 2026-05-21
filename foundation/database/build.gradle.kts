import dev.dimension.flare.buildlogic.FlarePlatform
import dev.dimension.flare.buildlogic.flare

plugins {
    id("dev.dimension.flare.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    flare {
        namespace = "dev.dimension.flare.foundation.database"
        platforms(
            FlarePlatform.ANDROID,
            FlarePlatform.JVM,
            FlarePlatform.IOS,
            FlarePlatform.WEB,
        )
        ksp(libs.room.compiler)
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
                implementation(projects.core.common)
                api(projects.core.model)
                api(projects.modules.account.model)
                api(projects.modules.translation.model)
                api(projects.social.model)
                api(projects.social.rss.model)
                api(libs.kotlinx.coroutines.core)
                api(libs.paging.common)
                api(libs.room.runtime)
                api(libs.room.paging)
                api(libs.sqlite)
                implementation(libs.sqlite.async)
                implementation(libs.kotlinx.serialization.json)
                api(dependencies.platform(libs.koin.bom))
                api(libs.koin.core)
            }
        }

        val nonWebMain by getting {
            dependencies {
                api(libs.sqlite.bundled)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(projects.foundation.filesystem)
            }
        }

        val wasmJsMain by getting {
            dependencies {
                implementation(libs.sqlite.web)
                implementation(libs.kotlinx.browser)
                implementation(npm("@androidx/sqlite-web-worker", file("sqlite-web-worker")))
            }
        }
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}
