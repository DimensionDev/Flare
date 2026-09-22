import XCTest
import UIKit
import SwiftUI
import CHTCollectionViewWaterfallLayout

@MainActor
final class TimelineCollectionViewTests: XCTestCase {
    func testSingleAndMultipleColumnsKeepTheSameReadingPosition() throws {
        let fixture = Fixture(width: 390, columns: 1)
        fixture.scroll(to: 4_000)
        let position = try fixture.readingPosition()

        fixture.resize(width: 900, columns: 3)
        try fixture.assertPosition(position)
        fixture.resize(width: 390, columns: 1)
        try fixture.assertPosition(position)
    }

    func testWidthChangesWithinTheSameColumnCountKeepPosition() throws {
        let fixture = Fixture(width: 800, columns: 2)
        fixture.scroll(to: 2_000)
        let position = try fixture.readingPosition()

        fixture.resize(width: 950, columns: 2)
        try fixture.assertPosition(position)
    }

    func testChangingTopInsetsKeepsPositionBelowTheBars() throws {
        let fixture = Fixture(width: 390, columns: 1)
        fixture.collectionView.contentInset.top = 88
        fixture.scroll(to: 3_000)
        let position = try fixture.readingPosition()

        fixture.collectionView.prepareForLayoutChange()
        fixture.collectionView.contentInset.top = 44
        fixture.resize(width: 900, columns: 3)
        try fixture.assertPosition(position)
    }

    func testTopStaysAtTopAfterResizing() {
        let fixture = Fixture(width: 390, columns: 1)
        fixture.collectionView.contentInset.top = 88
        fixture.scroll(to: -88)

        fixture.resize(width: 900, columns: 3)

        XCTAssertEqual(fixture.collectionView.contentOffset.y, -88, accuracy: 0.5)
    }

    func testScrollingAfterResizeReplacesTheOldReadingPosition() throws {
        let fixture = Fixture(width: 390, columns: 1)
        fixture.scroll(to: 3_000)
        fixture.resize(width: 900, columns: 3)
        fixture.scroll(to: 2_000)
        let position = try fixture.readingPosition()

        fixture.resize(width: 390, columns: 1)

        try fixture.assertPosition(position)
    }

    func testLaterHeightCorrectionsKeepTheReadingPosition() throws {
        let fixture = Fixture(width: 390, columns: 1)
        fixture.scroll(to: 3_000)
        let position = try fixture.readingPosition()
        fixture.resize(width: 900, columns: 3)

        fixture.extraHeight = 35
        fixture.collectionView.collectionViewLayout.invalidateLayout()
        fixture.settle()

        try fixture.assertPosition(position)
    }

    func testRemovedReadingItemFallsForwardToTheNextSurvivingItem() throws {
        let fixture = Fixture(width: 390, columns: 1)
        fixture.scroll(to: 3_000)
        let position = try fixture.readingPosition()
        fixture.collectionView.prepareForLayoutChange()
        var snapshot = fixture.dataSource.snapshot()
        snapshot.deleteItems([position.id])
        fixture.dataSource.apply(snapshot, animatingDifferences: false)
        fixture.settle()

        try fixture.assertPosition(Position(id: String(Int(position.id)! + 1), distance: position.distance))
    }

    func testReturningToAnotherTabRestoresItsItemAtTheNewWidth() throws {
        let store = TimelineScrollPositionStore()
        let first = Fixture(width: 390, columns: 1)
        first.scroll(to: 4_000)
        let expected = try first.readingPosition()
        store["account-a:home"] = first.collectionView.captureReadingPosition()
        let other = Fixture(width: 390, columns: 1)
        other.scroll(to: 1_000)
        store["account-a:bookmarks"] = other.collectionView.captureReadingPosition()

        let returning = Fixture(width: 900, columns: 3)
        returning.collectionView.restoreReadingPosition(try XCTUnwrap(store["account-a:home"]))
        returning.settle()
        try returning.assertPosition(expected)
        XCTAssertNil(store["account-b:home"])
    }

