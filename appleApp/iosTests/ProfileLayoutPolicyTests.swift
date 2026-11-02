import CoreGraphics
import SwiftUI
import XCTest

final class ProfileLayoutPolicyTests: XCTestCase {
    func testCardModeStartsWhenTheTimelineHasTwoColumns() {
        XCTAssertFalse(ProfileLayoutPolicy(width: 1_087).usesCard)
        XCTAssertTrue(ProfileLayoutPolicy(width: 1_088).usesCard)
    }

    func testUnfoldedLayoutPreservesTheTimelineFrame() {
        let layout = ProfileLayoutPolicy(width: 1_000)

        XCTAssertFalse(layout.usesCard)
        XCTAssertEqual(layout.profileWidth, 400)
        XCTAssertEqual(layout.columnSpacing, 8)
        XCTAssertEqual(1_000 - layout.profileWidth - layout.columnSpacing, 592)
    }

    func testBookFoldUsesBothEdgesOfTheReservedRegion() {
        let layout = ProfileLayoutPolicy(
            width: 944,
            division: CGRect(x: 456, y: 0, width: 32, height: 600)
        )

        XCTAssertFalse(layout.usesCard)
        XCTAssertEqual(layout.profileWidth, 456)
        XCTAssertEqual(layout.columnSpacing, 32)
        XCTAssertEqual(944 - layout.profileWidth - layout.columnSpacing, 456)
    }

    func testTrailingSystemBarDoesNotMoveTheSplitAwayFromTheFold() {
        let layout = ProfileLayoutPolicy(
            width: 880,
            division: CGRect(x: 456, y: 0, width: 32, height: 600)
        )

        XCTAssertEqual(layout.profileWidth, 456)
        XCTAssertEqual(layout.profileWidth + layout.columnSpacing, 488)
    }

    func testRightToLeftFoldAlignsWithAsymmetricReservedRegion() {
        let layout = ProfileLayoutPolicy(
            width: 880,
            division: CGRect(x: 456, y: 0, width: 32, height: 600),
            layoutDirection: .rightToLeft
        )

        XCTAssertEqual(layout.profileWidth, 392)
        XCTAssertEqual(layout.columnSpacing, 32)
        XCTAssertEqual(880 - layout.profileWidth - layout.columnSpacing, 456)
        XCTAssertEqual(880 - layout.profileWidth, 488)
    }

    func testMultipleTimelineColumnsKeepTheCardWhenFolded() {
        let layout = ProfileLayoutPolicy(
            width: 1_440,
            division: CGRect(x: 704, y: 0, width: 32, height: 900)
        )

        XCTAssertTrue(layout.usesCard)
        XCTAssertEqual(layout.profileWidth, 400)
        XCTAssertEqual(layout.columnSpacing, 8)
    }

    func testHorizontalInactiveAndOutsideDivisionsKeepTheUnfoldedLayout() {
        let unfolded = ProfileLayoutPolicy(width: 944)
        for division in [
            CGRect(x: 0, y: 284, width: 944, height: 32),
            CGRect(x: 472, y: 0, width: 0, height: 600),
            CGRect(x: -32, y: 0, width: 32, height: 600),
            CGRect(x: 944, y: 0, width: 32, height: 600)
        ] {
            XCTAssertEqual(ProfileLayoutPolicy(width: 944, division: division), unfolded)
        }
    }
}
