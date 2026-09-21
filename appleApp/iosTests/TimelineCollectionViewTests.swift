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
