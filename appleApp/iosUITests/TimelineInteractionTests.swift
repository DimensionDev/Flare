import XCTest

final class TimelineInteractionTests: XCTestCase {
    @MainActor private func run(_ scenario: String, gesture: Bool = false) {
        let app = XCUIApplication(bundleIdentifier: "dev.dimension.flare.timeline-tests")
        app.launchArguments = [scenario]
        app.launch()
        defer { app.terminate() }
        if gesture {
            XCTAssertTrue(app.staticTexts["gesture-ready"].waitForExistence(timeout: 10))
            let list = app.collectionViews["timeline-list"]
            let start = list.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.35))
            let end = list.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.8))
            start.press(forDuration: 0.05, thenDragTo: end,
                        withVelocity: scenario.contains("deceleration") ? .fast : .slow,
                        thenHoldForDuration: 0)
        }
        let result = app.staticTexts["timeline-result"]
        XCTAssertTrue(result.waitForExistence(timeout: 20))
        XCTAssertEqual(result.label, "PASS")
    }

    @MainActor func testHeightRefinementDoesNotInterruptScrollToTop() { run("scroll-to-top") }
    @MainActor func testHeightRefinementPreservesAnActivePan() { run("drag", gesture: true) }
    @MainActor func testHeightRefinementPreservesDeceleration() { run("deceleration", gesture: true) }
    @MainActor func testRefiningAPartlyHiddenCardKeepsVisibleContentDuringPan() { run("refine-reading-item", gesture: true) }
    @MainActor func testRefiningAPartlyHiddenCardKeepsVisibleContentDuringDeceleration() { run("refine-reading-item-deceleration", gesture: true) }
    @MainActor func testPrependDuringPullRefreshKeepsTheReadingItem() { run("pull-refresh", gesture: true) }
    @MainActor func testPrependDuringRefreshRevealKeepsTheReadingItem() { run("fast-refresh-prepend") }
    @MainActor func testSnapshotChangesPreserveAnActivePan() { run("snapshot-drag", gesture: true) }
    @MainActor func testSnapshotChangesPreserveDeceleration() { run("snapshot-deceleration", gesture: true) }
    @MainActor func testSnapshotChangesPreserveMultipleColumns() { run("snapshot-columns-deceleration", gesture: true) }
    @MainActor func testRefreshAnimatesWithoutExtraBlankSpace() { run("refresh") }
    @MainActor func testInitialRefreshAnimatesWithoutExtraBlankSpace() { run("initial-refresh") }
    @MainActor func testRefreshSpinnerSurvivesAnUnanimatedUpdate() { run("refresh-without-animations") }
}