    func testPrependingItemsKeepsTheReadingItemInsteadOfItsIndex() throws {
        let fixture = Fixture(width: 700, columns: 2)
        fixture.scroll(to: 2_000)
        let position = try fixture.readingPosition()
        fixture.collectionView.prepareForSnapshotChange()
        var snapshot = fixture.dataSource.snapshot()
        snapshot.insertItems(["200", "201", "202"], beforeItem: "0")
        fixture.dataSource.apply(snapshot, animatingDifferences: false)
        fixture.settle()
        try fixture.assertPosition(position)
    }

    func testPrependingAtTheTopKeepsThePreviouslyVisibleItem() throws {
        let fixture = Fixture(width: 390, columns: 1)
        fixture.collectionView.contentInset.top = 88
        fixture.scroll(to: -88)
        let position = try fixture.readingPosition()

        fixture.collectionView.prepareForSnapshotChange()
        var snapshot = fixture.dataSource.snapshot()
        snapshot.insertItems(["200", "201"], beforeItem: "0")
        fixture.dataSource.apply(snapshot, animatingDifferences: false)
        fixture.settle()

        try fixture.assertPosition(position)
    }

    func testPrependingAfterResizingAtTheTopKeepsTheVisibleItem() throws {
        let fixture = Fixture(width: 390, columns: 1)
        fixture.resize(width: 700, columns: 2)
        let position = try fixture.readingPosition()

        fixture.collectionView.prepareForSnapshotChange()
        var snapshot = fixture.dataSource.snapshot()
        snapshot.insertItems(["200", "201", "202"], beforeItem: "0")
        fixture.dataSource.apply(snapshot, animatingDifferences: false)
        fixture.settle()

        try fixture.assertPosition(position)
    }

    func testPrependingAfterAnExplicitTopRestoreKeepsTheLoadedItem() throws {
        let fixture = Fixture(width: 390, columns: 1)
        fixture.scroll(to: 2_000)
        fixture.collectionView.restoreReadingPosition(.top)
        fixture.settle()
        XCTAssertEqual(fixture.collectionView.contentOffset.y, 0, accuracy: 0.5)
        let position = try fixture.readingPosition()

        for id in ["200", "201"] {
            fixture.collectionView.prepareForSnapshotChange()
            var snapshot = fixture.dataSource.snapshot()
            snapshot.insertItems([id], beforeItem: snapshot.itemIdentifiers[0])
            fixture.dataSource.apply(snapshot, animatingDifferences: false)
            fixture.settle()
            try fixture.assertPosition(position)
        }
    }

    func testResizingUsesOneLayoutInstanceEvenForSingleColumn() throws {
        let fixture = Fixture(width: 390, columns: 1)
        let layout = fixture.collectionView.collectionViewLayout
        fixture.scroll(to: 4_000)
        let position = try fixture.readingPosition()
        fixture.resize(width: 1_050, columns: 3)
        fixture.resize(width: 850, columns: 2)
        fixture.resize(width: 390, columns: 1)
        XCTAssertTrue(layout === fixture.collectionView.collectionViewLayout)
        try fixture.assertPosition(position)
    }

    func testRefreshKeepsItsIndicatorAndReadingItemThroughHeightAndSnapshotChanges() async throws {
        let fixture = Fixture(width: 390, columns: 1)
        let window = fixture.showInWindow()
        defer { window.isHidden = true }
        let view = fixture.collectionView
        view.setTopContentInset(52)
        fixture.scroll(to: -52)
        let position = try fixture.readingPosition()
        view.refreshControl = UIRefreshControl()

        view.beginRefreshing(revealingIndicator: true)
        try await Task.sleep(for: .milliseconds(80))
        view.prepareForLayoutChange()
        fixture.extraHeight = 35
        view.collectionViewLayout.invalidateLayout()
        // A repeated SwiftUI update must not overwrite UIKit's refresh inset.
        view.setTopContentInset(52)
        view.layoutIfNeeded()
        try await Task.sleep(for: .milliseconds(400))
        XCTAssertLessThan(view.contentOffset.y, -82)

        view.prepareForSnapshotChange()
        var snapshot = fixture.dataSource.snapshot()
        snapshot.insertItems(["200", "201"], beforeItem: "0")
        await fixture.dataSource.apply(snapshot, animatingDifferences: false)
        fixture.settle()
        try fixture.assertPosition(position)

        view.endRefreshing()
        try await Task.sleep(for: .milliseconds(400))
        fixture.settle()
        XCTAssertEqual(view.contentInset.top, 52, accuracy: 0.5)
        XCTAssertFalse(view.isPresentingRefresh)
        try fixture.assertPosition(position)
    }

