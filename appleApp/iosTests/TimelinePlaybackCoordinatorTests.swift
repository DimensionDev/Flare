import XCTest

final class TimelinePlaybackCoordinatorTests: XCTestCase {
    @MainActor
    func testPhaseEndWaitsForAFullIdleIntervalAfterTheLastMovement() async throws {
        let playback = TimelinePlaybackCoordinator(arbiter: VideoPlaybackArbiter())
        var ended: ContinuousClock.Instant?
        var delay: Duration?
        playback.register(id: "video") { active in
            if active, let ended { delay = ended.duration(to: .now) }
        }
        playback.setScrolling(true, source: "vertical", vertical: true)
        playback.moved(source: "vertical", vertical: true)
        playback.update(.init(id: "video", isVisible: true, canStart: true, distance: 0))
        try await Task.sleep(for: .milliseconds(150))
        ended = .now
        playback.setScrolling(false, source: "vertical", vertical: true)
        try await Task.sleep(for: .milliseconds(350))
        XCTAssertNotNil(delay)
        XCTAssertGreaterThanOrEqual(delay ?? .zero, .milliseconds(200))
        playback.setSuspended(true)
    }

    @MainActor
    func testTimelineHandoffStopsThePreviousOwnerAndPresentationBlocksAutoplay() async throws {
        let arbiter = VideoPlaybackArbiter()
        let first = TimelinePlaybackCoordinator(arbiter: arbiter)
        let second = TimelinePlaybackCoordinator(arbiter: arbiter)
        var events: [String] = []
        first.register(id: "a") { events.append("a:\($0)") }
        second.register(id: "b") { events.append("b:\($0)") }
        first.update(.init(id: "a", isVisible: true, canStart: true, distance: 10))
        try await Task.sleep(for: .milliseconds(250))
        second.update(.init(id: "b", groupID: "row", isVisible: true, canStart: true, distance: 0))
        try await Task.sleep(for: .milliseconds(250))
        XCTAssertEqual(events, ["a:true"])
        second.setScrolling(true, source: "row", vertical: false)
        XCTAssertEqual(events, ["a:true"])
        second.setScrolling(false, source: "row", vertical: false)
        try await Task.sleep(for: .milliseconds(250))
        XCTAssertEqual(events, ["a:true", "a:false", "b:true"])
        let presentation = NSObject()
        arbiter.present(presentation)
        XCTAssertEqual(events.last, "b:false")
        first.setScrolling(true, source: "vertical", vertical: true)
        first.setScrolling(false, source: "vertical", vertical: true)
        try await Task.sleep(for: .milliseconds(250))
        XCTAssertEqual(events.last, "b:false")
        arbiter.withdraw(presentation)
        try await Task.sleep(for: .milliseconds(250))
        XCTAssertEqual(events.last, "a:true")
        first.setSuspended(true)
        second.setSuspended(true)
    }
}
