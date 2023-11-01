plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.mingwGen.mingwGenAnnotation)
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet.ksp)
    implementation(libs.kotlinx.coroutines.core)
}
