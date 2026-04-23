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
    compileOnly("com.android.tools.build:gradle:9.2.0")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:14.2.0")
}

gradlePlugin {
    plugins {
        create("flareMultiplatformLibrary") {
            id = "dev.dimension.flare.multiplatform-library"
            implementationClass = "dev.dimension.flare.buildlogic.FlareMultiplatformLibraryPlugin"
        }
        create("flareAndroidApplication") {
            id = "dev.dimension.flare.android-application"
            implementationClass = "dev.dimension.flare.buildlogic.FlareAndroidApplicationPlugin"
        }
        create("flareRootConventions") {
            id = "dev.dimension.flare.root-conventions"
            implementationClass = "dev.dimension.flare.buildlogic.FlareRootConventionsPlugin"
        }
    }
}
