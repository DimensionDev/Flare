import AVFoundation
import Combine
import XCTest
#if os(iOS)
import AVFAudio
import UIKit
#endif

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
        let url = try await makeVideo()
        defer { try? FileManager.default.removeItem(at: url) }

        let inline = VideoPlaybackSession()
        let detail = VideoPlaybackSession()
        defer {
            inline.detach()
            detail.detach()
            VideoPlaybackSession.releaseIdleBuffer()
        }
        inline.play(url: url.absoluteString)
        let player = try XCTUnwrap(inline.player)
        try await waitUntilReady(player)
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

    #if os(iOS)
    @MainActor
    func testPresentationPreservesPlaybackIntentAcrossATransportPause() async throws {
        let url = try await makeVideo()
        defer { try? FileManager.default.removeItem(at: url) }
        let arbiter = VideoPlaybackArbiter()
        let video = VideoPlaybackPresentation(arbiter: arbiter)
        let image = VideoPlaybackPresentation(arbiter: arbiter)
        let session = VideoPlaybackSession()
        defer {
            image.end()
            video.end()
            VideoPlaybackSession.releaseIdleBuffer()
        }
        video.begin()
        video.update(session, url: url.absoluteString, playing: true, rate: 1)
        let player = try XCTUnwrap(session.player)
        try await waitUntilReady(player)

        // A transport pause during a handoff is not a user pause command.
        player.pause()
        session.refresh()
        image.begin()
        XCTAssertNil(session.player)
        image.end()
        XCTAssertTrue(session.player === player)
        XCTAssertEqual(player.rate, 1, "Returning to the video must preserve its requested playback")

        video.update(session, url: url.absoluteString, playing: false, rate: 1)
        image.begin()
        image.end()
        XCTAssertEqual(player.rate, 0, "An explicit user pause must survive preemption")
        video.setSuspended(true)
        video.setSuspended(false)
        XCTAssertEqual(player.rate, 0, "Foregrounding must also preserve an explicit pause")
    }

    @MainActor
    func testAudioCategoryFollowsTheAttachedSurfaceThroughPauseSeekAndHandoff() async throws {
        let url = try await makeVideo()
        defer { try? FileManager.default.removeItem(at: url) }
        let inline = VideoPlaybackSession()
        let detail = VideoPlaybackSession()
        defer {
            inline.detach()
            detail.detach()
            VideoPlaybackSession.releaseIdleBuffer()
        }
        try AVAudioSession.sharedInstance().setCategory(.ambient, options: .mixWithOthers)
        inline.play(url: url.absoluteString)
        let player = try XCTUnwrap(inline.player)
        try await waitUntilReady(player)
        XCTAssertEqual(AVAudioSession.sharedInstance().category, .ambient)

        detail.play(url: url.absoluteString, muted: false)
        XCTAssertEqual(AVAudioSession.sharedInstance().category, .playback,
                       "Configure detail audio before starting its playback")
        detail.setPlaying(false)
        detail.seek(to: 17)
        XCTAssertEqual(AVAudioSession.sharedInstance().category, .playback,
                       "Pause and seek must not reconfigure the audio route")
        VideoPlaybackSession.continuePlayback(to: url.absoluteString)
        detail.detach()
        XCTAssertEqual(AVAudioSession.sharedInstance().category, .playback)
        inline.play(url: url.absoluteString)
        XCTAssertEqual(AVAudioSession.sharedInstance().category, .ambient)

        detail.play(url: url.absoluteString, muted: false)
        detail.detach()
        XCTAssertEqual(AVAudioSession.sharedInstance().category, .ambient)

        detail.play(url: url.absoluteString, muted: false)
        VideoPlaybackSession.continuePlayback(to: url.absoluteString)
        detail.detach()
        try await Task.sleep(for: .milliseconds(600))
        XCTAssertEqual(AVAudioSession.sharedInstance().category, .ambient,
                       "An abandoned handoff must release its audio session")
    }

    @MainActor
    func testReadyVideoCoversTheThumbnailInItsLetterboxArea() async throws {
        let url = try await makeVideo()
        defer { try? FileManager.default.removeItem(at: url) }
        let session = VideoPlaybackSession()
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 200, height: 100))
        let controller = UIViewController()
        let surface = VideoPlaybackSurfaceView(frame: window.bounds)
        controller.view.backgroundColor = .magenta
        window.rootViewController = controller
        controller.view.addSubview(surface)
        window.makeKeyAndVisible()
        surface.onReady = session.surfaceReady
        let subscription = session.updates.sink {
            surface.canDisplayFrame = session.hasRestoredPosition
        }
        defer {
            subscription.cancel()
            surface.player = nil
            session.detach()
            VideoPlaybackSession.releaseIdleBuffer()
            window.isHidden = true
        }
        session.play(url: url.absoluteString)
        surface.playerLayer.videoGravity = .resizeAspect
        surface.player = session.player
        for _ in 0..<200 {
            if surface.playerLayer.isReadyForDisplay, surface.alpha == 1, session.hasDisplayedFrame { break }
            try await Task.sleep(for: .milliseconds(10))
        }
        XCTAssertTrue(surface.playerLayer.isReadyForDisplay)
        XCTAssertEqual(surface.alpha, 1)
        XCTAssertTrue(session.hasDisplayedFrame)
        XCTAssertFalse(surface.playerLayer.videoRect.contains(CGPoint(x: 5, y: 50)))

        var pixel = [UInt8](repeating: 0, count: 4)
        pixel.withUnsafeMutableBytes { bytes in
            let context = CGContext(data: bytes.baseAddress, width: 1, height: 1, bitsPerComponent: 8,
                                    bytesPerRow: 4, space: CGColorSpaceCreateDeviceRGB(),
                                    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
            context.translateBy(x: -5, y: -50)
            controller.view.layer.render(in: context)
        }
        XCTAssertGreaterThan(pixel[3], 200)
        XCTAssertLessThan(Int(pixel[0]) + Int(pixel[1]) + Int(pixel[2]), 40,
                          "The ready video's letterbox must hide the thumbnail underneath")
        session.detach()
        session.surfaceReady()
        XCTAssertFalse(session.hasDisplayedFrame, "A stale surface callback must not hide the next placeholder")
    }
    #endif

    @MainActor
    private func waitUntilReady(_ player: AVPlayer) async throws {
        for _ in 0..<200 {
            if player.currentItem?.status == .readyToPlay { break }
            try await Task.sleep(for: .milliseconds(10))
        }
        XCTAssertEqual(player.currentItem?.status, .readyToPlay)
    }

    @MainActor
    private func makeVideo() async throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("playback-\(UUID()).mp4")
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

        return url
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
