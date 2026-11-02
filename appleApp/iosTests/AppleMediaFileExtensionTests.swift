import FlareAppleCore
import Foundation
import XCTest

final class AppleMediaFileExtensionTests: XCTestCase {
    func testSignedAndUppercaseGIFURLsUseAnimatedImages() {
        for value in [
            "https://example.test/image.gif",
            "https://example.test/image.gif?token=signed#preview",
            "https://example.test/image.GIF?token=signed",
            "file:///tmp/image.GIF"
        ] {
            XCTAssertTrue(AppleMediaFileExtension.isGIF(url: URL(string: value)), value)
        }
    }

    func testQueryAndFragmentDoNotDetermineImageFormat() {
        for value in [
            "https://example.test/image.jpg?name=animation.gif",
            "https://example.test/image.png#animation.gif",
            "https://example.test/gif"
        ] {
            XCTAssertFalse(AppleMediaFileExtension.isGIF(url: URL(string: value)), value)
        }
        XCTAssertFalse(AppleMediaFileExtension.isGIF(url: nil))
    }
}
