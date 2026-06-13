import AppKit
import SwiftUI
import KotlinSharedUI
import FlareAppleCore

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
            .background(WindowTitleVisibilityConfigurator())
        }
//        .defaultSize(width: 1120, height: 760)
//        .commands {
//            SidebarCommands()
//        }
    }
}

private struct WindowTitleVisibilityConfigurator: NSViewRepresentable {
    func makeNSView(context: Context) -> NSView {
        let view = NSView()
        DispatchQueue.main.async {
            configure(window: view.window)
        }
        return view
    }

    func updateNSView(_ nsView: NSView, context: Context) {
        DispatchQueue.main.async {
            configure(window: nsView.window)
        }
    }

    private func configure(window: NSWindow?) {
        window?.titleVisibility = .hidden
    }
}
