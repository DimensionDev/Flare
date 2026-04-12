import dev.dimension.flare.gradle.FlarePlatform

plugins {
    id("flare.kmp")
    id("flare.ktorfit")
    id("flare.koin")
}

flare {
    platforms(
        "dev.dimension.flare.service.mastodon",
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
                implementation(libs.bundles.kotlinx)
                implementation(libs.bundles.ktor)
                implementation(libs.ktor.client.resources)
                implementation(libs.ksoup)
                implementation(libs.twitter.parser)
                implementation(projects.core.common)
                api(projects.service.core)
                api(projects.data.model)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
