plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.skie)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
    explicitApi()
    applyDefaultHierarchyTemplate()
    androidLibrary {
        compileSdk = libs.versions.compileSdk.get().toInt()
        namespace = "dev.dimension.flare.shared.ui"
        minSdk = libs.versions.minSdk.get().toInt()
        experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { appleTarget ->
        appleTarget.binaries.framework {
            baseName = "sharedUI"
//            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(projects.shared)
                implementation(compose.ui)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.components.resources)
                implementation(libs.composeIcons.fontAwesome)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(project.dependencies.platform(libs.compose.bom))
                implementation(libs.compose.foundation)
            }
        }
        val appleMain by getting {
            dependencies {
                implementation(libs.cupertino)
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

skie {
    analytics {
        disableUpload.set(true)
        enabled.set(false)
    }
    features {
        enableSwiftUIObservingPreview = true
    }
}

compose.resources {
//    customDirectory(
//        sourceSetName = "commonMain",
//        directoryProvider = provider { layout.projectDirectory.dir("composeMain").dir("composeResources") }
//    )
    packageOfResClass = "dev.dimension.flare"
    generateResClass = always
}
