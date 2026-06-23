import AppKit
import FlareAppleCore
import KotlinSharedUI
import SwiftUI
import AppleFontAwesome

enum MacWindowID {
    static let compose = "compose"
    static let media = "media"
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
        .defaultSize(width: 480, height: 600)
        .windowResizability(.contentSize)
        .windowIdealSize(.fitToContent)
        .commands {
            MacAppCommands()
        }
//        .windowToolbarStyle(.unified(showsTitle: false))
        WindowGroup("home_compose", id: MacWindowID.compose, for: UUID.self) { requestID in
            FlareTheme {
                MacComposeWindowRoot(requestID: requestID.wrappedValue)
            }
        }
        .windowResizability(.contentSize)
        .windowIdealSize(.fitToContent)
        .defaultSize(width: 100, height: 80)
        .windowToolbarStyle(.unified)
        .restorationBehavior(.disabled)

        WindowGroup("Media", id: MacWindowID.media, for: MacMediaWindowValue.self) { request in
            FlareTheme {
                MacMediaWindowRoot(value: request.wrappedValue)
            }
        }
        .defaultSize(width: 960, height: 720)
        .defaultWindowPlacement { _, context in
            let visibleRect = context.defaultDisplay.visibleRect
            let maxWindowSize = CGSize(
                width: visibleRect.width * 0.8,
                height: visibleRect.height * 0.8
            )
            return WindowPlacement(
                .center,
                size: MacMediaWindowCoordinator.shared.placementSize(
                    maxWindowSize: maxWindowSize
                )
            )
        }
        .windowToolbarStyle(.unified(showsTitle: false))
        .restorationBehavior(.disabled)

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
        .windowToolbarStyle(.expanded)
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
                MacComposeWindowCoordinator.shared.openNew(openWindow: openWindow)
            } label: {
                Label {
                    Text("home_compose")
                } icon: {
                    Image(fontAwesome: .penToSquare)
                }
            }
            .keyboardShortcut("n")

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
                    Text("settings_agent_history_title")
                } icon: {
                    Image(fontAwesome: .robot)
                }
            }
        }
    }
}
