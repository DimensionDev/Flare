@preconcurrency import FlareUI
import AppKit

@main
@MainActor
final class FlareUIDemoMacApp: NSObject, NSApplicationDelegate {
    private static var retainedDelegate: FlareUIDemoMacApp?

    private var host: FlareDemoHost?
    private var window: NSWindow?

    static func main() {
        let application = NSApplication.shared
        let delegate = FlareUIDemoMacApp()
        retainedDelegate = delegate
        application.delegate = delegate
        application.setActivationPolicy(.regular)
        application.run()
    }

    func applicationDidFinishLaunching(_ notification: Notification) {
        let host = FlareDemoHost()
        let viewController = FlareAppKitDemoViewController(contentViewController: host.viewController)
        let window = NSWindow(contentViewController: viewController)
        window.title = "Flare UI · AppKit"
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

private final class FlareAppKitDemoViewController: NSViewController {
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
        installFlareDemoContentView(contentViewController.view, in: rootView)
        view = rootView
    }
}
