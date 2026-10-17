import XCTest
import UIKit
import SwiftUI
import KotlinSharedUI
import FlareAppleCore
import FlareAppleUI
@testable import Flare

@MainActor
final class TimelineRefreshIndicatorTests: XCTestCase {
    func testHomeDoubleTapUsesSharedRefreshStateInList() async throws {
        try await assertHomeRefresh(gallery: false, pull: false)
    }

    func testHomeDoubleTapUsesSharedRefreshStateInGallery() async throws {
        try await assertHomeRefresh(gallery: true, pull: false)
    }

    func testPullRefreshUsesSharedRefreshStateInList() async throws {
        try await assertHomeRefresh(gallery: false, pull: true)
    }

    func testPullRefreshUsesSharedRefreshStateInGallery() async throws {
        try await assertHomeRefresh(gallery: true, pull: true)
    }

    private func assertHomeRefresh(gallery: Bool, pull: Bool) async throws {
        let home = KotlinPresenter(presenter: HomeTimelineWithTabsPresenter())
        var tab: UiTimelineTabItem?
        for _ in 0..<100 {
            if case .success(let value) = onEnum(of: home.state.tabState) {
                tab = value.data.cast(UiTimelineTabItem.self).first
                if tab != nil { break }
            }
            try await Task.sleep(for: .milliseconds(10))
        }
        let helper = TimelinePresentationAppearancePatchHelper.shared
        let item = try XCTUnwrap(tab)
        let configured = item.withPresentationOverrides(title: "Refresh regression", icon: item.icon,
            appearancePatch: helper.setTimelineDisplayMode(patch: helper.empty, value: gallery ? .gallery : .plain),
            enabled: item.enabled, filterConfig: item.filterConfig)
        let screen = TimelineScreen(tabItem: configured, isHomeTimeline: true)
        let presenter = screen.presenter
        presenter.presenter.close()
        let cache = KotlinPresenter(presenter: LocalCacheSearchPresenter())
        cache.state.setQuery(value: "refresh-indicator-no-matching-post")
        for _ in 0..<200 {
            if cache.state.data.isEmpty { break }
            try await Task.sleep(for: .milliseconds(10))
        }
        let paging = cache.state.data
        XCTAssertTrue(paging.isEmpty, "Use a resolved local paging snapshot, without a network request")
        cache.presenter.close()
        // Drain the real presenter's initial Combine replay before installing controlled responses.
        try await Task.sleep(for: .milliseconds(100))
        let source = RefreshSource(paging: paging, presenter: presenter)
        source.publish()
        let host = UIHostingController(rootView: screen)
        let window = try TestWindow(controller: host)
        defer { window.close() }
        await settle(host)
        let list = try XCTUnwrap(descendants(host.view).compactMap { $0 as? UICollectionView }.first)
        XCTAssertEqual(list.delegate is UIGalleryTimelineController, gallery)
        let refresh = try XCTUnwrap(list.refreshControl)
        XCTAssertFalse(paging.isRefreshing_, "The independent reading refresh must be the only active flag")
        XCTAssertFalse(refresh.isRefreshing)

        func trigger() {
            if pull {
                refresh.beginRefreshing()
                refresh.sendActions(for: .valueChanged)
            } else {
                NotificationCenter.default.post(name: .tabDoubleTapped,
                    object: HomeTabsPresenterStateHomeTabs.home.name.lowercased())
            }
        }
        for attempt in 1...2 {
            trigger()
            await settle(host)
            XCTAssertEqual(source.requests, attempt)
            XCTAssertTrue(refresh.isRefreshing, "Shared refresh must drive both native list styles")
            if !pull {
                XCTAssertGreaterThan(refresh.convert(refresh.bounds, to: list).maxY, list.contentOffset.y,
                    "Programmatic refresh must be visible at the top")
                trigger()
                await settle(host)
                XCTAssertEqual(source.requests, attempt, "Double taps must not start overlapping refreshes")
            }
            source.finish()
            await settle(host)
            XCTAssertFalse(refresh.isRefreshing, "Completion must allow another refresh")
        }
    }

