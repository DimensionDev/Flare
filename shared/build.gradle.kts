import java.util.Locale

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktorfit)
    alias(libs.plugins.skie)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.room)
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    applyDefaultHierarchyTemplate()

    androidTarget()

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
        macosArm64(),
        macosX64(),
    ).forEach { appleTarget ->
        appleTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
            embedBitcode(org.jetbrains.kotlin.gradle.plugin.mpp.BitcodeEmbeddingMode.DISABLE)
        }
    }
    targets.forEach { target ->
        target.name.takeIf {
            it != "metadata"
        }?.let {
            "ksp${it.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }}"
        }?.let {
            dependencies.add(it, libs.ktorfit.ksp)
            dependencies.add(it, libs.room.compiler)
        }
    }

    sourceSets {
        all {
            languageSettings {
                optIn("kotlin.uuid.ExperimentalUuidApi")
            }
        }
        val commonMain by getting {
            dependencies {
                implementation(libs.bundles.kotlinx)
                implementation(dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                api(libs.paging.common)
                implementation(libs.bundles.ktorfit)
                implementation(libs.bundles.ktor)
                implementation(libs.okio)
                implementation(libs.napier)
                implementation(libs.kotlin.codepoints.deluxe)
                implementation(libs.ksoup)
                implementation(libs.mfm.multiplatform)
                implementation(libs.twitter.parser)
                implementation(libs.molecule.runtime)
                api(libs.bluesky)
                implementation(libs.room.runtime)
                implementation(libs.room.paging)
                implementation(libs.sqlite.bundled)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val composeMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.components.resources)
                implementation(libs.composeIcons.fontAwesome)
                implementation(libs.bundles.coil3)
                implementation(libs.compose.placeholder.material3)
            }
        }
        val androidMain by getting {
            dependsOn(composeMain)
            dependencies {
                implementation(project.dependencies.platform(libs.compose.bom))
                implementation(libs.compose.foundation)
            }
        }
        val appleMain by getting {
            dependsOn(composeMain)
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        val nativeMain by getting {
            dependencies {
                implementation(libs.stately.isolate)
                implementation(libs.stately.iso.collections)
            }
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

android {
    compileSdk = libs.versions.compileSdk.get().toInt()
    namespace = "dev.dimension.flare.shared"

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }
    kotlin {
        jvmToolchain(libs.versions.java.get().toInt())
    }
}

ktlint {
    version.set(libs.versions.ktlint)
    filter {
        exclude { element -> element.file.path.contains("build", ignoreCase = true) }
        exclude { element -> element.file.absolutePath.contains("data/network/misskey/api/", ignoreCase = true) }
        exclude { element -> element.file.absolutePath.contains("data/network/xqt/", ignoreCase = true) }
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
    customDirectory(
        sourceSetName = "commonMain",
        directoryProvider = provider { layout.projectDirectory.dir("composeMain").dir("composeResources") }
    )
    packageOfResClass = "dev.dimension.flare"
    generateResClass = always
}

afterEvaluate {
//    val kspCommonMainKotlinMetadata by tasks
    val runKtlintFormatOverCommonMainSourceSet by tasks
    val runKtlintCheckOverCommonMainSourceSet by tasks
    runKtlintFormatOverCommonMainSourceSet.dependsOn("kspCommonMainKotlinMetadata")
    runKtlintCheckOverCommonMainSourceSet.dependsOn("kspCommonMainKotlinMetadata")
}