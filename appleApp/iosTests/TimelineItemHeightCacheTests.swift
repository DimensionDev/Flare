import XCTest
import CoreGraphics

final class TimelineItemHeightCacheTests: XCTestCase {
    private let single = TimelineItemHeightCache.Geometry(widthInPixels: 780, multipleColumns: false)

    func testMatchingMeasurementsDoNotScheduleAnotherLayout() {
        var cache = TimelineItemHeightCache()
        cache.store(240, for: "post", geometry: single, renderHash: 1)
        XCTAssertFalse(cache.stage(241, for: "post", geometry: single, renderHash: 1))
        XCTAssertFalse(cache.hasPendingMeasurements)
        XCTAssertFalse(cache.applyPendingMeasurements())
        XCTAssertTrue(cache.stage(600, for: "post", geometry: single, renderHash: 1))
        XCTAssertFalse(cache.stage(240, for: "post", geometry: single, renderHash: 1))
        XCTAssertFalse(cache.hasPendingMeasurements)
        XCTAssertEqual(cache.height(for: "post", geometry: single), 240)
        XCTAssertTrue(cache.stage(240, for: "post", geometry: single, renderHash: 2))
        XCTAssertTrue(cache.hasPendingMeasurements)
        XCTAssertFalse(cache.applyPendingMeasurements())
        XCTAssertEqual(cache.height(for: "post", geometry: single, renderHash: 2), 240)
    }

    func testCellsReusePendingMeasurementsWithoutPublishingThemToTheLayout() {
        var cache = TimelineItemHeightCache()
        let wide = TimelineItemHeightCache.Geometry(widthInPixels: 980, multipleColumns: false)
        cache.store(240, for: "post", geometry: single, renderHash: 1)
        cache.stage(600, for: "post", geometry: single, renderHash: 2)
        XCTAssertEqual(cache.measuredHeight(for: "post", geometry: single, renderHash: 2), 600)
        XCTAssertEqual(cache.measuredHeight(for: "post", geometry: single, renderHash: 1), 240)
        XCTAssertNil(cache.measuredHeight(for: "post", geometry: wide, renderHash: 2))
        XCTAssertEqual(cache.height(for: "post", geometry: single), 240)
        XCTAssertNil(cache.height(for: "post", geometry: single, renderHash: 2))
        XCTAssertTrue(cache.applyPendingMeasurements())
        XCTAssertEqual(cache.measuredHeight(for: "post", geometry: single, renderHash: 2), 600)
        XCTAssertNil(cache.measuredHeight(for: "post", geometry: single, renderHash: 1))
    }

    func testCellMeasurementsStayInvisibleUntilThePreservedLayoutCommits() {
        var cache = TimelineItemHeightCache()
        cache.store(240, for: "post", geometry: single, renderHash: 1)
        cache.stage(600, for: "post", geometry: single, renderHash: 2)
        cache.stage(480, for: "new", geometry: single, renderHash: 1)
        XCTAssertEqual(cache.height(for: "post", geometry: single), 240)
        XCTAssertNil(cache.height(for: "post", geometry: single, renderHash: 2))
        XCTAssertNil(cache.height(for: "new", geometry: single))
        XCTAssertTrue(cache.hasPendingMeasurements)
        XCTAssertTrue(cache.applyPendingMeasurements())
        XCTAssertEqual(cache.height(for: "post", geometry: single, renderHash: 2), 600)
        XCTAssertEqual(cache.height(for: "new", geometry: single), 480)
        XCTAssertFalse(cache.hasPendingMeasurements)
        XCTAssertFalse(cache.applyPendingMeasurements())
    }

