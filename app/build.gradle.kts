// START Non-FOSS component
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsPlugin
// END Non-FOSS component
import com.google.gms.googleservices.GoogleServicesPlugin
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktorfit)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.google.services) apply false
    // START Non-FOSS component
    alias(libs.plugins.firebase.crashlytics) apply false
    // END Non-FOSS component
    alias(libs.plugins.compose.compiler)
    id("kotlin-parcelize")
}

// START Non-FOSS component
if (project.file("google-services.json").exists()) {
    apply<GoogleServicesPlugin>()
    apply<CrashlyticsPlugin>()
}
// END Non-FOSS component

android {
    namespace = "dev.dimension.flare"
    compileSdk = libs.versions.compileSdk.get().toInt()
    val fdroid = rootProject.file("fdroid.properties")
    val fdroidProp = Properties()
    fdroidProp.load(fdroid.inputStream())

    defaultConfig {
        applicationId = "dev.dimension.flare"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.compileSdk.get().toInt()
        versionCode = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: fdroidProp.getProperty("versionCode")?.toIntOrNull() ?: 1
        versionName = System.getenv("BUILD_VERSION")?.toString() ?: fdroidProp.getProperty("versionName")?.toString() ?: "0.0.0"

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
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
//    composeOptions {
//        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
//    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        disable.add("MissingTranslation")
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.bundles.kotlinx)
    implementation(platform(libs.koin.bom))
    implementation(libs.bundles.koin)
    implementation(libs.ktorfit.lib)
    ksp(libs.ktorfit.ksp)
    implementation(libs.bundles.coil3)
    implementation(libs.bundles.coil3.extensions)
    implementation(libs.bundles.ktor)
    implementation(libs.molecule.runtime)
    implementation(libs.ksoup)
    implementation(libs.bundles.accompanist)
    lintChecks(libs.compose.lint.checks)
    implementation(libs.composeIcons.fontAwesome)
    implementation(libs.datastore)
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.bundles.media3)
    implementation(libs.compose.placeholder.material3)
    implementation(libs.swiper)
    implementation(libs.reorderable)
    implementation(libs.androidx.window)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.compose.webview)
    implementation(projects.shared)
    implementation(projects.composeUi)
    implementation(libs.androidx.splash)
    implementation(libs.materialKolor)
    implementation(libs.colorpicker.compose)
    implementation(libs.material.motion.compose)
    implementation(libs.nestedScrollView)
    implementation(libs.precompose.molecule)
    implementation(libs.compose.placeholder.material3)
    implementation(libs.webkit)
    implementation(libs.bundles.navigation3)
    implementation(libs.haze)
    implementation(libs.haze.materials)

    // START Non-FOSS component
    if (project.file("google-services.json").exists()) {
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.crashlytics.ktx)
        implementation(libs.firebase.analytics.ktx)
        implementation(libs.kotlinx.coroutines.play.services)
    }
    // END Non-FOSS component

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

if (project.file("google-services.json").exists()) {
    afterEvaluate {
        val uploadCrashlyticsMappingFileRelease by tasks
        val processDebugGoogleServices by tasks
        uploadCrashlyticsMappingFileRelease.dependsOn(processDebugGoogleServices)
    }
}
