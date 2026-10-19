import XCTest
import UIKit
import SwiftUI
import KotlinSharedUI
import FlareAppleUI
@testable import Flare

@MainActor
final class TimelineControllerIntegrationTests: XCTestCase {
    func testGapButtonRejectsAnotherTapBeforeLoadingStateIsPublished() async throws {
        var activations = 0
        var finish: (([String]) -> KotlinUnit)?
        let gap = UiTimelineV2.Gap(statusKey: MicroBlogKey(id: "button-gap", host: "example.invalid"),
            createdAt: KotlinInstant.companion.fromEpochMilliseconds(epochMilliseconds: 0).toUi(),
            nextKey: "cursor", lowerBound: [:], revision: 0,
            itemKey: "button-gap", loading: false, failed: false,
            onActivate: { completion in
                activations += 1
                finish = completion
            })
        let view = TimelineGapUIView(frame: CGRect(x: 0, y: 0, width: 320, height: 44))
        view.configure(gap: gap)
        let button = try XCTUnwrap(view.subviews.compactMap { $0 as? UIButton }.first)
        button.sendActions(for: .touchUpInside)
        view.configure(gap: gap)
        button.sendActions(for: .touchUpInside)
        XCTAssertEqual(activations, 1)
        _ = try XCTUnwrap(finish)([])
        button.sendActions(for: .touchUpInside)
        XCTAssertEqual(activations, 2)
    }

    func testCellReusesItsMeasurementBeforeTheCoalescedHeightCommit() async throws {
        let fixture = await Fixture(posts: true)
        let cell = try XCTUnwrap(fixture.collection.visibleCells.compactMap { $0 as? TimelineUIKitCollectionViewCell }.first)
        let report = try XCTUnwrap(cell.onPreferredHeightChanged)
        var measurements = 0
        cell.onPreferredHeightChanged = { width, height in
            measurements += 1
            report(width, height)
        }
        // A new width has no cached measurement. All of these cell layouts occur
        // before the main queue can commit the reported height to the list.
        cell.bounds.size.width += 13
        for _ in 0..<10 {
            cell.setNeedsLayout()
            cell.layoutIfNeeded()
        }
        XCTAssertEqual(measurements, 1, "Pending measurements must also suppress repeated rich-text sizing")
    }

    func testGalleryNewReadingTargetWinsOverThePreviousSnapshotAnchor() async throws {
        let fixture = await GalleryFixture()
        fixture.collection.setContentOffset(CGPoint(x: 0, y: 1_500), animated: false)
        await fixture.settle()
        let reading = ReadingState(position: TimelineReadingPosition(itemKey: "case-100", offset: 0))
        fixture.controller.reading = reading
        fixture.content.items.insert(.post(makeRow(100)), at: 0)
        fixture.controller.submit(fixture.content)
        await fixture.settle()
        try fixture.assertPosition(("g:case-100", 0))
        XCTAssertNil(reading.restorePosition)
    }

    func testGalleryQueuesUpdatesUntilDraggingAndDecelerationFinish() async throws {
        let fixture = await GalleryFixture()
        fixture.collection.setContentOffset(CGPoint(x: 0, y: 1_500), animated: false)
        await fixture.settle()
        let anchor = try fixture.position()
        fixture.controller.scrollViewWillBeginDragging(fixture.collection)
        fixture.content.items.insert(.post(makeRow(100)), at: 0)
        fixture.controller.submit(fixture.content)
        fixture.content.items.insert(.post(makeRow(101)), at: 0)
        fixture.controller.submit(fixture.content)
        await fixture.settle()
        XCTAssertEqual(fixture.collection.numberOfItems(inSection: 0), 40)
        fixture.controller.scrollViewDidEndDragging(fixture.collection, willDecelerate: true)
        await fixture.settle()
        XCTAssertEqual(fixture.collection.numberOfItems(inSection: 0), 40)
        fixture.controller.scrollViewDidEndDecelerating(fixture.collection)
        await fixture.settle()
        XCTAssertEqual(fixture.collection.numberOfItems(inSection: 0), 42)
        try fixture.assertPosition(anchor)
    }

    func testGalleryRestoresTheBookmarkWithoutKeepingTheLaunchRefreshInset() async throws {
        let fixture = await GalleryFixture()
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousKeyWindow = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        window.rootViewController = fixture.controller
        fixture.collection.contentInsetAdjustmentBehavior = .automatic
        window.makeKeyAndVisible()
        defer {
            window.isHidden = true
            window.rootViewController = nil
            previousKeyWindow?.makeKey()
        }
        await fixture.settle()
        let restingInset = fixture.collection.adjustedContentInset.top
        fixture.content.isRefreshing = true
        fixture.controller.submit(fixture.content)
        await fixture.settle()
        let reading = ReadingState(position: TimelineReadingPosition(itemKey: "case-20", offset: -37))
        fixture.controller.reading = reading
        await fixture.settle()
        fixture.content.isRefreshing = false
        fixture.controller.submit(fixture.content)
        await fixture.settle()
        await fixture.settle()
        try fixture.assertPosition(("g:case-20", restingInset - 37))
        XCTAssertNil(reading.restorePosition)
        XCTAssertTrue(reading.saved.isEmpty)
    }

    func testGalleryPullRefreshDoesNotSaveTheSpinnerGapAsAReadingOffset() async throws {
        let fixture = await GalleryFixture()
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousKeyWindow = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        window.rootViewController = fixture.controller
        fixture.collection.contentInsetAdjustmentBehavior = .automatic
        window.makeKeyAndVisible()
        var finishRefresh: CheckedContinuation<Void, Never>?
        defer {
            finishRefresh?.resume()
            window.isHidden = true
            window.rootViewController = nil
            previousKeyWindow?.makeKey()
        }
        await fixture.settle()
        let restingInset = fixture.collection.adjustedContentInset.top
        let latest = ReadingState(position: TimelineReadingPosition(itemKey: "case-0", offset: 0))
        fixture.controller.reading = latest
        await fixture.settle()
        try fixture.assertPosition(("g:case-0", restingInset))
        // Pulling reaches the natural boundary, including Gallery's section padding,
        // before refreshing. Preserve that position rather than undoing the gesture.
        fixture.collection.setContentOffset(CGPoint(x: 0, y: -restingInset), animated: false)
        await fixture.settle()
        let anchor = try fixture.position()
        XCTAssertEqual(anchor.1 - restingInset, 16, accuracy: 0.5)
        let reading = ReadingState()
        fixture.controller.reading = reading
        fixture.controller.refreshCallback = { await withCheckedContinuation { finishRefresh = $0 } }
        fixture.controller.scrollViewWillBeginDragging(fixture.collection)
        fixture.collection.refreshControl?.beginRefreshing()
        fixture.collection.refreshControl?.sendActions(for: .valueChanged)
        await fixture.settle()
        fixture.collection.setContentOffset(CGPoint(x: 0, y: -fixture.collection.adjustedContentInset.top), animated: false)
        fixture.content.isRefreshing = true
        fixture.content.items.insert(.post(makeRow(100)), at: 0)
        fixture.controller.submit(fixture.content)
        fixture.controller.scrollViewDidEndDragging(fixture.collection, willDecelerate: false)
        await fixture.settle()
        NotificationCenter.default.post(name: UIApplication.willResignActiveNotification, object: nil)
        XCTAssertFalse(reading.saved.isEmpty)
        XCTAssertEqual(reading.saved.first?.itemKey, anchor.0.replacingOccurrences(of: "g:", with: ""))
        for saved in reading.saved {
            XCTAssertEqual(saved.offset, anchor.1 - restingInset, accuracy: 0.5)
        }
        fixture.content.isRefreshing = false
        fixture.controller.submit(fixture.content)
        finishRefresh?.resume()
        finishRefresh = nil
        await fixture.settle()
        await fixture.settle()
        try fixture.assertPosition(anchor)
    }

    func testGalleryGapKeepsItsPredecessorAboveTheViewportWhileFillingDownward() async throws {
        let fixture = await GalleryFixture()
        let reading = ReadingState()
        fixture.controller.reading = reading
        let gap = makeGap()
        fixture.content.items.insert(.post(gap), at: 4)
        fixture.controller.submit(fixture.content)
        await fixture.settle()
        let gapPath = try XCTUnwrap(fixture.source.indexPath(for: "g:\(gap.stableItemKey)"))
        let gapFrame = try XCTUnwrap(fixture.collection.layoutAttributesForItem(at: gapPath)?.frame)
        fixture.collection.setContentOffset(CGPoint(x: 0, y: gapFrame.minY), animated: false)
        await fixture.settle()
        let precedingPath = try XCTUnwrap(fixture.source.indexPath(for: "g:case-3"))
        let precedingFrame = try XCTUnwrap(fixture.collection.layoutAttributesForItem(at: precedingPath)?.frame)
        let expected = precedingFrame.minY - fixture.collection.contentOffset.y
        fixture.controller.willLoadTimelineGap(gap)
        fixture.content.items.insert(contentsOf: [.post(makeRow(100)), .post(makeRow(101))], at: 4)
        fixture.content.items[6] = .post(makeGap(revision: 1))
        fixture.controller.submit(fixture.content)
        await fixture.settle()
        try fixture.assertPosition(("g:case-3", expected))
        XCTAssertEqual(reading.saved.last?.itemKey, "case-3")
        let insertedPath = try XCTUnwrap(fixture.source.indexPath(for: "g:case-100"))
        let insertedFrame = try XCTUnwrap(fixture.collection.layoutAttributesForItem(at: insertedPath)?.frame)
        XCTAssertLessThan(insertedFrame.minY - fixture.collection.contentOffset.y, fixture.collection.bounds.height)
    }

