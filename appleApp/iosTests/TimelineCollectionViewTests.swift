import XCTest
import UIKit
import SwiftUI
import CHTCollectionViewWaterfallLayoutObjC

@MainActor
final class TimelineCollectionViewTests: XCTestCase {
    func testMeasuredHeightRefreshesFollowingVisibleCellAttributes() throws {
        let fixture = Fixture(width: 390, columns: 1)
        let view = fixture.collectionView
        let layout = view.collectionViewLayout
        fixture.heightOverride = { _, _ in 240 }
        layout.invalidateLayout()
        fixture.settle()
        let following = IndexPath(item: 2, section: 0)
        XCTAssertNotNil(view.cellForItem(at: following))
        view.invalidateMeasuredHeights(at: [IndexPath(item: 1, section: 0)]) {
            fixture.heightOverride = { id, _ in id == 1 ? 150 : 240 }
            return true
        }
        let expected = try XCTUnwrap(layout.layoutAttributesForItem(at: following)).frame
        XCTAssertEqual(view.layoutAttributesForItem(at: following)?.frame, expected,
                       "UIKit must discard cached positions for following cells in the same layout transaction")
        XCTAssertEqual(view.cellForItem(at: following)?.frame, expected)
    }

    func testSingleColumnHeightInvalidationOnlySizesChangedItems() throws {
        let fixture = Fixture(width: 402, columns: 1, itemCount: 1_000)
        let layout = try XCTUnwrap(fixture.collectionView.collectionViewLayout as? TimelineWaterfallLayout)
        var snapshot = fixture.dataSource.snapshot()
        snapshot.appendSections([1, 2])
        snapshot.appendItems(["1000"], toSection: 2)
        fixture.dataSource.apply(snapshot, animatingDifferences: false)
        fixture.settle()
        fixture.scroll(to: 2_500)
        let changes = [7: CGFloat(90), 501: CGFloat(-50), 999: CGFloat(140)]
        fixture.heightOverride = { id, width in
            Cell.height(index: id, width: width) + (changes[id] ?? 0)
        }
        fixture.sizingQueries = 0
        layout.invalidateMeasuredItems(at: changes.keys.map { IndexPath(item: $0, section: 0) })
        fixture.settle()
        XCTAssertEqual(fixture.sizingQueries, changes.count,
                       "Measuring three cells must not query all 1,001 loaded items")

        let paths = (0..<1_000).map { IndexPath(item: $0, section: 0) } + [IndexPath(item: 0, section: 2)]
        let incremental = try paths.map { try XCTUnwrap(layout.layoutAttributesForItem(at: $0)).frame }
        let contentSize = layout.collectionViewContentSize
        let rect = CGRect(x: 0, y: 2_500, width: 402, height: 600)
        let visible = Set((layout.layoutAttributesForElements(in: rect) ?? []).map(\.indexPath))
        layout.invalidateLayout()
        fixture.settle()
        XCTAssertEqual(layout.collectionViewContentSize, contentSize)
        for (path, frame) in zip(paths, incremental) {
            XCTAssertEqual(layout.layoutAttributesForItem(at: path)?.frame, frame, "\(path)")
        }
        XCTAssertEqual(Set((layout.layoutAttributesForElements(in: rect) ?? []).map(\.indexPath)), visible)
    }

    func testMultiColumnHeightInvalidationReflowsColumnsWithoutSizingUnchangedItems() throws {
        let fixture = Fixture(width: 900, columns: 2)
        let layout = try XCTUnwrap(fixture.collectionView.collectionViewLayout as? TimelineWaterfallLayout)
        fixture.heightOverride = { id, width in Cell.height(index: id, width: width) + (id == 0 ? 300 : 0) }
        fixture.sizingQueries = 0
        layout.invalidateMeasuredItems(at: [IndexPath(item: 0, section: 0)])
        fixture.settle()
        XCTAssertEqual(fixture.sizingQueries, 1)
        let frames = (0..<120).map { layout.layoutAttributesForItem(at: IndexPath(item: $0, section: 0))?.frame }
        layout.invalidateLayout()
        fixture.settle()
        XCTAssertEqual((0..<120).map { layout.layoutAttributesForItem(at: IndexPath(item: $0, section: 0))?.frame }, frames)
    }

