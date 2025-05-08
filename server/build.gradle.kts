plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    explicitApi()
    applyDefaultHierarchyTemplate()
    listOf(
        macosX64(),
        macosArm64(),
        linuxX64(),
        mingwX64(),
    ).forEach { nativeTarget ->
        nativeTarget.apply {
            binaries {
                executable {
                    entryPoint = "main"
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.bundles.ktor.server)
                implementation(libs.logback.classic)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.ktor.server.test.host)
                implementation(libs.ktor.client.resources)
                implementation(libs.ktor.client.content.negotiation)
            }
        }
    }
}