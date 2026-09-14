import AppKit

@main @MainActor final class AppDelegate: NSObject, NSApplicationDelegate {
    private var window: NSWindow!
    static func main() {
        setbuf(stdout, nil)
        let app = NSApplication.shared
        let delegate = AppDelegate()
        app.delegate = delegate
        app.setActivationPolicy(.regular)
        withExtendedLifetime(delegate) { app.run() }
    }
    func applicationDidFinishLaunching(_ notification: Notification) {
        window = NSWindow(contentRect: NSRect(x: 100, y: 100, width: 390, height: 780), styleMask: [.titled, .closable], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.title = "Flare / native lazy benchmark"
        let viewport = NSView(frame: NSRect(x: 0, y: 0, width: 390, height: 780))
        window.contentView = viewport
        window.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
        Task { @MainActor in
            // WindowServer reports occlusion asynchronously after the window is ordered front.
            for _ in 0..<100 where !window.occlusionState.contains(.visible) {
                try? await Task.sleep(nanoseconds: 10_000_000)
            }
            let visible = window.isVisible && window.occlusionState.contains(.visible)
            print("APPLE_LAZY_WINDOW,ordered=\(window.isVisible),visible=\(visible),active=\(NSApp.isActive)")
            if !visible && !ProcessInfo.processInfo.arguments.contains("--allow-occluded") {
                print("APPLE_LAZY_FAILED,macos,visibility,The benchmark window is not visible. Use an unlocked desktop for visible-window measurements.")
                exit(1)
            }
            await BenchmarkRunner(container: viewport).run()
        }
    }
}
