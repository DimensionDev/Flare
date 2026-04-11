plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly("com.android.tools.build:gradle-api:${libs.versions.agp.get()}")
    compileOnly("com.android.tools.build:gradle:${libs.versions.agp.get()}")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:${libs.versions.ktlintPlugin.get()}")
}

gradlePlugin {
    plugins {
        register("flareKmp") {
            id = "flare.kmp"
            implementationClass = "dev.dimension.flare.gradle.FlareKmpPlugin"
        }
        register("flareKoin") {
            id = "flare.koin"
            implementationClass = "dev.dimension.flare.gradle.FlareKoinPlugin"
        }
        register("flareKsp") {
            id = "flare.ksp"
            implementationClass = "dev.dimension.flare.gradle.FlareKspPlugin"
        }
    }
}
