import dev.dimension.flare.gradle.FlarePlatform

plugins {
    id("flare.kmp")
    id("flare.koin")
    alias(libs.plugins.compose.compiler)
}

flare {
    platforms(
        "dev.dimension.flare.service.core",
        FlarePlatform.Android,
        FlarePlatform.Desktop,
        FlarePlatform.Ios,
        FlarePlatform.WasmJs,
    )
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(dependencies.platform(libs.compose.bom))
                implementation(libs.compose.runtime)
                implementation(libs.datastore.core)
                implementation(libs.kotlinx.immutable)
                implementation(libs.kotlinx.serialization.json)
                api(projects.core.common)
                api(projects.data.model)
                api(projects.data.datastore)
                api(projects.data.database)
                api(libs.paging.common)
                api(libs.paging.compose)
            }
        }
    }
}
