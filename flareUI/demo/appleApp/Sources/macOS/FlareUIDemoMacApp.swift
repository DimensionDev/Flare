@preconcurrency import FlareUI
import AppKit

@main
final class FlareUIDemoMacApp: NSObject, NSApplicationDelegate {
    private var host: FlareDemoHost?
    private var window: NSWindow?

    func applicationDidFinishLaunching(_ notification: Notification) {
        let host = FlareDemoHost()
        let viewController = FlareAppKitDemoViewController(contentView: host.view)
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
    private let contentView: NSView

    init(contentView: NSView) {
        self.contentView = contentView
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("Use init(contentView:) instead")
    }

    override func loadView() {
        let rootView = NSView()
        contentView.translatesAutoresizingMaskIntoConstraints = false
        rootView.addSubview(contentView)

        NSLayoutConstraint.activate([
            contentView.leadingAnchor.constraint(equalTo: rootView.leadingAnchor, constant: 24),
            contentView.trailingAnchor.constraint(
                equalTo: rootView.trailingAnchor,
                constant: -24
            ),
            contentView.topAnchor.constraint(equalTo: rootView.topAnchor, constant: -24),
            contentView.bottomAnchor.constraint(
                greaterThanOrEqualTo: rootView.bottomAnchor,
                constant: 24
            ),
        ])
        view = rootView
    }
}