    func testFastCompletionWaitsForQueuedDataInBothListStyles() async throws {
        for gallery in [false, true] {
            let fixture = try await Fixture(gallery: gallery)
            defer { fixture.close() }
            let before = fixture.ids
            var finishPull: CheckedContinuation<Void, Never>?
            fixture.onRefresh { await withCheckedContinuation { finishPull = $0 } }
            fixture.beginInteraction()
            fixture.list.setContentOffset(CGPoint(x: 0, y: -180), animated: false)
            fixture.refresh.beginRefreshing()
            fixture.refresh.sendActions(for: .valueChanged)
            await settle(fixture.controller)
            fixture.input.isRefreshing = true
            fixture.submit()
            fixture.input.items.insert(.post(makeRow(100, media: false)), at: 0)
            fixture.input.isRefreshing = false
            fixture.submit()
            XCTAssertNotNil(finishPull)
            finishPull?.resume()
            await settle(fixture.controller)
            XCTAssertTrue(fixture.refresh.isRefreshing)
            XCTAssertEqual(fixture.ids, before, "Data must remain queued during the active pull")
            fixture.list.setContentOffset(CGPoint(x: 0, y: -fixture.list.adjustedContentInset.top), animated: false)
            fixture.endInteraction()
            await settle(fixture.controller)
            XCTAssertEqual(fixture.ids, [fixture.prefix + "case-100"] + before)
            XCTAssertFalse(fixture.refresh.isRefreshing, "End only after the latest result commits")
        }
    }

    func testPullCompletingWithoutAnObservedLoadingStateKeepsTopPost() async throws {
        for gallery in [false, true] {
            let fixture = try await Fixture(gallery: gallery)
            defer { fixture.close() }
            let path = try XCTUnwrap(fixture.dataSource.indexPath(for: fixture.prefix + "case-0"))
            let originalY = try XCTUnwrap(fixture.list.layoutAttributesForItem(at: path)).frame.minY - fixture.list.contentOffset.y
            fixture.beginInteraction()
            fixture.list.setContentOffset(CGPoint(x: 0, y: -180), animated: false)
            fixture.refresh.beginRefreshing()
            fixture.refresh.sendActions(for: .valueChanged)
            await settle(fixture.controller)
            XCTAssertTrue(fixture.refresh.isRefreshing, "A completed callback must wait for the active pull to settle")
            fixture.list.setContentOffset(CGPoint(x: 0, y: -fixture.list.adjustedContentInset.top), animated: false)
            fixture.endInteraction()
            await settle(fixture.controller)
            let y = try XCTUnwrap(fixture.list.layoutAttributesForItem(at: path)).frame.minY - fixture.list.contentOffset.y
            XCTAssertEqual(y, originalY, accuracy: 0.5)
            XCTAssertFalse(fixture.refresh.isRefreshing)
        }
    }

    func testFastProgrammaticRefreshCommitsBeforeCollapsingAndKeepsTopPost() async throws {
        for gallery in [false, true] {
            let fixture = try await Fixture(gallery: gallery)
            defer { fixture.close() }
            let anchor = fixture.prefix + "case-0"
            let originalPath = try XCTUnwrap(fixture.dataSource.indexPath(for: anchor))
            let originalY = try XCTUnwrap(fixture.list.layoutAttributesForItem(at: originalPath)).frame.minY - fixture.list.contentOffset.y
            fixture.input.isRefreshing = true
            fixture.submit()
            try await Task.sleep(for: .milliseconds(40))
            XCTAssertTrue(fixture.refresh.isRefreshing)
            fixture.input.isRefreshing = false
            fixture.input.items.insert(contentsOf: (100..<120).map { .post(makeRow($0, media: false)) }, at: 0)
            fixture.submit()
            await settle(fixture.controller)
            await settle(fixture.controller) // Wait for both the native reveal and collapse animations.
            let path = try XCTUnwrap(fixture.dataSource.indexPath(for: anchor))
            let y = try XCTUnwrap(fixture.list.layoutAttributesForItem(at: path)).frame.minY - fixture.list.contentOffset.y
            XCTAssertEqual(y, originalY, accuracy: 0.5, "A fast refresh reveal/collapse must retain the top post (gallery=\(gallery))")
            XCTAssertFalse(fixture.refresh.isRefreshing)
        }
    }

    func testInitialSuppressionDoesNotSuppressLaterOrUserRefresh() async throws {
        for gallery in [false, true] {
            let fixture = try await Fixture(gallery: gallery, initialRefresh: true, suppressInitial: true)
            defer { fixture.close() }
            XCTAssertFalse(fixture.refresh.isRefreshing)
            fixture.input.isRefreshing = false
            fixture.submit()
            await settle(fixture.controller)
            fixture.input.isRefreshing = true
            fixture.submit()
            await settle(fixture.controller)
            XCTAssertTrue(fixture.refresh.isRefreshing)
            fixture.input.isRefreshing = false
            fixture.submit()
            await settle(fixture.controller)
            XCTAssertFalse(fixture.refresh.isRefreshing)
        }
        for gallery in [false, true] {
            let fixture = try await Fixture(gallery: gallery, initialRefresh: true, suppressInitial: true)
            defer { fixture.close() }
            fixture.refresh.beginRefreshing()
            fixture.refresh.sendActions(for: .valueChanged)
            fixture.submit()
            await settle(fixture.controller)
            XCTAssertTrue(fixture.refresh.isRefreshing, "An explicit pull must bypass first-load suppression")
        }
    }

