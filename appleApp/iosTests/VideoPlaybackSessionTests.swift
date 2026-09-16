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
        XCTAssertEqual(player?.rate, 0)
        first.play(url: url, position: first.position)
        XCTAssertTrue(player === first.player)
        XCTAssertEqual(first.position, 37)
        first.detach()
    }

    @MainActor
    func testWarmHandoffPreservesLoadedItemAndIdleBufferExpires() async throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("playback-\(UUID()).mp4")
        defer { try? FileManager.default.removeItem(at: url) }
        let writer = try AVAssetWriter(outputURL: url, fileType: .mp4)
        let input = AVAssetWriterInput(mediaType: .video, outputSettings: [
            AVVideoCodecKey: AVVideoCodecType.h264, AVVideoWidthKey: 16, AVVideoHeightKey: 16
        ])
        let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input, sourcePixelBufferAttributes: nil)
        writer.add(input)
        XCTAssertTrue(writer.startWriting())
        writer.startSession(atSourceTime: .zero)
        var buffer: CVPixelBuffer?
        XCTAssertEqual(CVPixelBufferCreate(nil, 16, 16, kCVPixelFormatType_32ARGB, nil, &buffer), kCVReturnSuccess)
        let frame = try XCTUnwrap(buffer)
        for seconds in [0, 30, 60] {
            while !input.isReadyForMoreMediaData { try await Task.sleep(for: .milliseconds(10)) }
            XCTAssertTrue(adaptor.append(frame, withPresentationTime: CMTime(seconds: Double(seconds), preferredTimescale: 600)))
        }
        input.markAsFinished()
        await writer.finishWriting()
        XCTAssertEqual(writer.status, .completed)

        let inline = VideoPlaybackSession()
        let detail = VideoPlaybackSession()
        defer {
            inline.detach()
            detail.detach()
            VideoPlaybackSession.releaseIdleBuffer()
        }
        inline.play(url: url.absoluteString)
        let player = try XCTUnwrap(inline.player)
        for _ in 0..<200 {
            if player.currentItem?.status == .readyToPlay { break }
            try await Task.sleep(for: .milliseconds(10))
        }
        let item = try XCTUnwrap(player.currentItem)
        XCTAssertEqual(item.status, .readyToPlay)
        detail.play(url: url.absoluteString)
        XCTAssertNil(inline.player)
        XCTAssertTrue(detail.player === player)
        XCTAssertTrue(player.currentItem === item)
        detail.detach()
        XCTAssertEqual(player.rate, 0)
        XCTAssertTrue(player.currentItem === item)
        try await Task.sleep(for: .milliseconds(100))
        VideoPlaybackSession.setPosition(for: url.absoluteString, seconds: 17)
        inline.play(url: url.absoluteString)
        XCTAssertTrue(player.currentItem === item)
        XCTAssertEqual(inline.position, 17, "A seek committed between surfaces must beat the retained position")
        inline.detach()
        try await Task.sleep(for: .milliseconds(5200))
        XCTAssertTrue(player.items().isEmpty)
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
        XCTAssertEqual(player?.rate, 0)
    }
}
