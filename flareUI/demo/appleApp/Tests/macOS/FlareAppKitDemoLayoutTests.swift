import AppKit
import XCTest

final class FlareAppKitDemoLayoutTests: XCTestCase {
    @MainActor
    func testNavigationContentFillsTheRootView() {
        let rootView = NSView(frame: NSRect(x: 0, y: 0, width: 320, height: 200))
        let contentView = IntrinsicContentView()

        installFlareDemoContentView(contentView, in: rootView)
        rootView.layoutSubtreeIfNeeded()

        XCTAssertEqual(contentView.frame, rootView.bounds)
    }
}

private final class IntrinsicContentView: NSView {
    override var intrinsicContentSize: NSSize {
        NSSize(width: 100, height: 100)
    }
}
