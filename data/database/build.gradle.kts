import dev.dimension.flare.gradle.FlarePlatform
import dev.dimension.flare.gradle.addKspDependencyForTargets

plugins {
    id("flare.kmp")
    id("flare.ksp")
    alias(libs.plugins.room)
    id("flare.koin")
}

flare {
    platforms(
        "dev.dimension.flare.data.database",
        FlarePlatform.Android,
        FlarePlatform.Desktop,
        FlarePlatform.Ios,
        FlarePlatform.WasmJs
    )
}

kotlin {
    addKspDependencyForTargets(project, libs.room.compiler)

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(projects.core.common)
                api(projects.data.model)
                api(libs.room.runtime)
                api(libs.room.paging)
                api(libs.paging.common)
                implementation(libs.bluesky)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.serialization.protobuf)
                implementation(libs.kotlinx.immutable)
            }
        }
        val nonWebMain by getting {
            dependencies {
                implementation(libs.sqlite.bundled)
            }
        }
        val webMain by getting {
            dependencies {
                implementation(libs.sqlite.web)
                implementation(libs.kotlinx.browser)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.core.ktx)
            }
        }
    }
}

room3 {
    schemaDirectory("${projectDir}/schemas")
}
