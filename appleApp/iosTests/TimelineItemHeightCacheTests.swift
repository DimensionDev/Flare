import XCTest
import CoreGraphics

final class TimelineItemHeightCacheTests: XCTestCase {
    private let single = TimelineItemHeightCache.Geometry(widthInPixels: 780, multipleColumns: false)

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