    func testIncrementalGeometryMatchesFullLayoutWithFractionalSizesAndSectionColumns() throws {
        for columns in [1, 2, 3] {
            for direction in [CHTCollectionViewWaterfallLayoutItemRenderDirection.shortestFirst, .leftToRight, .rightToLeft] {
                let fixture = Fixture(width: 901, columns: columns)
                let layout = try XCTUnwrap(fixture.collectionView.collectionViewLayout as? TimelineWaterfallLayout)
                fixture.sectionColumns = [2: 1]
                layout.itemRenderDirection = direction
                layout.sectionInset = UIEdgeInsets(top: 4.25, left: 17, bottom: 6.125, right: 19)
                layout.headerInset.top = 1.125
                layout.footerInset.top = 2.25
                layout.minimumInteritemSpacing = 0.75
                layout.minimumContentHeight = 1_000
                var overrides: [Int: CGFloat] = [:]
                fixture.heightOverride = { id, _ in overrides[id] ?? CGFloat(93 + id % 13 * 12) + 0.375 }
                var snapshot = fixture.dataSource.snapshot()
                snapshot.appendSections([1, 2])
                snapshot.appendItems(["120", "121"], toSection: 2)
                fixture.dataSource.apply(snapshot, animatingDifferences: false)
                fixture.settle()
                let paths = (0..<120).map { IndexPath(item: $0, section: 0) }
                    + [IndexPath(item: 0, section: 2), IndexPath(item: 1, section: 2)]
                for generation in 0..<3 {
                    overrides[7] = CGFloat(95 + generation * 91) + 0.625
                    overrides[33] = CGFloat(400 - generation * 80) + 0.125
                    overrides[120] = CGFloat(130 + generation * 23) + 0.875
                    let changed = [IndexPath(item: 7, section: 0), IndexPath(item: 33, section: 0), IndexPath(item: 0, section: 2)]
                    fixture.sizingQueries = 0
                    // Coalesce separate cell reports before the next layout pass.
                    for path in changed {
                        layout.invalidateMeasuredItems(at: [path])
                    }
                    fixture.settle()
                    XCTAssertEqual(fixture.sizingQueries, changed.count)
                    overrides[33]! += 19.125
                    layout.invalidateMeasuredItems(at: [IndexPath(item: 33, section: 0)])
                    fixture.settle()
                    XCTAssertEqual(fixture.sizingQueries, changed.count + 1)
                    let frames = paths.map { layout.layoutAttributesForItem(at: $0)?.frame }
                    let size = layout.collectionViewContentSize
                    let rects = stride(from: CGFloat(0), through: size.height, by: 421).map {
                        CGRect(x: 0, y: $0, width: 901, height: 600)
                    }
                    let visible = rects.map { Set((layout.layoutAttributesForElements(in: $0) ?? []).map(\.indexPath)) }
                    layout.invalidateLayout()
                    fixture.settle()
                    XCTAssertEqual(layout.collectionViewContentSize, size)
                    XCTAssertEqual(paths.map { layout.layoutAttributesForItem(at: $0)?.frame }, frames,
                                   "columns=\(columns), direction=\(direction), generation=\(generation)")
                    XCTAssertEqual(rects.map { Set((layout.layoutAttributesForElements(in: $0) ?? []).map(\.indexPath)) }, visible)
                }
            }
        }
    }

    func testSnapshotAndRotationSupersedeQueuedHeightInvalidations() throws {
        let fixture = Fixture(width: 390, columns: 1)
        let layout = try XCTUnwrap(fixture.collectionView.collectionViewLayout as? TimelineWaterfallLayout)
        layout.invalidateMeasuredItems(at: [IndexPath(item: 119, section: 0)])
        var snapshot = fixture.dataSource.snapshot()
        snapshot.deleteItems(["119"])
        snapshot.insertItems(["200"], beforeItem: "0")
        fixture.dataSource.apply(snapshot, animatingDifferences: false)
        fixture.settle()
        let paths = (0..<120).map { IndexPath(item: $0, section: 0) }
        let replacementFrames = paths.map { layout.layoutAttributesForItem(at: $0)?.frame }
        layout.invalidateLayout()
        fixture.settle()
        XCTAssertEqual(paths.map { layout.layoutAttributesForItem(at: $0)?.frame }, replacementFrames)
        fixture.resize(width: 901, columns: 3)
        let frames = paths.map { layout.layoutAttributesForItem(at: $0)?.frame }
        let size = layout.collectionViewContentSize
        layout.invalidateLayout()
        fixture.settle()
        XCTAssertEqual(paths.map { layout.layoutAttributesForItem(at: $0)?.frame }, frames)
        XCTAssertEqual(layout.collectionViewContentSize, size)
    }

