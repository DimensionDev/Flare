import SwiftUI
import shared

@main
struct FlareApp: SwiftUI.App {
    #if os(macOS)
    @NSApplicationDelegateAdaptor(AppDelegate.self) var delegate
    #else
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    #endif
    init() {
        KoinHelper.shared.start()
    }

    var body: some Scene {
        WindowGroup {
            #if os(macOS)
            ProvideWindowSizeClass {
                RouterView()
            }
            .handlesExternalEvents(preferring: ["flare"], allowing: ["flare"])
            #else
            RouterView()
            #endif
        }
        #if os(macOS)
        WindowGroup(id: "image-view", for: String.self) { $url in
            ImageViewWindow(url: url)
        }
        .windowStyle(.hiddenTitleBar)
        WindowGroup(id: "video-view", for: String.self) { $url in
            VideoViewWindow(url: url)
        }
        .windowStyle(.hiddenTitleBar)
        #endif
    }
}
