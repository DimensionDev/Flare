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
        namespace = "dev.dimension.flare.data.database"
        platforms(
            FlarePlatform.ANDROID,
            FlarePlatform.JVM,
            FlarePlatform.IOS,
        )
        ksp(libs.room.compiler)
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.core.model)
                api(libs.kotlinx.coroutines.core)
                api(libs.room.runtime)
                api(libs.sqlite.bundled)
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}