    func testReorderingItemsAfterHeightRefinementMatchesFullLayout() throws {
        let fixture = Fixture(width: 901, columns: 3)
        let layout = fixture.collectionView.collectionViewLayout
        fixture.collectionView.invalidateMeasuredHeights(at: [IndexPath(item: 1, section: 0)]) {
            fixture.heightOverride = { id, width in Cell.height(index: id, width: width) + (id == 1 ? 220 : 0) }
            return true
        }
        var snapshot = fixture.dataSource.snapshot()
        snapshot.moveItem("1", afterItem: "77")
        fixture.dataSource.apply(snapshot, animatingDifferences: false)
        fixture.settle()
        let paths = (0..<120).map { IndexPath(item: $0, section: 0) }
        let frames = paths.map { layout.layoutAttributesForItem(at: $0)?.frame }
        layout.invalidateLayout()
        fixture.settle()
        XCTAssertEqual(paths.map { layout.layoutAttributesForItem(at: $0)?.frame }, frames)
    }

    func testMinimumContentHeightCanChangeAfterIncrementalLayout() throws {
        let fixture = Fixture(width: 390, columns: 2, itemCount: 3)
        let layout = try XCTUnwrap(fixture.collectionView.collectionViewLayout as? TimelineWaterfallLayout)
        fixture.collectionView.invalidateMeasuredHeights(at: [IndexPath(item: 1, section: 0)]) {
            fixture.heightOverride = { id, width in Cell.height(index: id, width: width) + (id == 1 ? 400 : 0) }
            return true
        }
        let naturalHeight = layout.collectionViewContentSize.height
        layout.minimumContentHeight = naturalHeight + 100
        XCTAssertEqual(layout.collectionViewContentSize.height, naturalHeight + 100)
        layout.minimumContentHeight = 0
        XCTAssertEqual(layout.collectionViewContentSize.height, naturalHeight)
    }

    func testHeightPublicationDuringTopRefreshCollapseKeepsReadingPosition() async throws {
        let fixture = Fixture(width: 402, columns: 1)
        let window = fixture.showInWindow()
        defer { window.isHidden = true }
        let view = fixture.collectionView
        view.alwaysBounceVertical = true
        view.contentInsetAdjustmentBehavior = .automatic
        window.rootViewController?.additionalSafeAreaInsets.top = 100
        view.setTopContentInset(39)
        window.layoutIfNeeded()
        fixture.settle()
        let restingInset = view.adjustedContentInset.top
        fixture.scroll(to: -restingInset)
        view.refreshControl = UIRefreshControl()
        view.beginRefreshing(revealingIndicator: true)
        try await Task.sleep(for: .milliseconds(400))
        fixture.settle()

        view.endRefreshing()
        try await Task.sleep(for: .milliseconds(30))
        XCTAssertLessThan(view.contentOffset.y, -view.adjustedContentInset.top - 1)
        XCTAssertTrue(view.shouldDeferSnapshotChanges)
        let oldOffset = view.contentOffset.y
        view.invalidateMeasuredHeights(at: [IndexPath(item: 0, section: 0)]) {
            fixture.heightOverride = { id, width in
                Cell.height(index: id, width: width) + (id == 0 ? 35 : 0)
            }
            return true
        }
        XCTAssertEqual(view.contentOffset.y, oldOffset, accuracy: 0.5, "Height publication must preserve the in-flight collapse position")
        try await Task.sleep(for: .milliseconds(400))
        fixture.settle()
        // Bookmark cleanup is dispatched outside the collection's layout pass.
        try await Task.sleep(for: .milliseconds(10))
        XCTAssertEqual(view.contentOffset.y, -restingInset, accuracy: 0.5)
        XCTAssertFalse(view.isPresentingRefresh)
        XCTAssertFalse(view.hasReadingPosition)
    }