    func testRefreshPreservesPostAndOffsetAwayFromTopInBothListStyles() async throws {
        for gallery in [false, true] {
            let fixture = try await Fixture(gallery: gallery)
            defer { fixture.close() }
            fixture.scroll(900)
            await settle(fixture.controller)
            let anchor = fixture.prefix + (try XCTUnwrap(fixture.reading.itemKey))
            let visible = try XCTUnwrap(fixture.dataSource.indexPath(for: anchor))
            let offset = try XCTUnwrap(fixture.list.layoutAttributesForItem(at: visible)).frame.minY - fixture.list.contentOffset.y
            fixture.input.isRefreshing = true
            fixture.submit()
            await settle(fixture.controller)
            XCTAssertEqual(fixture.list.contentOffset.y, 900, accuracy: 0.5, "Background refresh must not reveal by scrolling (gallery=\(gallery))")
            // A failed refresh retains posts; the following request must still be presentable.
            fixture.input.isRefreshing = false
            fixture.input.footer = .error
            fixture.input.appendFailure = .init(error: KotlinThrowable(message: "offline"), retry: {})
            fixture.submit()
            await settle(fixture.controller)
            XCTAssertFalse(fixture.refresh.isRefreshing)
            XCTAssertEqual(fixture.ids, (0..<60).map { fixture.prefix + "case-\($0)" })
            fixture.input.isRefreshing = true
            fixture.submit()
            await settle(fixture.controller)
            XCTAssertTrue(fixture.refresh.isRefreshing)
            fixture.input.items.insert(contentsOf: (100..<120).map { .post(makeRow($0, media: false)) }, at: 0)
            fixture.input.footer = .none
            fixture.input.isRefreshing = false
            fixture.submit()
            await settle(fixture.controller)
            let path = try XCTUnwrap(fixture.dataSource.indexPath(for: anchor))
            let newOffset = try XCTUnwrap(fixture.list.layoutAttributesForItem(at: path)).frame.minY - fixture.list.contentOffset.y
            XCTAssertEqual(newOffset, offset, accuracy: 0.5, "Retain the reported reading post \(anchor) (gallery=\(gallery))")
            XCTAssertEqual(fixture.ids, (Array(100..<120) + Array(0..<60)).map { fixture.prefix + "case-\($0)" })
            XCTAssertFalse(fixture.refresh.isRefreshing)
        }
    }

    func testCachedPositionRestoresWhileTheReadingSessionRefreshesInBothListStyles() async throws {
        for gallery in [false, true] {
            let fixture = try await Fixture(gallery: gallery, initialRefresh: true,
                position: TimelineReadingPosition(itemKey: "case-20", offset: -23.5, requestId: 1, latest: false))
            defer { fixture.close() }
            XCTAssertNil(fixture.reading.position, "Cached posts must restore without waiting for the network")
            let path = try XCTUnwrap(fixture.dataSource.indexPath(for: fixture.prefix + "case-20"))
            let frame = try XCTUnwrap(fixture.list.layoutAttributesForItem(at: path)).frame
            XCTAssertEqual(frame.minY - fixture.list.contentOffset.y, -23.5, accuracy: 0.5)
            XCTAssertTrue(fixture.refresh.isRefreshing)
            fixture.input.isRefreshing = false
            fixture.submit()
            await settle(fixture.controller)
            XCTAssertEqual(frame.minY - fixture.list.contentOffset.y, -23.5, accuracy: 0.5)
        }
    }

    func testPositionStillWaitsForPagingToCommitInBothListStyles() async throws {
        for gallery in [false, true] {
            let fixture = try await Fixture(gallery: gallery, initialRefresh: true, pagingRefreshing: true,
                position: TimelineReadingPosition(itemKey: "case-20", offset: -23.5, requestId: 1, latest: false))
            defer { fixture.close() }
            XCTAssertNotNil(fixture.reading.position)
            fixture.input.isPagingRefreshing = false
            fixture.submit()
            await settle(fixture.controller)
            XCTAssertNil(fixture.reading.position)
            let path = try XCTUnwrap(fixture.dataSource.indexPath(for: fixture.prefix + "case-20"))
            let frame = try XCTUnwrap(fixture.list.layoutAttributesForItem(at: path)).frame
            XCTAssertEqual(frame.minY - fixture.list.contentOffset.y, -23.5, accuracy: 0.5)
            XCTAssertTrue(fixture.refresh.isRefreshing)
        }
    }

