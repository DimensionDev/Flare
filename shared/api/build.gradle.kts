import dev.dimension.flare.gradle.FlarePlatform.Android
import dev.dimension.flare.gradle.FlarePlatform.Desktop
import dev.dimension.flare.gradle.FlarePlatform.Ios
import dev.dimension.flare.gradle.FlarePlatform.Linux
import dev.dimension.flare.gradle.FlarePlatform.Macos
import dev.dimension.flare.gradle.FlarePlatform.Windows

plugins {
    id("flare.kmp")
}

flare {
    platforms( "dev.dimension.flare.shared.api", Android, Desktop, Ios, Macos, Linux, Windows)
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.ktor.resources)
            }
        }
    }
}
