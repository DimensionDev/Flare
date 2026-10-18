import XCTest

/// Opt-in acceptance against the normal app, real feeds and durable storage.
/// Enable Remember Home reading position and Refresh on launch through Settings.
/// RSS cases use Apple Newsroom and Kotlin Blog Home tabs. Live cases require
/// already signed-in accounts with Featured and Mixed tabs; FLARE_RANKED_TAB and
/// FLARE_MIXED_TAB can override those names. Run timer setup/cleanup separately.
/// Bookmarks and feeds are never seeded, and application animations remain enabled.
final class HomeTimelineInteractionTests: XCTestCase {
    private struct Bookmark {
        let identifier: String
        let top: CGFloat
    }

    @MainActor
    func testCurrentTabPositionSurvivesRestart() throws {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        launch(app)
        defer { app.terminate() }
        try selectTab("Kotlin Blog", in: app)
        showLatestIfAvailable(in: app)
        let bookmark = try scrollAndRemember(in: app, swipes: 1, name: "single-tab-before-restart")
        XCUIDevice.shared.press(.home)
        app.terminate()
        launch(app)
        assertSelectedTab("Kotlin Blog", in: app)
        assertRestored(bookmark, in: app, name: "single-tab-after-restart")
    }

    @MainActor
    func testTabPositionsAndLastSelectionSurviveProcessRestarts() throws {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        launch(app)
        defer { app.terminate() }

        try selectTab("Apple Newsroom", in: app)
        showLatestIfAvailable(in: app)
        let apple = try scrollAndRemember(in: app, swipes: 2, name: "apple-before-switch")
        try selectTab("Kotlin Blog", in: app)
        showLatestIfAvailable(in: app)
        let kotlin = try scrollAndRemember(in: app, swipes: 1, name: "kotlin-before-switch")

        try selectTab("Apple Newsroom", in: app)
        assertRestored(apple, in: app, name: "apple-after-tab-switch")
        try selectTab("Kotlin Blog", in: app)
        assertRestored(kotlin, in: app, name: "kotlin-after-tab-switch")

        XCUIDevice.shared.press(.home)
        app.terminate()
        launch(app)
        assertSelectedTab("Kotlin Blog", in: app)
        assertRestored(kotlin, in: app, name: "kotlin-after-background-restart")
        try selectTab("Apple Newsroom", in: app)
        assertRestored(apple, in: app, name: "apple-after-background-restart")

        // Terminate while foregrounded as well: tab restoration must not depend on
        // UIKit preserving the old controller or the user opening another tab first.
        app.terminate()
        launch(app)
        assertSelectedTab("Apple Newsroom", in: app)
        assertRestored(apple, in: app, name: "apple-after-foreground-restart")
        try selectTab("Kotlin Blog", in: app)
        assertRestored(kotlin, in: app, name: "kotlin-after-foreground-restart")

        // A subsequent user scroll must replace the restored bookmark.
        let updated = try scrollAndRemember(in: app, swipes: 1, name: "kotlin-updated-bookmark")
        XCTAssertTrue(updated.identifier != kotlin.identifier || abs(updated.top - kotlin.top) > 10)
        app.terminate()
        launch(app)
        assertSelectedTab("Kotlin Blog", in: app)
        assertRestored(updated, in: app, name: "kotlin-updated-after-restart")
    }

    // Opt-in acceptance with accounts and tabs configured through the normal app.
    // No credentials, feed responses or reading positions are seeded.
    @MainActor
    func testConfigureLiveReadingSettings() throws {
        continueAfterFailure = false
        let app = liveApp()
        launch(app)
        defer { app.terminate() }
        openBehaviorSettings(in: app)
        for name in ["Remember Home reading position", "Refresh home timeline on launch"] {
            let toggle = app.switches.matching(NSPredicate(format: "label BEGINSWITH %@", name)).firstMatch
            XCTAssertTrue(toggle.waitForExistence(timeout: 10))
            if toggle.value as? String != "1" {
                toggle.coordinate(withNormalizedOffset: CGVector(dx: 0.93, dy: 0.5)).tap()
            }
            let enabled = XCTNSPredicateExpectation(predicate: NSPredicate(format: "value == '1'"), object: toggle)
            XCTAssertEqual(XCTWaiter.wait(for: [enabled], timeout: 5), .completed)
        }
    }