    func testInsetsAndColumnsCanChangeWhileRefreshing() async throws {
        let fixture = Fixture(width: 390, columns: 1)
        let window = fixture.showInWindow()
        defer { window.isHidden = true }
        let view = fixture.collectionView
        view.setTopContentInset(52)
        fixture.scroll(to: -52)
        let position = try fixture.readingPosition()
        view.refreshControl = UIRefreshControl()
        view.beginRefreshing(revealingIndicator: true)
        let refreshInset = view.contentInset.top - 52

        view.prepareForLayoutChange()
        view.setTopContentInset(88)
        fixture.resize(width: 900, columns: 3)
        XCTAssertEqual(view.contentInset.top, 88 + refreshInset, accuracy: 0.5)
        try await Task.sleep(for: .milliseconds(400))
        try fixture.assertPosition(position)
        view.endRefreshing()
        try await Task.sleep(for: .milliseconds(400))
        XCTAssertEqual(view.contentInset.top, 88, accuracy: 0.5)
        try fixture.assertPosition(position)
    }

    func testFastRefreshAndUserScrollingDoNotRestoreAnOutdatedPosition() async throws {
        let fixture = Fixture(width: 390, columns: 1)
        let window = fixture.showInWindow()
        defer { window.isHidden = true }
        let view = fixture.collectionView
        view.setTopContentInset(52)
        fixture.scroll(to: -52)
        view.refreshControl = UIRefreshControl()
        view.beginRefreshing(revealingIndicator: true)
        try await Task.sleep(for: .milliseconds(50))
        view.endRefreshing()
        try await Task.sleep(for: .milliseconds(400))
        XCTAssertEqual(view.contentOffset.y, -52, accuracy: 0.5)

        view.beginRefreshing(revealingIndicator: true)
        try await Task.sleep(for: .milliseconds(50))
        view.interruptRefreshForScrolling()
        fixture.scroll(to: 1_400)
        view.endRefreshing()
        try await Task.sleep(for: .milliseconds(400))
        XCTAssertEqual(view.contentOffset.y, 1_400, accuracy: 0.5)
        XCTAssertEqual(view.contentInset.top, 52, accuracy: 0.5)
    }

    func testRefreshAwayFromTopAndCancellationKeepExplicitPositions() async throws {
        let fixture = Fixture(width: 390, columns: 1)
        let window = fixture.showInWindow()
        defer { window.isHidden = true }
        let view = fixture.collectionView
        view.setTopContentInset(52)
        fixture.scroll(to: 1_400)
        view.refreshControl = UIRefreshControl()
        view.beginRefreshing(revealingIndicator: true)
        try await Task.sleep(for: .milliseconds(400))
        XCTAssertEqual(view.contentOffset.y, 1_400, accuracy: 0.5)
        view.endRefreshing()
        try await Task.sleep(for: .milliseconds(400))
        XCTAssertEqual(view.contentOffset.y, 1_400, accuracy: 0.5)

        fixture.scroll(to: -52)
        view.beginRefreshing(revealingIndicator: true)
        try await Task.sleep(for: .milliseconds(50))
        view.cancelRefresh()
        fixture.scroll(to: 800)
        try await Task.sleep(for: .milliseconds(400))
        XCTAssertEqual(view.contentOffset.y, 800, accuracy: 0.5)
        XCTAssertEqual(view.contentInset.top, 52, accuracy: 0.5)
    }