    func testGalleryErrorEmptyAndFooterUpdatesCompleteRefreshAndCanRetry() async throws {
        let fixture = try await Fixture(gallery: true)
        defer { fixture.close() }
        for state in [TimelineContent.State.error, .empty, .loaded] {
            fixture.input.isRefreshing = true
            fixture.submit()
            await settle(fixture.controller)
            XCTAssertTrue(fixture.refresh.isRefreshing)
            fixture.input.state = state
            fixture.input.isRefreshing = false
            fixture.input.failure = .init(error: KotlinThrowable(message: "offline"), retry: {})
            fixture.input.footer = .error
            fixture.input.appendFailure = fixture.input.failure
            fixture.submit()
            await settle(fixture.controller)
            XCTAssertFalse(fixture.refresh.isRefreshing)
            XCTAssertEqual(fixture.ids.first, state == .error ? "__g_error__" : state == .empty ? "__g_empty__" : "g:case-0")
        }
        fixture.input.footer = .loading
        fixture.submit()
        await settle(fixture.controller)
        XCTAssertEqual(fixture.dataSource.snapshot().itemIdentifiers.last, "__g_fl__")
        fixture.input.footer = .end
        fixture.submit()
        await settle(fixture.controller)
        XCTAssertEqual(fixture.dataSource.snapshot().itemIdentifiers.last, "__g_fend__")
        fixture.input.items[0] = .post(makeRow(0, liked: true, media: false))
        fixture.submit()
        await settle(fixture.controller)
        XCTAssertEqual(fixture.ids.count, 60)
    }

    func testGalleryFailureRebindsRetryForUnchangedErrorRows() async throws {
        let fixture = try await Fixture(gallery: true)
        defer { fixture.close() }
        var retried = 0
        for footer in [false, true] {
            fixture.input.state = footer ? .loaded : .error
            fixture.input.footer = footer ? .error : .none
            for result in [1, 10] {
                let failure = TimelineContent.Failure(error: KotlinThrowable(message: "offline"), retry: { retried = result })
                fixture.input.failure = failure
                fixture.input.appendFailure = failure
                fixture.submit()
                await settle(fixture.controller)
            }
            let id = footer ? "__g_fe__" : "__g_error__"
            let path = try XCTUnwrap(fixture.dataSource.indexPath(for: id))
            fixture.list.scrollToItem(at: path, at: .bottom, animated: false)
            await settle(fixture.controller)
            let cell = try XCTUnwrap(fixture.list.cellForItem(at: path))
            let button = try XCTUnwrap(descendants(cell).compactMap { $0 as? UIButton }.first)
            button.sendActions(for: .touchUpInside)
            XCTAssertEqual(retried, 10, "The retry action must belong to the committed failure")
            retried = 0
        }
    }

    @MainActor
    private final class RefreshSource {
        let paging: PagingState<UiTimelineV2>
        let presenter: KotlinPresenter<TimelineItemPresenterState>
        var requests = 0
        var refreshing = false
        var completion: (@Sendable ((any Error)?) -> Void)?
        init(paging: PagingState<UiTimelineV2>, presenter: KotlinPresenter<TimelineItemPresenterState>) {
            self.paging = paging
            self.presenter = presenter
        }
        func publish() { presenter.state = ScreenState(source: self) }
        func begin(_ completion: @escaping @Sendable ((any Error)?) -> Void) {
            requests += 1
            self.completion = completion
            refreshing = true
            publish()
        }
        func finish() {
            refreshing = false
            publish()
            let callback = completion
            completion = nil
            callback?(nil)
        }
    }

    @MainActor
    private final class ScreenState: NSObject, TimelineItemPresenterState {
        let listState: PagingState<UiTimelineV2>
        let isRefreshing: Bool
        let readingState: (any TimelineReadingState)?
        var snapshot: TimelineSnapshot? { nil }
        private weak var source: RefreshSource?
        init(source: RefreshSource) {
            self.source = source
            listState = source.paging
            isRefreshing = source.refreshing
            let reading = ReadingState()
            reading.isRefreshing = source.refreshing
            readingState = reading
        }
        func onHomeEntered() {}
        func refreshSync() {}
        nonisolated func __refreshSuspend(completionHandler: @escaping @Sendable ((any Error)?) -> Void) {
            Task { @MainActor [weak self] in self?.source?.begin(completionHandler) }
        }
        nonisolated func __refreshAutomatically(completionHandler: @escaping @Sendable ((any Error)?) -> Void) {
            __refreshSuspend(completionHandler: completionHandler)
        }
    }

