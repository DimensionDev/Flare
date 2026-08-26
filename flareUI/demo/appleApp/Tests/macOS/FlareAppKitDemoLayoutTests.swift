import AppKit
import XCTest

final class FlareAppKitDemoLayoutTests: XCTestCase {
    @MainActor
    func testContentViewStaysInsideTwentyFourPointInsets() {
        let rootView = NSView(frame: NSRect(x: 0, y: 0, width: 320, height: 200))
        let contentView = IntrinsicContentView()

        installFlareDemoContentView(contentView, in: rootView)
        rootView.layoutSubtreeIfNeeded()

        XCTAssertEqual(contentView.frame.minX, 24, accuracy: 0.001)
        XCTAssertEqual(contentView.frame.maxX, 296, accuracy: 0.001)
        XCTAssertEqual(contentView.frame.maxY, 176, accuracy: 0.001)
        XCTAssertGreaterThanOrEqual(contentView.frame.minY, 24)
    }
}

private final class IntrinsicContentView: NSView {
    override var intrinsicContentSize: NSSize {
        NSSize(width: 100, height: 100)
    }
}
