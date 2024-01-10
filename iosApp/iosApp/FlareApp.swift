import SwiftUI
import shared

@main
struct FlareApp: SwiftUI.App {
    init() {
        KoinHelper.shared.start()
    }

    var body: some Scene {
        WindowGroup {
            #if os(macOS)
            ProvideWindowSizeClass {
                RouterView()
                    .handlesExternalEvents(preferring: ["flare"], allowing: ["flare"])
            }
            #else
            RouterView()
            #endif
        }
        #if os(macOS)
        .windowStyle(.hiddenTitleBar)
        #endif
    }
}
