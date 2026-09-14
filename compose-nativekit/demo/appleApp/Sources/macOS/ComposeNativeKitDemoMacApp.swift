@preconcurrency import ComposeNativeKit
import AppKit

@main
@MainActor
final class ComposeNativeKitDemoMacApp: NSObject, NSApplicationDelegate {
    private static var retainedDelegate: ComposeNativeKitDemoMacApp?

    private var host: ComposeNativeKitDemoHost?
    private var window: NSWindow?

    static func main() {
        let application = NSApplication.shared
        let delegate = ComposeNativeKitDemoMacApp()
        retainedDelegate = delegate
        application.delegate = delegate
        application.setActivationPolicy(.regular)
        application.run()
    }

    func applicationDidFinishLaunching(_ notification: Notification) {
        let host = ComposeNativeKitDemoHost()
        let viewController = ComposeNativeKitAppKitDemoViewController(contentViewController: host.viewController)
        let window = NSWindow(contentViewController: viewController)
        window.title = "Compose NativeKit · AppKit"
        window.setContentSize(NSSize(width: 640, height: 480))
        window.center()
        window.makeKeyAndOrderFront(nil)

        self.host = host
        self.window = window
        NSApp.activate(ignoringOtherApps: true)
    }

    func applicationWillTerminate(_ notification: Notification) {
        host?.dispose()
        host = nil
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        true
    }
}

private final class ComposeNativeKitAppKitDemoViewController: NSViewController {
    private let contentViewController: NSViewController

    init(contentViewController: NSViewController) {
        self.contentViewController = contentViewController
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("Use init(contentViewController:) instead")
    }

    override func loadView() {
        let rootView = NSView()
        addChild(contentViewController)
        installComposeNativeKitDemoContentView(contentViewController.view, in: rootView)
        view = rootView
    }
}
