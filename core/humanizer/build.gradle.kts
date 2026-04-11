import dev.dimension.flare.gradle.FlarePlatform

plugins {
    id("flare.kmp")
    id("flare.koin")
}

flare {
    platforms(
        "dev.dimension.flare.core.humanizer",
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
                implementation(libs.kotlinx.datetime)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.prettytime)
            }
        }
    }
}