    func testPullRefreshDoesNotSaveTheElasticDragDistance() async throws {
        let fixture = Fixture(width: 390, columns: 1)
        let window = fixture.showInWindow()
        defer { window.isHidden = true }
        let view = fixture.collectionView
        view.setTopContentInset(52)
        fixture.scroll(to: -52)
        let position = try fixture.readingPosition()
        view.refreshControl = UIRefreshControl()
        // UIKit starts a pull refresh before sending valueChanged to the controller.
        view.refreshControl?.beginRefreshing()
        view.setContentOffset(CGPoint(x: 0, y: -180), animated: false)
        view.beginRefreshing(revealingIndicator: false)
        view.endRefreshing()
        try await Task.sleep(for: .milliseconds(400))
        try fixture.assertPosition(position)
        XCTAssertEqual(view.contentInset.top, 52, accuracy: 0.5)
    }

    func testRefreshStartedBeforeWindowAttachmentRevealsItsIndicator() async throws {
        let fixture = Fixture(width: 390, columns: 1)
        let view = fixture.collectionView
        view.setTopContentInset(52)
        fixture.scroll(to: -52)
        view.frame = .zero
        view.refreshControl = UIRefreshControl()
        view.beginRefreshing(revealingIndicator: true)
        view.frame = CGRect(x: 0, y: 0, width: 390, height: 600)
        let window = fixture.showInWindow()
        defer { window.isHidden = true }
        try await Task.sleep(for: .milliseconds(400))
        XCTAssertLessThan(view.contentOffset.y, -82)
        XCTAssertTrue(view.refreshControl?.isRefreshing == true, "inset=\(view.contentInset.top), presenting=\(view.isPresentingRefresh)")
        view.endRefreshing()
        try await Task.sleep(for: .milliseconds(400))
        XCTAssertEqual(view.contentOffset.y, -52, accuracy: 0.5,
            "inset=\(view.contentInset.top), resting=\(view.restingAdjustedTopInset), presenting=\(view.isPresentingRefresh)")
    }

    func testNewRefreshCanReverseAnUnfinishedCollapse() async throws {
        let fixture = Fixture(width: 390, columns: 1)
        let window = fixture.showInWindow()
        defer { window.isHidden = true }
        let view = fixture.collectionView
        view.setTopContentInset(52)
        fixture.scroll(to: -52)
        view.refreshControl = UIRefreshControl()
        view.beginRefreshing(revealingIndicator: true)
        try await Task.sleep(for: .milliseconds(400))
        view.endRefreshing()
        try await Task.sleep(for: .milliseconds(50))
        view.beginRefreshing(revealingIndicator: true)
        try await Task.sleep(for: .milliseconds(400))
        XCTAssertLessThan(view.contentOffset.y, -82)
        XCTAssertEqual(view.contentInset.top - 52, view.refreshControl?.bounds.height ?? 0, accuracy: 0.5)
        view.endRefreshing()
        try await Task.sleep(for: .milliseconds(400))
        XCTAssertEqual(view.contentOffset.y, -52, accuracy: 0.5)
    }

    func testRefreshFinishedBeforeWindowAttachmentDoesNotStartLater() async throws {
        let fixture = Fixture(width: 390, columns: 1)
        let view = fixture.collectionView
        view.setTopContentInset(52)
        fixture.scroll(to: -52)
        view.frame = .zero
        view.refreshControl = UIRefreshControl()
        view.beginRefreshing(revealingIndicator: true)
        view.endRefreshing()
        view.frame = CGRect(x: 0, y: 0, width: 390, height: 600)
        let window = fixture.showInWindow()
        defer { window.isHidden = true }
        try await Task.sleep(for: .milliseconds(400))
        XCTAssertFalse(view.isPresentingRefresh)
        XCTAssertFalse(view.refreshControl?.isRefreshing == true)
        XCTAssertEqual(view.contentOffset.y, -52, accuracy: 0.5)
        XCTAssertEqual(view.contentInset.top, 52, accuracy: 0.5)
    }

