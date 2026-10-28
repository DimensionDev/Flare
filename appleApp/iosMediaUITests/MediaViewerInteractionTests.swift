import UIKit
import XCTest

final class MediaViewerInteractionTests: XCTestCase {
    @MainActor
    private func openMedia(video: Bool = false, withoutPost: Bool = false, indicator: Bool = false, hidePostActions: Bool = false, translation: Bool = false) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--media-viewer-test", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        if video { app.launchArguments.append("--video") }
        if translation { app.launchArguments.append("--image-translation-fixture") }
        if withoutPost { app.launchArguments.append("--without-post") }
        if indicator { app.launchArguments.append("--indicator") }
        if hidePostActions { app.launchArguments.append("--hide-post-actions") }
        app.launch()
        let open = app.buttons["media-fixture-open"]
        XCTAssertTrue(open.waitForExistence(timeout: 15))
        open.tap()
        XCTAssertTrue(app.buttons["Close"].waitForExistence(timeout: 10))
        if !withoutPost {
            XCTAssertTrue(app.buttons["Sheet Grabber"].waitForExistence(timeout: 10))
            if !hidePostActions { XCTAssertTrue(app.buttons["More"].firstMatch.waitForExistence(timeout: 10)) }
        }
        return app
    }

    @MainActor
    func testUnconfiguredImageTranslationIsHiddenWithPost() {
        assertUnconfiguredTranslation(withoutPost: false)
    }

    @MainActor
    func testUnconfiguredImageTranslationIsHiddenWithoutPost() {
        assertUnconfiguredTranslation(withoutPost: true)
    }

    @MainActor
    func testTranslationSettingsPagePresentsImageFieldEditor() {
        let app = XCUIApplication()
        app.launchArguments = ["--media-viewer-test", "--translation-settings", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        defer { app.terminate() }
        let open = app.buttons["media-fixture-open"]
        XCTAssertTrue(open.waitForExistence(timeout: 15))
        open.tap()
        assertImageTranslationFields(in: app)
    }

    @MainActor
    func testImageTranslationCanSwitchBetweenSharedAndCustomConfiguration() {
        let app = XCUIApplication()
        app.launchArguments = ["--media-viewer-test", "--translation-settings", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        defer { app.terminate() }
        let open = app.buttons["media-fixture-open"]
        XCTAssertTrue(open.waitForExistence(timeout: 15))
        open.tap()
        let provider = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Image translation provider")).firstMatch
        XCTAssertTrue(provider.waitForExistence(timeout: 5))
        provider.tap()
        app.buttons["OpenAI Images compatible"].tap()
        let sharedRow = app.switches.matching(NSPredicate(format: "label BEGINSWITH %@", "Use translation and summary AI settings")).firstMatch
        let shared = sharedRow.switches.firstMatch
        XCTAssertTrue(shared.waitForExistence(timeout: 5))
        if shared.value as? String == "0" { shared.tap() }
        XCTAssertEqual(shared.value as? String, "1")
        let model = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Image model")).firstMatch
        let apiKey = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "API key")).firstMatch
        XCTAssertFalse(model.exists)
        XCTAssertFalse(apiKey.exists)
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "image-translation-shared-ai-settings"
        attachment.lifetime = .keepAlways
        add(attachment)
        shared.tap()
        XCTAssertEqual(shared.value as? String, "0")
        app.swipeUp()
        XCTAssertTrue(model.waitForExistence(timeout: 5))
        XCTAssertTrue(apiKey.waitForExistence(timeout: 5))
        app.swipeDown()
        shared.tap()
        XCTAssertEqual(shared.value as? String, "1")
        XCTAssertFalse(model.exists)
        XCTAssertFalse(apiKey.exists)
        provider.tap()
        app.buttons["Gemini"].tap()
        XCTAssertFalse(sharedRow.exists)
        XCTAssertTrue(apiKey.waitForExistence(timeout: 5))
        provider.tap()
        app.buttons["Off"].tap()
    }

    @MainActor
    func testLeavingSharedImageConfigurationPrefillsFieldsAndRefreshesAfterSharingAgain() {
        let app = XCUIApplication()
        app.launchArguments = ["--media-viewer-test", "--translation-settings", "--image-translation-prefill-fixture", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        defer { app.terminate() }
        let open = app.buttons["media-fixture-open"]
        XCTAssertTrue(open.waitForExistence(timeout: 15))
        open.tap()
        let sharedRow = app.switches.matching(NSPredicate(format: "label BEGINSWITH %@", "Use translation and summary AI settings")).firstMatch
        let shared = sharedRow.switches.firstMatch
        XCTAssertTrue(shared.waitForExistence(timeout: 5))
        XCTAssertEqual(shared.value as? String, "1")
        shared.tap()
        app.swipeUp()
        let server = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Service URL")).firstMatch
        let apiKey = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "API key")).firstMatch
        let model = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Image model")).firstMatch
        XCTAssertTrue(server.waitForExistence(timeout: 5))
        XCTAssertTrue(server.label.contains("http://127.0.0.1:18473/v1"))
        XCTAssertTrue(apiKey.waitForExistence(timeout: 5))
        XCTAssertTrue(apiKey.label.contains("prefill-fixture-key"))
        apiKey.tap()
        let editor = app.textFields.firstMatch
        XCTAssertTrue(editor.waitForExistence(timeout: 5))
        XCTAssertEqual(editor.value as? String, "prefill-fixture-key")
        XCTAssertFalse(app.secureTextFields.firstMatch.exists)
        app.navigationBars["API key"].buttons["Cancel"].tap()
        app.swipeUp()
        XCTAssertTrue(model.waitForExistence(timeout: 5))
        XCTAssertTrue(model.label.contains("prefill-fixture-image"))
        model.tap()
        XCTAssertFalse(app.buttons["Enter manually…"].exists)
        app.buttons["offline-image-alt"].tap()
        XCTAssertTrue(model.waitForExistence(timeout: 5))
        XCTAssertTrue(model.label.contains("offline-image-alt"))
        app.swipeDown()
        shared.tap()
        XCTAssertEqual(shared.value as? String, "1")
        XCTAssertFalse(model.exists)
        shared.tap()
        XCTAssertEqual(shared.value as? String, "0")
        app.swipeUp()
        XCTAssertTrue(model.waitForExistence(timeout: 5))
        XCTAssertTrue(model.label.contains("prefill-fixture-image"))
        XCTAssertFalse(model.label.contains("offline-image-alt"))
    }

    @MainActor
    func testImageTranslationServerEditorPrefillsAndCancelsSuggestedChanges() {
        let app = XCUIApplication()
        app.launchArguments = ["--media-viewer-test", "--translation-settings", "--image-translation-fixture", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        defer { app.terminate() }
        let open = app.buttons["media-fixture-open"]
        XCTAssertTrue(open.waitForExistence(timeout: 15))
        open.tap()
        app.swipeUp()
        let server = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Service URL")).firstMatch
        XCTAssertTrue(server.waitForExistence(timeout: 5))
        server.tap()
        let editor = app.textFields.firstMatch
        XCTAssertTrue(editor.waitForExistence(timeout: 5))
        let original = "http://127.0.0.1:18473/v1"
        XCTAssertEqual(editor.value as? String, original)
        // Place the caret after the prefilled URL before replacing it.
        editor.coordinate(withNormalizedOffset: CGVector(dx: 0.95, dy: 0.5)).tap()
        editor.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: original.count) + "api.openai")
        XCTAssertEqual(editor.value as? String, "api.openai")
        let suggestion = app.buttons["https://api.openai.com/v1/"]
        XCTAssertTrue(suggestion.waitForExistence(timeout: 5))
        suggestion.tap()
        XCTAssertEqual(editor.value as? String, "https://api.openai.com/v1/")
        app.navigationBars["Service URL"].buttons["Cancel"].tap()
        XCTAssertTrue(server.waitForExistence(timeout: 5))
        server.tap()
        XCTAssertTrue(editor.waitForExistence(timeout: 5))
        XCTAssertEqual(editor.value as? String, original)
        app.navigationBars["Service URL"].buttons["Cancel"].tap()
    }

    @MainActor
    func testImageTranslationSelectsDiscoveredModelsAndRegionsWithoutManualInput() {
        let app = XCUIApplication()
        app.launchArguments = ["--media-viewer-test", "--translation-settings", "--image-translation-fixture", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        defer { app.terminate() }
        let open = app.buttons["media-fixture-open"]
        XCTAssertTrue(open.waitForExistence(timeout: 15))
        open.tap()
        app.swipeUp()
        let model = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Image model")).firstMatch
        XCTAssertTrue(model.waitForExistence(timeout: 5))
        model.tap()
        XCTAssertFalse(app.buttons["Enter manually…"].exists)
        app.buttons["offline-image-alt"].tap()
        XCTAssertTrue(model.label.contains("offline-image-alt"))
        app.swipeDown()
        let provider = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Image translation provider")).firstMatch
        provider.tap()
        app.buttons["Tencent Cloud"].tap()
        app.swipeUp()
        let region = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Region")).firstMatch
        XCTAssertTrue(region.waitForExistence(timeout: 5))
        region.tap()
        XCTAssertFalse(app.buttons["Enter manually…"].exists)
        XCTAssertFalse(app.textFields.firstMatch.exists)
        app.buttons["ap-beijing"].tap()
        XCTAssertTrue(region.label.contains("ap-beijing"))
    }

    @MainActor
    private func assertImageTranslationFields(in app: XCUIApplication) {
        let provider = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Image translation provider")).firstMatch
        XCTAssertTrue(provider.waitForExistence(timeout: 5))
        provider.tap()
        app.buttons["Gemini"].tap()
        let apiKey = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "API key")).firstMatch
        XCTAssertTrue(apiKey.waitForExistence(timeout: 5))
        apiKey.tap()
        let editor = app.textFields.firstMatch
        XCTAssertTrue(editor.waitForExistence(timeout: 5))
        XCTAssertFalse(app.secureTextFields.firstMatch.exists)
        let initialValue = editor.value as? String
        editor.tap()
        editor.typeText("discarded")
        app.buttons["Cancel"].tap()
        apiKey.tap()
        XCTAssertTrue(editor.waitForExistence(timeout: 5))
        XCTAssertEqual(editor.value as? String, initialValue)
        editor.tap()
        editor.typeText("offline-key")
        app.navigationBars["API key"].buttons["Done"].tap()
        XCTAssertTrue(apiKey.waitForExistence(timeout: 5))
        let saved = NSPredicate { _, _ in apiKey.label.contains("offline-key") }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: saved, object: nil)], timeout: 5), .completed)
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "image-translation-native-settings"
        attachment.lifetime = .keepAlways
        add(attachment)
        provider.tap()
        app.buttons["Off"].tap()
    }

    @MainActor
    private func assertUnconfiguredTranslation(withoutPost: Bool) {
        let app = openMedia(withoutPost: withoutPost)
        defer { app.terminate() }
        XCTAssertFalse(app.buttons["Translate image"].exists)
        XCTAssertFalse(app.buttons["Translate again"].exists)
        XCTAssertFalse(app.alerts["Image translation"].exists)
        XCTAssertTrue(app.buttons["Share image"].exists)
        XCTAssertFalse(app.navigationBars["Image translation"].exists)
        XCTAssertFalse(app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Image translation provider")).firstMatch.exists)
        if !withoutPost {
            XCTAssertTrue(app.buttons["Sheet Grabber"].waitForExistence(timeout: 5))
            let postAction = app.buttons["More"].firstMatch
            let restored = NSPredicate { _, _ in postAction.isHittable }
            XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: restored, object: nil)], timeout: 5), .completed)
        }
    }

    @MainActor
    func testVideoDoesNotOfferImageTranslation() {
        let app = openMedia(video: true, withoutPost: true)
        defer { app.terminate() }
        XCTAssertFalse(app.buttons["Translate image"].exists)
    }

    @MainActor
    func testLocalProviderReturnsDisplayedSavableImage() async throws {
        let health = URL(string: "http://127.0.0.1:18473/health")!
        var request = URLRequest(url: health)
        request.timeoutInterval = 2
        guard let (data, _) = try? await URLSession.shared.data(for: request),
              (try? JSONDecoder().decode([String: Bool].self, from: data))?["fixture"] == true else {
            throw XCTSkip("Start scripts/test_image_translation_server.py for the offline provider test")
        }
        let app = openMedia(withoutPost: true, translation: true)
        defer { app.terminate() }
        let before = XCTAttachment(screenshot: app.screenshot())
        before.name = "image-translation-before-loading"
        before.lifetime = .keepAlways
        add(before)
        app.buttons["Translate image"].tap()
        let loading = app.buttons.matching(NSPredicate(format: "value == %@", "Translating image")).firstMatch
        XCTAssertTrue(loading.waitForExistence(timeout: 5))
        let processing = XCTAttachment(screenshot: app.screenshot())
        processing.name = "image-translation-loading-blur"
        processing.lifetime = .keepAlways
        add(processing)
        let original = app.buttons["Show original"]
        XCTAssertTrue(original.waitForExistence(timeout: 10))
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "image-translation-local-provider-result"
        attachment.lifetime = .keepAlways
        add(attachment)
        original.tap()
        app.buttons["Show translation"].tap()
        XCTAssertTrue(original.waitForExistence(timeout: 5))
        app.buttons["Share image"].tap()
        XCTAssertTrue(app.cells["Copy"].waitForExistence(timeout: 5))
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
    func testHiddenPostActionsStayHiddenWhenSheetExpandsAndCollapses() {
        let app = openMedia(hidePostActions: true)
        defer { app.terminate() }
        let grabber = app.buttons["Sheet Grabber"]
        XCTAssertFalse(app.buttons["More"].exists)
        grabber.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).press(forDuration: 0.05,
                      thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.15)),
                      withVelocity: .slow, thenHoldForDuration: 0)
        XCTAssertLessThan(grabber.frame.minY, app.frame.height * 0.3)
        app.swipeUp()
        app.swipeUp()
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label CONTAINS '1970'")).firstMatch.isHittable)
        XCTAssertFalse(app.buttons["More"].exists)
        let expanded = XCTAttachment(screenshot: app.screenshot())
        expanded.name = "hidden-actions-expanded"
        expanded.lifetime = .keepAlways
        add(expanded)
        grabber.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).press(forDuration: 0.05,
                      thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.9)),
                      withVelocity: .slow, thenHoldForDuration: 0)
        XCTAssertGreaterThan(grabber.frame.minY, app.frame.height * 0.6)
        XCTAssertFalse(app.buttons["More"].exists)
        XCTAssertFalse(app.staticTexts["Media dismissed"].exists)
        let collapsed = XCTAttachment(screenshot: app.screenshot())
        collapsed.name = "hidden-actions-collapsed"
        collapsed.lifetime = .keepAlways
        add(collapsed)
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
