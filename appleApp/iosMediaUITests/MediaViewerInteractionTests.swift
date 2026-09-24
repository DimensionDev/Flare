import XCTest

final class MediaViewerInteractionTests: XCTestCase {
    @MainActor
    private func openMedia(video: Bool = false, withoutPost: Bool = false) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--media-viewer-test", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        if video { app.launchArguments.append("--video") }
        if withoutPost { app.launchArguments.append("--without-post") }
        app.launch()
        let open = app.buttons["media-fixture-open"]
        XCTAssertTrue(open.waitForExistence(timeout: 15))
        open.tap()
        XCTAssertTrue(app.buttons["Close"].waitForExistence(timeout: 10))
        if !withoutPost { XCTAssertTrue(app.staticTexts["Media Test User"].waitForExistence(timeout: 10)) }
        return app
    }

    @MainActor
    private func assertDismisses(upward: Bool, video: Bool = false, withoutPost: Bool = false) {
        let app = openMedia(video: video, withoutPost: withoutPost)
        defer { app.terminate() }
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: upward ? 0.65 : 0.18))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: upward ? 0.08 : 0.78))
        start.press(forDuration: 0.05, thenDragTo: end, withVelocity: .fast, thenHoldForDuration: 0)
        let dismissed = app.staticTexts["Media dismissed"].waitForExistence(timeout: 4)
        if !dismissed {
            let screenshot = XCTAttachment(screenshot: app.screenshot())
            screenshot.lifetime = .keepAlways
            add(screenshot)
            print("MEDIA-REPRO \(app.debugDescription)")
        }
        XCTAssertTrue(dismissed, "The media viewer must dismiss after a vertical swipe outside the post sheet")
    }

    @MainActor func testImageSwipeUpWithSummary() { assertDismisses(upward: true) }
    @MainActor func testImageSwipeDownWithSummary() { assertDismisses(upward: false) }
    @MainActor func testImageSwipeUpWithoutPost() { assertDismisses(upward: true, withoutPost: true) }
    @MainActor func testVideoSwipeDownWithSummary() { assertDismisses(upward: false, video: true) }
    @MainActor func testVideoSwipeUpWithSummary() { assertDismisses(upward: true, video: true) }

    @MainActor
    func testSheetDragExpandsAndCollapsesWithoutDismissingViewer() {
        let app = openMedia()
        defer { app.terminate() }
        let grabber = app.buttons["Sheet Grabber"]
        XCTAssertTrue(grabber.waitForExistence(timeout: 5))
        grabber.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).press(forDuration: 0.05,
                      thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.15)),
                      withVelocity: .slow, thenHoldForDuration: 0)
        XCTAssertLessThan(grabber.frame.minY, app.frame.height * 0.3)
        XCTAssertFalse(app.staticTexts["Media dismissed"].exists)
        grabber.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).press(forDuration: 0.05,
                      thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.9)),
                      withVelocity: .slow, thenHoldForDuration: 0)
        XCTAssertGreaterThan(grabber.frame.minY, app.frame.height * 0.6)
        XCTAssertFalse(app.staticTexts["Media dismissed"].exists)
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.65))
        start.press(forDuration: 0.05,
                    thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.08)),
                    withVelocity: .fast, thenHoldForDuration: 0)
        XCTAssertTrue(app.staticTexts["Media dismissed"].waitForExistence(timeout: 4))
    }

    @MainActor
    func testVideoSummaryLayout() {
        checkSummaryLayout(video: true)
    }

    @MainActor
    func testImageSummaryLayout() {
        checkSummaryLayout(video: false)
    }

    @MainActor
    private func checkSummaryLayout(video: Bool) {
        let app = openMedia(video: video)
        defer { app.terminate() }
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = video ? "video-summary" : "image-summary"
        screenshot.lifetime = .keepAlways
        add(screenshot)
        let action = app.buttons["More"].firstMatch.frame
        let sheet = app.otherElements.containing(.button, identifier: "More").allElementsBoundByIndex
            .map(\.frame)
            .filter { $0.minY > 0 && $0.width > 300 && $0.height > action.height }
            .min { $0.height < $1.height }
        XCTAssertNotNil(sheet)
        if let sheet {
            let bottomGap = sheet.maxY - action.maxY
            print("MEDIA-LAYOUT sheet=\(sheet) action=\(action) bottomGap=\(bottomGap)")
            // iPhone sheets reserve up to 34pt for the home indicator, plus our 8pt padding.
            XCTAssertLessThanOrEqual(bottomGap, 44, "The summary must not leave extra space beyond its native safe area and bottom padding")
        }
    }
}
