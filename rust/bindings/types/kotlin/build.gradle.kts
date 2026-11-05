plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    `java-library`
}

group = "dev.dimension.flare.counter"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {}

tasks.withType<Jar> {
    manifest {
        attributes["Implementation-Title"] = "dev.dimension.flare.counter"
        attributes["Implementation-Version"] = "1.0.0"
    }
}
