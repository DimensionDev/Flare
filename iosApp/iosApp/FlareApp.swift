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
//        .windowStyle(.hiddenTitleBar)
        #endif
        #if os(macOS)
        WindowGroup(id: "image-view", for: String.self) { $url in
            ImageViewWindow(url: url)
        }
        .windowStyle(.hiddenTitleBar)
        #endif
    }
}
