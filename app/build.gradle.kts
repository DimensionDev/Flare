@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktorfit)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "dev.dimension.flare"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.dimension.flare"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.0"
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
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)
    implementation(libs.bundles.paging)
    implementation(libs.bundles.navigation)
    implementation(libs.bundles.kotlinx)
    implementation(libs.bundles.koject)
    ksp(libs.koject.processor.app)
    implementation(libs.ktorfit.lib)
    ksp(libs.ktorfit.ksp)
    implementation(libs.bundles.coil)
    implementation(libs.bundles.ktor)
    implementation(libs.twitter.parser)
    implementation(libs.material.icons.extended)
    implementation(libs.molecule.runtime)
    implementation(libs.jsoup)
    implementation(libs.bundles.accompanist)
    implementation(libs.store)
    implementation(libs.nestedScrollView)
    implementation(libs.bundles.compose.destinations)
    ksp(libs.compose.destinations.ksp)
    lintChecks(libs.compose.lint.checks)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
}

configure<de.jensklingenberg.ktorfit.gradle.KtorfitGradleConfiguration> {
    version = libs.versions.ktorfit.get()
}
