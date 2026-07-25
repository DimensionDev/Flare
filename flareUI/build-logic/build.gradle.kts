plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    compileOnly("com.android.tools.build:gradle:9.3.0")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:14.2.0")
}

kotlin {
    jvmToolchain(25)
}

gradlePlugin {
    plugins {
        create("flareUiMultiplatformLibrary") {
            id = "dev.dimension.flareui.multiplatform-library"
            implementationClass = "dev.dimension.flareui.buildlogic.FlareUiMultiplatformLibraryPlugin"
        }
        create("flareUiAndroidApplication") {
            id = "dev.dimension.flareui.android-application"
            implementationClass = "dev.dimension.flareui.buildlogic.FlareUiAndroidApplicationPlugin"
        }
        create("flareUiRootConventions") {
            id = "dev.dimension.flareui.root-conventions"
            implementationClass = "dev.dimension.flareui.buildlogic.FlareUiRootConventionsPlugin"
        }
        create("flareUiResources") {
            id = "dev.dimension.flareui.resources"
            implementationClass = "dev.dimension.flareui.buildlogic.FlareUiResourcesPlugin"
        }
    }
}
