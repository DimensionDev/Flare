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
        create("nativeKitMultiplatformLibrary") {
            id = "dev.dimension.compose.nativekit.multiplatform-library"
            implementationClass = "dev.dimension.compose.nativekit.buildlogic.NativeKitMultiplatformLibraryPlugin"
        }
        create("nativeKitRootConventions") {
            id = "dev.dimension.compose.nativekit.root-conventions"
            implementationClass = "dev.dimension.compose.nativekit.buildlogic.NativeKitRootConventionsPlugin"
        }
    }
}
