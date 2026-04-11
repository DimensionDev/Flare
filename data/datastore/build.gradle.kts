import dev.dimension.flare.gradle.FlarePlatform

plugins {
    id("flare.kmp")
    id("flare.koin")
}

flare {
    platforms(
        "dev.dimension.flare.data.datastore",
        FlarePlatform.Android,
        FlarePlatform.Desktop,
        FlarePlatform.Ios,
        FlarePlatform.WasmJs
    )
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(projects.data.model)
                implementation(libs.datastore.core)
                implementation(libs.okio)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.protobuf)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
