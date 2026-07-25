import dev.dimension.flareui.buildlogic.FlareUiPlatform
import dev.dimension.flareui.buildlogic.flareUi

plugins {
    id("dev.dimension.flareui.multiplatform-library")
    id("dev.dimension.flareui.resources")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
}

flareUiResources {
    namespace.set("demo")
    accessorName.set("DemoResources")
}

kotlin {
    flareUi {
        namespace = "dev.dimension.flare.flareui.demo.shared"
        platforms(
            FlareUiPlatform.ANDROID,
            FlareUiPlatform.IOS,
            FlareUiPlatform.MACOS,
        )
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":core"))
            }
        }
    }
}
