plugins {
    kotlin("multiplatform")
}

kotlin {
    targetHierarchy.default()
    jvm()
    ios()
    iosSimulatorArm64()
    tvos()
    macosX64()
    macosArm64()
    linuxX64()
    linuxArm64()
    mingwX64()
    watchosX64()
    watchosArm64()
    js(IR) {
        browser()
        nodejs()
    }
}