package dev.dimension.flare.common

object SandboxHelper {
    fun configureSandboxArgs() {
        val isSandboxed = System.getenv("APP_SANDBOX_CONTAINER_ID") != null
        if (isSandboxed) {
            val resourcesPath = System.getProperty("compose.application.resources.dir")
            System.setProperty("androidx.sqlite.driver.bundled.path", resourcesPath)
            System.setProperty("jna.nounpack", "true")
            System.setProperty("jna.boot.library.path", resourcesPath)
        }
    }
}
