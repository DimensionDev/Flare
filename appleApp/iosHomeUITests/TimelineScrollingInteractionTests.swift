import XCTest

/// Offline production-controller regressions, independent of account/feed state.
final class TimelineScrollingInteractionTests: XCTestCase {
    @MainActor func testPrependingAHistoryPageStaysWithinTheScrollingFrameBudget() {
        for columns in [1, 2] {
            let control = checkPrepend(count: 0, columns: columns, initialCount: 180)
            let inserted = checkPrepend(count: 20, columns: columns, initialCount: 180, frameBudget: 25)
            // Ordinary cell entry can miss a frame too; insertion must not add a multi-frame stall.
            XCTAssertLessThan(inserted, max(35, control + 8.3))
        }
    }

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

    @MainActor @discardableResult
    private func checkPrepend(count: Int, columns: Int = 1, initialCount: Int = 60, frameBudget: Double? = nil) -> Double {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["--timeline-scrolling-test", "--prepend-count=\(count)", "--initial-count=\(initialCount)"]
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
        if let frameBudget {
            let value = result.label.components(separatedBy: "insertionFrameMS=").last?.components(separatedBy: " ").first
            let gap = Double(value ?? "") ?? .infinity
            // The frame publishing the inserted page has a tighter budget than the whole pan.
            XCTAssertLessThan(gap, frameBudget, result.label)
        }
        return Double(result.label.components(separatedBy: "frameGapMS=").last ?? "") ?? .infinity
    }
}
