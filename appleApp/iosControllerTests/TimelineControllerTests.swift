import XCTest
import UIKit
import SwiftUI
import ObjectiveC
import KotlinSharedUI
import FlareAppleCore
import FlareAppleUI
@testable import Flare

@MainActor
final class TimelineControllerIntegrationTests: XCTestCase {
    func testItemMetadataCacheTracksPayloadChangesReorderingAndRemoval() {
        var cache = TimelineContent.ItemMetadataCache()
        let original = makeRow(0, media: false)
        let unchanged = makeRow(1, media: false)
        let first = cache.update([.post(original), nil, .post(unchanged)])
        XCTAssertEqual(first.map { $0?.id }, ["t:case-0", nil, "t:case-1"])
        let changed = makeRow(0, liked: true, media: false)
        let second = cache.update([.post(unchanged), .post(changed), .post(changed), nil])
        XCTAssertEqual(second.map { $0?.id }, ["t:case-1", "t:case-0", "t:case-0", nil])
        XCTAssertEqual(second[0]?.renderHash, first[2]?.renderHash)
        XCTAssertEqual(second[1]?.renderHash, changed.renderHash)
        XCTAssertNotEqual(second[1]?.renderHash, first[0]?.renderHash)
        XCTAssertEqual(Set(cache.entries.keys), [ObjectIdentifier(unchanged), ObjectIdentifier(changed)])
        XCTAssertTrue(cache.update([]).isEmpty)
        XCTAssertTrue(cache.entries.isEmpty)
        XCTAssertEqual(cache.update([.post(changed)])[0]?.renderHash, changed.renderHash)
        XCTAssertEqual(cache.entries.count, 1)
    }