    @MainActor
    func testLiveRecommendedAndMixedRestoration() throws {
        continueAfterFailure = false
        let app = liveApp()
        launch(app)
        defer { app.terminate() }
        let rankedName = ProcessInfo.processInfo.environment["FLARE_RANKED_TAB"] ?? "Featured"
        let mixedName = ProcessInfo.processInfo.environment["FLARE_MIXED_TAB"] ?? "Mixed"
        try selectTab(rankedName, in: app)
        let ranked = try scrollAndRemember(in: app, swipes: 2, name: "ranked-before-switch")
        XCTAssertTrue(ranked.identifier.contains("home-history/"), "Enable Remember Home reading position before this acceptance test")
        try selectTab(mixedName, in: app)
        let mixed = try scrollAndRemember(in: app, swipes: 2, name: "mixed-before-switch")
        XCTAssertTrue(mixed.identifier.contains("home-history/"))
        try selectTab(rankedName, in: app)
        assertRestored(ranked, in: app, name: "ranked-after-tab-switch")
        try selectTab(mixedName, in: app)
        assertRestored(mixed, in: app, name: "mixed-after-tab-switch")
        XCUIDevice.shared.press(.home)
        app.terminate()
        launch(app)
        assertSelectedTab(mixedName, in: app)
        assertRestored(mixed, in: app, name: "mixed-after-background-restart-and-launch-refresh")
        try selectTab(rankedName, in: app)
        assertRestored(ranked, in: app, name: "ranked-after-background-restart-and-launch-refresh")
        app.terminate()
        launch(app)
        assertSelectedTab(rankedName, in: app)
        assertRestored(ranked, in: app, name: "ranked-after-foreground-restart-and-launch-refresh")
        try selectTab(mixedName, in: app)
        assertRestored(mixed, in: app, name: "mixed-after-foreground-restart-and-launch-refresh")
    }

    @MainActor
    func testLiveRecommendedManualGap() throws {
        try verifyManualGap(tab: ProcessInfo.processInfo.environment["FLARE_RANKED_TAB"] ?? "Featured", name: "ranked")
    }

    @MainActor
    func testLiveMixedManualGap() throws {
        try verifyManualGap(tab: ProcessInfo.processInfo.environment["FLARE_MIXED_TAB"] ?? "Mixed", name: "mixed")
    }

