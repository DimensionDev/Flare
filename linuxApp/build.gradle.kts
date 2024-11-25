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
                implementation("org.gtkkn:gtk4")
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