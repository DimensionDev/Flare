import XCTest

/// Offline production-controller regressions, independent of account/feed state.
final class TimelineScrollingInteractionTests: XCTestCase {
    @MainActor func testMeasuredHeightsDuringPrependPreserveNativePan() {
        checkPrepend(count: 2)
    }

    @MainActor func testMeasuredHeightsDuringLargePrependPreserveNativePan() {
        checkPrepend(count: 20)
    }

    @MainActor func testMeasuredHeightsDuringPrependPreserveNativePanInTwoColumns() {
        checkPrepend(count: 2, columns: 2)
    }

    @MainActor func testMeasuredHeightsDuringAThousandItemPrependPreserveNativePan() {
        checkPrepend(count: 1_000)
    }

    @MainActor private func checkPrepend(count: Int, columns: Int = 1) {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["--timeline-scrolling-test", "--prepend-count=\(count)"]
        if columns == 2 { app.launchArguments.append("--two-columns") }
        app.launch()
        defer { app.terminate() }
        XCTAssertTrue(app.staticTexts["scrolling-test-ready"].waitForExistence(timeout: 20))
        let list = app.collectionViews["scrolling-test-list"]
        let start = list.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.4))
        let end = list.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.8))
        start.press(forDuration: 0.05, thenDragTo: end, withVelocity: .slow, thenHoldForDuration: 0.4)
        let result = app.staticTexts["scrolling-test-result"]
        XCTAssertTrue(result.waitForExistence(timeout: 10))
        print("TIMELINE-SCROLL columns=\(columns) prepend=\(count) \(result.label)")
        XCTAssertTrue(result.label.hasPrefix("PASS"), result.label)
    }
}