    func testInitialRefreshCanRevealBeforeAnyReadingItemsExist() async throws {
        let view = TimelineCollectionView(frame: .zero, collectionViewLayout: UICollectionViewFlowLayout())
        view.contentInsetAdjustmentBehavior = .never
        view.setTopContentInset(52)
        view.refreshControl = UIRefreshControl()
        view.beginRefreshing(revealingIndicator: true)
        view.frame = CGRect(x: 0, y: 0, width: 390, height: 600)
        let window = UIWindow(frame: view.frame)
        let controller = UIViewController()
        window.rootViewController = controller
        controller.view.addSubview(view)
        window.makeKeyAndVisible()
        defer { window.isHidden = true }
        try await Task.sleep(for: .milliseconds(400))
        XCTAssertLessThan(view.contentOffset.y, -82)
        view.endRefreshing()
        try await Task.sleep(for: .milliseconds(400))
        XCTAssertEqual(view.contentOffset.y, -52, accuracy: 0.5)
    }

    func testAutomaticInsetsCountTheRefreshControlOnlyOnce() async throws {
        let fixture = Fixture(width: 390, columns: 1)
        let window = fixture.showInWindow()
        defer { window.isHidden = true }
        let view = fixture.collectionView
        view.alwaysBounceVertical = true
        view.contentInsetAdjustmentBehavior = .automatic
        window.rootViewController?.additionalSafeAreaInsets.top = 100
        view.setTopContentInset(52)
        window.layoutIfNeeded()
        fixture.settle()
        let restingInset = view.adjustedContentInset.top
        fixture.scroll(to: -restingInset)
        let control = UIRefreshControl()
        view.refreshControl = control

        view.beginRefreshing(revealingIndicator: true)
        try await Task.sleep(for: .milliseconds(400))
        fixture.settle()
        XCTAssertEqual(view.restingAdjustedTopInset, restingInset, accuracy: 0.5)
        let first = try XCTUnwrap(view.layoutAttributesForItem(at: IndexPath(item: 0, section: 0))).frame
        XCTAssertEqual(first.minY - view.contentOffset.y, restingInset + control.bounds.height, accuracy: 0.5)

        view.endRefreshing()
        try await Task.sleep(for: .milliseconds(400))
        fixture.settle()
        XCTAssertEqual(view.adjustedContentInset.top, restingInset, accuracy: 0.5)
        XCTAssertEqual(view.contentOffset.y, -restingInset, accuracy: 0.5)
    }

    func testNativeRefreshStartsWithAnimationsEnabled() async throws {
        final class RefreshControl: UIRefreshControl {
            var animationStates: [Bool] = []
            override func beginRefreshing() {
                animationStates.append(UIView.areAnimationsEnabled)
                super.beginRefreshing()
            }
        }
        let fixture = Fixture(width: 390, columns: 1)
        let window = fixture.showInWindow()
        defer { window.isHidden = true }
        let view = fixture.collectionView
        let control = RefreshControl()
        view.refreshControl = control
        UIView.performWithoutAnimation { view.beginRefreshing(revealingIndicator: true) }
        try await Task.sleep(for: .milliseconds(400))
        XCTAssertTrue(control.isRefreshing)
        XCTAssertFalse(control.animationStates.isEmpty)
        XCTAssertTrue(control.animationStates.allSatisfy { $0 })
        view.cancelRefresh()
    }

