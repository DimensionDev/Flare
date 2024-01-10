plugins {
    id("com.github.johnrengelman.shadow").version("8.1.1")
    kotlin("jvm")
}

dependencies {
    implementation(projects.shared)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}