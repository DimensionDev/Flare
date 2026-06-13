package dev.dimension.flare.common

import android.annotation.SuppressLint
import android.os.Build

public val isMiuiOrHyperOs: Boolean
    get() = SystemUtils.isMiuiOrHyperOs

internal actual object SystemUtils {
    actual val isBlurSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val isMiuiOrHyperOs: Boolean by lazy {
        systemProperty("ro.miui.ui.version.name").isNotEmpty() ||
            systemProperty("ro.miui.ui.version.code").isNotEmpty() ||
            systemProperty("ro.mi.os.version.name").isNotEmpty() ||
            systemProperty("ro.mi.os.version.code").isNotEmpty()
    }

    @SuppressLint("PrivateApi")
    private fun systemProperty(key: String): String =
        runCatching {
            Class
                .forName("android.os.SystemProperties")
                .getMethod("get", String::class.java)
                .invoke(null, key) as? String
        }.getOrNull()
            .orEmpty()
}
