import XCTest

final class TimelineAutoplayPolicyTests: XCTestCase {
    private func video(_ id: String, group: String? = nil, visible: Bool = true,
                       selected: Bool = true, canStart: Bool = true, distance: Double = 0)
        -> TimelineAutoplayPolicy.Candidate {
        .init(id: id, groupID: group, isVisible: visible, isSelected: selected,
              canStart: canStart, distance: distance)
    }

    func testScrollRetainsVisiblePlayerWithoutStartingAnother() {
        var policy = TimelineAutoplayPolicy()
        XCTAssertEqual(policy.select(from: [video("a")], isScrolling: false), "a")
        let partial = video("a", canStart: false, distance: 500)
        XCTAssertEqual(policy.select(from: [partial, video("b")], isScrolling: true), "a")
        XCTAssertEqual(policy.select(from: [partial, video("b")], isScrolling: false), "a")
        XCTAssertNil(policy.select(from: [video("a", visible: false), video("b")], isScrolling: true))
        XCTAssertEqual(policy.select(from: [video("b")], isScrolling: false), "b")
    }

    func testNoNewPlayerStartsDuringScrollingOrBelowStartThreshold() {
        var policy = TimelineAutoplayPolicy()
        XCTAssertNil(policy.select(from: [video("a")], isScrolling: true))
        XCTAssertNil(policy.select(from: [video("a", canStart: false)], isScrolling: false))
        XCTAssertEqual(policy.select(from: [video("a")], isScrolling: false), "a")
    }

    func testUserCarouselChoiceTakesOverOnlyAfterScrollingStops() {
        var policy = TimelineAutoplayPolicy()
        let candidates = [video("a", group: "first"), video("b", group: "second", distance: 100)]
        XCTAssertEqual(policy.select(from: candidates, isScrolling: false), "a")
        policy.interactedWithCarousel("second")
        XCTAssertEqual(policy.select(from: candidates, isScrolling: true), "a")
        XCTAssertEqual(policy.select(from: candidates, isScrolling: false), "b")
    }

    func testSelectedImageKeepsTimelineQuietUntilNextVerticalInteraction() {
        var policy = TimelineAutoplayPolicy()
        let a = video("a", group: "first")
        let unselectedVideo = video("b", group: "second", selected: false)
        XCTAssertEqual(policy.select(from: [a], isScrolling: false), "a")
        policy.interactedWithCarousel("second")
        XCTAssertEqual(policy.select(from: [a, unselectedVideo], isScrolling: true), "a")
        XCTAssertNil(policy.select(from: [a, unselectedVideo], isScrolling: false))
        XCTAssertNil(policy.select(from: [a, unselectedVideo, video("c")], isScrolling: false))
        policy.verticalScrollBegan()
        XCTAssertNil(policy.select(from: [a], isScrolling: true))
        XCTAssertEqual(policy.select(from: [a], isScrolling: false), "a")
    }

    func testSelectedImageInCurrentCarouselStopsVisibleSliver() {
        var policy = TimelineAutoplayPolicy()
        XCTAssertEqual(policy.select(from: [video("a", group: "row")], isScrolling: false), "a")
        policy.interactedWithCarousel("row")
        let sliver = video("a", group: "row", selected: false, canStart: false)
        XCTAssertEqual(policy.select(from: [sliver], isScrolling: true), "a")
        XCTAssertNil(policy.select(from: [sliver], isScrolling: false))
    }

    func testUnselectedNeighborNeverStartsAndUnavailableChoiceDoesNotFallBack() {
        var policy = TimelineAutoplayPolicy()
        XCTAssertNil(policy.select(from: [video("neighbor", selected: false)], isScrolling: false))
        policy.interactedWithCarousel("row")
        XCTAssertNil(policy.select(from: [video("other"), video("chosen", group: "row", canStart: false)], isScrolling: false))
        XCTAssertEqual(policy.select(from: [video("other"), video("chosen", group: "row")], isScrolling: false), "chosen")
        XCTAssertNil(policy.select(from: [video("chosen", group: "row", visible: false)], isScrolling: true))
    }

    func testAutomaticSelectionUsesClosestCandidateAndRetainsItOnTies() {
        var policy = TimelineAutoplayPolicy()
        XCTAssertEqual(policy.select(from: [video("far", distance: 100), video("near", distance: 5)], isScrolling: false), "near")
        XCTAssertEqual(policy.select(from: [video("far", distance: 5), video("near", distance: 5)], isScrolling: false), "near")
    }
}
