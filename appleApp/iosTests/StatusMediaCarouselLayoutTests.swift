import UIKit
import XCTest

@MainActor
final class StatusMediaCarouselLayoutTests: XCTestCase {
    func testResizeRequeriesDimensionsAndUpdatesVisibleCells() throws {
        let fixture = Fixture()
        for size in [CGSize(width: 430, height: 236), CGSize(width: 900, height: 400),
                     CGSize(width: 430, height: 73), CGSize(width: 430, height: 236)] {
            fixture.view.frame.size = size
            fixture.view.layoutIfNeeded()
            let attributes = try XCTUnwrap(fixture.view.layoutAttributesForItem(at: IndexPath(item: 0, section: 0)))
            XCTAssertEqual(attributes.size, fixture.itemSize)
            let cell = try XCTUnwrap(fixture.view.cellForItem(at: IndexPath(item: 0, section: 0)))
            XCTAssertEqual(cell.bounds.size, fixture.itemSize)
        }
    }

    func testHorizontalScrollingDoesNotInvalidateDelegateSizes() {
        let fixture = Fixture()
        let layout = fixture.view.collectionViewLayout
        var newBounds = fixture.view.bounds
        newBounds.origin.x = 50
        let context = layout.invalidationContext(forBoundsChange: newBounds) as! UICollectionViewFlowLayoutInvalidationContext
        XCTAssertFalse(context.invalidateFlowLayoutDelegateMetrics)
        let measurements = fixture.measurements
        fixture.view.contentOffset.x = 50
        fixture.view.layoutIfNeeded()
        XCTAssertEqual(fixture.measurements, measurements)
    }

    private final class Fixture: NSObject, UICollectionViewDataSource, UICollectionViewDelegateFlowLayout {
        let view: UICollectionView
        var measurements = 0
        var itemSize: CGSize { CGSize(width: min(view.bounds.height * 1.18, view.bounds.width * 0.9), height: view.bounds.height) }

        override init() {
            let layout = StatusMediaCarouselLayout()
            layout.scrollDirection = .horizontal
            layout.minimumLineSpacing = 4
            view = UICollectionView(frame: CGRect(x: 0, y: 0, width: 430, height: 236), collectionViewLayout: layout)
            super.init()
            view.contentInsetAdjustmentBehavior = .never
            view.register(UICollectionViewCell.self, forCellWithReuseIdentifier: "media")
            view.dataSource = self
            view.delegate = self
            view.reloadData()
            view.layoutIfNeeded()
        }

        func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int { 4 }
        func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
            collectionView.dequeueReusableCell(withReuseIdentifier: "media", for: indexPath)
        }
        func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout,
                            sizeForItemAt indexPath: IndexPath) -> CGSize {
            measurements += 1
            return itemSize
        }
    }
}