    func testGalleryGapKeepsItsPredecessorWhenPagingReplacesAndExpandsTheWindow() async throws {
        let fixture = await GalleryFixture()
        let heights: [Float] = [240, 1_040, 420, 760]
        fixture.content.items = (0..<40).map { .post(makeRow($0, imageHeight: heights[$0 % heights.count])) }
        let gap = makeGap()
        fixture.content.items.insert(.post(gap), at: 18)
        fixture.controller.submit(fixture.content)
        await fixture.settle()
        let path = try XCTUnwrap(fixture.source.indexPath(for: "g:case-17"))
        let frame = try XCTUnwrap(fixture.collection.layoutAttributesForItem(at: path)?.frame)
        fixture.collection.setContentOffset(CGPoint(x: 0, y: frame.minY - 150), animated: false)
        await fixture.settle()
        fixture.controller.willLoadTimelineGap(gap)
        var expanded = fixture.content.items
        expanded.insert(contentsOf: (100..<120).map { .post(makeRow($0)) }, at: 18)
        expanded[38] = .post(makeGap(revision: 1))
        // A refresh loads 20 rows around the predecessor. The remaining gap is
        // outside this window until local paging expands it again.
        fixture.content.items = Array(expanded[11..<31])
        fixture.controller.submit(fixture.content)
        await fixture.settle()
        try fixture.assertPosition(("g:case-17", 150))
        fixture.content.items = expanded
        fixture.controller.submit(fixture.content)
        await fixture.settle()
        try fixture.assertPosition(("g:case-17", 150))
    }

    func testGalleryGapKeepsItsPredecessorWhenExistingCardsChangeHeight() async throws {
        for footerChanges in [false, true] {
            let fixture = await GalleryFixture()
            let gap = makeGap()
            fixture.content.items.insert(.post(gap), at: 18)
            fixture.controller.submit(fixture.content)
            await fixture.settle()
            let path = try XCTUnwrap(fixture.source.indexPath(for: "g:case-17"))
            let frame = try XCTUnwrap(fixture.collection.layoutAttributesForItem(at: path)?.frame)
            fixture.collection.setContentOffset(CGPoint(x: 0, y: frame.minY - 150), animated: false)
            await fixture.settle()
            fixture.controller.willLoadTimelineGap(gap)
            fixture.content.items[14] = .post(makeRow(14, imageHeight: 1_200))
            if footerChanges { fixture.content.footer = .loading }
            fixture.controller.submit(fixture.content)
            await fixture.settle()
            try fixture.assertPosition(("g:case-17", 150))
        }
    }

    func testGalleryPagingHintsWaitUntilTheRestoredViewportIsReady() async throws {
        let fixture = await GalleryFixture()
        let reading = ReadingState(position: TimelineReadingPosition(itemKey: "case-30", offset: -37))
        fixture.controller.reading = reading
        var prematureHints = 0
        var hints: [Int] = []
        fixture.content.access = { index in
            if reading.restorePosition != nil || (fixture.collection as! TimelineCollectionView).hasReadingPosition {
                prematureHints += 1
            }
            hints.append(index)
        }
        fixture.controller.submit(fixture.content)
        await fixture.settle()
        XCTAssertEqual(prematureHints, 0, "An unrestored Gallery viewport must not advance Paging")
        XCTAssertFalse(hints.isEmpty, "The restored Gallery viewport must resume paging")
        try fixture.assertPosition(("g:case-30", -37))
    }

    func testGalleryNewReadingTargetReleasesThePreviousGapAnchor() async throws {
        let fixture = await GalleryFixture()
        let gap = makeGap()
        fixture.content.items.insert(.post(gap), at: 18)
        fixture.controller.submit(fixture.content)
        await fixture.settle()
        fixture.controller.willLoadTimelineGap(gap)
        let reading = ReadingState(position: TimelineReadingPosition(itemKey: "case-30", offset: -37))
        fixture.controller.reading = reading
        fixture.controller.submit(fixture.content)
        await fixture.settle()
        try fixture.assertPosition(("g:case-30", -37))
        fixture.content.items.insert(.post(makeRow(100)), at: 0)
        fixture.controller.submit(fixture.content)
        await fixture.settle()
        try fixture.assertPosition(("g:case-30", -37))
    }

    func testGalleryDraggingReleasesThePreviousGapAnchor() async throws {
        let fixture = await GalleryFixture()
        let gap = makeGap()
        fixture.content.items.insert(.post(gap), at: 18)
        fixture.controller.submit(fixture.content)
        await fixture.settle()
        fixture.controller.willLoadTimelineGap(gap)
        fixture.controller.scrollViewWillBeginDragging(fixture.collection)
        fixture.collection.setContentOffset(CGPoint(x: 0, y: 2_500), animated: false)
        fixture.controller.scrollViewDidEndDragging(fixture.collection, willDecelerate: false)
        await fixture.settle()
        let anchor = try fixture.position()
        fixture.content.items.insert(.post(makeRow(100)), at: 0)
        fixture.controller.submit(fixture.content)
        await fixture.settle()
        try fixture.assertPosition(anchor)
    }

    func testGalleryAppearanceChangesKeepTheGapPredecessor() async throws {
        let fixture = await GalleryFixture()
        let gap = makeGap()
        fixture.content.items.insert(.post(gap), at: 18)
        fixture.controller.submit(fixture.content)
        await fixture.settle()
        let path = try XCTUnwrap(fixture.source.indexPath(for: "g:case-17"))
        let frame = try XCTUnwrap(fixture.collection.layoutAttributesForItem(at: path)?.frame)
        fixture.collection.setContentOffset(CGPoint(x: 0, y: frame.minY - 150), animated: false)
        await fixture.settle()
        fixture.controller.willLoadTimelineGap(gap)
        let helper = TimelinePresentationAppearancePatchHelper.shared
        fixture.controller.appearance = GalleryUIKitAppearance(timeline: helper.resolve(
            base: TimelineAppearance.companion.Default,
            patch: helper.setShowMedia(patch: helper.empty, value: false)
        ))
        await fixture.settle()
        try fixture.assertPosition(("g:case-17", 150))
    }

    func testNewReadingTargetReleasesThePreviousGapAnchor() async throws {
        let fixture = await Fixture(posts: true)
        let gap = makeGap()
        fixture.input.items.insert(.post(gap), at: 18)
        await fixture.apply()
        fixture.controller.willLoadTimelineGap(gap)
        let reading = ReadingState(position: TimelineReadingPosition(itemKey: "case-30", offset: -37))
        fixture.controller.reading = reading
        await fixture.apply()
        try fixture.assertPosition(("t:case-30", -37))
        fixture.input.items.insert(.post(makeRow(100)), at: 0)
        await fixture.apply()
        try fixture.assertPosition(("t:case-30", -37))
    }

    func testGapRestoresItsOriginalOffsetAfterATemporaryPagingBoundaryClamp() async throws {
        let fixture = await Fixture(posts: true)
        let gap = makeGap()
        fixture.input.items.insert(.post(gap), at: 18)
        await fixture.apply()
        let path = try XCTUnwrap(fixture.collection.readingIndexPath?("t:case-17"))
        let frame = try XCTUnwrap(fixture.collection.layoutAttributesForItem(at: path)?.frame)
        await fixture.scroll(frame.minY - 150)
        fixture.controller.willLoadTimelineGap(gap)
        var expanded = fixture.input.items
        expanded.insert(contentsOf: (100..<120).map { .post(makeRow($0)) }, at: 18)
        expanded[38] = .post(makeGap(revision: 1))
        fixture.input.items = Array(expanded[17..<37])
        await fixture.apply()
        // The predecessor is temporarily the first row, so +150 cannot be
        // displayed without overscrolling. Expanding must recover the original.
        let clamped = try XCTUnwrap(fixture.collection.layoutAttributesForItem(at: IndexPath(item: 0, section: 0))?.frame)
        XCTAssertLessThan(clamped.minY - fixture.collection.contentOffset.y, 150)
        fixture.input.items = expanded
        await fixture.apply()
        try fixture.assertPosition(("t:case-17", 150))
    }

    func testGalleryRemovingTheGapPredecessorAllowsSubsequentViewportAnchors() async throws {
        let fixture = await GalleryFixture()
        let gap = makeGap()
        fixture.content.items.insert(.post(gap), at: 18)
        fixture.controller.submit(fixture.content)
        await fixture.settle()
        fixture.collection.setContentOffset(CGPoint(x: 0, y: 1_500), animated: false)
        await fixture.settle()
        fixture.controller.willLoadTimelineGap(gap)
        fixture.content.items.remove(at: 17)
        fixture.controller.submit(fixture.content)
        await fixture.settle()
        let anchor = try fixture.position()
        fixture.content.items.insert(.post(makeRow(100)), at: 0)
        fixture.controller.submit(fixture.content)
        await fixture.settle()
        try fixture.assertPosition(anchor)
    }

    func testGalleryReplacesErrorMessagesAndRetryActionsWithoutChangingRowIDs() async throws {
        for footer in [false, true] {
            let fixture = await GalleryFixture()
            var firstRetries = 0
            var latestRetries = 0
            fixture.content.items = footer ? [.post(makeRow(1))] : []
            fixture.content.state = footer ? .loaded : .error
            fixture.content.footer = footer ? .error : .none
            let first = TimelineContent.Failure(error: KotlinThrowable(message: "first error"), retry: { firstRetries += 1 })
            fixture.content.failure = footer ? nil : first
            fixture.content.appendFailure = footer ? first : nil
            fixture.controller.submit(fixture.content)
            await fixture.settle()
            let latest = TimelineContent.Failure(error: KotlinThrowable(message: "latest error"), retry: { latestRetries += 1 })
            fixture.content.failure = footer ? nil : latest
            fixture.content.appendFailure = footer ? latest : nil
            fixture.controller.submit(fixture.content)
            await fixture.settle()
            let error = try XCTUnwrap(descendants(fixture.collection).compactMap { $0 as? ListErrorUIView }.first)
            let labels = descendants(error).compactMap { ($0 as? UILabel)?.text }
            XCTAssertTrue(labels.contains("latest error"))
            let button = try XCTUnwrap(descendants(error).compactMap { $0 as? UIButton }.first)
            button.sendActions(for: .touchUpInside)
            XCTAssertEqual(firstRetries, 0)
            XCTAssertEqual(latestRetries, 1)
        }
    }