    func testAutomaticInsetsFollowSafeAreaAndPageChangesDuringRefresh() async throws {
        let fixture = Fixture(width: 390, columns: 1)
        let window = fixture.showInWindow()
        defer { window.isHidden = true }
        let view = fixture.collectionView
        view.alwaysBounceVertical = true
        view.contentInsetAdjustmentBehavior = .automatic
        window.rootViewController?.additionalSafeAreaInsets.top = 100
        view.setTopContentInset(52)
        window.layoutIfNeeded()
        fixture.settle()
        let originalInset = view.adjustedContentInset.top
        let originalSafeArea = view.safeAreaInsets.top
        fixture.scroll(to: -originalInset)
        let control = UIRefreshControl()
        view.refreshControl = control
        view.beginRefreshing(revealingIndicator: true)
        try await Task.sleep(for: .milliseconds(400))

        window.rootViewController?.additionalSafeAreaInsets.top = 130
        view.setTopContentInset(88)
        window.layoutIfNeeded()
        fixture.resize(width: 900, columns: 3)
        let expectedInset = originalInset + 36 + view.safeAreaInsets.top - originalSafeArea
        XCTAssertEqual(view.restingAdjustedTopInset, expectedInset, accuracy: 0.5)
        XCTAssertEqual(view.contentOffset.y, -expectedInset - control.bounds.height, accuracy: 0.5)
        view.endRefreshing()
        try await Task.sleep(for: .milliseconds(400))
        fixture.settle()
        XCTAssertEqual(view.contentOffset.y, -expectedInset, accuracy: 0.5)
    }

    func testLegacyRefreshNeverAcquiresAnAutomaticReadingAnchor() async throws {
        let fixture = Fixture(width: 390, columns: 2)
        let window = fixture.showInWindow()
        defer { window.isHidden = true }
        let view = fixture.collectionView
        view.preservesReadingPosition = false
        view.setTopContentInset(52)
        fixture.scroll(to: -52)
        view.refreshControl = UIRefreshControl()
        view.refreshControl?.beginRefreshing()
        try await Task.sleep(for: .milliseconds(350))
        // Profile media keeps UIKit's refresh and its existing scroll restoration.
        view.interruptRefreshForScrolling()
        fixture.scroll(to: 1_400)
        view.refreshControl?.endRefreshing()
        try await Task.sleep(for: .milliseconds(400))
        fixture.settle()
        XCTAssertFalse(view.isPresentingRefresh)
        XCTAssertFalse(view.hasReadingPosition)
        view.setContentOffset(CGPoint(x: 0, y: 1_600), animated: false)
        fixture.settle()
        XCTAssertEqual(view.contentOffset.y, 1_600, accuracy: 0.5)
    }

    func testPinnedHeaderSelectsTheFirstUncoveredItemWithoutChangingItsOffset() throws {
        let fixture = Fixture(width: 390, columns: 1)
        let view = fixture.collectionView
        view.setTopContentInset(52)
        view.readingTopOcclusion = { 40 }
        let first = try XCTUnwrap(view.layoutAttributesForItem(at: IndexPath(item: 0, section: 0)))
        fixture.scroll(to: first.frame.maxY - 10 - 52)
        let secondBefore = try XCTUnwrap(view.layoutAttributesForItem(at: IndexPath(item: 1, section: 0))).frame.minY - view.contentOffset.y
        XCTAssertEqual(view.captureReadingPosition()?.itemID, "1")
        view.prepareForLayoutChange()
        fixture.extraHeight = 100
        view.collectionViewLayout.invalidateLayout()
        fixture.settle()
        let secondAfter = try XCTUnwrap(view.layoutAttributesForItem(at: IndexPath(item: 1, section: 0))).frame.minY - view.contentOffset.y
        XCTAssertEqual(secondAfter, secondBefore, accuracy: 0.5)
    }

