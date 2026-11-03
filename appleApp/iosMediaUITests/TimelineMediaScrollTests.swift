import XCTest

final class TimelineMediaScrollTests: XCTestCase {
    @MainActor
    func testMediaStaysStableWhenNavigationBarReappearsWhileScrollingBack() throws {
        try assertReverseScrolling(fast: false)
    }

    @MainActor
    func testMediaStaysStableDuringFastReverseScrolling() throws {
        try assertReverseScrolling(fast: true)
    }

    @MainActor
    func testMediaLimitUpdatesAfterRotation() throws {
        let app = openTimeline()
        defer { XCUIDevice.shared.orientation = .portrait; app.terminate() }
        let portraitHeight = try XCTUnwrap(measurements(in: app)["mediaHeight"])
        XCTAssertGreaterThan(portraitHeight, 250)
        XCUIDevice.shared.orientation = .landscapeLeft
        let smaller = NSPredicate { _, _ in
            guard let height = try? self.measurements(in: app)["mediaHeight"] else { return false }
            return height > 0 && height < portraitHeight - 50
        }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: smaller, object: nil)], timeout: 5), .completed)
        XCUIDevice.shared.orientation = .portrait
        let restored = NSPredicate { _, _ in
            guard let height = try? self.measurements(in: app)["mediaHeight"] else { return false }
            return abs(height - portraitHeight) <= 1
        }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: restored, object: nil)], timeout: 5), .completed)
    }

    @MainActor
    private func assertReverseScrolling(fast: Bool) throws {
        guard #available(iOS 27.0, *) else { throw XCTSkip("Navigation bar minimization requires iOS 27") }
        let app = openTimeline()
        defer { app.terminate() }
        let list = app.collectionViews["timeline-list"]
        let high = list.coordinate(withNormalizedOffset: CGVector(dx: 0.7, dy: 0.25))
        let low = list.coordinate(withNormalizedOffset: CGVector(dx: 0.7, dy: 0.85))
        for _ in 0..<(fast ? 4 : 2) {
            low.press(forDuration: 0.1, thenDragTo: high, withVelocity: fast ? .fast : .slow,
                      thenHoldForDuration: fast ? 0 : 0.15)
        }
        try assertStableMeasurements(in: app)
        app.buttons["timeline-scroll-reset"].tap()
        for _ in 0..<(fast ? 3 : 1) {
            high.press(forDuration: 0.1, thenDragTo: low, withVelocity: fast ? .fast : .slow,
                       thenHoldForDuration: fast ? 0 : 0.15)
        }
        try assertStableMeasurements(in: app)
    }

    @MainActor
    private func openTimeline() -> XCUIApplication {
        XCUIDevice.shared.orientation = .portrait
        let app = XCUIApplication()
        app.launchArguments = ["--timeline-media-scroll-test", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        let list = app.collectionViews["timeline-list"]
        XCTAssertTrue(list.waitForExistence(timeout: 20))
        return app
    }

    @MainActor
    private func assertStableMeasurements(in app: XCUIApplication) throws {
        let raw = app.staticTexts["timeline-scroll-metrics"].label
        let values = try measurements(in: app)
        XCTAssertGreaterThan(try XCTUnwrap(values["samples"]), 30, raw)
        XCTAssertGreaterThan(try XCTUnwrap(values["topInsetRange"]), 20, "The navigation bar must actually animate: \(raw)")
        // Allow the final pan sample to straddle touch-up at high velocity.
        XCTAssertLessThan(try XCTUnwrap(values["maxError"]), 20, raw)
        XCTAssertLessThanOrEqual(try XCTUnwrap(values["maxHeightChange"]), 1, raw)
    }

    @MainActor
    private func measurements(in app: XCUIApplication) throws -> [String: Double] {
        let raw = app.staticTexts["timeline-scroll-metrics"].label
        return try XCTUnwrap(JSONSerialization.jsonObject(with: Data(raw.utf8)) as? [String: Double])
    }
}