    @MainActor
    private final class GalleryFixture {
        let controller = UIGalleryTimelineController()
        var content = TimelineContent()
        var collection: UICollectionView { controller.view.subviews.compactMap { $0 as? UICollectionView }.first! }
        var source: UICollectionViewDiffableDataSource<Int, String> { collection.dataSource as! UICollectionViewDiffableDataSource<Int, String> }

        init() async {
            content.state = .loaded
            content.items = (0..<40).map { .post(makeRow($0)) }
            controller.submit(content)
            controller.loadViewIfNeeded()
            controller.view.frame = CGRect(x: 0, y: 0, width: 390, height: 600)
            collection.contentInsetAdjustmentBehavior = .never
            await settle()
        }

        func settle() async {
            for _ in 0..<15 {
                controller.view.setNeedsLayout()
                controller.view.layoutIfNeeded()
                collection.layoutIfNeeded()
                try? await Task.sleep(for: .milliseconds(10))
            }
        }

        func position() throws -> (String, CGFloat) {
            let path = try XCTUnwrap(collection.indexPathsForVisibleItems.sorted().first)
            let id = try XCTUnwrap(source.itemIdentifier(for: path))
            let frame = try XCTUnwrap(collection.layoutAttributesForItem(at: path)?.frame)
            return (id, frame.minY - collection.contentOffset.y)
        }

        func assertPosition(_ anchor: (String, CGFloat)) throws {
            let path = try XCTUnwrap(source.indexPath(for: anchor.0))
            let frame = try XCTUnwrap(collection.layoutAttributesForItem(at: path)?.frame)
            XCTAssertEqual(frame.minY - collection.contentOffset.y, anchor.1, accuracy: 0.5)
        }
    }

    func testPagingHintsWaitUntilTheRestoredViewportIsReady() async throws {
        let fixture = await Fixture(posts: true)
        let reading = ReadingState(position: TimelineReadingPosition(itemKey: "case-30", offset: -37))
        fixture.controller.reading = reading
        var prematureHints = 0
        var hints: [Int] = []
        fixture.input.access = { index in
            if reading.restorePosition != nil || fixture.collection.hasReadingPosition { prematureHints += 1 }
            hints.append(index)
        }
        await fixture.apply()
        XCTAssertEqual(prematureHints, 0, "Estimated or unrestored geometry must not advance Paging")
        XCTAssertFalse(hints.isEmpty, "Visible rows must resume paging once the bookmark has settled")
        try fixture.assertPosition(("t:case-30", -37))
    }

    func testViewportReportsDoNotEnumerateTheWholeTimeline() async throws {
        let fixture = await Fixture(posts: true)
        await fixture.scroll(2_000)
        let reading = ReadingState()
        fixture.controller.reading = reading
        let itemOrder = fixture.collection.readingItemIDs
        var orderReads = 0
        fixture.collection.readingItemIDs = { orderReads += 1; return itemOrder?() ?? [] }
        fixture.collection.beginExternalScrollInteraction()
        for _ in 0..<100 { fixture.controller.scrollViewDidScroll(fixture.collection) }
        XCTAssertEqual(reading.viewports.count, 100)
        XCTAssertEqual(orderReads, 0, "Viewport reporting should only inspect visible items")
        fixture.collection.endScrollInteraction()
    }

    func testStatusBarScrollRequestsTheHistoryHeadBeyondTheLoadedWindow() async throws {
        let fixture = await Fixture(posts: true)
        await fixture.scroll(2_000)
        let reading = ReadingState()
        fixture.controller.reading = reading
        XCTAssertFalse(fixture.controller.scrollViewShouldScrollToTop(fixture.collection))
        XCTAssertEqual(reading.latestRequests, 1)
        XCTAssertTrue(reading.saved.isEmpty)
    }

    func testHorizontalPostMediaDoesNotInterceptTheTimelineStatusBarAction() async throws {
        let fixture = await Fixture(posts: true)
        let helper = TimelinePresentationAppearancePatchHelper.shared
        fixture.controller.appearance = TimelineUIKitAppearance(timeline: helper.resolve(
            base: TimelineAppearance.companion.Default,
            patch: helper.setMediaLayout(patch: helper.empty, value: .carousel)
        ))
        fixture.input.items = [.post(makeRow(1))]
        await fixture.apply()
        let nested = descendants(fixture.collection).compactMap { $0 as? UIScrollView }
            .filter { $0 !== fixture.collection && !$0.isHidden && $0.isScrollEnabled }
        XCTAssertFalse(nested.isEmpty, "Exercise a real horizontal media scroller")
        XCTAssertTrue(nested.allSatisfy { !$0.scrollsToTop }, "UIKit requires a single status-bar scroll recipient on iPhone")
    }

    func testGalleryStatusBarScrollRequestsTheHistoryHeadBeyondTheLoadedWindow() async throws {
        let fixture = await GalleryFixture()
        let reading = ReadingState()
        fixture.controller.reading = reading
        XCTAssertEqual(fixture.collection.delegate?.scrollViewShouldScrollToTop?(fixture.collection), false)
        XCTAssertEqual(reading.latestRequests, 1)
        XCTAssertTrue(reading.saved.isEmpty)
    }

    func testReadingBookmarkArrivingDuringRefreshRevealSurvivesTheAnimation() async throws {
        let fixture = await Fixture(posts: true)
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousKeyWindow = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        window.rootViewController = fixture.controller
        window.makeKeyAndVisible()
        defer {
            window.isHidden = true
            window.rootViewController = nil
            previousKeyWindow?.makeKey()
        }
        await fixture.settle()

        // On cold launch the remote refresh can reveal its spinner before the
        // saved bookmark and its cached rows have reached the native controller.
        fixture.collection.beginRefreshing(revealingIndicator: true)
        let reading = ReadingState(position: TimelineReadingPosition(itemKey: "case-30", offset: -37))
        fixture.controller.reading = reading
        await fixture.settle()
        fixture.collection.endRefreshing()
        await fixture.settle()
        await fixture.settle()

        try fixture.assertPosition(("t:case-30", -37))
        XCTAssertNil(reading.restorePosition)
        XCTAssertTrue(reading.saved.isEmpty)
    }

    func testRestoringAReadingBookmarkDoesNotSaveAProgrammaticScroll() async throws {
        let fixture = await Fixture(posts: true)
        let reading = ReadingState(position: TimelineReadingPosition(itemKey: "case-30", offset: -37))
        fixture.controller.reading = reading
        await fixture.settle()

        try fixture.assertPosition(("t:case-30", -37))
        XCTAssertNil(reading.restorePosition)
        XCTAssertTrue(reading.saved.isEmpty)

        fixture.input.items.insert(.post(makeRow(100)), at: 0)
        await fixture.apply()
        try fixture.assertPosition(("t:case-30", -37))
        XCTAssertTrue(reading.saved.isEmpty)
    }

    func testLoadingAGapFillsDownwardEvenWhenItsPredecessorIsAboveTheViewport() async throws {
        let fixture = await Fixture(posts: true)
        let reading = ReadingState()
        fixture.controller.reading = reading
        let gap = makeGap()
        fixture.input.items.insert(.post(gap), at: 4)
        await fixture.apply()
        let gapPath = try XCTUnwrap(fixture.collection.readingIndexPath?("t:\(gap.stableItemKey)"))
        // Start at the gap itself: preserving the first visible row would move the reader down.
        let frame = try XCTUnwrap(fixture.collection.layoutAttributesForItem(at: gapPath)?.frame)
        await fixture.scroll(frame.minY + 40)
        let precedingPath = try XCTUnwrap(fixture.collection.readingIndexPath?("t:case-3"))
        let precedingFrame = try XCTUnwrap(fixture.collection.layoutAttributesForItem(at: precedingPath)?.frame)
        let expected = precedingFrame.minY - fixture.collection.contentOffset.y - fixture.collection.adjustedContentInset.top

        fixture.controller.willLoadTimelineGap(gap)
        fixture.input.items.insert(contentsOf: [.post(makeRow(100)), .post(makeRow(101))], at: 4)
        fixture.input.items[6] = .post(makeGap(revision: 1))
        await fixture.apply()

        try fixture.assertPosition(("t:case-3", expected))
        XCTAssertEqual(reading.saved.last?.itemKey, "case-3")
        XCTAssertFalse(fixture.collection.readingItemIDs?().contains("t:\(gap.stableItemKey)") ?? true)
        let insertedPath = try XCTUnwrap(fixture.collection.readingIndexPath?("t:case-100"))
        let insertedFrame = try XCTUnwrap(fixture.collection.layoutAttributesForItem(at: insertedPath)?.frame)
        XCTAssertLessThan(insertedFrame.minY - fixture.collection.contentOffset.y, fixture.collection.bounds.height)
    }

