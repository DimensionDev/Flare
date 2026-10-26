import XCTest
import CoreGraphics

final class TimelineItemHeightCacheTests: XCTestCase {
    private let single = TimelineItemHeightCache.Geometry(widthInPixels: 780, multipleColumns: false)

    func testRenderChangeKeepsGeometryButRequiresFreshMeasurement() {
        var cache = TimelineItemHeightCache()
        XCTAssertTrue(cache.publish(70, for: "post", geometry: single, renderHash: 1))

        XCTAssertNil(cache.height(for: "post", geometry: single, renderHash: 2))
        XCTAssertEqual(cache.height(for: "post", geometry: single), 70)
        XCTAssertFalse(cache.publish(70, for: "post", geometry: single, renderHash: 2))
        XCTAssertEqual(cache.height(for: "post", geometry: single, renderHash: 2), 70)
        XCTAssertTrue(cache.publish(170, for: "post", geometry: single, renderHash: 3))
    }

    func testMeasurementsDoNotLeakBetweenWidthsOrCardStyles() {
        var cache = TimelineItemHeightCache()
        let wide = TimelineItemHeightCache.Geometry(widthInPixels: 980, multipleColumns: false)
        let card = TimelineItemHeightCache.Geometry(widthInPixels: 780, multipleColumns: true)
        cache.publish(70, for: "post", geometry: single, renderHash: 1)
        XCTAssertNil(cache.height(for: "post", geometry: wide))
        XCTAssertNil(cache.height(for: "post", geometry: card))
        cache.publish(60, for: "post", geometry: wide, renderHash: 1)
        XCTAssertEqual(cache.height(for: "post", geometry: single), 70)
        cache.publish(90, for: "post", geometry: card, renderHash: 1)
        XCTAssertNil(cache.height(for: "post", geometry: single), "Only the two most recent geometries are retained")
    }

    func testRemovedItemsReleaseTheirMeasurements() {
        var cache = TimelineItemHeightCache()
        cache.publish(70, for: "removed", geometry: single, renderHash: 1)
        cache.publish(90, for: "kept", geometry: single, renderHash: 1)
        cache.keep(["kept"])
        XCTAssertNil(cache.height(for: "removed", geometry: single))
        XCTAssertEqual(cache.height(for: "kept", geometry: single), 90)
    }

    func testSmallCorrectionsCannotAccumulateWithoutUpdatingTheLayout() {
        var cache = TimelineItemHeightCache()
        cache.publish(70, for: "post", geometry: single, renderHash: 1)
        XCTAssertFalse(cache.publish(71, for: "post", geometry: single, renderHash: 2))
        XCTAssertEqual(cache.height(for: "post", geometry: single, renderHash: 2), 70)
        XCTAssertTrue(cache.publish(72, for: "post", geometry: single, renderHash: 3))
        XCTAssertEqual(cache.height(for: "post", geometry: single), 72)
    }

    func testPendingMeasurementIsReusableWithoutChangingPublishedGeometry() {
        var cache = TimelineItemHeightCache()
        cache.publish(240, for: "post", geometry: single, renderHash: 1)
        cache.stage(464, for: "post", geometry: single, renderHash: 2)

        XCTAssertEqual(cache.height(for: "post", geometry: single), 240)
        XCTAssertEqual(cache.measuredHeight(for: "post", geometry: single, renderHash: 2), 464)
        XCTAssertTrue(cache.hasPendingMeasurements)
        XCTAssertTrue(cache.commitPendingMeasurements { _, _, _ in true })
        XCTAssertEqual(cache.height(for: "post", geometry: single), 464)
        XCTAssertFalse(cache.hasPendingMeasurements)
    }

    func testOnlyTheLatestMeasurementOfEachGeometryIsCommitted() {
        var cache = TimelineItemHeightCache()
        cache.stage(300, for: "post", geometry: single, renderHash: 1)
        cache.stage(400, for: "post", geometry: single, renderHash: 2)
        XCTAssertNil(cache.measuredHeight(for: "post", geometry: single, renderHash: 1))
        XCTAssertEqual(cache.measuredHeight(for: "post", geometry: single, renderHash: 2), 400)
        XCTAssertTrue(cache.commitPendingMeasurements { _, _, hash in hash == 2 })
        XCTAssertEqual(cache.height(for: "post", geometry: single), 400)
        XCTAssertFalse(cache.commitPendingMeasurements { _, _, _ in true })
    }

