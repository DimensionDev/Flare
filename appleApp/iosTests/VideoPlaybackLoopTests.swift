import AVFoundation
import XCTest

extension VideoPlaybackSessionTests {
    @MainActor
    func testShortVideoLoopsWithIndependentAssetsAndPreservesPauseAndSeek() async throws {
        let url = try await makeVideo(frameDuration: 0.22)
        let session = VideoPlaybackSession()
        defer {
            session.detach()
            VideoPlaybackSession.releaseIdleBuffer()
            try? FileManager.default.removeItem(at: url)
        }
        session.play(url: url.absoluteString)
        let player = try XCTUnwrap(session.player)
        try await waitUntilReady(player)
        var playedItems: [AVPlayerItem] = []
        for _ in 0..<800 {
            if let item = player.currentItem, playedItems.last !== item {
                XCTAssertFalse(playedItems.contains { $0 === item }, "Each loop needs a fresh item")
                XCTAssertFalse(playedItems.contains { $0.asset === item.asset },
                               "Sharing an asset across loops can freeze short videos on iOS")
                playedItems.append(item)
                if playedItems.count == 8 { break }
            }
            try await Task.sleep(for: .milliseconds(10))
        }
        XCTAssertEqual(playedItems.count, 8, "Playback must continue after the initial queue is consumed")
        XCTAssertEqual(player.items().count, 3)
        XCTAssertEqual(Set(player.items().map { ObjectIdentifier($0.asset) }).count, 3)

        session.setPlaying(false)
        let pausedItem = try XCTUnwrap(player.currentItem)
        let pausedTime = player.currentTime().seconds
        try await Task.sleep(for: .milliseconds(200))
        XCTAssertTrue(player.currentItem === pausedItem)
        XCTAssertEqual(player.currentTime().seconds, pausedTime, accuracy: 0.01)
        session.seek(to: 0.5)
        for _ in 0..<200 {
            if !session.isRestoringPosition { break }
            try await Task.sleep(for: .milliseconds(10))
        }
        XCTAssertFalse(session.isRestoringPosition)
        XCTAssertEqual(player.rate, 0, "Seeking must preserve a user pause")
        XCTAssertEqual(player.currentTime().seconds, 0.5, accuracy: 0.02)
        session.setPlaying(true)
        for _ in 0..<200 {
            if player.currentItem !== pausedItem { break }
            try await Task.sleep(for: .milliseconds(10))
        }
        XCTAssertFalse(player.currentItem === pausedItem, "Playback must loop again after a seek")
        XCTAssertEqual(player.rate, 1)
    }
}
