import dev.dimension.flare.gradle.FlarePlatform

plugins {
    id("flare.kmp")
}

flare {
    platforms(
        "dev.dimension.flare.readability",
        FlarePlatform.Android,
        FlarePlatform.Desktop,
        FlarePlatform.Ios,
        FlarePlatform.Macos,
        FlarePlatform.Linux,
        FlarePlatform.Windows
    )
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.ksoup)
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
