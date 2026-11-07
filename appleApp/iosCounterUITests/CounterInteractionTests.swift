import XCTest

final class CounterInteractionTests: XCTestCase {
    @MainActor
    func testCruxStateAndRepeatedPageExit() {
        let app = XCUIApplication()
        app.launchArguments = ["--counter-test", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        defer { app.terminate() }

        for _ in 0..<8 {
            app.buttons["counter-compose"].tap()
            expect(app.staticTexts["counter-value"], "0")
            app.buttons["counter-increment"].tap()
            expect(app.staticTexts["counter-value"], "1")
            app.buttons["counter-decrement"].tap()
            expect(app.staticTexts["counter-value"], "0")
            app.buttons["counter-decrement"].tap()
            expect(app.staticTexts["counter-value"], "-1")
            app.buttons["counter-reset"].tap()
            expect(app.staticTexts["counter-value"], "0")
            app.navigationBars["Counter"].buttons.firstMatch.tap()
        }
    }

    @MainActor
    func testInteractiveBackReturnsToHome() {
        let app = XCUIApplication()
        app.launchArguments = ["--counter-test", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        defer { app.terminate() }
        XCTAssertTrue(app.buttons["counter-compose"].waitForExistence(timeout: 15))
        app.buttons["counter-compose"].tap()
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.01, dy: 0.5))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0.5))
        start.press(forDuration: 0.1, thenDragTo: end)
        XCTAssertTrue(app.buttons["counter-compose"].exists)
    }

    @MainActor
    func testStackedCounterPagesHaveIndependentCores() {
        let app = XCUIApplication()
        app.launchArguments = ["--counter-test", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        defer { app.terminate() }
        XCTAssertTrue(app.buttons["counter-compose"].waitForExistence(timeout: 15))
        app.buttons["counter-compose"].tap()
        expect(app.staticTexts["counter-value"], "0")
        app.buttons["counter-increment"].tap()
        expect(app.staticTexts["counter-value"], "1")
        app.buttons["counter-next"].tap()
        expect(app.staticTexts["counter-value"], "0")
        app.buttons["counter-decrement"].tap()
        expect(app.staticTexts["counter-value"], "-1")
        app.navigationBars["Counter"].buttons.firstMatch.tap()
        expect(app.staticTexts["counter-value"], "1")
        app.navigationBars["Counter"].buttons.firstMatch.tap()
    }

    @MainActor
    func testActionFailureKeepsContentAndAllowsRecovery() {
        let app = XCUIApplication()
        app.launchArguments = ["--counter-test", "--counter-overflow", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        defer { app.terminate() }
        XCTAssertTrue(app.buttons["counter-compose"].waitForExistence(timeout: 15))
        app.buttons["counter-compose"].tap()
        let maximum = Int64.max.formatted(.number.locale(Locale(identifier: "en_US")))
        expect(app.staticTexts["counter-value"], maximum)
        app.buttons["counter-increment"].tap()
        expect(app.staticTexts["counter-error"], "counter value or state revision would overflow")
        expect(app.staticTexts["counter-value"], maximum)
        app.buttons["counter-reset"].tap()
        expect(app.staticTexts["counter-value"], "0")
        XCTAssertFalse(app.staticTexts["counter-error"].exists)
        app.navigationBars["Counter"].buttons.firstMatch.tap()
    }

    @MainActor
    private func expect(_ element: XCUIElement, _ label: String) {
        XCTAssertTrue(element.waitForExistence(timeout: 15))
        let matches = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label == %@", label), object: element)
        XCTAssertEqual(XCTWaiter.wait(for: [matches], timeout: 10), .completed, "Expected \(label), got \(element.label)")
    }
}