    func testMeasuringAnotherColumnDoesNotMoveTheCurrentReadingItem() throws {
        let fixture = Fixture(width: 900, columns: 2)
        var otherColumnHeight: CGFloat = 100
        fixture.heightOverride = { index, _ in
            index == 0 ? 600 : (index == 1 ? otherColumnHeight : 100)
        }
        fixture.collectionView.collectionViewLayout.invalidateLayout()
        fixture.settle()
        fixture.scroll(to: 350)
        let position = try fixture.readingPosition()
        XCTAssertEqual(position.id, "0")
        fixture.collectionView.invalidateMeasuredHeights {
            otherColumnHeight += 41
            return true
        }
        fixture.settle()
        try fixture.assertPosition(position)
    }

    func testOffsetCompensationReusesGeometryButDoesNotHideTheNextInvalidation() throws {
        let fixture = Fixture(width: 390, columns: 1)
        let layout = try XCTUnwrap(fixture.collectionView.collectionViewLayout as? TimelineWaterfallLayout)
        fixture.sizingQueries = 0
        layout.readingOffset = { 150 }
        layout.prepare()
        fixture.settle()
        XCTAssertEqual(fixture.collectionView.contentOffset.y, 150, accuracy: 0.5)
        XCTAssertEqual(fixture.sizingQueries, 0, "An offset adjustment must reuse the committed geometry")
        layout.readingOffset = nil

        let before = try XCTUnwrap(layout.layoutAttributesForItem(at: IndexPath(item: 10, section: 0))).frame
        fixture.extraHeight = 20
        layout.invalidateLayout()
        fixture.settle()
        XCTAssertEqual(fixture.sizingQueries, 120, "A later height change still requires one complete layout")
        let after = try XCTUnwrap(layout.layoutAttributesForItem(at: IndexPath(item: 10, section: 0))).frame
        XCTAssertGreaterThan(after.minY, before.minY)
    }

    func testPrependResolvesTheViewportBeforeConfiguringCells() throws {
        let fixture = Fixture(width: 390, columns: 1)
        fixture.scroll(to: 2_500)
        let position = try fixture.readingPosition()
        let layout = try XCTUnwrap(fixture.collectionView.collectionViewLayout as? TimelineWaterfallLayout)
        layout.readingOffset = {
            guard let path = fixture.dataSource.indexPath(for: position.id),
                  let frame = layout.layoutAttributesForItem(at: path)?.frame else { return nil }
            return frame.minY - position.distance
        }
        defer { layout.readingOffset = nil }
        fixture.configuredItems = []
        var snapshot = fixture.dataSource.snapshot()
        snapshot.insertItems((-20..<0).map(String.init), beforeItem: "0")
        fixture.dataSource.apply(snapshot, animatingDifferences: false)
        fixture.settle()
        try fixture.assertPosition(position)
        XCTAssertNotNil(fixture.collectionView.cellForItem(at: try XCTUnwrap(fixture.dataSource.indexPath(for: position.id))))
        XCTAssertFalse(fixture.configuredItems.contains { $0.hasPrefix("-") },
                       "The old offset must not materialize prepended cells outside the final viewport")
    }

    func testRoundTripKeepsTheOriginalItemWhenAnotherColumnStartsEarlier() throws {
        let fixture = Fixture(width: 390, columns: 1)
        let frame = try XCTUnwrap(fixture.collectionView.layoutAttributesForItem(at: IndexPath(item: 1, section: 0))).frame
        fixture.scroll(to: frame.minY + 50)
        let position = try fixture.readingPosition()
        XCTAssertEqual(position.id, "1")

        fixture.resize(width: 900, columns: 3)
        try fixture.assertPosition(position)
        XCTAssertNotEqual(try fixture.readingPosition().id, position.id, "The visual first column is not the saved reading item")
        fixture.resize(width: 390, columns: 1)
        try fixture.assertPosition(position)
        XCTAssertFalse(fixture.collectionView.hasReadingPosition, "A completed rotation must not continuously enforce an offset")
    }