    func testCellMeasurementsDuringPrependPreserveDraggingPost() async throws {
        try await assertPrependMeasurementsPreservePost(scrollFlag: #selector(getter: UIScrollView.isDragging))
    }

    func testCellMeasurementsDuringPrependPreserveDeceleratingPost() async throws {
        try await assertPrependMeasurementsPreservePost(scrollFlag: #selector(getter: UIScrollView.isDecelerating))
    }

    private func assertPrependMeasurementsPreservePost(scrollFlag: Selector) async throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousKeyWindow = scene.windows.first(where: \.isKeyWindow)
        let controller = UITimelineCollectionViewController(detailStatusKey: nil)
        let host = UIViewController()
        host.addChild(controller)
        host.view.addSubview(controller.view)
        controller.view.frame = host.view.bounds
        controller.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        controller.didMove(toParent: host)
        var input = TimelineContent()
        input.state = .loaded
        input.items = (0..<120).map { .post(makeRow($0, media: false)) }
        controller.submit(input, columns: 1)
        let window = UIWindow(windowScene: scene)
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer {
            window.isHidden = true
            window.rootViewController = nil
            previousKeyWindow?.makeKey()
        }
        try await Task.sleep(for: .milliseconds(500))
        let list = try XCTUnwrap(controller.view.subviews.compactMap { $0 as? TimelineCollectionView }.first)
        list.layoutIfNeeded()
        let path = try XCTUnwrap(list.readingIndexPath?("t:case-20"))
        let frame = try XCTUnwrap(list.layoutAttributesForItem(at: path)).frame
        controller.restoreEffectiveContentOffset(frame.minY + 40, animated: false)
        try await Task.sleep(for: .milliseconds(300))

        let method = try XCTUnwrap(class_getInstanceMethod(UIScrollView.self, scrollFlag))
        let original = method_getImplementation(method)
        let getter = unsafeBitCast(original, to: (@convention(c) (UIScrollView, Selector) -> Bool).self)
        let override: @convention(block) (UIScrollView) -> Bool = { $0 === list || getter($0, scrollFlag) }
        let implementation = imp_implementationWithBlock(override)
        method_setImplementation(method, implementation)
        defer {
            method_setImplementation(method, original)
            imp_removeBlock(implementation)
        }
        controller.scrollViewWillBeginDragging(list)
        for step in 0..<70 {
            let anchor = try XCTUnwrap(list.captureReadingViewport())
            let path = try XCTUnwrap(list.readingIndexPath?(anchor.id))
            let cell = try XCTUnwrap(list.cellForItem(at: path))
            let screenY = cell.convert(.zero, to: window).y
            list.setContentOffset(CGPoint(x: 0, y: list.contentOffset.y - 15), animated: false)
            if step.isMultiple(of: 7) {
                input.items.insert(contentsOf: (200 + step * 20..<220 + step * 20).map {
                    .post(makeRow($0, media: false))
                }, at: 0)
                controller.submit(input, columns: 1)
            }
            // Real cells report heights during both the snapshot and offset-compensation layouts.
            try await Task.sleep(for: .milliseconds(25))
            let currentPath = try XCTUnwrap(list.readingIndexPath?(anchor.id))
            let currentCell = try XCTUnwrap(list.cellForItem(at: currentPath))
            let drift = currentCell.convert(.zero, to: window).y - screenY - 15
            guard abs(drift) <= 0.5 else {
                throw NSError(domain: "TimelineReadingPosition", code: step,
                              userInfo: [NSLocalizedDescriptionKey: "\(anchor.id) moved \(drift)pt at step \(step)"])
            }
        }
        try await Task.sleep(for: .milliseconds(100))
        guard !list.hasReadingPosition else { throw NSError(domain: "Unfinished reading restoration", code: 1) }
        let expected = (0..<10).reversed().flatMap { Array(200 + $0 * 140..<220 + $0 * 140) } + Array(0..<120)
        guard list.readingItemIDs?() == expected.map({ "t:case-\($0)" }) else {
            throw NSError(domain: "Missing or reordered reading posts", code: 1)
        }
    }

    func testRenamingHomeTabKeepsTheNativeListAndReadingPosition() async throws {
        let home = KotlinPresenter(presenter: HomeTimelineWithTabsPresenter())
        var firstTab: UiTimelineTabItem?
        for _ in 0..<100 {
            if case .success(let success) = onEnum(of: home.state.tabState) {
                firstTab = success.data.cast(UiTimelineTabItem.self).first
                if firstTab != nil { break }
            }
            try await Task.sleep(for: .milliseconds(10))
        }
        let tab = try XCTUnwrap(firstTab)
        let renamed = tab.withPresentationOverrides(title: "Renamed reading tab", icon: tab.icon,
            appearancePatch: tab.appearancePatch, enabled: tab.enabled, filterConfig: tab.filterConfig)
        XCTAssertEqual(tab.contentSourceKey, renamed.contentSourceKey)
        XCTAssertNotEqual(tab.loaderKey, renamed.loaderKey)
        let accessories = (0..<80).map { UITimelineCollectionViewAccessoryItem(id: String($0), view: Header()) }
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousKeyWindow = scene.windows.first(where: \.isKeyWindow)
        let host = UIHostingController(rootView: TimelineScreen(tabItem: tab, allowGalleryMode: false,
            isHomeTimeline: true, accessoryItems: accessories))
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
        await settle()
        let list = try XCTUnwrap(descendants(host.view).compactMap { $0 as? TimelineCollectionView }.first)
        let controller = try XCTUnwrap(list.delegate as? UITimelineCollectionViewController)
        controller.restoreEffectiveContentOffset(800, animated: false)
        await settle()
        let position = try XCTUnwrap(list.captureReadingPosition())
        let offset = list.contentOffset.y
        XCTAssertGreaterThan(offset, 500)
        host.rootView = TimelineScreen(tabItem: renamed, allowGalleryMode: false, isHomeTimeline: true, accessoryItems: accessories)
        await settle()
        XCTAssertTrue(descendants(host.view).contains { $0 === list })
        XCTAssertEqual(list.captureReadingPosition()?.itemID, position.itemID)
        XCTAssertEqual(list.contentOffset.y, offset, accuracy: 0.5)
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

    func testManualRefreshWithGapKeepsFractionalPositionAndNewPostsRemainScrollable() async throws {
        let fixture = await Fixture(posts: true)
        let reading = ReadingState()
        reading.position = TimelineReadingPosition(itemKey: "case-20", offset: -37.25, requestId: 1, latest: false)
        fixture.controller.readingState = reading
        await fixture.settle()
        let original = fixture.input.items
        let gap = UiTimelineV2.Message(user: nil, statusKey: MicroBlogKey(id: "gap", host: "test"), icon: .info,
            type: UiTimelineV2.MessageTypeLocalized(data: .readingGap, args: []),
            createdAt: KotlinInstant.companion.fromEpochMilliseconds(epochMilliseconds: 0).toUi(),
            clickEvent: ClickEventNoop.shared, accountType: AccountType.Guest.shared,
            itemKey: "reading-gap:test", readingGap: nil)
        fixture.input.isRefreshing = true
        await fixture.apply()
        fixture.input.items = (100..<110).map { .post(makeRow($0)) } + [.post(gap)] + original
        fixture.input.isRefreshing = false
        await fixture.apply()
        try fixture.assertPosition(("t:case-20", -37.25))
        XCTAssertEqual(reading.itemKey, "case-20")
        XCTAssertEqual(reading.offset, -37.25, accuracy: 0.5)
        XCTAssertNil(reading.position)
        await fixture.scroll(0)
        XCTAssertEqual(reading.itemKey, "case-100")
        XCTAssertTrue(fixture.collection.readingItemIDs?().contains("t:case-20") == true)
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

    func testSessionRestoresStablePostAndFractionalOffsetAfterLoading() async throws {
        let fixture = await Fixture(initialState: .loading)
        let reading = ReadingState()
        reading.position = TimelineReadingPosition(itemKey: "case-20", offset: -37.25, requestId: 1, latest: false)
        fixture.controller.readingState = reading
        await fixture.settle()
        XCTAssertNotNil(reading.position)
        fixture.input.state = .loaded
        fixture.input.items = (0..<60).map { .post(makeRow($0)) }
        await fixture.apply()
        XCTAssertNil(reading.position)
        try fixture.assertPosition(("t:case-20", -37.25))
        XCTAssertEqual(reading.itemKey, "case-20")
        XCTAssertEqual(reading.offset, -37.25, accuracy: 0.5)
    }

    func testReadingViewportReportsDoNotReadTheWholeSnapshotAndPrependKeepsPosition() async throws {
        let fixture = await Fixture(posts: true)
        let reading = ReadingState()
        fixture.controller.readingState = reading
        await fixture.scroll(1_500)
        let position = try fixture.position()
        let itemIDs = fixture.collection.readingItemIDs
        var fullOrderReads = 0
        fixture.collection.readingItemIDs = {
            fullOrderReads += 1
            return itemIDs?() ?? []
        }
        fixture.controller.beginExternalScrollInteraction()
        for _ in 0..<100 {
            fixture.collection.resetReadingPosition()
            fixture.controller.scrollViewDidScroll(fixture.collection)
        }
        fixture.controller.endExternalScrollInteraction()
        XCTAssertEqual(fullOrderReads, 0, "Continuous viewport reports must only inspect visible items")
        XCTAssertEqual(reading.itemKey, String(position.id.dropFirst(2)))
        XCTAssertEqual(reading.offset, Double(position.offset), accuracy: 0.5)
        fixture.collection.readingItemIDs = itemIDs

        fixture.input.items.insert(.post(makeRow(100)), at: 0)
        await fixture.apply()
        try fixture.assertPosition(position)
    }

    func testSessionAtTopCapturesAPostInsteadOfFollowLatest() async throws {
        let fixture = await Fixture(posts: true)
        let reading = ReadingState()
        fixture.controller.readingState = reading
        await fixture.settle()
        XCTAssertEqual(reading.itemKey, "case-0")
        XCTAssertEqual(reading.offset, 0, accuracy: 0.5)
        fixture.input.items.insert(.post(makeRow(100)), at: 0)
        await fixture.apply()
        try fixture.assertPosition(("t:case-0", 0))
    }

    func testSessionAtTopSkipsTheChangelogAndCheckpointsTheFirstVisiblePost() async throws {
        let fixture = await Fixture(posts: true, accessories: [UITimelineCollectionViewAccessoryItem(id: "change_log", view: Header())])
        let reading = ReadingState()
        fixture.controller.readingState = reading
        await fixture.scroll(1_500)
        XCTAssertNotEqual(reading.itemKey, "case-0")
        await fixture.scroll(0)
        XCTAssertEqual(try fixture.position().id, "a:change_log", "Transient layout anchoring must still include accessories")
        XCTAssertEqual(reading.itemKey, "case-0")
        let path = try XCTUnwrap(fixture.collection.readingIndexPath?("t:case-0"))
        let frame = try XCTUnwrap(fixture.collection.layoutAttributesForItem(at: path)?.frame)
        XCTAssertEqual(reading.offset, Double(frame.minY - fixture.collection.contentOffset.y), accuracy: 0.5)
    }

    func testSessionViewLatestWaitsForThePublishedHead() async throws {
        let fixture = await Fixture(posts: true)
        await fixture.scroll(1_500)
        let previousPosition = try fixture.position()
        let reading = ReadingState()
        reading.position = TimelineReadingPosition(itemKey: "case-20", offset: 0, requestId: 3, latest: true)
        fixture.controller.readingState = reading
        await fixture.settle()
        XCTAssertNotNil(reading.position)
        try fixture.assertPosition(previousPosition)

        fixture.input.items = ([20, 100] + Array(21..<60) + Array(0..<20)).map { .post(makeRow($0)) }
        await fixture.apply()
        XCTAssertNil(reading.position)
        XCTAssertEqual(fixture.controller.effectiveContentOffsetY, 0, accuracy: 0.5)
        XCTAssertEqual(reading.itemKey, "case-20")
    }

    func testUserScrollCancelsPendingSessionRestoration() async throws {
        let fixture = await Fixture(initialState: .loading)
        let reading = ReadingState()
        reading.position = TimelineReadingPosition(itemKey: "case-20", offset: -37, requestId: 2, latest: false)
        fixture.controller.readingState = reading
        fixture.controller.beginExternalScrollInteraction()
        XCTAssertNil(reading.position)
        fixture.controller.endExternalScrollInteraction()
        fixture.input.state = .loaded
        fixture.input.items = (0..<60).map { .post(makeRow($0)) }
        await fixture.apply()
        XCTAssertEqual(fixture.controller.effectiveContentOffsetY, 0, accuracy: 0.5)
    }

    private final class ReadingState: NSObject, TimelineReadingState {
        var position: TimelineReadingPosition?
        var hasNewContent = false
        var isRefreshing = false
        var itemKey: String?
        var offset: Double = 0
        func updateViewport(itemKey: String?, offset: Double, atTop: Bool, interacting: Bool) {
            self.itemKey = itemKey
            self.offset = offset
        }
        func positionRestored(requestId: Int64) {
            if position?.requestId == requestId { position = nil }
        }
        func cancelRestoration() { position = nil }
        func savePosition() {}
        nonisolated func __refreshAutomatically(completionHandler: @escaping @Sendable ((any Error)?) -> Void) { completionHandler(nil) }
        nonisolated func __showLatest(completionHandler: @escaping @Sendable ((any Error)?) -> Void) { completionHandler(nil) }
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
private func makeRow(_ index: Int, liked: Bool = false, media: Bool = true) -> UiTimelineV2 {
    let text = "Post \(index): " + String(repeating: "Rotation should preserve this reading item while text wraps and media resizes. ", count: index % 4 + 1)
    let style = RenderTextStyle(link: nil, bold: false, italic: false, strikethrough: false, monospace: false, code: false, underline: false, small: false, time: false)
    let rich = UiRichText(renderRuns: [RenderContent.Text(runs: [RenderRun.Text(text: text, style: style)], block: RenderBlockStyle())], isRtl: false, raw: text, innerText: text, imageUrls: [])
    let action = ActionMenu.Item(updateKey: "like", icon: liked ? .unlike : .like, text: nil, count: UiNumber(value: liked ? 2 : 1), color: liked ? .red : .contentColor, clickEvent: ClickEventNoop.shared, actionFamily: .like, enabled: true)
    let images: [UiMedia] = media ? (0..<(index % 3 == 0 ? 1 : 4)).map { i in UiMediaImage(url: "https://example.invalid/fixture-\(i).png", previewUrl: "https://example.invalid/fixture-\(i).png", description: nil, height: 600, width: 800, sensitive: false, customHeaders: nil) } : []
    let profile = UiProfile(key: MicroBlogKey(id: "user", host: "example.invalid"), handle: UiHandle(raw: "reader", host: "example.invalid"), avatar: nil as UiMediaImage?, nameInternal: rich, platformId: "mastodon", platformIcon: .world, clickEvent: ClickEventNoop.shared, banner: nil, description: nil, sourceLanguages: [], translationDisplayState: .hidden, matrices: UiProfile.Matrices(fansCount: 0, followsCount: 0, statusesCount: 0, platformFansCount: nil), mark: [], bottomContent: nil)
    let post = UiTimelineV2.Post(platformId: "mastodon", images: images, sensitive: false, contentWarning: nil, user: profile, platformIcon: .world, sourceLanguages: [], translationDisplayState: .hidden, content: UiTranslatableText(original: rich, translation: nil), actions: [action], poll: nil, statusKey: MicroBlogKey(id: "case-\(index)", host: "example.invalid"), card: nil, createdAt: KotlinInstant.companion.fromEpochMilliseconds(epochMilliseconds: 0).toUi(), emojiReactions: [], sourceChannel: nil, visibility: nil, replyToHandle: nil, references: [], clickEvent: ClickEventNoop.shared, mediaClickPolicy: .openStatusMedia, accountType: AccountType.Guest.shared, itemKey: "case-\(index)")
    return UiTimelineV2.TimelinePostItem(post: post, presentation: UiTimelineV2.PostPresentation(message: nil, inlineParents: [], quotes: [], repost: nil, notificationKey: nil), itemKey: "case-\(index)")
}
