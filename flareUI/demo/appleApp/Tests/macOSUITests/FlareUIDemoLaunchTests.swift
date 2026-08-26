import XCTest

final class FlareUIDemoLaunchTests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testLaunchShowsWindow() {
        let app = XCUIApplication()
        app.launch()
        defer { app.terminate() }

        XCTAssertTrue(
            app.windows.firstMatch.waitForExistence(timeout: 5),
            "The AppKit demo should show its main window after launch"
        )
    }
}
