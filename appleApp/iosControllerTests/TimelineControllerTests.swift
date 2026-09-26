import XCTest
import UIKit
import SwiftUI
import KotlinSharedUI
import FlareAppleUI
@testable import Flare

@MainActor
final class TimelineControllerIntegrationTests: XCTestCase {
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
        fixture.input.isRefreshing = true
        fixture.controller.submit(fixture.input, columns: 1)
        fixture.input.isRefreshing = false
        fixture.controller.submit(fixture.input, columns: 1)
        XCTAssertTrue(fixture.collection.isPresentingRefresh)
        await fixture.settle()
        XCTAssertFalse(fixture.collection.isPresentingRefresh)
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

        init(posts: Bool = false, columns: Int = 1) async {
            self.columns = columns
            if posts {
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
            guard case .item(let id, let offset, _) = bookmark else { throw NSError(domain: "Expected reading item", code: 1) }
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
private func makeRow(_ index: Int, liked: Bool = false) -> UiTimelineV2 {
    let text = "Post \(index): " + String(repeating: "Rotation should preserve this reading item while text wraps and media resizes. ", count: index % 4 + 1)
    let style = RenderTextStyle(link: nil, bold: false, italic: false, strikethrough: false, monospace: false, code: false, underline: false, small: false, time: false)
    let rich = UiRichText(renderRuns: [RenderContent.Text(runs: [RenderRun.Text(text: text, style: style)], block: RenderBlockStyle())], isRtl: false, raw: text, innerText: text, imageUrls: [])
    let action = ActionMenu.Item(updateKey: "like", icon: liked ? .unlike : .like, text: nil, count: UiNumber(value: liked ? 2 : 1), color: liked ? .red : .contentColor, clickEvent: ClickEventNoop.shared, actionFamily: .like, enabled: true)
    let images: [UiMedia] = (0..<(index % 3 == 0 ? 1 : 4)).map { i in UiMediaImage(url: "https://example.invalid/fixture-\(i).png", previewUrl: "https://example.invalid/fixture-\(i).png", description: nil, height: 600, width: 800, sensitive: false, customHeaders: nil) }
    let profile = UiProfile(key: MicroBlogKey(id: "user", host: "example.invalid"), handle: UiHandle(raw: "reader", host: "example.invalid"), avatar: nil as UiMediaImage?, nameInternal: rich, platformId: "mastodon", platformIcon: .world, clickEvent: ClickEventNoop.shared, banner: nil, description: nil, sourceLanguages: [], translationDisplayState: .hidden, matrices: UiProfile.Matrices(fansCount: 0, followsCount: 0, statusesCount: 0, platformFansCount: nil), mark: [], bottomContent: nil)
    let post = UiTimelineV2.Post(platformId: "mastodon", images: images, sensitive: false, contentWarning: nil, user: profile, platformIcon: .world, sourceLanguages: [], translationDisplayState: .hidden, content: UiTranslatableText(original: rich, translation: nil), actions: [action], poll: nil, statusKey: MicroBlogKey(id: "case-\(index)", host: "example.invalid"), card: nil, createdAt: KotlinInstant.companion.fromEpochMilliseconds(epochMilliseconds: 0).toUi(), emojiReactions: [], sourceChannel: nil, visibility: nil, replyToHandle: nil, references: [], clickEvent: ClickEventNoop.shared, mediaClickPolicy: .openStatusMedia, accountType: AccountType.Guest.shared, itemKey: "case-\(index)")
    return UiTimelineV2.TimelinePostItem(post: post, presentation: UiTimelineV2.PostPresentation(message: nil, inlineParents: [], quotes: [], repost: nil, notificationKey: nil), itemKey: "case-\(index)")
}
