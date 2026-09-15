import AVFoundation
import Combine
import XCTest

final class VideoPlaybackSessionTests: XCTestCase {
    @MainActor
    func testMissingMediaReportsFailureWithoutPollingAndStopsUpdatesAfterDetach() async throws {
        let session = VideoPlaybackSession()
        var updateCount = 0
        let subscription = session.updates.sink { updateCount += 1 }
        defer {
            subscription.cancel()
            session.detach()
        }
        session.play(url: URL(fileURLWithPath: "/missing-playback-test-\(UUID()).mp4").absoluteString)
        for _ in 0..<200 {
            if case .error = session.state { break }
            try await Task.sleep(for: .milliseconds(10))
        }
        guard case .error = session.state else {
            return XCTFail("A failed item must update the session without a refresh timer")
        }
        XCTAssertFalse(session.isPlaying)
        session.detach()
        let detachedUpdateCount = updateCount
        try await Task.sleep(for: .milliseconds(100))
        XCTAssertEqual(updateCount, detachedUpdateCount)
        if case .idle = session.state {} else {
            XCTFail("Queued callbacks must not update a detached session")
        }
    }

    @MainActor
    func testAllSessionsReuseOneActualPlayerAndDetachOldSurfaces() {
        let first = VideoPlaybackSession()
        let second = VideoPlaybackSession()
        let url = URL(fileURLWithPath: "/video-player-instance-test.mp4").absoluteString
        first.play(url: url, position: 37)
        let player = first.player
        XCTAssertNotNil(player)
        second.play(url: url)
        XCTAssertNil(first.player)
        XCTAssertEqual(first.position, 37)
        XCTAssertTrue(player === second.player)
        second.detach()
        XCTAssertNil(second.player)
        XCTAssertTrue(player?.items().isEmpty == true)
        first.play(url: url, position: first.position)
        XCTAssertTrue(player === first.player)
        XCTAssertEqual(first.position, 37)
        first.detach()
    }

    @MainActor
    func testImagePresentationPreemptsAnExistingVideoViewerAndReturnsItsPlayer() {
        let arbiter = VideoPlaybackArbiter()
        let video = VideoPlaybackPresentation(arbiter: arbiter)
        let image = VideoPlaybackPresentation(arbiter: arbiter)
        let session = VideoPlaybackSession()
        let url = URL(fileURLWithPath: "/video-presentation-test.mp4").absoluteString
        video.begin()
        video.update(session, url: url, position: 37, playing: true, rate: 1)
        let player = session.player
        XCTAssertNotNil(player)
        image.begin()
        XCTAssertNil(session.player)
        video.update(session, url: url, position: 37, playing: true, rate: 1)
        XCTAssertNil(session.player)
        let backgroundTimeline = NSObject()
        arbiter.interacted(backgroundTimeline)
        image.end()
        XCTAssertTrue(session.player === player)
        XCTAssertEqual(session.position, 37)
        video.end()
        XCTAssertNil(session.player)
        XCTAssertTrue(player?.items().isEmpty == true)
    }
}