    func testRepeatedMeasurementsCommitOnlyTheLatestPayloadAndGeometry() {
        var cache = TimelineItemHeightCache()
        let wide = TimelineItemHeightCache.Geometry(widthInPixels: 980, multipleColumns: false)
        cache.stage(600, for: "post", geometry: single, renderHash: 1)
        cache.stage(360, for: "post", geometry: wide, renderHash: 2)
        XCTAssertTrue(cache.applyPendingMeasurements())
        XCTAssertNil(cache.height(for: "post", geometry: single))
        XCTAssertNil(cache.height(for: "post", geometry: wide, renderHash: 1))
        XCTAssertEqual(cache.height(for: "post", geometry: wide, renderHash: 2), 360)
        cache.stage(361, for: "post", geometry: wide, renderHash: 3)
        XCTAssertFalse(cache.applyPendingMeasurements())
        XCTAssertEqual(cache.height(for: "post", geometry: wide, renderHash: 3), 360)
    }

    func testPruningAndResetDiscardUncommittedMeasurements() {
        var cache = TimelineItemHeightCache()
        cache.stage(600, for: "removed", geometry: single, renderHash: 1)
        cache.stage(480, for: "kept", geometry: single, renderHash: 1)
        cache.keep(["kept"])
        XCTAssertTrue(cache.applyPendingMeasurements())
        XCTAssertNil(cache.height(for: "removed", geometry: single))
        XCTAssertEqual(cache.height(for: "kept", geometry: single), 480)
        cache.stage(600, for: "kept", geometry: single, renderHash: 2)
        cache.removeAll()
        XCTAssertFalse(cache.hasPendingMeasurements)
        XCTAssertFalse(cache.applyPendingMeasurements())
        XCTAssertNil(cache.height(for: "kept", geometry: single))
    }

    func testRenderChangeKeepsGeometryButRequiresFreshMeasurement() {
        var cache = TimelineItemHeightCache()
        XCTAssertTrue(cache.store(70, for: "post", geometry: single, renderHash: 1))

        XCTAssertNil(cache.height(for: "post", geometry: single, renderHash: 2))
        XCTAssertEqual(cache.height(for: "post", geometry: single), 70)
        XCTAssertFalse(cache.store(70, for: "post", geometry: single, renderHash: 2))
        XCTAssertEqual(cache.height(for: "post", geometry: single, renderHash: 2), 70)
        XCTAssertTrue(cache.store(170, for: "post", geometry: single, renderHash: 3))
    }

    func testMeasurementsDoNotLeakBetweenWidthsOrCardStyles() {
        var cache = TimelineItemHeightCache()
        let wide = TimelineItemHeightCache.Geometry(widthInPixels: 980, multipleColumns: false)
        let card = TimelineItemHeightCache.Geometry(widthInPixels: 780, multipleColumns: true)
        cache.store(70, for: "post", geometry: single, renderHash: 1)
        XCTAssertNil(cache.height(for: "post", geometry: wide))
        XCTAssertNil(cache.height(for: "post", geometry: card))
        cache.store(60, for: "post", geometry: wide, renderHash: 1)
        XCTAssertEqual(cache.height(for: "post", geometry: single), 70)
        cache.store(90, for: "post", geometry: card, renderHash: 1)
        XCTAssertNil(cache.height(for: "post", geometry: single), "Only the two most recent geometries are retained")
    }

    func testRemovedItemsReleaseTheirMeasurements() {
        var cache = TimelineItemHeightCache()
        cache.store(70, for: "removed", geometry: single, renderHash: 1)
        cache.store(90, for: "kept", geometry: single, renderHash: 1)
        cache.keep(["kept"])
        XCTAssertNil(cache.height(for: "removed", geometry: single))
        XCTAssertEqual(cache.height(for: "kept", geometry: single), 90)
    }

    func testSmallCorrectionsCannotAccumulateWithoutUpdatingTheLayout() {
        var cache = TimelineItemHeightCache()
        cache.store(70, for: "post", geometry: single, renderHash: 1)
        XCTAssertFalse(cache.store(71, for: "post", geometry: single, renderHash: 2))
        XCTAssertEqual(cache.height(for: "post", geometry: single, renderHash: 2), 70)
        XCTAssertTrue(cache.store(72, for: "post", geometry: single, renderHash: 3))
        XCTAssertEqual(cache.height(for: "post", geometry: single), 72)
    }
}