    func testDelayedMeasurementDoesNotLoseAnOffsetClampedByAnEstimate() throws {
        let fixture = Fixture(width: 390, columns: 1)
        fixture.heightOverride = { _, _ in 700 }
        fixture.collectionView.collectionViewLayout.invalidateLayout()
        fixture.settle()
        fixture.scroll(to: 700 + 400)
        let position = try fixture.readingPosition()

        fixture.collectionView.prepareForLayoutChange()
        fixture.collectionView.isReadingLayoutReady = { _ in false }
        fixture.heightOverride = { _, _ in 240 }
        fixture.resize(width: 600, columns: 1)
        XCTAssertTrue(fixture.collectionView.hasReadingPosition, "An estimate must not finish restoration")
        // A real cell can report its height several run-loop turns after resizing.
        fixture.heightOverride = { _, _ in 650 }
        fixture.collectionView.isReadingLayoutReady = { _ in true }
        fixture.collectionView.invalidateMeasuredHeights()
        fixture.settle()
        try fixture.assertPosition(position)
        XCTAssertFalse(fixture.collectionView.hasReadingPosition)

        fixture.collectionView.prepareForLayoutChange()
        fixture.heightOverride = { _, _ in 700 }
        fixture.resize(width: 390, columns: 1)
        try fixture.assertPosition(position)
    }

    func testActuallyShorterCardRetainsItsOriginalOffsetForTheReturnWidth() throws {
        let fixture = Fixture(width: 390, columns: 1)
        fixture.heightOverride = { _, width in width < 500 ? 700 : 200 }
        fixture.collectionView.collectionViewLayout.invalidateLayout()
        fixture.settle()
        fixture.scroll(to: 1_100)
        let position = try fixture.readingPosition()
        fixture.resize(width: 600, columns: 1)
        XCTAssertEqual(try fixture.readingPosition().id, position.id)
        XCTAssertFalse(fixture.collectionView.hasReadingPosition)
        fixture.resize(width: 390, columns: 1)
        try fixture.assertPosition(position)
    }

    func testScrollingSupersedesARestoreWaitingForMeasurements() throws {
        let fixture = Fixture(width: 390, columns: 1)
        fixture.scroll(to: 3_000)
        fixture.collectionView.isReadingLayoutReady = { _ in false }
        fixture.resize(width: 900, columns: 3)
        XCTAssertTrue(fixture.collectionView.hasReadingPosition)

        fixture.scroll(to: 2_000)
        let position = try fixture.readingPosition()
        fixture.collectionView.isReadingLayoutReady = { _ in true }
        fixture.collectionView.invalidateMeasuredHeights()
        fixture.settle()
        fixture.resize(width: 390, columns: 1)
        try fixture.assertPosition(position)
    }

    func testContentExpansionAfterReflowKeepsTheDisplayedOffset() throws {
        let fixture = Fixture(width: 390, columns: 1)
        fixture.heightOverride = { _, width in width < 500 ? 700 : 200 }
        fixture.collectionView.collectionViewLayout.invalidateLayout()
        fixture.settle()
        fixture.scroll(to: 1_100)
        let original = try fixture.readingPosition()
        fixture.resize(width: 600, columns: 1)
        let clamped = try fixture.readingPosition()

        // A local expansion after measurement has settled is a new content change,
        // not another step of the rotation that originally clamped the offset.
        fixture.heightOverride = { _, _ in 700 }
        fixture.collectionView.invalidateMeasuredHeights()
        fixture.settle()
        try fixture.assertPosition(clamped)
        fixture.resize(width: 390, columns: 1)
        try fixture.assertPosition(original)
    }