    @MainActor
    private final class ReadingState: NSObject, TimelineReadingState {
        var position: TimelineReadingPosition?
        var hasNewContent = false
        var isRefreshing = false
        var itemKey: String?
        var offset = 0.0
        func updateViewport(itemKey: String?, offset: Double, atTop: Bool, interacting: Bool) {
            self.itemKey = itemKey
            self.offset = offset
        }
        func positionRestored(requestId: Int64) { position = nil }
        func cancelRestoration() { position = nil }
        func savePosition() {}
        nonisolated func __refreshAutomatically(completionHandler: @escaping @Sendable ((any Error)?) -> Void) { completionHandler(nil) }
        nonisolated func __showLatest(completionHandler: @escaping @Sendable ((any Error)?) -> Void) { completionHandler(nil) }
    }

    @MainActor
    private final class Fixture {
        let controller: UIViewController
        let list: UICollectionView
        let window: TestWindow
        let gallery: Bool
        let reading = ReadingState()
        let prefix: String
        var dataSource: UICollectionViewDiffableDataSource<Int, String> { list.dataSource as! UICollectionViewDiffableDataSource<Int, String> }
        var ids: [String] { dataSource.snapshot().itemIdentifiers(inSection: gallery ? 0 : 1) }
        var refresh: UIRefreshControl { list.refreshControl! }
        func onRefresh(_ action: @escaping () async -> Void) {
            if let value = controller as? UIGalleryTimelineController { value.refreshCallback = action }
            else { (controller as! UITimelineCollectionViewController).refreshCallback = action }
        }
        var input = TimelineContent()
        init(gallery: Bool, initialRefresh: Bool = false, suppressInitial: Bool = false, pagingRefreshing: Bool = false,
             position: TimelineReadingPosition? = nil) async throws {
            self.gallery = gallery
            reading.position = position
            prefix = gallery ? "g:" : "t:"
            input.state = .loaded
            input.items = (0..<60).map { .post(makeRow($0, media: false)) }
            input.isRefreshing = initialRefresh
            input.isPagingRefreshing = pagingRefreshing
            if gallery {
                let value = UIGalleryTimelineController()
                value.readingState = reading
                value.suppressInitialRefreshIndicator = suppressInitial
                value.submit(input)
                controller = value
            } else {
                let value = UITimelineCollectionViewController(detailStatusKey: nil)
                value.readingState = reading
                value.suppressInitialRefreshIndicator = suppressInitial
                value.submit(input, columns: 1)
                controller = value
            }
            window = try TestWindow(controller: controller)
            list = try XCTUnwrap(controller.view.subviews.compactMap { $0 as? UICollectionView }.first)
            list.contentInsetAdjustmentBehavior = .never
            await settle(controller)
        }
        func submit() {
            if let value = controller as? UIGalleryTimelineController { value.submit(input) }
            else { (controller as! UITimelineCollectionViewController).submit(input, columns: 1) }
        }
        func scroll(_ y: CGFloat) {
            if let value = controller as? UITimelineCollectionViewController { value.restoreEffectiveContentOffset(y, animated: false) }
            else { list.setContentOffset(CGPoint(x: 0, y: y), animated: false) }
        }
        func beginInteraction() {
            if let value = controller as? UITimelineCollectionViewController { value.beginExternalScrollInteraction() }
            else { list.delegate?.scrollViewWillBeginDragging?(list) }
        }
        func endInteraction() {
            if let value = controller as? UITimelineCollectionViewController { value.endExternalScrollInteraction() }
            else { list.delegate?.scrollViewDidEndDragging?(list, willDecelerate: false) }
        }
        func close() { window.close() }
    }

    @MainActor
    private final class TestWindow {
        let window: UIWindow
        let previous: UIWindow?
        init(controller: UIViewController) throws {
            let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
            previous = scene.windows.first(where: \.isKeyWindow)
            window = UIWindow(windowScene: scene)
            window.rootViewController = controller
            window.makeKeyAndVisible()
        }
        func close() {
            window.isHidden = true
            window.rootViewController = nil
            previous?.makeKey()
        }
    }
}

@MainActor
private func settle(_ controller: UIViewController) async {
    for _ in 0..<40 {
        controller.view.setNeedsLayout()
        controller.view.layoutIfNeeded()
        try? await Task.sleep(for: .milliseconds(10))
    }
}

@MainActor
private func descendants(_ view: UIView) -> [UIView] { [view] + view.subviews.flatMap(descendants) }
