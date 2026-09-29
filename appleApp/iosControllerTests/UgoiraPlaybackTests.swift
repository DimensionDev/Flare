import FlareAppleCore
import KotlinSharedUI
import XCTest

@testable import FlareAppleUI

final class UgoiraPlaybackTests: XCTestCase {
    @MainActor
    func testPresenterInitialStateCanBeObservedFromSwift() async throws {
        let media = UiMediaUgoira(
            statusKey: MicroBlogKey(id: "ugoira-test", host: "pixiv.net"),
            accountKey: MicroBlogKey(id: "ugoira-test-reader", host: "pixiv.net"),
            previewUrl: "https://i.pximg.net/ugoira-test.jpg", originalFrameUrl: nil,
            description: nil, height: 100, width: 100, sensitive: false, customHeaders: nil)
        let presenter = KotlinPresenter(presenter: UgoiraPresenter(media: media))
        XCTAssertNil(presenter.state.animation)
        XCTAssertEqual(presenter.state.progress, 0)
        XCTAssertFalse(presenter.state.failed)

        let session = UgoiraPlaybackSession()
        defer { session.detach() }
        session.play(media)
        // An absent account fails preparation without a Pixiv request, exercising native recomposition.
        for _ in 0..<500 {
            if session.failed { break }
            try await Task.sleep(for: .milliseconds(10))
        }
        XCTAssertTrue(session.failed, "Playback activation must report the missing account through Presenter state")
        XCTAssertNil(session.image)
    }

    func testFrameDelayPreservesTimingAndCatchesUpAcrossLoopBoundaries() {
        // The variable-duration sequence is 40, 80, 100 ms (220 ms per loop).
        XCTAssertEqual(ugoiraFrameDelay(frameEnd: 0.12, duration: 0.22, selectedAt: 0.06, now: 0.07), 0.05, accuracy: 0.000_001)
        XCTAssertEqual(ugoiraFrameDelay(frameEnd: 0.22, duration: 0.22, selectedAt: 0.12, now: 0.13), 0.09, accuracy: 0.000_001)
        XCTAssertEqual(ugoiraFrameDelay(frameEnd: 0.22, duration: 0.22, selectedAt: 0.34, now: 0.35), 0.09, accuracy: 0.000_001)
        XCTAssertEqual(ugoiraFrameDelay(frameEnd: 0.22, duration: 0.22, selectedAt: 0.12, now: 0.23), 0.001, accuracy: 0.000_001)
        XCTAssertEqual(ugoiraFrameDelay(frameEnd: 0.22, duration: 0.22, selectedAt: 0.12, now: 0.68), 0.001, accuracy: 0.000_001)
    }
}
