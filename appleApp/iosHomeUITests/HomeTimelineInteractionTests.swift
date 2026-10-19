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
        // XCTest deliberately leaves app animations enabled. Wait for View latest
        // to finish before choosing a gap and its predecessor as the baseline.
        let posts = app.cells.matching(NSPredicate(format: "identifier BEGINSWITH 'timeline-item:' AND NOT identifier BEGINSWITH 'timeline-item:timeline-gap:'"))
        _ = stableTop(of: posts.firstMatch)
        let gaps = app.cells.matching(NSPredicate(format: "identifier BEGINSWITH 'timeline-item:timeline-gap:'"))
        var found: XCUIElement?
        for _ in 0..<180 {
            found = gaps.allElementsBoundByAccessibilityElement.first { element in
                guard element.exists else { return false }
                let frame = element.frame
                return frame.minY > app.frame.height * 0.2 && frame.maxY < app.frame.height * 0.82
            }
            if found != nil { break }
            scrollGapSearch(in: app)
        }
        let gap = try XCTUnwrap(found, "A real refresh must leave a manual gap in this feed")
        let gapID = gap.identifier
        let gapTop = stableTop(of: gap)
        let predecessor = try XCTUnwrap(posts
            .allElementsBoundByAccessibilityElement.filter { $0.exists && $0.frame.minY < gapTop }.max { $0.frame.minY < $1.frame.minY })
        let anchor = Bookmark(identifier: predecessor.identifier, top: predecessor.frame.minY)
        assertRestored(anchor, in: app, name: "\(name)-gap-before-click")
        // Merely reaching the gap must not advance it.
        Thread.sleep(forTimeInterval: 5)
        XCTAssertTrue(gap.exists)
        XCTAssertEqual(gap.frame.minY, gapTop, accuracy: 1)
        print("HOME-GAP \(name) waiting: top=\(gapTop)")
        var filledDownward = false
        for page in 1...5 {
            let loading = gap.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Loading missing posts…")).firstMatch
            gap.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
            let observedLoading = loading.waitForExistence(timeout: 3)
            if observedLoading {
                XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: loading)], timeout: 45), .completed)
            }
            let advanced = NSPredicate { _, _ in !gap.exists || gap.frame.minY > gapTop + 20 }
            filledDownward = XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: advanced, object: nil)], timeout: 5) == .completed
            assertRestored(anchor, in: app, name: "\(name)-gap-after-click-\(page)")
            if filledDownward { break }
            XCTAssertTrue(observedLoading, "Each explicit click must start a page load")
            let retry = gap.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Could not load posts. Tap to retry.")).firstMatch
            XCTAssertFalse(retry.exists, "The live page must load successfully")
            // Recommended sources can return an entire page of already-seen posts.
            // That page keeps the gap in place and still requires another click.
            Thread.sleep(forTimeInterval: 5)
            XCTAssertTrue(gap.exists)
            XCTAssertEqual(gap.frame.minY, gapTop, accuracy: 1)
            print("HOME-GAP \(name) page \(page) kept its position and still waits for a click")
        }
        XCTAssertTrue(filledDownward, "Explicit page loads must eventually fill this live gap downward")
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
        attachScreenshot(in: app, name: "\(name)-remaining-gap-below-inserted-items")
        print("HOME-GAP \(name) remaining gap still waits for a click")
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
    private func openSettings(in app: XCUIApplication) {
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
    }

    @MainActor
    private func openBehaviorSettings(in app: XCUIApplication) {
        openSettings(in: app)
        let behavior = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Behavior")).firstMatch
        XCTAssertTrue(behavior.waitForExistence(timeout: 10))
        behavior.tap()
        Thread.sleep(forTimeInterval: 1)
    }

    @MainActor
    func testEnableLiveCards() {
        setLiveDisplayMode("Card", rememberPrevious: true)
    }

    @MainActor
    func testEnableLiveGallery() {
        setLiveDisplayMode("Gallery (home timeline only)", rememberPrevious: true)
    }

    @MainActor
    func testRestoreLiveDisplayMode() throws {
        let mode = try XCTUnwrap(ProcessInfo.processInfo.environment["FLARE_RESTORE_DISPLAY_MODE"]
            ?? UserDefaults.standard.string(forKey: "acceptance.originalDisplayMode"))
        XCTAssertTrue(displayModes.contains(mode), "The original display mode must be known before restoring it")
        setLiveDisplayMode(mode)
    }

    private let displayModes = ["Card", "Plain (single column only)", "Gallery (home timeline only)"]

    @MainActor
    private func setLiveDisplayMode(_ mode: String, rememberPrevious: Bool = false) {
        continueAfterFailure = false
        let app = liveApp()
        launch(app)
        defer { app.terminate() }
        openSettings(in: app)
        let layout = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Layout")).firstMatch
        XCTAssertTrue(layout.waitForExistence(timeout: 10))
        layout.tap()
        let picker = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Timeline display mode")).firstMatch
        XCTAssertTrue(picker.waitForExistence(timeout: 10))
        let current = displayModes.first { picker.label.hasSuffix($0) }
        XCTAssertNotNil(current, "Read the selected mode from the native picker label")
        if rememberPrevious {
            UserDefaults.standard.set(current, forKey: "acceptance.originalDisplayMode")
            print("HOME-DISPLAY original=\(current ?? "unknown")")
        }
        if current == mode { return }
        picker.tap()
        let choice = app.buttons.matching(NSPredicate(format: "label == %@", mode)).firstMatch
        XCTAssertTrue(choice.waitForExistence(timeout: 5))
        choice.tap()
        let applied = NSPredicate { _, _ in picker.exists && picker.label.hasSuffix(mode) }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: applied, object: nil)], timeout: 5), .completed)
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
        // Launch refresh runs independently of selecting the tab. Let it settle
        // before navigating to the latest head that the pull must start from.
        Thread.sleep(forTimeInterval: 15)
        showLatestIfAvailable(in: app)
        // A read notice may already be dismissed while the restored viewport is
        // farther down. Use the native status-bar action before pulling, otherwise
        // this gesture merely scrolls existing content instead of refreshing it.
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.1, dy: 0.025)).tap()
        Thread.sleep(forTimeInterval: 2)
        // Latest aligns the first post, while Gallery has padding above that post.
        // Settle at the natural scroll boundary before recording the pull's anchor;
        // otherwise the gesture itself scrolls through that padding before refreshing.
        let top = app.coordinate(withNormalizedOffset: CGVector(dx: 0.98, dy: 0.3))
        top.press(forDuration: 0.05, thenDragTo: top.withOffset(CGVector(dx: 0, dy: 40)),
                  withVelocity: .slow, thenHoldForDuration: 0)
        Thread.sleep(forTimeInterval: 1)
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
        guard ProcessInfo.processInfo.environment["FLARE_UI_SCREENSHOTS"] == "1" else { return }
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
        let matches = NSPredicate { _, _ in selected.exists && selected.label.contains(name) }
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: matches, object: nil)], timeout: 20), .completed,
                       "Home title must become \(name)")
    }

    @MainActor
    private func selectTab(_ name: String, in app: XCUIApplication) throws {
        let selected = title(in: app)
        XCTAssertTrue(selected.waitForExistence(timeout: 20), "Home title must exist")
        if !selected.label.contains(name) {
            let loginNotice = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "Login session expired")).firstMatch
            if loginNotice.exists {
                print("HOME-NAV waiting for login notification")
                XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: loginNotice)], timeout: 20), .completed)
            }
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
        let latest = app.buttons["home-new-content"]
        if latest.waitForExistence(timeout: 10) { latest.tap() }
    }

    @MainActor
    private func scrollAndRemember(in app: XCUIApplication, swipes: Int, name: String) throws -> Bookmark {
        for _ in 0..<swipes {
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.1, dy: 0.78)).press(
                forDuration: 0.05,
                thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.1, dy: 0.32)),
                withVelocity: .slow,
                thenHoldForDuration: 0
            )
        }
        let posts = app.cells.matching(NSPredicate(format: "identifier BEGINSWITH 'timeline-item:' AND NOT identifier BEGINSWITH 'timeline-item:timeline-gap:'"))
        _ = stableTop(of: posts.firstMatch)
        let navigationBottom = app.navigationBars.firstMatch.frame.maxY
        // Match the native reading anchor, including a card with only a small
        // visible portion below the bar. Picking another column's next card can
        // report a false drift when a restored paging window reflows the grid.
        let visible = posts
            .allElementsBoundByAccessibilityElement
            .compactMap { element -> (item: XCUIElement, frame: CGRect)? in
                guard element.exists else { return nil }
                let frame = element.frame
                guard frame.maxY > navigationBottom, frame.minY < app.frame.height * 0.75 else { return nil }
                return (element, frame)
            }
            .sorted {
                abs($0.frame.minY - $1.frame.minY) > 0.5
                    ? $0.frame.minY < $1.frame.minY : $0.frame.minX < $1.frame.minX
            }
        let selected = try XCTUnwrap(visible.first, "A real item must intersect the reading viewport")
        let bookmark = Bookmark(identifier: selected.item.identifier, top: stableTop(of: selected.item))
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
        attachScreenshot(in: app, name: name)
        let actual = item.exists ? item.frame.minY : .nan
        print("HOME-RESTORE \(name): sameItem=\(item.exists), expected=\(bookmark.top), actual=\(actual)")
        XCTAssertEqual(result, .completed, "\(name): expected the same item at \(bookmark.top) pt, found \(actual) pt")
    }
}
