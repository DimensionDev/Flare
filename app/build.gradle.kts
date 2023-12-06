import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktorfit)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "dev.dimension.flare"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.dimension.flare"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.compileSdk.get().toInt()
        versionCode = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: 1
        versionName = System.getenv("BUILD_VERSION")?.toString() ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        packaging {
            resources {
                excludes.add("/META-INF/{AL2.0,LGPL2.1}")
                excludes.add("DebugProbesKt.bin")
            }
        }
    }

    val file = rootProject.file("signing.properties")
    val hasSigningProps = file.exists()

    signingConfigs {
        if (hasSigningProps) {
            create("flare") {
                val signingProp = Properties()
                signingProp.load(file.inputStream())
                storeFile = rootProject.file(signingProp.getProperty("storeFile"))
                storePassword = signingProp.getProperty("storePassword")
                keyAlias = signingProp.getProperty("keyAlias")
                keyPassword = signingProp.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (hasSigningProps) {
                signingConfig = signingConfigs.getByName("flare")
            }
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasSigningProps) {
                signingConfig = signingConfigs.getByName("flare")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }
    kotlinOptions {
        jvmTarget = libs.versions.java.get()
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.complier.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.util)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material3)
    implementation(libs.material3WindowSizeClass)
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)
    implementation(libs.bundles.navigation)
    implementation(libs.bundles.kotlinx)
    implementation(platform(libs.koin.bom))
    implementation(libs.bundles.koin)
    implementation(libs.ktorfit.lib)
    ksp(libs.ktorfit.ksp)
    implementation(libs.bundles.coil)
    implementation(libs.bundles.ktor)
    implementation(libs.material.icons.extended)
    implementation(libs.molecule.runtime)
    implementation(libs.ktml)
    implementation(libs.bundles.accompanist)
    implementation(libs.nestedScrollView)
    implementation(libs.bundles.compose.destinations)
    ksp(libs.compose.destinations.ksp)
    lintChecks(libs.compose.lint.checks)
    implementation(libs.androidx.credentials)
    implementation(libs.zoomable)
    implementation(libs.bundles.compose.richtext)
    implementation(libs.composeIcons.fontAwesome)

    implementation(projects.shared)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
}

ktlint {
    version.set(libs.versions.ktlint)
    filter {
        exclude { element -> element.file.path.contains("build", ignoreCase = true) }
    }
}
