import XCTest

final class MacCounterInteractionTests: XCTestCase {
    @MainActor
    func testCounterWindowsKeepIndependentRustState() throws {
        let app = XCUIApplication()
        app.launchArguments = ["--counter-overflow", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        defer { app.terminate() }

        let windows = app.windows.containing(.staticText, identifier: "counter-value")
        let maximum = Int64.max.formatted(.number.locale(Locale(identifier: "en_US")))
        openCounter(in: app)
        let first = windows.firstMatch
        expect(first.staticTexts["counter-value"], maximum)

        first.buttons["counter-increment"].click()
        expect(first.staticTexts["counter-error"], "counter value or state revision would overflow")
        expect(first.staticTexts["counter-value"], maximum)
        first.buttons["counter-reset"].click()
        expect(first.staticTexts["counter-value"], "0")
        first.buttons["counter-increment"].click()
        expect(first.staticTexts["counter-value"], "1")

        openCounter(in: app)
        XCTAssertTrue(windows.element(boundBy: 1).waitForExistence(timeout: 15))
        let second = try XCTUnwrap(windows.allElementsBoundByIndex.first {
            ($0.staticTexts["counter-value"].value as? String) == maximum
        })
        second.buttons["counter-reset"].click()
        expect(second.staticTexts["counter-value"], "0")
        let values = windows.allElementsBoundByIndex.map { $0.staticTexts["counter-value"].value as? String }
        XCTAssertEqual(Set(values), ["0", "1"])
    }

    @MainActor
    private func openCounter(in app: XCUIApplication) {
        let fileMenu = app.menuBars.menuBarItems["File"]
        XCTAssertTrue(fileMenu.waitForExistence(timeout: 15))
        fileMenu.click()
        fileMenu.menuItems["Rust Counter"].click()
    }

    @MainActor
    private func expect(_ element: XCUIElement, _ text: String) {
        XCTAssertTrue(element.waitForExistence(timeout: 15))
        let matches = XCTNSPredicateExpectation(predicate: NSPredicate(format: "value == %@", text), object: element)
        XCTAssertEqual(XCTWaiter.wait(for: [matches], timeout: 10), .completed, "Expected \(text), got \(String(describing: element.value))")
    }
}
