import dev.dimension.flare.gradle.FlarePlatform

plugins {
    id("flare.kmp")
}

flare {
    platforms(
        "dev.dimension.flare.core.deeplink",
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
                implementation(libs.ktor.http)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