    func testLeadingGapRevealsInsertedPostsDespiteConcurrentRefreshInBothNativeLayouts() async throws {
        let gap = makeGap()
        let list = await Fixture(posts: true)
        list.input.items.insert(.post(gap), at: 0)
        await list.apply()
        await list.scroll(0)
        list.controller.willLoadTimelineGap(gap)
        list.input.items.insert(.post(makeRow(200)), at: 0)
        list.input.items.insert(contentsOf: [.post(makeRow(100)), .post(makeRow(101))], at: 1)
        list.input.items[3] = .post(makeGap(revision: 1))
        await list.apply()
        list.controller.didLoadTimelineGap(gap, insertedKeys: ["case-100", "case-101"])
        await list.settle()
        try list.assertPosition(("t:case-100", 0))

        let gallery = await GalleryFixture()
        gallery.content.items.insert(.post(gap), at: 0)
        gallery.controller.submit(gallery.content)
        await gallery.settle()
        let path = try XCTUnwrap(gallery.source.indexPath(for: "g:\(gap.stableItemKey)"))
        let offset = try XCTUnwrap(gallery.collection.layoutAttributesForItem(at: path)?.frame.minY)
        gallery.controller.willLoadTimelineGap(gap)
        gallery.content.items[0] = .post(makeGap(failed: true))
        gallery.controller.submit(gallery.content)
        await gallery.settle()
        gallery.controller.didLoadTimelineGap(gap, insertedKeys: ["case-100", "case-101"])
        gallery.content.items.insert(.post(makeRow(200)), at: 0)
        gallery.content.items.insert(contentsOf: [.post(makeRow(100)), .post(makeRow(101))], at: 1)
        gallery.content.items[3] = .post(makeGap(revision: 1, failed: true))
        gallery.controller.submit(gallery.content)
        await gallery.settle()
        try gallery.assertPosition(("g:case-100", offset))
    }

    func testGalleryLeadingGapCompletionAfterItsPageHasRenderedRestoresInsertion() async throws {
        let gap = makeGap()
        let gallery = await GalleryFixture()
        gallery.content.items.insert(.post(gap), at: 0)
        gallery.controller.submit(gallery.content)
        await gallery.settle()
        let path = try XCTUnwrap(gallery.source.indexPath(for: "g:\(gap.stableItemKey)"))
        let offset = try XCTUnwrap(gallery.collection.layoutAttributesForItem(at: path)?.frame.minY)
        gallery.controller.willLoadTimelineGap(gap)
        gallery.content.items.insert(contentsOf: [.post(makeRow(200)), .post(makeRow(100)), .post(makeRow(101))], at: 0)
        gallery.content.items[3] = .post(makeGap(revision: 1))
        gallery.controller.submit(gallery.content)
        await gallery.settle()
        gallery.controller.didLoadTimelineGap(gap, insertedKeys: ["case-100", "case-101"])
        await gallery.settle()
        try gallery.assertPosition(("g:case-100", offset))
    }

    func testFailedLeadingGapDoesNotJumpToALaterRefreshInBothNativeLayouts() async throws {
        let gap = makeGap()
        let list = await Fixture(posts: true)
        list.controller.reading = ReadingState()
        list.input.items.insert(.post(gap), at: 0)
        await list.apply()
        list.controller.willLoadTimelineGap(gap)
        list.controller.didLoadTimelineGap(gap, insertedKeys: [])
        list.input.items[0] = .post(makeGap(failed: true))
        await list.apply()
        let position = try list.position()
        list.input.items.insert(.post(makeRow(100)), at: 0)
        await list.apply()
        try list.assertPosition(position)

        let gallery = await GalleryFixture()
        gallery.controller.reading = ReadingState()
        gallery.content.items.insert(.post(gap), at: 0)
        gallery.controller.submit(gallery.content)
        await gallery.settle()
        gallery.controller.willLoadTimelineGap(gap)
        gallery.controller.didLoadTimelineGap(gap, insertedKeys: [])
        gallery.content.items[0] = .post(makeGap(failed: true))
        gallery.controller.submit(gallery.content)
        await gallery.settle()
        let realPostPath = try XCTUnwrap(gallery.source.indexPath(for: "g:case-0"))
        let realPostFrame = try XCTUnwrap(gallery.collection.layoutAttributesForItem(at: realPostPath)?.frame)
        let galleryPosition = ("g:case-0", realPostFrame.minY - gallery.collection.contentOffset.y)
        gallery.content.items.insert(.post(makeRow(100)), at: 0)
        gallery.controller.submit(gallery.content)
        await gallery.settle()
        try gallery.assertPosition(galleryPosition)
    }

    private final class ReadingState: NSObject, TimelineReadingState {
        var restorePosition: TimelineReadingPosition?
        var isAtLatest = true
        var hasNewContent = false
        var saved: [TimelineReadingPosition] = []
        var viewports: [TimelineReadingPosition] = []
        var latestRequests = 0

        init(position: TimelineReadingPosition? = nil) { restorePosition = position }
        func viewportChanged(itemKey: String, offset: Double) {
            viewports.append(TimelineReadingPosition(itemKey: itemKey, offset: offset))
        }
        func positionChanged(itemKey: String, offset: Double) {
            saved.append(TimelineReadingPosition(itemKey: itemKey, offset: offset))
        }
        func restorationFinished() { restorePosition = nil }
        func showLatest() { latestRequests += 1 }
        func restoreCurrentPosition() {}
        func savePosition() {}
    }

    func testSwitchingListsAlwaysStartsAtTopButUpdatingTheSameListKeepsPosition() async throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousKeyWindow = scene.windows.first(where: \.isKeyWindow)
        let host = UIHostingController(rootView: ListSwitchTestView(key: "mixed").modifier(IOSTimelineListEnvironment()))
        let window = UIWindow(windowScene: scene)
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer {
            window.isHidden = true
            window.rootViewController = nil
            previousKeyWindow?.makeKey()
        }

        func settle() async {
            for _ in 0..<25 {
                host.view.setNeedsLayout()
                host.view.layoutIfNeeded()
                try? await Task.sleep(for: .milliseconds(10))
            }
        }