    func testUnchangedHeightCompletesTheNewRenderWithoutSchedulingLayout() {
        var cache = TimelineItemHeightCache()
        cache.publish(70, for: "post", geometry: single, renderHash: 1)
        cache.stage(71, for: "post", geometry: single, renderHash: 2)
        XCTAssertFalse(cache.hasPendingMeasurements)
        XCTAssertEqual(cache.height(for: "post", geometry: single, renderHash: 2), 70)
        cache.stage(72, for: "post", geometry: single, renderHash: 3)
        XCTAssertEqual(cache.height(for: "post", geometry: single), 70)
        XCTAssertTrue(cache.commitPendingMeasurements { _, _, _ in true })
        XCTAssertEqual(cache.height(for: "post", geometry: single), 72)
    }

    func testReturningToThePublishedHeightCancelsAnUncommittedCorrection() {
        var cache = TimelineItemHeightCache()
        cache.publish(70, for: "post", geometry: single, renderHash: 1)
        cache.stage(150, for: "post", geometry: single, renderHash: 2)
        cache.stage(70, for: "post", geometry: single, renderHash: 3)
        XCTAssertFalse(cache.hasPendingMeasurements)
        XCTAssertEqual(cache.measuredHeight(for: "post", geometry: single, renderHash: 3), 70)
    }

    func testStaleMeasurementsAreDiscardedWhenTheSnapshotChanges() {
        var cache = TimelineItemHeightCache()
        cache.stage(300, for: "removed", geometry: single, renderHash: 1)
        cache.stage(400, for: "changed", geometry: single, renderHash: 1)
        cache.stage(500, for: "current", geometry: single, renderHash: 2)
        cache.keep(["changed", "current"])
        XCTAssertEqual(Set(cache.pendingItemIDs), ["changed", "current"])
        XCTAssertNil(cache.measuredHeight(for: "removed", geometry: single, renderHash: 1))
        XCTAssertTrue(cache.commitPendingMeasurements { id, _, hash in id == "current" && hash == 2 })
        XCTAssertNil(cache.height(for: "changed", geometry: single))
        XCTAssertEqual(cache.height(for: "current", geometry: single), 500)
        XCTAssertFalse(cache.hasPendingMeasurements)
        XCTAssertTrue(cache.pendingItemIDs.isEmpty)
    }

    func testClearingMeasurementsAlsoClearsPendingGeometry() {
        var cache = TimelineItemHeightCache()
        cache.stage(300, for: "post", geometry: single, renderHash: 1)
        cache.removeAll()
        XCTAssertFalse(cache.hasPendingMeasurements)
        XCTAssertNil(cache.measuredHeight(for: "post", geometry: single, renderHash: 1))
        XCTAssertFalse(cache.commitPendingMeasurements { _, _, _ in true })
    }

    func testChangingContentDiscardsPendingMeasurementsButRetainsKnownGeometry() {
        var cache = TimelineItemHeightCache()
        cache.publish(70, for: "post", geometry: single, renderHash: 1)
        cache.stage(300, for: "post", geometry: single, renderHash: 2)
        cache.discardPendingMeasurements()
        XCTAssertFalse(cache.hasPendingMeasurements)
        XCTAssertEqual(cache.height(for: "post", geometry: single), 70)
        XCTAssertNil(cache.measuredHeight(for: "post", geometry: single, renderHash: 2))
    }

    func testPendingGeometryIsBoundedAndDoesNotLeakAcrossWidths() {
        var cache = TimelineItemHeightCache()
        for width in 1...100 {
            let geometry = TimelineItemHeightCache.Geometry(widthInPixels: width, multipleColumns: false)
            cache.stage(CGFloat(width), for: "post", geometry: geometry, renderHash: 1)
        }
        var committed = 0
        XCTAssertTrue(cache.commitPendingMeasurements { _, _, _ in committed += 1; return true })
        XCTAssertEqual(committed, 2)
        XCTAssertNil(cache.height(for: "post", geometry: .init(widthInPixels: 1, multipleColumns: false)))
        XCTAssertEqual(cache.height(for: "post", geometry: .init(widthInPixels: 100, multipleColumns: false)), 100)
    }
}

private extension TimelineItemHeightCache {
    @discardableResult
    mutating func publish(_ height: CGFloat, for id: String, geometry: Geometry, renderHash: Int32) -> Bool {
        stage(height, for: id, geometry: geometry, renderHash: renderHash)
        return commitPendingMeasurements { _, _, _ in true }
    }
}
