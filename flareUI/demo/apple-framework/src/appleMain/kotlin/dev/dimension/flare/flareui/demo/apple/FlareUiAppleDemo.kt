package dev.dimension.flare.flareui.demo.apple

import dev.dimension.flare.flareui.apple.FlareUiTreeHost
import dev.dimension.flare.flareui.apple.createFlareUiTreeHost
import dev.dimension.flare.flareui.demo.FlareUiDemo

/**
 * Concrete factory exported as `FlareUiAppleDemo.shared` for the standalone demo apps.
 */
public object FlareUiAppleDemo {
    public fun createHost(): FlareUiTreeHost =
        createFlareUiTreeHost {
            FlareUiDemo()
        }
}
