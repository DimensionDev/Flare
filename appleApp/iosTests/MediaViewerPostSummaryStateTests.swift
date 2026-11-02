import XCTest

final class MediaViewerPostSummaryStateTests: XCTestCase {
    func testReopeningWaitsForTheCurrentMediaMeasurement() {
        var summary = MediaViewerPostSummaryState()
        for height: CGFloat in [132, 180, 132, 132] {
            summary.reset()
            XCTAssertFalse(summary.hasMeasurement)
            XCTAssertTrue(summary.measure(height))
            XCTAssertTrue(summary.hasMeasurement)
            XCTAssertEqual(summary.height, height)
            summary.beginPresentation()
            XCTAssertNil(summary.finishPresentation())
        }
    }

    func testHidingDuringPresentationDiscardsDeferredHeight() {
        var summary = MediaViewerPostSummaryState()
        summary.measure(132)
        summary.beginPresentation()
        XCTAssertFalse(summary.measure(180))
        summary.reset()

        XCTAssertFalse(summary.hasMeasurement)
        XCTAssertNil(summary.finishPresentation())
        XCTAssertTrue(summary.measure(160))
        XCTAssertEqual(summary.height, 160)
    }

    func testPresentationDefersOnlyTheLatestHeight() throws {
        var summary = MediaViewerPostSummaryState()
        summary.measure(132)
        summary.beginPresentation()
        XCTAssertFalse(summary.measure(160))
        XCTAssertFalse(summary.measure(180))
        XCTAssertEqual(summary.height, 132)

        let height = try XCTUnwrap(summary.finishPresentation())
        XCTAssertEqual(height, 180)
        XCTAssertTrue(summary.measure(height))
        XCTAssertEqual(summary.height, 180)
        XCTAssertNil(summary.finishPresentation())
    }

    func testInvalidMeasurementsDoNotMakeTheSummaryReady() {
        var summary = MediaViewerPostSummaryState()
        for height: CGFloat in [0, -1, .nan, .infinity] {
            XCTAssertFalse(summary.measure(height))
            XCTAssertFalse(summary.hasMeasurement)
        }
        XCTAssertTrue(summary.measure(132.2))
        XCTAssertEqual(summary.height, 133)
        XCTAssertFalse(summary.measure(132.8))
    }
}
