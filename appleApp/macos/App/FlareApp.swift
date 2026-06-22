import AppKit
import FlareAppleCore
import KotlinSharedUI
import SwiftUI
import AppleFontAwesome

enum MacWindowID {
    static let rssManagement = "rss-management"
}

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
        .commands {
            MacAppCommands()
        }
//        .windowToolbarStyle(.unified(showsTitle: false))
        WindowGroup("settings_rss_management_title", id: MacWindowID.rssManagement) {
            FlareTheme {
                RssScreen()
            }
        }
        .defaultSize(width: 1120, height: 760)
        .windowToolbarStyle(.unified)

        Settings {
            FlareTheme {
                MacSettingsScreen()
            }
        }
        .windowToolbarStyle(.unified)
//        .defaultSize(width: 1120, height: 760)
//        .commands {
//            SidebarCommands()
//        }
    }
}

private struct MacAppCommands: Commands {
    @Environment(\.openWindow) private var openWindow

    var body: some Commands {
        CommandGroup(replacing: .newItem) {
            Button {
            } label: {
                Label {
                    Text("draft_box_title")
                } icon: {
                    Image(fontAwesome: .penToSquare)
                }
            }

            Button {
                openWindow(id: MacWindowID.rssManagement)
            } label: {
                Label {
                    Text("settings_rss_management_title")
                } icon: {
                    Image(fontAwesome: .squareRss)
                }
            }

            Button {
            } label: {
                Label {
                    Text("local_history_title")
                } icon: {
                    Image(fontAwesome: .clockRotateLeft)
                }
            }

            Button {
            } label: {
                Label {
                    Text("settings_agent_history_title")
                } icon: {
                    Image(fontAwesome: .robot)
                }
            }
        }
    }
}