    func testTabToTopSupersedesARestoreWaitingForMeasurements() async throws {
        let fixture = Fixture(width: 390, columns: 1)
        let window = fixture.showInWindow()
        defer { window.isHidden = true }
        let view = fixture.collectionView
        view.setTopContentInset(88)
        fixture.scroll(to: 3_000)
        view.isReadingLayoutReady = { _ in false }
        fixture.resize(width: 700, columns: 2)
        XCTAssertTrue(view.hasReadingPosition)

        view.setContentOffset(CGPoint(x: 0, y: -88), animated: true)
        try await Task.sleep(for: .milliseconds(500))
        view.isReadingLayoutReady = { _ in true }
        view.invalidateMeasuredHeights()
        fixture.settle()
        // Yield the main-actor test task so the queued completion can run.
        try await Task.sleep(for: .milliseconds(20))
        XCTAssertEqual(view.contentOffset.y, -88, accuracy: 0.5)
        XCTAssertFalse(view.hasReadingPosition)
        fixture.resize(width: 390, columns: 1)
        XCTAssertEqual(view.contentOffset.y, -88, accuracy: 0.5)
    }

    func testPrependDuringDelayedRotationKeepsTheOriginalItem() throws {
        let fixture = Fixture(width: 390, columns: 1)
        fixture.scroll(to: 3_000)
        let position = try fixture.readingPosition()
        fixture.collectionView.isReadingLayoutReady = { _ in false }
        fixture.resize(width: 700, columns: 2)
        fixture.collectionView.prepareForSnapshotChange()
        var snapshot = fixture.dataSource.snapshot()
        snapshot.insertItems(["200", "201"], beforeItem: "0")
        fixture.dataSource.apply(snapshot, animatingDifferences: false)
        fixture.settle()
        XCTAssertTrue(fixture.collectionView.hasReadingPosition)

        fixture.collectionView.isReadingLayoutReady = { _ in true }
        fixture.collectionView.invalidateMeasuredHeights()
        fixture.settle()
        try fixture.assertPosition(position)
        XCTAssertFalse(fixture.collectionView.hasReadingPosition)
        fixture.resize(width: 390, columns: 1)
        try fixture.assertPosition(position)
    }

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
        fixture.collectionView.invalidateMeasuredHeights()
        fixture.settle()

