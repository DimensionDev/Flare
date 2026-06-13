import AppKit
import FlareAppleCore
import KotlinSharedUI
import SwiftUI

@main
struct FlareApp: App {
    init() {
        AppleSharedHelper.shared.initialize(
            inAppNotification: SwiftInAppNotification.shared,
            swiftFormatter: Formatter.shared,
            swiftPlatformTextRenderer: PlatformTextRenderer.shared,
            swiftOnDeviceAI: FoundationModelOnDeviceAI.shared
        )
    }

    var body: some Scene {
        WindowGroup {
            FlareTheme {
                RootView()
            }
        }
        .windowToolbarStyle(.unifiedCompact(showsTitle: false))
        Settings {
            FlareTheme {
                MacSettingsScreen()
            }
        }
//        .defaultSize(width: 1120, height: 760)
//        .commands {
//            SidebarCommands()
//        }
    }
}