    @MainActor
    private func verifyManualGap(tab: String, name: String) throws {
        continueAfterFailure = false
        let app = liveApp()
        launch(app)
        defer { app.terminate() }
        try selectTab(tab, in: app)
        showLatestIfAvailable(in: app)
        let gaps = app.cells.matching(NSPredicate(format: "identifier BEGINSWITH 'timeline-item:timeline-gap:'"))
        var found: XCUIElement?
        for _ in 0..<180 {
            found = gaps.allElementsBoundByIndex.first { $0.frame.minY > app.frame.height * 0.2 && $0.frame.maxY < app.frame.height * 0.82 }
            if found != nil { break }
            scrollGapSearch(in: app)
        }
        let gap = try XCTUnwrap(found, "A real refresh must leave a manual gap in this feed")
        let gapID = gap.identifier
        let gapTop = gap.frame.minY
        let predecessor = try XCTUnwrap(app.cells.matching(NSPredicate(format: "identifier BEGINSWITH 'timeline-item:' AND NOT identifier BEGINSWITH 'timeline-item:timeline-gap:'"))
            .allElementsBoundByIndex.filter { $0.frame.minY < gapTop }.max { $0.frame.minY < $1.frame.minY })
        let anchor = Bookmark(identifier: predecessor.identifier, top: predecessor.frame.minY)
        assertRestored(anchor, in: app, name: "\(name)-gap-before-click")
        // Merely reaching the gap must not advance it.
        Thread.sleep(forTimeInterval: 5)
        XCTAssertTrue(gap.exists)
        XCTAssertEqual(gap.frame.minY, gapTop, accuracy: 1)
        print("HOME-GAP \(name) waiting: \(gapID), top=\(gapTop)")
        gap.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        let advanced = NSPredicate { _, _ in !gap.exists || gap.frame.minY > gapTop + 20 }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: advanced, object: nil)], timeout: 45), .completed,
                       "One explicit click must fill this real gap downward")
        assertRestored(anchor, in: app, name: "\(name)-gap-after-click")
        // Scroll to the same remaining gap, proving it moved after the newly inserted items.
        let sameGap = app.cells.matching(NSPredicate(format: "identifier == %@", gapID)).firstMatch
        for _ in 0..<180 {
            if sameGap.exists && sameGap.frame.minY > app.frame.height * 0.2 && sameGap.frame.maxY < app.frame.height * 0.82 { break }
            scrollGapSearch(in: app, towardTop: sameGap.exists && sameGap.frame.minY < app.frame.height * 0.2)
        }
        XCTAssertTrue(sameGap.exists && sameGap.frame.minY > app.frame.height * 0.2 && sameGap.frame.maxY < app.frame.height * 0.82,
                      "This fixture has another remote page, so its remaining gap must be visible below the inserted items")
        let remainingTop = stableTop(of: sameGap)
        Thread.sleep(forTimeInterval: 5)
        XCTAssertTrue(sameGap.exists)
        XCTAssertEqual(sameGap.frame.minY, remainingTop, accuracy: 1, "The remaining gap must also wait for a click")
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "\(name)-remaining-gap-below-inserted-items"
        screenshot.lifetime = .keepAlways
        add(screenshot)
        print("HOME-GAP \(name) remaining: \(gapID)")
    }

    @MainActor
    private func scrollGapSearch(in app: XCUIApplication, towardTop: Bool = false) {
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.1, dy: towardTop ? 0.3 : 0.75)).press(
            forDuration: 0.05,
            thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.1, dy: towardTop ? 0.75 : 0.3)),
            withVelocity: .slow, thenHoldForDuration: 0.2
        )
    }

    @MainActor
    private func stableTop(of item: XCUIElement) -> CGFloat {
        var previous: CGFloat = .nan
        var samples = 0
        let settled = NSPredicate { _, _ in
            guard item.exists else { return false }
            let current = item.frame.minY
            samples = abs(current - previous) <= 1 ? samples + 1 : 0
            previous = current
            return samples >= 3
        }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: settled, object: nil)], timeout: 15), .completed)
        return previous
    }

    @MainActor
    private func openBehaviorSettings(in app: XCUIApplication) {
        let menu = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Open navigation menu")).firstMatch
        XCTAssertTrue(menu.waitForExistence(timeout: 20))
        menu.tap()
        Thread.sleep(forTimeInterval: 1)
        // Real accounts make the drawer longer than a screen. Reveal its footer
        // before asking XCTest to compute a hit point for the Settings button.
        for _ in 0..<3 {
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.8)).press(
                forDuration: 0.05,
                thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.25)),
                withVelocity: .slow, thenHoldForDuration: 0.2
            )
        }
        let settings = app.buttons.matching(NSPredicate(format: "label == %@", "Settings")).firstMatch
        XCTAssertTrue(settings.waitForExistence(timeout: 5))
        settings.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        let behavior = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Behavior")).firstMatch
        XCTAssertTrue(behavior.waitForExistence(timeout: 10))
        behavior.tap()
        Thread.sleep(forTimeInterval: 1)
    }

    @MainActor
    func testEnableLiveAutomaticRefresh() {
        setAutomaticRefresh("Every minute")
    }

    @MainActor
    func testDisableLiveAutomaticRefresh() {
        setAutomaticRefresh("Off")
    }

    @MainActor
    private func setAutomaticRefresh(_ interval: String) {
        continueAfterFailure = false
        let app = liveApp()
        launch(app)
        defer { app.terminate() }
        print("HOME-DRIVER timer settings with settled drawer")
        openBehaviorSettings(in: app)
        let picker = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Auto-refresh home timeline")).firstMatch
        XCTAssertTrue(picker.waitForExistence(timeout: 10))
        picker.tap()
        let choice = app.buttons.matching(NSPredicate(format: "label == %@", interval)).firstMatch
        XCTAssertTrue(choice.waitForExistence(timeout: 5))
        choice.tap()
        Thread.sleep(forTimeInterval: 1)
    }

    @MainActor
    func testLiveRecommendedAutomaticRefresh() throws {
        try verifyAutomaticRefresh(tab: ProcessInfo.processInfo.environment["FLARE_RANKED_TAB"] ?? "Featured", name: "ranked")
    }

    @MainActor
    func testLiveMixedAutomaticRefresh() throws {
        try verifyAutomaticRefresh(tab: ProcessInfo.processInfo.environment["FLARE_MIXED_TAB"] ?? "Mixed", name: "mixed")
    }

    @MainActor
    func testLiveRecommendedPullRefresh() throws {
        try verifyPullRefresh(tab: ProcessInfo.processInfo.environment["FLARE_RANKED_TAB"] ?? "Featured", name: "ranked")
    }

    @MainActor
    func testLiveMixedPullRefresh() throws {
        try verifyPullRefresh(tab: ProcessInfo.processInfo.environment["FLARE_MIXED_TAB"] ?? "Mixed", name: "mixed")
    }

    @MainActor
    func testLiveRecommendedNewContentNotice() throws {
        try verifyPullRefresh(tab: ProcessInfo.processInfo.environment["FLARE_RANKED_TAB"] ?? "Featured", name: "ranked", verifyNotice: true)
    }

    @MainActor
    func testLiveMixedNewContentNotice() throws {
        try verifyPullRefresh(tab: ProcessInfo.processInfo.environment["FLARE_MIXED_TAB"] ?? "Mixed", name: "mixed", verifyNotice: true)
    }

    @MainActor
    private func verifyPullRefresh(tab: String, name: String, verifyNotice: Bool = false) throws {
        continueAfterFailure = false
        let app = liveApp()
        launch(app)
        defer { app.terminate() }
        try selectTab(tab, in: app)
        showLatestIfAvailable(in: app)
        Thread.sleep(forTimeInterval: 2)
        let bookmark = try scrollAndRemember(in: app, swipes: 0, name: "\(name)-before-pull-refresh")
        print("HOME-PULL \(name) starting")
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.98, dy: 0.25)).press(
            forDuration: 0.05,
            thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.98, dy: 0.8)),
            withVelocity: .slow, thenHoldForDuration: 0
        )
        Thread.sleep(forTimeInterval: 15)
        assertRestored(bookmark, in: app, name: "\(name)-after-pull-refresh")
        if verifyNotice {
            let notice = app.buttons["home-new-content"]
            XCTAssertTrue(notice.waitForExistence(timeout: 15), "The live feed must prepend content to exercise the notice")
            attachScreenshot(in: app, name: "\(name)-new-content-notice-visible")
            notice.tap()
            XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: notice)], timeout: 10), .completed)
            attachScreenshot(in: app, name: "\(name)-new-content-notice-dismissed")
            _ = try scrollAndRemember(in: app, swipes: 1, name: "\(name)-scrolled-away-after-notice")
            XCTAssertFalse(notice.exists, "Scrolling away from the latest post must not announce it again")
            attachScreenshot(in: app, name: "\(name)-no-notice-after-scrolling-away")
            print("HOME-NOTICE \(name): shown, clicked, dismissed, and stayed dismissed after scrolling")
        }
    }

    @MainActor
    private func attachScreenshot(in app: XCUIApplication, name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    @MainActor
    private func verifyAutomaticRefresh(tab: String, name: String) throws {
        continueAfterFailure = false
        let app = liveApp()
        launch(app)
        defer { app.terminate() }
        try selectTab(tab, in: app)
        let bookmark = try scrollAndRemember(in: app, swipes: 1, name: "\(name)-before-timed-refresh")
        print("HOME-AUTO \(name) waiting for the configured one-minute timer")
        Thread.sleep(forTimeInterval: 80)
        assertRestored(bookmark, in: app, name: "\(name)-after-timed-refresh")
    }

    @MainActor
    private func launch(_ app: XCUIApplication) {
        app.launch()
        // Live media can keep Xcode 27's global animation-idle wait pending forever.
        // This changes only XCTest synchronization; app animations remain enabled.
        // Every reading-position assertion still requires three stable frame samples.
        if app.responds(to: NSSelectorFromString("setCurrentInteractionOptions:")) {
            app.setValue(3, forKey: "currentInteractionOptions")
        }
    }

    @MainActor
    private func liveApp() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        return app
    }

    @MainActor
    private func title(in app: XCUIApplication) -> XCUIElement {
        app.staticTexts.matching(identifier: "home-timeline-title").firstMatch
    }

    @MainActor
    private func assertSelectedTab(_ name: String, in app: XCUIApplication) {
        let selected = title(in: app)
        XCTAssertTrue(selected.waitForExistence(timeout: 20), "Home title must exist")
        XCTAssertTrue(selected.label.contains(name), "Expected \(name), found \(selected.label)\n\(app.debugDescription)")
    }

    @MainActor
    private func selectTab(_ name: String, in app: XCUIApplication) throws {
        let selected = title(in: app)
        XCTAssertTrue(selected.waitForExistence(timeout: 20), "Home title must exist")
        if !selected.label.contains(name) {
            selected.tap()
            let option = app.buttons.matching(NSPredicate(format: "label == %@", name)).firstMatch
            XCTAssertTrue(option.waitForExistence(timeout: 5), "Configured Home tab must exist: \(name)")
            option.tap()
        }
        assertSelectedTab(name, in: app)
        XCTAssertTrue(app.collectionViews["timeline-list"].waitForExistence(timeout: 15))
        let item = app.cells.matching(NSPredicate(format: "identifier BEGINSWITH 'timeline-item:'")).firstMatch
        XCTAssertTrue(item.waitForExistence(timeout: 30), "The configured public feed must have loaded")
    }

    @MainActor
    private func showLatestIfAvailable(in app: XCUIApplication) {
        let latest = app.buttons["View latest posts"]
        if latest.waitForExistence(timeout: 10) { latest.tap() }
    }

    @MainActor
    private func scrollAndRemember(in app: XCUIApplication, swipes: Int, name: String) throws -> Bookmark {
        for _ in 0..<swipes {
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.78)).press(
                forDuration: 0.05,
                thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.32)),
                withVelocity: .slow,
                thenHoldForDuration: 0
            )
        }
        let navigationBottom = app.navigationBars.firstMatch.frame.maxY
        let visible = app.cells.matching(NSPredicate(format: "identifier BEGINSWITH 'timeline-item:' AND NOT identifier BEGINSWITH 'timeline-item:timeline-gap:'"))
            .allElementsBoundByIndex
            .filter { $0.frame.maxY > navigationBottom + 30 && $0.frame.minY < app.frame.height * 0.75 }
            .sorted { $0.frame.minY < $1.frame.minY }
        let item = try XCTUnwrap(visible.first, "A real item must intersect the reading viewport")
        let bookmark = Bookmark(identifier: item.identifier, top: item.frame.minY)
        assertRestored(bookmark, in: app, name: name)
        return bookmark
    }

    @MainActor
    private func assertRestored(_ bookmark: Bookmark, in app: XCUIApplication, name: String) {
        let item = app.cells.matching(NSPredicate(format: "identifier == %@", bookmark.identifier)).firstMatch
        var matchingSamples = 0
        let restored = NSPredicate { _, _ in
            let matches = item.exists && abs(item.frame.minY - bookmark.top) <= 1
            matchingSamples = matches ? matchingSamples + 1 : 0
            return matchingSamples >= 3
        }
        let result = XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: restored, object: nil)], timeout: 20)
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = name
        screenshot.lifetime = .keepAlways
        add(screenshot)
        let actual = item.exists ? item.frame.minY : .nan
        print("HOME-RESTORE \(name): \(bookmark.identifier), expected=\(bookmark.top), actual=\(actual)")
        XCTAssertEqual(result, .completed, "\(name): expected the same item at \(bookmark.top) pt, found \(actual) pt")
    }
}