        try fixture.assertPosition(position)
    }

    func testPublishingMeasuredHeightsCapturesTheOldLayoutBeforeCommit() throws {
        let fixture = Fixture(width: 390, columns: 1)
        fixture.scroll(to: 3_000)
        let path = try XCTUnwrap(fixture.collectionView.indexPathsForVisibleItems
            .sorted().first { fixture.collectionView.layoutAttributesForItem(at: $0)!.frame.minY >= 3_000 })
        let id = try XCTUnwrap(fixture.dataSource.itemIdentifier(for: path))
        let offset = try XCTUnwrap(fixture.collectionView.layoutAttributesForItem(at: path)).frame.minY - 3_000
        fixture.collectionView.invalidateMeasuredHeights {
            fixture.extraHeight = 224
            return true
        }
        fixture.settle()
        try fixture.assertPosition(Position(id: id, distance: offset))
    }

    func testDiscardedMeasurementsAllowThePendingReadingPositionToFinish() {
        let fixture = Fixture(width: 390, columns: 1)
        fixture.scroll(to: 3_000)
        var ready = false
        fixture.collectionView.isReadingLayoutReady = { _ in ready }
        fixture.collectionView.prepareForLayoutChange(preferringVisibleTop: true)
        XCTAssertTrue(fixture.collectionView.hasReadingPosition)
        fixture.collectionView.invalidateMeasuredHeights {
            ready = true
            return false
        }
        fixture.collectionView.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.01))
        XCTAssertFalse(fixture.collectionView.hasReadingPosition)
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

    func testReplacingEveryItemDuringRefreshReturnsBelowTheTopInset() async throws {
        let fixture = Fixture(width: 390, columns: 1)
        let window = fixture.showInWindow()
        defer { window.isHidden = true }
        let view = fixture.collectionView
        view.setTopContentInset(74)
        fixture.scroll(to: -74)
        view.refreshControl = UIRefreshControl()
        view.beginRefreshing(revealingIndicator: true)
        try await Task.sleep(for: .milliseconds(400))

        view.prepareForSnapshotChange()
        var snapshot = NSDiffableDataSourceSnapshot<Int, String>()
        snapshot.appendSections([0])
        snapshot.appendItems((200..<320).map(String.init))
        await fixture.dataSource.apply(snapshot, animatingDifferences: false)
        fixture.settle()
        view.endRefreshing()
        try await Task.sleep(for: .milliseconds(500))
        fixture.settle()

        XCTAssertEqual(view.contentOffset.y, -74, accuracy: 0.5)
        XCTAssertEqual(try fixture.readingPosition().id, "200")
        XCTAssertEqual(try fixture.readingPosition().distance, 0, accuracy: 0.5)
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

    func testCancellingRefreshCollapseDoesNotResumeOrHoldSnapshots() async throws {
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
        view.cancelRefresh()
        fixture.scroll(to: 800)
        try await Task.sleep(for: .milliseconds(400))

        XCTAssertEqual(view.contentOffset.y, 800, accuracy: 0.5)
        XCTAssertFalse(view.isPresentingRefresh)
        XCTAssertFalse(view.shouldDeferSnapshotChanges)
        XCTAssertFalse(view.hasReadingPosition)
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

    func testMeasuringAPartlyHiddenEstimateKeepsTheNextVisibleTop() throws {
        let fixture = Fixture(width: 390, columns: 1)
        let view = fixture.collectionView
        fixture.heightOverride = { _, _ in 240 }
        view.collectionViewLayout.invalidateLayout()
        fixture.settle()
        let frame = try XCTUnwrap(view.layoutAttributesForItem(at: IndexPath(item: 20, section: 0))).frame
        fixture.scroll(to: frame.minY + 200)
        let next = IndexPath(item: 21, section: 0)
        let screenY = try XCTUnwrap(view.layoutAttributesForItem(at: next)).frame.minY - view.contentOffset.y

        fixture.heightOverride = { id, _ in id == 20 ? 100 : 240 }
        view.invalidateMeasuredHeights()
        fixture.settle()

        XCTAssertEqual(try XCTUnwrap(view.layoutAttributesForItem(at: next)).frame.minY - view.contentOffset.y,
                       screenY, accuracy: 0.5)
    }

    func testMeasuringACardFillingTheViewportKeepsThatItemVisible() throws {
        let fixture = Fixture(width: 390, columns: 1)
        let view = fixture.collectionView
        fixture.heightOverride = { _, _ in 1_200 }
        view.collectionViewLayout.invalidateLayout()
        fixture.settle()
        fixture.scroll(to: 400)

        fixture.heightOverride = { _, _ in 200 }
        view.invalidateMeasuredHeights()
        fixture.settle()

        XCTAssertEqual(try fixture.readingPosition().id, "0")
        let frame = try XCTUnwrap(view.layoutAttributesForItem(at: IndexPath(item: 0, section: 0))).frame
        XCTAssertGreaterThan(frame.maxY, view.contentOffset.y)
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
        var sectionColumns: [Int: Int] = [:]
        var extraHeight: CGFloat = 0
        var heightOverride: ((Int, CGFloat) -> CGFloat)?
        var sizingQueries = 0
        var configuredItems: [String] = []

        init(width: CGFloat, columns: Int, itemCount: Int = 120) {
            self.columns = columns
            collectionView = TimelineCollectionView(
                frame: CGRect(x: 0, y: 0, width: width, height: 600),
                collectionViewLayout: Self.layout(columns: columns)
            )
            super.init()
            collectionView.delegate = self
            collectionView.contentInsetAdjustmentBehavior = .never
            let registration = UICollectionView.CellRegistration<Cell, String> { [weak self] cell, _, id in
                self?.configuredItems.append(id)
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
            snapshot.appendItems((0..<itemCount).map(String.init))
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
                collectionView.prepareForGeometryChange()
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
            sizingQueries += 1
            let width = (view.bounds.width - CGFloat(columns - 1) * 8) / CGFloat(columns)
            let index = Int(dataSource.itemIdentifier(for: path)!)!
            return CGSize(width: width, height: heightOverride?(index, width) ?? (Cell.height(index: index, width: width) + extraHeight))
        }

        func collectionView(_ view: UICollectionView, layout: UICollectionViewLayout, columnCountForSection section: Int) -> Int {
            sectionColumns[section] ?? columns
        }

        func scrollViewDidEndScrollingAnimation(_ scrollView: UIScrollView) {
            collectionView.endProgrammaticScrolling()
        }

        static func layout(columns: Int) -> UICollectionViewLayout {
            let layout = TimelineWaterfallLayout()
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