        for key in ["mixed", "other", "mixed"] {
            host.rootView = ListSwitchTestView(key: key).modifier(IOSTimelineListEnvironment())
            await settle()
            let list = try XCTUnwrap(descendants(host.view).compactMap { $0 as? TimelineCollectionView }.first)
            XCTAssertEqual(list.contentOffset.y + list.restingAdjustedTopInset, 0, accuracy: 0.5)
            let controller = try XCTUnwrap(list.delegate as? UITimelineCollectionViewController)
            controller.restoreEffectiveContentOffset(800, animated: false)
            await settle()
            let offset = list.contentOffset.y
            XCTAssertGreaterThan(offset, 500)

            host.rootView = ListSwitchTestView(key: key).modifier(IOSTimelineListEnvironment())
            await settle()
            XCTAssertTrue(descendants(host.view).contains { $0 === list })
            XCTAssertEqual(list.contentOffset.y, offset, accuracy: 0.5)
        }
    }

    func testControllerResizesWithoutReplacingItsLayoutOrLosingPosition() async throws {
        let fixture = await Fixture()
        await fixture.scroll(2_500)
        let position = try fixture.position()
        let layout = fixture.collection.collectionViewLayout
        await fixture.resize(width: 900, columns: 2)
        try fixture.assertPosition(position)
        await fixture.resize(width: 390, columns: 1)
        try fixture.assertPosition(position)
        XCTAssertTrue(layout === fixture.collection.collectionViewLayout)
    }

    func testLateTopContentPreservesTheReadingPositionClampedByATallerViewport() async throws {
        for changesHeader in [true, false] {
            for offset: CGFloat in [0, 120, 600] {
                let fixture = await Fixture(posts: true)
                fixture.controller.topContentInset = 88
                fixture.input.items = Array(fixture.input.items.prefix(4))
                await fixture.apply()
                await fixture.scroll(offset)
                let expected = try fixture.position()
                let originalHeight = fixture.controller.view.frame.height

                // All rows fit after resizing, but a scrolled item's bookmark must survive.
                fixture.collection.prepareForLayoutChange()
                fixture.controller.view.frame.size.height = fixture.collection.contentSize.height + 1_000
                await fixture.settle()
                XCTAssertEqual(fixture.controller.effectiveContentOffsetY, 0, accuracy: 0.5)

                if changesHeader {
                    fixture.input.header = UiStateSuccess(data: makeRow(200))
                } else {
                    fixture.controller.accessoryItems = [
                        UITimelineCollectionViewAccessoryItem(id: "notice", view: Header())
                    ]
                }
                await fixture.apply()
                fixture.controller.view.frame.size.height = originalHeight
                await fixture.settle()

                if offset == 0 {
                    XCTAssertEqual(fixture.controller.effectiveContentOffsetY, 0, accuracy: 0.5)
                } else {
                    try fixture.assertPosition(expected)
                }
            }
        }
    }

    func testRemovingVisibleContentRestoresTheNextItem() async throws {
        let fixture = await Fixture()
        await fixture.scroll(2_500)
        let position = try fixture.position()
        let index = Int(position.id.dropFirst(2))!
        fixture.controller.accessoryItems.removeAll { $0.id == String(index) }
        await fixture.settle()
        try fixture.assertPosition(("a:\(index + 1)", position.offset))
    }

    func testWeiboContentSwitchKeepsOffsetEvenWhenTheOtherTabIsShorter() async {
        let fixture = await Fixture()
        fixture.controller.update(data: nil, columnCount: 1, contentKey: "comments")
        await fixture.settle()
        await fixture.scroll(2_500)
        fixture.controller.update(data: nil, columnCount: 1, contentKey: "reposts")
        fixture.controller.accessoryItems = Array(fixture.controller.accessoryItems.prefix(2))
        await fixture.settle()
        XCTAssertEqual(fixture.controller.effectiveContentOffsetY, 2_500, accuracy: 0.5)
    }

    func testDetailHeaderAndCommentsCanLoadInEitherOrderWithoutLeavingTop() async {
        for hasTabs in [false, true] {
            for headerFirst in [false, true] {
                let fixture = await Fixture(initialState: .loading)
                fixture.controller.topContentInset = 88
                fixture.controller.suppressInitialRefreshIndicator = true
                fixture.controller.accessoryItems = hasTabs
                    ? [UITimelineCollectionViewAccessoryItem(id: "tabs", view: Header())] : []
                fixture.input.header = UiStateLoading<UiTimelineV2>()
                await fixture.apply()

                if headerFirst {
                    fixture.input.header = UiStateSuccess(data: makeRow(100))
                } else {
                    fixture.input.state = .loaded
                    fixture.input.items = (0..<20).map { .post(makeRow($0)) }
                }
                await fixture.apply()
                fixture.input.header = UiStateSuccess(data: makeRow(100))
                fixture.input.state = .loaded
                fixture.input.items = (0..<20).map { .post(makeRow($0)) }
                await fixture.apply()

                XCTAssertEqual(fixture.controller.effectiveContentOffsetY, 0, accuracy: 0.5)
            }
        }
    }

    func testDetailHeaderLoadingKeepsCommentsTheUserHasScrolledTo() async throws {
        let fixture = await Fixture(initialState: .loading)
        fixture.input.header = UiStateLoading<UiTimelineV2>()
        await fixture.apply()
        fixture.input.state = .loaded
        fixture.input.items = (0..<20).map { .post(makeRow($0)) }
        await fixture.apply()
        await fixture.scroll(1_200)
        let position = try fixture.position()

        fixture.input.header = UiStateSuccess(data: makeRow(100))
        await fixture.apply()

        try fixture.assertPosition(position)
    }

    func testRepeatedInitialLoadingWithDetailTabsDoesNotRestoreAPlaceholderBookmark() async {
        for repeatsAccessories in [false, true] {
            let tabs = [UITimelineCollectionViewAccessoryItem(id: "vvo_status_tabs", view: Header())]
            let fixture = await Fixture(initialState: .loading, header: UiStateLoading<UiTimelineV2>(), accessories: tabs)
            // The real detail screen includes its header and tabs in the first snapshot.
            if repeatsAccessories {
                fixture.controller.accessoryItems = tabs
                await fixture.settle()
            } else {
                await fixture.apply()
            }
            fixture.input.header = UiStateSuccess(data: makeRow(100))
            await fixture.apply()
            XCTAssertEqual(fixture.controller.effectiveContentOffsetY, 0, accuracy: 0.5)

            fixture.input.state = .loaded
            fixture.input.items = (0..<20).map { .post(makeRow($0)) }
            await fixture.apply()
            XCTAssertEqual(fixture.controller.effectiveContentOffsetY, 0, accuracy: 0.5)
        }
    }

    func testHostedSectionsLoadingAboveAPendingLayoutBookmarkPreserveItsOriginalTopness() async throws {
        for offset: CGFloat in [0, 120] {
            let tags = TimelineHostedAccessoryView()
            tags.update(AnyView(Color.clear.frame(height: 140)))
            let accessories = [
                UITimelineCollectionViewAccessoryItem(id: "tags_title", view: Header(), pinnedView: UIView()),
                UITimelineCollectionViewAccessoryItem(id: "tags", view: tags),
                UITimelineCollectionViewAccessoryItem(id: "posts_title", view: Header(), pinnedView: UIView())
            ]
            let fixture = await Fixture(initialState: .loading, accessories: accessories)
            if offset > 0 { await fixture.scroll(offset) }
            // Hosted sections report their height while the post placeholders are still visible.
            tags.update(AnyView(Color.clear.frame(height: 160)))
            tags.setNeedsLayout()
            tags.layoutIfNeeded()
            await fixture.settle()
            XCTAssertTrue(fixture.collection.hasReadingPosition)
            let expected = try fixture.position()

            fixture.controller.accessoryItems = [
                UITimelineCollectionViewAccessoryItem(id: "users_title", view: Header(), pinnedView: UIView()),
                UITimelineCollectionViewAccessoryItem(id: "users", view: Header())
            ] + accessories
            await fixture.settle()
            if offset == 0 {
                XCTAssertEqual(fixture.controller.effectiveContentOffsetY, 0, accuracy: 0.5)
            } else {
                try fixture.assertPosition(expected)
            }

            fixture.input.state = .loaded
            fixture.input.items = (0..<20).map { .post(makeRow($0)) }
            await fixture.apply()
            if offset == 0 {
                XCTAssertEqual(fixture.controller.effectiveContentOffsetY, 0, accuracy: 0.5)
            } else {
                try fixture.assertPosition(expected)
            }
        }
    }

    func testLateTopSectionsKeepTopOrTheScrolledReadingItem() async throws {
        // Home's notice, search users, and discover users/tags load separately from posts.
        let scenarios: [(initial: [String], loaded: [String])] = [
            ([], ["change_log"]),
            (["posts_title"], ["users_title", "users", "posts_title"]),
            (["posts_title"], ["users_title", "users", "tags_title", "tags", "posts_title"])
        ]
        for sections in scenarios {
            for offset: CGFloat in [0, 1_200] {
                let fixture = await Fixture(initialState: .loading)
                fixture.controller.topContentInset = 88
                func accessories(_ ids: [String]) -> [UITimelineCollectionViewAccessoryItem] {
                    ids.map {
                        UITimelineCollectionViewAccessoryItem(
                            id: $0, view: Header(), pinnedView: $0.hasSuffix("_title") ? UIView() : nil
                        )
                    }
                }
                fixture.controller.accessoryItems = accessories(sections.initial)
                fixture.input.state = .loaded
                fixture.input.items = (0..<20).map { .post(makeRow($0)) }
                await fixture.apply()
                XCTAssertEqual(fixture.controller.effectiveContentOffsetY, 0, accuracy: 0.5)
                if offset > 0 { await fixture.scroll(offset) }
                let position = try fixture.position()

                fixture.controller.accessoryItems = accessories(sections.loaded)
                await fixture.settle()

                if offset == 0 {
                    XCTAssertEqual(fixture.controller.effectiveContentOffsetY, 0, accuracy: 0.5)
                } else {
                    try fixture.assertPosition(position)
                }
            }
        }
    }

    func testPrependingPostsAtTopStillPreservesTheReadingItem() async throws {
        let fixture = await Fixture(posts: true)
        let position = try fixture.position()
        fixture.input.items.insert(.post(makeRow(100)), at: 0)
        await fixture.apply()
        try fixture.assertPosition(position)
    }

    func testLateTopContentDuringReleasedPullRefreshStaysVisibleAfterRefresh() async throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousKeyWindow = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        defer {
            window.isHidden = true
            window.rootViewController = nil
            previousKeyWindow?.makeKey()
        }

        for changesHeader in [true, false] {
            let fixture = await Fixture(posts: true)
            fixture.controller.topContentInset = 88
            window.rootViewController = fixture.controller
            window.makeKeyAndVisible()
            await fixture.settle()
            let collection = fixture.collection

            fixture.controller.beginExternalScrollInteraction()
            collection.setContentOffset(CGPoint(x: 0, y: -180), animated: false)
            collection.refreshControl?.beginRefreshing()
            fixture.input.isRefreshing = true
            await fixture.apply()
            // Release at UIKit's refresh inset while the request remains in flight.
            collection.setContentOffset(CGPoint(x: 0, y: -collection.adjustedContentInset.top), animated: false)
            fixture.controller.endExternalScrollInteraction()
            await fixture.settle()
            XCTAssertTrue(collection.refreshControl?.isRefreshing == true)
            XCTAssertFalse(collection.shouldDeferSnapshotChanges)
            XCTAssertLessThan(fixture.controller.effectiveContentOffsetY, -1)

            if changesHeader {
                fixture.input.header = UiStateSuccess(data: makeRow(200))
            } else {
                fixture.controller.accessoryItems = [
                    UITimelineCollectionViewAccessoryItem(id: "notice", view: Header())
                ]
            }
            await fixture.apply()
            fixture.input.isRefreshing = false
            await fixture.apply()
            await fixture.settle()

            XCTAssertFalse(collection.isPresentingRefresh)
            XCTAssertEqual(fixture.controller.effectiveContentOffsetY, 0, accuracy: 0.5)
        }
    }

    func testSwitchingToALoadedProfileListDoesNotOverrideALaterScroll() async throws {
        let fixture = await Fixture(posts: true)
        fixture.controller.restoreEffectiveContentOffset(0, animated: false)
        fixture.controller.restoreEffectiveContentOffsetAfterNextSnapshot(0)
        await fixture.scroll(1_000)
        let expected = try fixture.position()
        fixture.input.items.insert(.post(makeRow(100)), at: 0)
        await fixture.apply()
        try fixture.assertPosition(expected)
    }

    func testPinnedHeaderUsesCommittedAccessoriesUntilTheNextSnapshot() async {
        let fixture = await Fixture()
        var visibility: [Bool] = []
        fixture.controller.accessoryItems[0] = UITimelineCollectionViewAccessoryItem(
            id: "0", view: Header(), onVisibilityChanged: { visibility.append($0) }, pinnedView: UIView()
        )
        await fixture.settle()
        await fixture.scroll(2_500)
        let occlusion = fixture.collection.readingTopOcclusion?() ?? 0
        XCTAssertTrue(occlusion > 0)
        fixture.controller.accessoryItems.removeFirst()
        XCTAssertEqual(fixture.collection.readingTopOcclusion?() ?? 0, occlusion, accuracy: 0.5)
        visibility.removeAll()
        fixture.controller.viewWillDisappear(false)
        XCTAssertEqual(visibility, [false])
        await fixture.settle()
        XCTAssertEqual(fixture.collection.readingTopOcclusion?() ?? 0, 0, accuracy: 0.5)
    }

    func testFastRefreshBeginSurvivesCoalescingUntilItsResultCommits() async {
        let fixture = await Fixture(posts: true)
        fixture.input.key = "comments"
        await fixture.apply()
        fixture.input.isRefreshing = true
        fixture.controller.submit(fixture.input, columns: 1)
        fixture.input.isRefreshing = false
        fixture.controller.submit(fixture.input, columns: 1)
        XCTAssertTrue(fixture.collection.isPresentingRefresh)
        await fixture.settle()
        XCTAssertFalse(fixture.collection.isPresentingRefresh)
    }

    func testInitialContentCommitsBeforeRefreshRevealFinishes() async throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousKeyWindow = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        defer {
            window.isHidden = true
            window.rootViewController = nil
            previousKeyWindow?.makeKey()
        }

        for initialState in [TimelineContent.State.unbound, .loading] {
            let fixture = await Fixture(initialState: initialState)
            window.rootViewController = fixture.controller
            window.makeKeyAndVisible()
            await fixture.settle()

            fixture.input.state = .loaded
            fixture.input.items = [.post(makeRow(0))]
            fixture.input.isRefreshing = true
            fixture.controller.submit(fixture.input, columns: 1)

            // Observe the queued submission before UIKit finishes revealing refresh.
            await withCheckedContinuation { continuation in
                DispatchQueue.main.async {
                    XCTAssertTrue(fixture.collection.isPresentingRefresh)
                    XCTAssertTrue(fixture.collection.shouldDeferSnapshotChanges)
                    XCTAssertEqual(fixture.collection.readingItemIDs?().first, "t:case-0",
                        "Initial content must replace placeholders before the refresh reveal finishes")
                    continuation.resume()
                }
            }
            await fixture.settle()
        }
    }

    func testInitialRefreshCollapseAcceptsLateCellMeasurements() async throws {
        try await checkTopRefreshCollapseWithLateCellMeasurement(switchedContent: false)
    }

    func testKeyedTabRefreshCollapseAcceptsLateCellMeasurements() async throws {
        try await checkTopRefreshCollapseWithLateCellMeasurement(switchedContent: true)
    }

    private func checkTopRefreshCollapseWithLateCellMeasurement(switchedContent: Bool) async throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousKeyWindow = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        defer {
            window.isHidden = true
            window.rootViewController = nil
            previousKeyWindow?.makeKey()
        }
        let fixture = await Fixture(posts: switchedContent, initialState: switchedContent ? nil : .loading)
        fixture.input.key = "previous"
        await fixture.apply()
        fixture.collection.contentInsetAdjustmentBehavior = .automatic
        window.rootViewController = fixture.controller
        window.makeKeyAndVisible()
        await fixture.settle()

        fixture.input.key = switchedContent ? "next" : "previous"
        fixture.input.state = .loaded
        fixture.input.items = (100..<160).map { .post(makeRow($0)) }
        await fixture.apply()
        fixture.input.isRefreshing = true
        await fixture.apply()
        try await Task.sleep(for: .milliseconds(250))
        let view = fixture.collection
        let restingInset = view.restingAdjustedTopInset
        fixture.input.isRefreshing = false
        fixture.controller.submit(fixture.input, columns: 1)
        try await Task.sleep(for: .milliseconds(40))
        XCTAssertTrue(view.shouldDeferSnapshotChanges)
        XCTAssertLessThan(view.contentOffset.y, -view.adjustedContentInset.top - 1)

        let path = try XCTUnwrap(view.readingIndexPath?("t:case-100"))
        let cell = try XCTUnwrap(view.cellForItem(at: path) as? TimelineUIKitCollectionViewCell)
        let report = try XCTUnwrap(cell.onPreferredHeightChanged)
        let frame = try XCTUnwrap(view.layoutAttributesForItem(at: path)?.frame)
        // Use the cell callback so the controller stages and commits the measurement.
        report(frame.width, frame.height + 35)
        try await Task.sleep(for: .milliseconds(500))
        await fixture.settle()
        XCTAssertEqual(view.contentOffset.y, -restingInset, accuracy: 0.5)
        XCTAssertEqual(view.captureViewportPosition()?.itemID, "t:case-100")
        XCTAssertFalse(view.isPresentingRefresh)
        XCTAssertFalse(view.hasReadingPosition)
    }

    func testRefreshingKeyedSwitchCommitsBeforeStartingRefreshPresentation() async throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousKeyWindow = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        defer {
            window.isHidden = true
            window.rootViewController = nil
            previousKeyWindow?.makeKey()
        }

        for suppressInitialRefreshIndicator in [false, true] {
            let fixture = await Fixture(posts: true)
            fixture.controller.suppressInitialRefreshIndicator = suppressInitialRefreshIndicator
            fixture.input.key = "comments"
            await fixture.apply()
            window.rootViewController = fixture.controller
            window.makeKeyAndVisible()
            await fixture.settle()

            fixture.input.key = "reposts"
            fixture.input.items = [.post(makeRow(100))]
            fixture.input.isRefreshing = true
            fixture.controller.submit(fixture.input, columns: 1)
            XCTAssertFalse(fixture.collection.isPresentingRefresh,
                "A keyed switch must not reveal refresh using the old content's state")

            await withCheckedContinuation { continuation in
                DispatchQueue.main.async {
                    XCTAssertEqual(fixture.collection.readingItemIDs?(), ["t:case-100"],
                        "The new tab must commit without waiting for the old tab's refresh reveal")
                    if suppressInitialRefreshIndicator {
                        XCTAssertFalse(fixture.collection.isPresentingRefresh,
                            "The new content's initial refresh suppression must apply before presentation")
                    }
                    continuation.resume()
                }
            }
            await fixture.settle()
        }
    }

    func testPullRefreshCommitsOnlyTheLatestInputAfterTheGestureSettles() async throws {
        let fixture = await Fixture(posts: true)
        let collection = fixture.collection
        let originalIDs = collection.readingItemIDs?()
        fixture.controller.beginExternalScrollInteraction()
        collection.setContentOffset(CGPoint(x: 0, y: -180), animated: false)
        collection.beginRefreshing(revealingIndicator: false)

        fixture.input.items.insert(.post(makeRow(100)), at: 0)
        await fixture.apply()
        fixture.input.items.insert(.post(makeRow(101)), at: 0)
        await fixture.apply()
        XCTAssertEqual(collection.readingItemIDs?(), originalIDs,
            "A prepend must not turn the elastic pull distance into a reading offset")
        XCTAssertTrue(collection.isPresentingRefresh, "Refresh cannot finish before its queued result commits")

        // The profile's external scroll coordinator reports the same interaction
        // boundary as a native pan. Model its return to the resting refresh inset.
        collection.setContentOffset(CGPoint(x: 0, y: -collection.adjustedContentInset.top), animated: false)
        fixture.controller.endExternalScrollInteraction()
        await fixture.settle()

        XCTAssertEqual(Array(collection.readingItemIDs?().prefix(3) ?? []), ["t:case-101", "t:case-100", "t:case-0"])
        try fixture.assertPosition(("t:case-0", 0))
        XCTAssertFalse(collection.isPresentingRefresh)
    }

    func testUnboundInputDoesNotConsumeInitialRefreshSuppression() async {
        let fixture = await Fixture()
        fixture.controller.suppressInitialRefreshIndicator = true
        fixture.input.state = .loaded
        fixture.input.items = [.post(makeRow(0))]
        fixture.input.isRefreshing = true
        await fixture.apply()
        XCTAssertFalse(fixture.collection.isPresentingRefresh)
    }

    func testPrependAndAppendKeepTheReadingItemInOneAndTwoColumns() async throws {
        for columns in [1, 2] {
            let fixture = await Fixture(posts: true, columns: columns)
            await fixture.scroll(2_500)
            let expected = try fixture.position()
            fixture.input.items.insert(.post(makeRow(100)), at: 0)
            fixture.input.items.append(.post(makeRow(101)))
            await fixture.apply()
            try fixture.assertPosition(expected)
            XCTAssertFalse(fixture.collection.hasReadingPosition)
        }
    }

    func testAThousandPrependedPostsKeepTheReadingItemInOneAndTwoColumns() async throws {
        for columns in [1, 2] {
            let fixture = await Fixture(posts: true, columns: columns)
            await fixture.scroll(2_500)
            let expected = try fixture.position()
            fixture.input.items.insert(contentsOf: (1_000..<2_000).map { .post(makeRow($0)) }, at: 0)
            await fixture.apply()
            try fixture.assertPosition(expected)
            XCTAssertFalse(fixture.collection.hasReadingPosition)
        }
    }

    func testAThousandPrependedGalleryPostsKeepTheReadingItem() async throws {
        let fixture = await GalleryFixture()
        fixture.collection.setContentOffset(CGPoint(x: 0, y: 2_500), animated: false)
        await fixture.settle()
        let expected = try fixture.position()
        fixture.content.items.insert(contentsOf: (1_000..<2_000).map { .post(makeRow($0)) }, at: 0)
        fixture.controller.submit(fixture.content)
        await fixture.settle()
        try fixture.assertPosition(expected)
    }

    func testReplacementAfterRefreshRespectsTheAppBarInset() async {
        let fixture = await Fixture(posts: true)
        fixture.controller.topContentInset = 74
        await fixture.scroll(2_500)
        fixture.input.isRefreshing = true
        await fixture.apply()
        fixture.input.items = (100..<160).map { .post(makeRow($0)) }
        fixture.input.isRefreshing = false
        await fixture.apply()
        XCTAssertEqual(fixture.controller.effectiveContentOffsetY, 0, accuracy: 0.5)
        XCTAssertEqual(fixture.collection.contentOffset.y, -74, accuracy: 0.5)
        XCTAssertFalse(fixture.collection.hasReadingPosition)
    }

    func testLikeDoesNotReplaceOtherVisibleCardsOrMoveTheViewport() async throws {
        let fixture = await Fixture(posts: true)
        await fixture.scroll(1_000)
        let expected = try fixture.position()
        let visible = fixture.collection.indexPathsForVisibleItems
        let oldCells = Dictionary(uniqueKeysWithValues: visible.compactMap { path in
            fixture.collection.cellForItem(at: path).map { (path, $0) }
        })
        let index = try XCTUnwrap(visible.sorted().first?.item)
        fixture.input.items[index] = .post(makeRow(index, liked: true))
        await fixture.apply()
        try fixture.assertPosition(expected)
        for (path, cell) in oldCells where path.item != index {
            XCTAssertTrue(cell === fixture.collection.cellForItem(at: path))
        }
    }

    func testRapidInputsCommitOnlyTheLatestRowsAndPagingSource() async throws {
        let fixture = await Fixture(posts: true)
        var accessed: [Int] = []
        for generation in 1...20 {
            var input = fixture.input
            input.items = (generation * 100..<generation * 100 + 60).map { .post(makeRow($0)) }
            input.access = { accessed.append(generation * 100 + $0) }
            fixture.controller.submit(input, columns: generation.isMultiple(of: 2) ? 1 : 2)
        }
        await fixture.settle()
        XCTAssertEqual(fixture.collection.readingItemIDs?().first, "t:case-2000")
        XCTAssertFalse(accessed.isEmpty)
        XCTAssertTrue(accessed.allSatisfy { $0 >= 2_000 && $0 < 2_060 })
        XCTAssertEqual(fixture.controller.columnCount, 1)
    }

    func testFooterAndContentChangesUseTheSameCommitAndKeepPosition() async throws {
        let fixture = await Fixture(posts: true)
        await fixture.scroll(2_500)
        let expected = try fixture.position()
        fixture.input.footer = .loading
        fixture.input.items[4] = .post(makeRow(4, liked: true))
        await fixture.apply()
        try fixture.assertPosition(expected)
        fixture.input.footer = .end
        fixture.input.items.append(.post(makeRow(120)))
        await fixture.apply()
        try fixture.assertPosition(expected)
        XCTAssertFalse(fixture.collection.hasReadingPosition)
    }

    func testReloadThroughPlaceholdersKeepsTheCurrentReadingPosition() async throws {
        let fixture = await Fixture(posts: true)
        await fixture.scroll(2_500)
        let expected = try fixture.position()
        let items = fixture.input.items
        fixture.input.state = .loading
        fixture.input.items = []
        await fixture.apply()
        fixture.input.state = .loaded
        fixture.input.items = [.post(makeRow(100))] + items
        await fixture.apply()
        try fixture.assertPosition(expected)
    }

    func testTopContentChangingDuringPlaceholderReloadKeepsTheSavedReadingPosition() async throws {
        for changesHeader in [true, false] {
            let fixture = await Fixture(posts: true)
            // The shorter placeholder list fits, temporarily clamping the viewport to the top.
            fixture.controller.view.frame.size.height = 1_200
            fixture.controller.topContentInset = 88
            await fixture.scroll(2_500)
            let expected = try fixture.position()
            let items = fixture.input.items

            fixture.input.state = .loading
            fixture.input.items = []
            await fixture.apply()
            XCTAssertEqual(fixture.controller.effectiveContentOffsetY, 0, accuracy: 0.5)

            if changesHeader {
                fixture.input.header = UiStateSuccess(data: makeRow(200))
            } else {
                fixture.controller.accessoryItems = [
                    UITimelineCollectionViewAccessoryItem(id: "notice", view: Header())
                ]
            }
            await fixture.apply()

            fixture.input.state = .loaded
            fixture.input.items = [.post(makeRow(100))] + items
            await fixture.apply()
            try fixture.assertPosition(expected)
        }
    }

    func testNavigationKeepsTheLiveListButReopeningStartsAtTop() async throws {
        let fixture = await Fixture(posts: true)
        await fixture.scroll(1_500)
        let expected = try fixture.position()
        // Deeper navigation returns to the same controller.
        fixture.controller.viewWillDisappear(false)
        fixture.controller.viewWillAppear(false)
        await fixture.settle()
        try fixture.assertPosition(expected)
        // Reopening creates a fresh list with no saved position.
        let reopened = await Fixture(posts: true)
        XCTAssertEqual(reopened.controller.effectiveContentOffsetY, 0, accuracy: 0.5)
    }

    func testImagesAndPositionSurviveRepeatedWidthAndColumnChanges() async throws {
        let fixture = await Fixture(posts: true)
        await fixture.scroll(2_500)
        let expected = try fixture.position()
        for width: CGFloat in [900, 390, 650, 390, 1_024, 390] {
            let columns = TimelineColumnPolicy.adaptive.columnCount(for: width)
            await fixture.resize(width: width, columns: columns)
            if width == 390 { try fixture.assertPosition(expected) }
            for cell in fixture.collection.visibleCells {
                for media in descendants(cell).compactMap({ $0 as? StatusMediaUIView }).filter({ !$0.isHidden }) {
                    guard media.bounds.width > 1, let expectedHeight = media.timelineHeight(for: media.bounds.width) else { continue }
                    XCTAssertEqual(media.bounds.height, expectedHeight, accuracy: 1)
                    XCTAssertLessThanOrEqual(media.bounds.width, cell.bounds.width)
                }
            }
            XCTAssertFalse(fixture.collection.hasReadingPosition)
        }
    }

    func testWaterfallUsesMeasuredHeightsForFractionalColumnWidths() async throws {
        let fixture = await Fixture(posts: true, columns: 2)
        // Two columns produce a half-pixel width at 3x and 2x respectively.
        for width: CGFloat in [871, 871.5] {
            await fixture.resize(width: width, columns: 2)
            let paths = fixture.collection.indexPathsForVisibleItems
            XCTAssertGreaterThanOrEqual(paths.count, 2)
            for path in paths {
                let cell = try XCTUnwrap(fixture.collection.cellForItem(at: path) as? TimelineUIKitCollectionViewCell)
                let card = try XCTUnwrap(descendants(cell).compactMap { $0 as? AdaptiveTimelineCardUIView }.first)
                let frame = try XCTUnwrap(fixture.collection.layoutAttributesForItem(at: path)?.frame)
                let measuredHeight = ceil(try XCTUnwrap(card.timelineHeight(for: frame.width))) + 1
                XCTAssertEqual(frame.height, measuredHeight, accuracy: 1,
                    "Item \(path) must use its measured height at container width \(width), rather than the 240pt estimate")
            }
        }
    }

    func testEstimatedCellHeightDoesNotPaintOverTheFollowingItem() throws {
        let cell = TimelineUIKitCollectionViewCell(frame: CGRect(x: 0, y: 0, width: 390, height: 240))
        cell.configureTimeline(data: makeRow(0), index: 0, totalCount: 2,
                               appearance: TimelineUIKitAppearance(timeline: TimelineAppearance.companion.Default), detailStatusKey: nil,
                               aiTldrEnabled: false, isMultipleColumn: false, openURL: nil)
        let container = UIView(frame: CGRect(x: 0, y: 0, width: 390, height: 800))
        container.addSubview(cell)
        container.layoutIfNeeded()
        let card = try XCTUnwrap(descendants(cell).compactMap { $0 as? AdaptiveTimelineCardUIView }.first)
        XCTAssertGreaterThan(try XCTUnwrap(card.timelineHeight(for: 390)), cell.bounds.height)

        // Crop the actual rendered card below its temporary estimated cell boundary.
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.preferredRange = .standard
        let image = UIGraphicsImageRenderer(size: CGSize(width: 390, height: 400), format: format).image { context in
            UIColor.white.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 390, height: 400))
            context.cgContext.translateBy(x: 0, y: -cell.bounds.height)
            container.layer.render(in: context.cgContext)
        }
        add(XCTAttachment(image: image))
        let bitmap = try XCTUnwrap(image.cgImage)
        XCTAssertEqual(bitmap.bitsPerPixel, 32)
        let bytes = try XCTUnwrap(bitmap.dataProvider?.data) as Data
        let isBlank = (0..<bitmap.height).allSatisfy { row in
            let start = row * bitmap.bytesPerRow
            return bytes[start..<(start + bitmap.width * 4)].allSatisfy { $0 == 255 }
        }
        XCTAssertTrue(isBlank, "A newly inserted card painted outside its estimated cell")
    }

    func testQuotedCarouselClipsImagesToTheCardCornersAfterScrollingAndResizing() throws {
        let post = try XCTUnwrap(makeRow(1) as? UiTimelineV2.TimelinePostItem).post
        let helper = TimelinePresentationAppearancePatchHelper.shared
        let appearance = StatusUIKitAppearance(timeline: helper.resolve(
            base: TimelineAppearance.companion.Default,
            patch: helper.setMediaLayout(patch: helper.empty, value: .carousel)
        ))
        let status = StatusUIKitView()
        status.configure(data: post, appearance: appearance, showMedia: false,
                         forceHideActions: true, quotes: [post], allowsMediaCarousel: true)
        let quotedStatus = try XCTUnwrap(descendants(status).compactMap { $0 as? StatusUIKitView }
            .first { $0 !== status })
        let card = try XCTUnwrap(quotedStatus.superview)
        let carousel = try XCTUnwrap(descendants(quotedStatus).compactMap { $0 as? UICollectionView }.first)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        format.preferredRange = .standard
        let image = UIGraphicsImageRenderer(size: CGSize(width: 8, height: 8), format: format).image { context in
            UIColor.magenta.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 8, height: 8))
        }

        for width: CGFloat in [336, 280, 336] {
            status.prepareForFitting(width: width)
            status.frame = CGRect(x: 0, y: 0, width: width, height: try XCTUnwrap(status.timelineHeight(for: width)))
            status.layoutIfNeeded()
            XCTAssertGreaterThan(carousel.contentSize.width, carousel.bounds.width)
            let viewport = carousel.convert(carousel.bounds, to: card)
            XCTAssertEqual(viewport.minX, 0, accuracy: 0.5)
            XCTAssertEqual(viewport.maxX, card.bounds.maxX, accuracy: 0.5)

            for offset: CGFloat in [0, 40, 100] {
                carousel.setContentOffset(CGPoint(x: offset, y: 0), animated: false)
                carousel.layoutIfNeeded()
                // Supply decoded pixels at the image leaf; retain the real media cells and clipping hierarchy.
                let imageViews = carousel.visibleCells.flatMap(descendants).compactMap { $0 as? UIImageView }
                    .filter { $0.contentMode == .scaleAspectFill }
                XCTAssertFalse(imageViews.isEmpty)
                imageViews.forEach { $0.image = image }

                let screenshot = UIGraphicsImageRenderer(bounds: card.bounds, format: format).image { context in
                    UIColor.black.setFill()
                    context.fill(card.bounds)
                    card.layer.render(in: context.cgContext)
                }
                let bitmap = try XCTUnwrap(screenshot.cgImage)
                XCTAssertEqual(bitmap.bitsPerPixel, 32)
                let bytes = try XCTUnwrap(bitmap.dataProvider?.data) as Data
                let boundary = UIBezierPath(roundedRect: card.bounds, cornerRadius: card.layer.cornerRadius)
                var insidePixels = 0
                var outsidePixels = 0
                for y in Int((card.bounds.maxY - card.layer.cornerRadius) * format.scale)..<bitmap.height {
                    for x in 0..<bitmap.width {
                        let index = y * bitmap.bytesPerRow + x * 4
                        guard bytes[index] > 192, bytes[index + 1] < 64, bytes[index + 2] > 192 else { continue }
                        let point = CGPoint(x: (CGFloat(x) + 0.5) / format.scale, y: (CGFloat(y) + 0.5) / format.scale)
                        if boundary.contains(point) { insidePixels += 1 } else { outsidePixels += 1 }
                    }
                }
                XCTAssertGreaterThan(insidePixels, 100, "The media must actually render beside the bottom corners")
                XCTAssertEqual(outsidePixels, 0, "Quoted media escaped its card at width \(width), offset \(offset)")
                if outsidePixels > 0 {
                    add(XCTAttachment(image: screenshot))
                }
            }
        }
    }

    private func descendants(_ view: UIView) -> [UIView] { [view] + view.subviews.flatMap(descendants) }

    private struct ListSwitchTestView: View {
        let key: String
        @Environment(\.timelineListRenderer) private var renderer

        var body: some View {
            renderer?(TimelineListRequest(
                key: key,
                content: .none,
                headers: (0..<80).map { index in
                    TimelineListHeader(id: String(index)) { Color.clear.frame(height: 120) }
                }
            ))
        }
    }

    @MainActor
    private final class Fixture {
        let controller = UITimelineCollectionViewController(detailStatusKey: nil)
        var collection: TimelineCollectionView { controller.view.subviews.compactMap { $0 as? TimelineCollectionView }.first! }

        var input = TimelineContent()
        var columns: Int

        init(posts: Bool = false, columns: Int = 1, initialState: TimelineContent.State? = nil,
             header: UiState<UiTimelineV2>? = nil, accessories: [UITimelineCollectionViewAccessoryItem] = []) async {
            self.columns = columns
            input.header = header
            controller.accessoryItems = accessories
            if let initialState {
                input.state = initialState
            } else if posts {
                input.state = .loaded
                input.items = (0..<60).map { .post(makeRow($0)) }
            } else {
                controller.accessoryItems = (0..<80).map { UITimelineCollectionViewAccessoryItem(id: String($0), view: Header()) }
            }
            controller.submit(input, columns: columns)
            controller.loadViewIfNeeded()
            controller.view.frame = CGRect(x: 0, y: 0, width: columns == 1 ? 390 : 900, height: 600)
            collection.contentInsetAdjustmentBehavior = .never
            await settle()
        }

        func settle() async {
            for _ in 0..<25 {
                controller.view.setNeedsLayout()
                controller.view.layoutIfNeeded()
                collection.layoutIfNeeded()
                try? await Task.sleep(for: .milliseconds(10))
            }
        }

        func scroll(_ offset: CGFloat) async {
            controller.restoreEffectiveContentOffset(offset, animated: false)
            await settle()
        }

        func apply() async {
            controller.submit(input, columns: columns)
            await settle()
        }

        func resize(width: CGFloat, columns: Int) async {
            self.columns = columns
            controller.submit(input, columns: columns)
            controller.view.frame.size.width = width
            await settle()
        }

        func position() throws -> (id: String, offset: CGFloat) {
            let bookmark = try XCTUnwrap(collection.captureReadingPosition())
            guard case .item(let id, let offset, _, _) = bookmark else { throw NSError(domain: "Expected reading item", code: 1) }
            return (id, offset)
        }

        func assertPosition(_ position: (id: String, offset: CGFloat), file: StaticString = #filePath, line: UInt = #line) throws {
            let path = try XCTUnwrap(collection.readingIndexPath?(position.id), file: file, line: line)
            let frame = try XCTUnwrap(collection.layoutAttributesForItem(at: path)?.frame, file: file, line: line)
            XCTAssertEqual(frame.minY - collection.contentOffset.y - collection.adjustedContentInset.top, position.offset, accuracy: 0.5, file: file, line: line)
        }
    }

    private final class Header: UIView {
        override func systemLayoutSizeFitting(_ targetSize: CGSize, withHorizontalFittingPriority: UILayoutPriority, verticalFittingPriority: UILayoutPriority) -> CGSize {
            CGSize(width: targetSize.width, height: 100 + ceil(1_000 / max(targetSize.width, 1)) * 20)
        }
    }
}

