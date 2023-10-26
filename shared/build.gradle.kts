plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktorfit)
    alias(libs.plugins.skie)
    alias(libs.plugins.compose.jb)
//    alias(libs.plugins.ktlint)
}

kotlin {
    targetHierarchy.default()
//    mingwX64()

    androidTarget()

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
//            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.bundles.sqldelight)
                implementation(libs.bundles.kotlinx)
                implementation(libs.koject.core)
                implementation(libs.koject.compose.core)
                 implementation(libs.paging.common)
                implementation(libs.ktorfit.lib)
                implementation(libs.bundles.ktor)
                implementation(libs.okio)
                implementation(libs.uuid)
                implementation(libs.molecule.runtime)
                implementation(libs.kermit)
                api(libs.paging.compose.common)
                implementation(libs.ktml)
                implementation(libs.mfm.multiplatform)
                implementation(libs.bluesky)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.sqldelight.android.driver)
            }
        }
        val nativeMain by getting {
            dependencies {
                implementation(libs.sqldelight.native.driver)
//                implementation(libs.sqliter.driver)
                implementation(libs.stately.isolate)
                implementation(libs.stately.iso.collections)
            }
        }
    }
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("dev.dimension.flare.data.database.app")
            srcDirs("src/commonMain/sqldelight/app")
        }
        create("CacheDatabase") {
            packageName.set("dev.dimension.flare.data.database.cache")
            srcDirs("src/commonMain/sqldelight/cache")
        }
        create("VersionDatabase") {
            packageName.set("dev.dimension.flare.data.database.version")
            srcDirs("src/commonMain/sqldelight/version")
        }
    }
}

android {
    compileSdk = libs.versions.compileSdk.get().toInt()
    namespace = "dev.dimension.flare.shared"

//    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
//    sourceSets["main"].res.srcDirs("src/androidMain/res")
//    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

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

dependencies {
    val kspTarget = listOf(
        "kspAndroid",
        "kspIosX64",
        "kspIosArm64",
        "kspIosSimulatorArm64",
    )
    add("kspAndroid", libs.koject.processor.lib)
    add("kspIosX64", libs.koject.processor.app)
    add("kspIosArm64", libs.koject.processor.app)
    add("kspIosSimulatorArm64", libs.koject.processor.app)

    kspTarget.forEach { target ->
        add(target, libs.ktorfit.ksp)
    }
}

//ksp {
//    arg("moduleName", project.name)
//}

//ktlint {
//    version.set(libs.versions.ktlint)
//    filter {
//        exclude { element ->
//            element.file.path.contains("generated/")
//        }
//    }
//}

skie {
    analytics {
        disableUpload.set(true)
        enabled.set(false)
    }
}