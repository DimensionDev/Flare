import XCTest

final class MediaViewerImageLayoutPolicyTests: XCTestCase {
    func testTallImageThumbnailFillsWidthBeforeOriginalLoads() {
        XCTAssertTrue(
            MediaViewerImageLayoutPolicy.shouldFillWidth(
                mediaAspectRatio: 9.0 / 30.0
            )
        )
    }

    func testRegularImageThumbnailKeepsAspectFit() {
        XCTAssertFalse(
            MediaViewerImageLayoutPolicy.shouldFillWidth(
                mediaAspectRatio: 4.0 / 3.0
            )
        )
    }

    func testLoadedTallImageUsesTheSameFillWidthRule() {
        XCTAssertTrue(
            MediaViewerImageLayoutPolicy.shouldFillWidth(
                imageSize: CGSize(width: 900, height: 3_000)
            )
        )
    }

    func testInvalidAspectRatioKeepsAspectFit() {
        XCTAssertFalse(
            MediaViewerImageLayoutPolicy.shouldFillWidth(mediaAspectRatio: nil)
        )
        XCTAssertFalse(
            MediaViewerImageLayoutPolicy.shouldFillWidth(mediaAspectRatio: 0)
        )
    }

    func testPreviewAndLoadedImageKeepTheSameLayoutWithKnownOriginalAspectRatio() {
        let mediaAspectRatio: CGFloat = 9.0 / 30.0
        let previewShouldFill = MediaViewerImageLayoutPolicy.shouldFillWidth(
            mediaAspectRatio: mediaAspectRatio,
            measuredImageSize: CGSize(width: 300, height: 300)
        )
        let loadedShouldFill = MediaViewerImageLayoutPolicy.shouldFillWidth(
            mediaAspectRatio: mediaAspectRatio,
            measuredImageSize: CGSize(width: 900, height: 3_000)
        )

        XCTAssertEqual(previewShouldFill, loadedShouldFill)
    }
}