    func testQueryPositionsExpireWithoutEvictingVisitedTimelinesOrLiveTabs() {
        let store = TimelineScrollPositionStore()
        store["home"] = .item(id: "home-item", distanceFromTop: -20, itemOrder: ["home-item"])
        var query: NSObject? = NSObject()
        weak var weakQuery = query
        let position = TimelineCollectionView.ReadingPosition.item(id: "result", distanceFromTop: -10, itemOrder: ["result"])
        store.save(position, for: "query:posts", owner: query)
        store.save(position, for: "query:users", owner: query)
        XCTAssertEqual(store["query:posts"]?.itemID, "result")
        XCTAssertEqual(store["query:users"]?.itemID, "result")
        query = nil
        XCTAssertNil(weakQuery)
        XCTAssertNil(store["query:posts"])
        XCTAssertNil(store["query:users"])
        XCTAssertEqual(store["home"]?.itemID, "home-item")

        let activeQuery = NSObject()
        for index in 0..<100 {
            let expiredQuery = NSObject()
            store.save(position, for: "old-query-\(index)", owner: expiredQuery)
        }
        store.save(position, for: "active-query", owner: activeQuery)
        for index in 0..<100 { XCTAssertNil(store["old-query-\(index)"]) }
        XCTAssertEqual(store["active-query"]?.itemID, "result")
        XCTAssertEqual(store["home"]?.itemID, "home-item")
    }

    func testRefinedHeightDoesNotReplayAnUnreachableEstimatedOffset() throws {
        let fixture = Fixture(width: 390, columns: 1)
        let view = fixture.collectionView
        let frame = try XCTUnwrap(view.layoutAttributesForItem(at: IndexPath(item: 0, section: 0))).frame
        fixture.scroll(to: frame.height - 2)
        view.prepareForLayoutChange()
        fixture.extraHeight = -100
        view.collectionViewLayout.invalidateLayout()
        fixture.settle()
        let measuredPosition = try fixture.readingPosition()

        view.prepareForLayoutChange()
        fixture.extraHeight = -80
        view.collectionViewLayout.invalidateLayout()
        fixture.settle()
        try fixture.assertPosition(measuredPosition)
    }

    func testColumnThresholdIncludesInsetsAndSpacing() {
        XCTAssertEqual(TimelineColumnPolicy.adaptive.columnCount(for: 679), 1)
        XCTAssertEqual(TimelineColumnPolicy.adaptive.columnCount(for: 680), 2)
        XCTAssertEqual(TimelineColumnPolicy.adaptive.columnCount(for: 1_008), 3)
        XCTAssertEqual(TimelineColumnPolicy.single.columnCount(for: 1_400), 1)
        XCTAssertEqual(TimelineColumnPolicy.adaptive.columnCount(for: 0), 1)
    }

    func testHostedHeaderReportsAnAsynchronousHeightChange() {
        let header = TimelineHostedAccessoryView()
        header.frame = CGRect(x: 0, y: 0, width: 320, height: 44)
        header.update(AnyView(Text("Header").frame(maxWidth: .infinity)))
        header.layoutIfNeeded()
        var changes = 0
        header.onHeightChanged = { changes += 1 }
        header.update(AnyView(Text(String(repeating: "Long header content ", count: 40))))
        header.setNeedsLayout()
        header.layoutIfNeeded()
        XCTAssertGreaterThan(changes, 0)
    }

    private struct Position {
        let id: String
        let distance: CGFloat
    }

    private final class Fixture: NSObject, @preconcurrency CHTCollectionViewDelegateWaterfallLayout {
        let collectionView: TimelineCollectionView
        var dataSource: UICollectionViewDiffableDataSource<Int, String>!
        var columns: Int
        var extraHeight: CGFloat = 0

        init(width: CGFloat, columns: Int) {
            self.columns = columns
            collectionView = TimelineCollectionView(
                frame: CGRect(x: 0, y: 0, width: width, height: 600),
                collectionViewLayout: Self.layout(columns: columns)
            )
            super.init()
            collectionView.delegate = self
            collectionView.contentInsetAdjustmentBehavior = .never
            let registration = UICollectionView.CellRegistration<Cell, String> { cell, _, id in
                cell.index = Int(id)!
            }
            dataSource = UICollectionViewDiffableDataSource<Int, String>(collectionView: collectionView) { view, path, id in
                view.dequeueConfiguredReusableCell(using: registration, for: path, item: id)
            }
            collectionView.readingItemIDs = { [weak self] in self?.dataSource.snapshot().itemIdentifiers ?? [] }
            collectionView.readingItemID = { [weak self] in self?.dataSource.itemIdentifier(for: $0) }
            collectionView.readingIndexPath = { [weak self] in self?.dataSource.indexPath(for: $0) }
            var snapshot = NSDiffableDataSourceSnapshot<Int, String>()
            snapshot.appendSections([0])
            snapshot.appendItems((0..<120).map(String.init))
            dataSource.apply(snapshot, animatingDifferences: false)
            settle()
        }