@MainActor
private func makeGap(revision: Int64 = 0, failed: Bool = false) -> UiTimelineV2.Gap {
    UiTimelineV2.Gap(statusKey: MicroBlogKey(id: "gap", host: "example.invalid"),
        createdAt: KotlinInstant.companion.fromEpochMilliseconds(epochMilliseconds: revision).toUi(),
        nextKey: "cursor", lowerBound: [:], revision: revision,
        itemKey: "timeline-gap:test", loading: false, failed: failed, onActivate: nil)
}

@MainActor
private func makeRow(_ index: Int, liked: Bool = false, imageHeight: Float = 600) -> UiTimelineV2 {
    let text = "Post \(index): " + String(repeating: "Rotation should preserve this reading item while text wraps and media resizes. ", count: index % 4 + 1)
    let style = RenderTextStyle(link: nil, bold: false, italic: false, strikethrough: false, monospace: false, code: false, underline: false, small: false, time: false)
    let rich = UiRichText(renderRuns: [RenderContent.Text(runs: [RenderRun.Text(text: text, style: style)], block: RenderBlockStyle())], isRtl: false, raw: text, innerText: text, imageUrls: [])
    let action = ActionMenu.Item(updateKey: "like", icon: liked ? .unlike : .like, text: nil, count: UiNumber(value: liked ? 2 : 1), color: liked ? .red : .contentColor, clickEvent: ClickEventNoop.shared, actionFamily: .like, enabled: true)
    let images: [UiMedia] = (0..<(index % 3 == 0 ? 1 : 4)).map { i in UiMediaImage(url: "https://example.invalid/fixture-\(i).png", previewUrl: "https://example.invalid/fixture-\(i).png", description: nil, height: imageHeight, width: 800, sensitive: false, customHeaders: nil) }
    let profile = UiProfile(key: MicroBlogKey(id: "user", host: "example.invalid"), handle: UiHandle(raw: "reader", host: "example.invalid"), avatar: nil as UiMediaImage?, nameInternal: rich, platformId: "mastodon", platformIcon: .world, clickEvent: ClickEventNoop.shared, banner: nil, description: nil, sourceLanguages: [], translationDisplayState: .hidden, matrices: UiProfile.Matrices(fansCount: 0, followsCount: 0, statusesCount: 0, platformFansCount: nil), mark: [], bottomContent: nil)
    let post = UiTimelineV2.Post(platformId: "mastodon", images: images, sensitive: false, contentWarning: nil, user: profile, platformIcon: .world, sourceLanguages: [], translationDisplayState: .hidden, content: UiTranslatableText(original: rich, translation: nil), actions: [action], poll: nil, statusKey: MicroBlogKey(id: "case-\(index)", host: "example.invalid"), card: nil, createdAt: KotlinInstant.companion.fromEpochMilliseconds(epochMilliseconds: 0).toUi(), emojiReactions: [], sourceChannel: nil, visibility: nil, replyToHandle: nil, references: [], clickEvent: ClickEventNoop.shared, mediaClickPolicy: .openStatusMedia, accountType: AccountType.Guest.shared, itemKey: "case-\(index)")
    return UiTimelineV2.TimelinePostItem(post: post, presentation: UiTimelineV2.PostPresentation(message: nil, inlineParents: [], quotes: [], repost: nil, notificationKey: nil), itemKey: "case-\(index)")
}
