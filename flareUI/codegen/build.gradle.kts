plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
    explicitApi()
}

dependencies {
    implementation(libs.ksp.symbol.processing.api)
    testImplementation(kotlin("test"))
}
