// build.gradle.kts
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ktlint)
}

kotlin {
    applyDefaultHierarchyTemplate()
    linuxX64 {
        binaries {
            executable {
                entryPoint = "dev.dimension.flare.main"
            }
        }
    }
    sourceSets {
        val linuxMain by getting {
            dependencies {
                implementation(projects.shared)
                implementation(libs.kotlinx.coroutines.test)
                implementation("moe.tlaster.gtkkn:gtk4:0.0.1-SNAPSHOT")
            }
        }
    }
}


ktlint {
    version.set(libs.versions.ktlint)
    filter {
        exclude { element -> element.file.path.contains("build", ignoreCase = true) }
    }
}