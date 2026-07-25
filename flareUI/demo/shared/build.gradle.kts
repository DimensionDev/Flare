import dev.dimension.flare.buildlogic.FlarePlatform
import dev.dimension.flare.buildlogic.flare

plugins {
    id("dev.dimension.flare.multiplatform-library")
    id("dev.dimension.flare.ui-resources")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
}

flareUiResources {
    namespace.set("demo")
    accessorName.set("DemoResources")
}

kotlin {
    flare {
        namespace = "dev.dimension.flare.flareui.demo.shared"
        platforms(
            FlarePlatform.ANDROID,
            FlarePlatform.IOS,
            FlarePlatform.MACOS,
        )
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.flareUI.core)
            }
        }
    }
}
