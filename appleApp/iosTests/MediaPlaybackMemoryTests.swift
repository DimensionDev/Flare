import XCTest

final class MediaPlaybackMemoryTests: XCTestCase {
    @MainActor
    func testLatestPositionWinsAndNewAppMemoryStartsEmpty() {
        let memory = MediaPlaybackMemory()
        memory.save(37, for: "video")
        memory.save(12, for: "video")
        memory.save(.nan, for: "video")
        XCTAssertEqual(memory.position(for: "video"), 12)
        memory.save(0, for: "video")
        XCTAssertEqual(memory.position(for: "video"), 0)
        XCTAssertEqual(MediaPlaybackMemory().position(for: "video"), 0)
    }

    @MainActor
    func testReturnSelectionSurvivesUnmountAndIgnoresUnrelatedCollections() {
        let selections = TimelineMediaSelections()
        var first = "a"
        var unrelated = "c"
        selections.register(id: "first", urls: ["a", "b"]) { first = $0 }
        selections.register(id: "other", urls: ["c", "d"]) { unrelated = $0 }
        selections.remove(id: "first")
        selections.returned(urls: ["a", "b"], selectedURL: "b")
        selections.register(id: "remounted", urls: ["a", "b"]) { first = $0 }
        XCTAssertEqual(first, "b")
        XCTAssertEqual(unrelated, "c")
        selections.returned(urls: ["a", "b"], selectedURL: "c")
        XCTAssertEqual(first, "b")
        first = "a"
        selections.remove(id: "remounted")
        selections.register(id: "again", urls: ["a", "b"]) { first = $0 }
        XCTAssertEqual(first, "a", "An applied return must not override a later user selection")
    }

    @MainActor
    func testReturningCarouselMovementDoesNotOverrideMoreRecentUserInteraction() async {
        let arbiter = VideoPlaybackArbiter()
        let timeline = TimelinePlaybackCoordinator(arbiter: arbiter)
        let other = NSObject()
        arbiter.register(other, stop: {}, reconsider: {})
        timeline.selectMedia(groupID: "post", mediaURL: "b", userInitiated: false)
        arbiter.interacted(other)
        timeline.moved(source: "post", vertical: false)
        try? await Task.sleep(for: .milliseconds(250))
        XCTAssertFalse(arbiter.acquire(timeline))
        XCTAssertTrue(arbiter.acquire(other))
        timeline.setScrolling(true, source: "post", vertical: false)
        XCTAssertTrue(arbiter.acquire(timeline), "A new user drag still takes priority")
        timeline.setSuspended(true)
    }

    @MainActor
    func testViewerReturnsSelectionOnlyToItsOriginBeforeAutoplayReconsiders() {
        let arbiter = VideoPlaybackArbiter()
        let timeline = NSObject()
        let other = NSObject()
        let viewer = NSObject()
        var events: [String] = []
        arbiter.register(timeline, stop: {}, reconsider: { events.append("resume") },
                         mediaReturned: { _, url in events.append(url) })
        arbiter.register(other, stop: {}, reconsider: {}, mediaReturned: { _, _ in XCTFail("Wrong timeline") })
        arbiter.register(viewer, stop: { events.append("save-progress") }, reconsider: {})
        arbiter.interacted(timeline)
        arbiter.present(viewer)
        XCTAssertTrue(arbiter.acquire(viewer))
        // A full-screen cover can temporarily hide the original timeline.
        arbiter.withdraw(timeline)
        events.removeAll()
        arbiter.withdraw(viewer, mediaURLs: ["a", "b"], selectedMediaURL: "b")
        XCTAssertEqual(events, ["save-progress", "b", "resume"])
        XCTAssertFalse(arbiter.acquire(other))
        XCTAssertTrue(arbiter.acquire(timeline))
    }

    func testReturnedVideoWaitsForVisibilityAndImageKeepsTimelineQuiet() {
        var policy = TimelineAutoplayPolicy()
        let a = TimelineAutoplayPolicy.Candidate(id: "a", groupID: "post", isVisible: true, canStart: true, distance: 0, mediaURL: "a")
        let clippedB = TimelineAutoplayPolicy.Candidate(id: "b", groupID: "post", isVisible: true, isSelected: false,
                                                       canStart: false, distance: 20, mediaURL: "b")
        policy.returnedToMedia(groupID: "post", mediaURL: "b")
        XCTAssertNil(policy.select(from: [a, clippedB], isScrolling: false))
        let b = TimelineAutoplayPolicy.Candidate(id: "b", groupID: "post", isVisible: true, isSelected: false,
                                                canStart: true, distance: 20, mediaURL: "b")
        XCTAssertEqual(policy.select(from: [a, b], isScrolling: false), "b")
        policy.returnedToMedia(groupID: "post", mediaURL: "image")
        XCTAssertNil(policy.select(from: [a, b], isScrolling: false))
        policy.verticalScrollBegan()
        XCTAssertEqual(policy.select(from: [a, b], isScrolling: false), "a")
    }
}