        func scroll(to offset: CGFloat) {
            // The controller resets this when a scroll gesture or explicit jump begins.
            collectionView.resetReadingPosition()
            collectionView.setContentOffset(CGPoint(x: 0, y: offset), animated: false)
            settle()
        }

        func showInWindow() -> UIWindow {
            let window = UIWindow(frame: collectionView.frame)
            let controller = UIViewController()
            window.rootViewController = controller
            controller.view.addSubview(collectionView)
            window.makeKeyAndVisible()
            controller.view.layoutIfNeeded()
            settle()
            return window
        }

        func resize(width: CGFloat, columns: Int) {
            if self.columns != columns {
                collectionView.prepareForLayoutChange()
                self.columns = columns
                (collectionView.collectionViewLayout as! CHTCollectionViewWaterfallLayout).columnCount = columns
                collectionView.collectionViewLayout.invalidateLayout()
            }
            collectionView.frame.size.width = width
            settle()
        }

        func settle() {
            for _ in 0..<5 {
                collectionView.setNeedsLayout()
                collectionView.layoutIfNeeded()
                RunLoop.main.run(until: Date().addingTimeInterval(0.01))
            }
        }

        func readingPosition() throws -> Position {
            let top = collectionView.contentOffset.y + collectionView.adjustedContentInset.top
            let item = try XCTUnwrap(collectionView.indexPathsForVisibleItems.compactMap { path -> (String, CGRect)? in
                guard let id = dataSource.itemIdentifier(for: path),
                      let frame = collectionView.layoutAttributesForItem(at: path)?.frame,
                      frame.maxY > top else { return nil }
                return (id, frame)
            }.min { left, right in
                left.1.minY == right.1.minY ? left.1.minX < right.1.minX : left.1.minY < right.1.minY
            })
            return Position(id: item.0, distance: item.1.minY - top)
        }

        func assertPosition(_ position: Position, file: StaticString = #filePath, line: UInt = #line) throws {
            let path = try XCTUnwrap(dataSource.indexPath(for: position.id), file: file, line: line)
            let frame = try XCTUnwrap(collectionView.layoutAttributesForItem(at: path)?.frame, file: file, line: line)
            let top = collectionView.contentOffset.y + collectionView.adjustedContentInset.top
            XCTAssertEqual(frame.minY - top, position.distance, accuracy: 0.5, file: file, line: line)
        }

        func collectionView(_ view: UICollectionView, layout: UICollectionViewLayout, sizeForItemAt path: IndexPath) -> CGSize {
            let width = (view.bounds.width - CGFloat(columns - 1) * 8) / CGFloat(columns)
            return CGSize(width: width, height: Cell.height(index: Int(dataSource.itemIdentifier(for: path)!)!, width: width) + extraHeight)
        }

        static func layout(columns: Int) -> UICollectionViewLayout {
            let layout = CHTCollectionViewWaterfallLayout()
            layout.columnCount = columns
            layout.minimumColumnSpacing = 8
            layout.minimumInteritemSpacing = 2
            return layout
        }
    }

    private final class Cell: UICollectionViewCell {
        var index = 0

        static func height(index: Int, width: CGFloat) -> CGFloat {
            CGFloat(100 + index % 7 * 31) + ceil(800 / max(width, 1)) * 15
        }

        override func preferredLayoutAttributesFitting(_ attributes: UICollectionViewLayoutAttributes) -> UICollectionViewLayoutAttributes {
            let result = attributes.copy() as! UICollectionViewLayoutAttributes
            result.size.height = Self.height(index: index, width: result.size.width)
            return result
        }
    }
}
