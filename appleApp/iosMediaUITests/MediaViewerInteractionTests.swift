import UIKit
import XCTest

final class MediaViewerInteractionTests: XCTestCase {
    @MainActor
    private func openMedia(video: Bool = false, withoutPost: Bool = false, indicator: Bool = false) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--media-viewer-test", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        if video { app.launchArguments.append("--video") }
        if withoutPost { app.launchArguments.append("--without-post") }
        if indicator { app.launchArguments.append("--indicator") }
        app.launch()
        let open = app.buttons["media-fixture-open"]
        XCTAssertTrue(open.waitForExistence(timeout: 15))
        open.tap()
        XCTAssertTrue(app.buttons["Close"].waitForExistence(timeout: 10))
        if !withoutPost { XCTAssertTrue(app.buttons["More"].firstMatch.waitForExistence(timeout: 10)) }
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
    func testCancelledImageDragsWithSummary() throws {
        try assertCancelledImageDragsRestorePosition()
    }

    @MainActor
    func testCancelledImageDragsWithoutPost() throws {
        try assertCancelledImageDragsRestorePosition(withoutPost: true)
    }

    @MainActor
    func testCancelledImageDragsWithIndicator() throws {
        try assertCancelledImageDragsRestorePosition(indicator: true)
    }

    @MainActor
    private func assertCancelledImageDragsRestorePosition(withoutPost: Bool = false, indicator: Bool = false) throws {
        let app = openMedia(withoutPost: withoutPost, indicator: indicator)
        defer { app.terminate() }
        let before = app.screenshot()
        let original = try XCTUnwrap(imageVerticalBounds(in: before), "The fixture image must be visible")
        // Repeated cancellations must not accumulate drift in either direction.
        for delta in [0.15, 0.15, -0.15] {
            let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.45))
            let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.45 + delta))
            start.press(forDuration: 0.05, thenDragTo: end, withVelocity: .slow, thenHoldForDuration: 0.3)
            XCTAssertTrue(app.buttons["Close"].exists)
            XCTAssertFalse(app.staticTexts["Media dismissed"].exists)
            let returned = NSPredicate { _, _ in
                guard let current = self.imageVerticalBounds(in: app.screenshot()) else { return false }
                return abs(current.lowerBound - original.lowerBound) <= 1
                    && abs(current.upperBound - original.upperBound) <= 1
            }
            let result = XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: returned, object: nil)], timeout: 2)
            if result != .completed {
                for (name, screenshot) in [("before-cancelled-drag", before), ("after-cancelled-drag", app.screenshot())] {
                    let attachment = XCTAttachment(screenshot: screenshot)
                    attachment.name = name
                    attachment.lifetime = .keepAlways
                    add(attachment)
                }
            }
            XCTAssertEqual(result, .completed, "The rendered image must return to its original position after a cancelled drag")
        }
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.65)).press(
            forDuration: 0.05,
            thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.08)),
            withVelocity: .fast, thenHoldForDuration: 0
        )
        XCTAssertTrue(app.staticTexts["Media dismissed"].waitForExistence(timeout: 4))
    }

    // Read the rendered teal fixture to catch movement inside the pager,
    // independently of the accessibility wrapper's frame.
    private func imageVerticalBounds(in screenshot: XCUIScreenshot) -> ClosedRange<CGFloat>? {
        guard let image = screenshot.image.cgImage else { return nil }
        var pixels = [UInt8](repeating: 0, count: image.width * image.height * 4)
        let rows: [Int] = pixels.withUnsafeMutableBytes { buffer in
            guard let context = CGContext(
                data: buffer.baseAddress, width: image.width, height: image.height,
                bitsPerComponent: 8, bytesPerRow: image.width * 4,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue
            ) else { return [] }
            context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
            return (0..<image.height).filter { y in
                let offset = (y * image.width + image.width / 2) * 4
                let red = Int(buffer[offset])
                let green = Int(buffer[offset + 1])
                let blue = Int(buffer[offset + 2])
                return green > 120 && blue > 120 && green > red + 30 && blue > red + 30
            }
        }
        guard let first = rows.first, let last = rows.last else { return nil }
        return (CGFloat(first) / screenshot.image.scale)...(CGFloat(last + 1) / screenshot.image.scale)
    }

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
    func testSummaryFitsProfileAndMediaControls() throws {
        let image = try XCTUnwrap(checkSummaryLayout(video: false))
        let indicator = try XCTUnwrap(checkSummaryLayout(video: false, indicator: true))
        let video = try XCTUnwrap(checkSummaryLayout(video: true))

        XCTAssertEqual(image.sheet.width, indicator.sheet.width, accuracy: 1)
        XCTAssertEqual(image.sheet.width, video.sheet.width, accuracy: 1)
        XCTAssertEqual(image.bottomGap, indicator.bottomGap, accuracy: 2)
        XCTAssertEqual(image.bottomGap, video.bottomGap, accuracy: 2)
        XCTAssertGreaterThan(indicator.sheet.height, image.sheet.height, "The summary must grow to fit the page indicator")
        XCTAssertGreaterThan(video.sheet.height, indicator.sheet.height, "The summary must grow to fit video controls")
    }

    @MainActor
    @discardableResult
    private func checkSummaryLayout(video: Bool, indicator: Bool = false) -> (sheet: CGRect, bottomGap: CGFloat)? {
        let app = openMedia(video: video, indicator: indicator)
        defer { app.terminate() }
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
            let screenshot = XCTAttachment(screenshot: app.screenshot())
            screenshot.name = video ? "video-summary" : (indicator ? "indicator-summary" : "image-summary")
            screenshot.lifetime = .keepAlways
            add(screenshot)
            return (sheet, bottomGap)
        }
        return nil
    }
}
