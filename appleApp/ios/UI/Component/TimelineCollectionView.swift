import UIKit

/// Keeps a reading position across width changes and replacement layouts.
final class TimelineCollectionView: UICollectionView {
    var readingItemID: ((IndexPath) -> String?)?
    var readingIndexPath: ((String) -> IndexPath?)?
    var readingItemIDs: (() -> [String])?
    var readingTopInset: (() -> CGFloat)?
    var preservesReadingPosition = true {
        didSet {
            if !preservesReadingPosition {
                resetReadingPosition()
            }
        }
    }

    enum ReadingPosition {
        case top
        case item(id: String, distanceFromTop: CGFloat, itemOrder: [String])

        var itemID: String? {
            if case .item(let id, _, _) = self { return id }
            return nil
        }
    }

    private var readingPosition: ReadingPosition?
    private var isRestoringReadingPosition = false

    override var frame: CGRect {
        willSet {
            // Setting frame can resize bounds without calling its setter.
            if frame.size != newValue.size, bounds.width > 1, bounds.height > 1 {
                prepareForLayoutChange()
            }
        }
    }

    override var bounds: CGRect {
        willSet {
            if bounds.size != newValue.size, bounds.width > 1, bounds.height > 1 {
                prepareForLayoutChange()
            }
        }
    }

    func prepareForLayoutChange() {
        guard preservesReadingPosition,
              readingPosition == nil,
              !isRestoringReadingPosition,
              !isTracking, !isDragging, !isDecelerating else { return }

        readingPosition = captureReadingPosition()
    }

    func captureReadingPosition() -> ReadingPosition? {
        guard bounds.width > 1, bounds.height > 1 else { return nil }
        let viewportTop = contentOffset.y + (readingTopInset?() ?? adjustedContentInset.top)
        if contentOffset.y + adjustedContentInset.top <= 1 {
            return .top
        }
        let viewportBottom = contentOffset.y + bounds.height - adjustedContentInset.bottom
        let firstItem = indexPathsForVisibleItems.compactMap { indexPath -> (id: String, frame: CGRect)? in
            guard let id = readingItemID?(indexPath),
                  let frame = layoutAttributesForItem(at: indexPath)?.frame,
                  frame.maxY > viewportTop,
                  frame.minY < viewportBottom else { return nil }
            return (id, frame)
        }.min { lhs, rhs in
            if abs(lhs.frame.minY - rhs.frame.minY) > 0.5 {
                return lhs.frame.minY < rhs.frame.minY
            }
            return lhs.frame.minX < rhs.frame.minX
        }
        if let firstItem {
            return .item(
                id: firstItem.id,
                distanceFromTop: firstItem.frame.minY - viewportTop,
                itemOrder: readingItemIDs?() ?? [firstItem.id]
            )
        }
        return nil
    }

    func restoreReadingPosition(_ position: ReadingPosition) {
        readingPosition = position
        setNeedsLayout()
    }

    func resetReadingPosition() {
        readingPosition = nil
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        guard !isRestoringReadingPosition else { return }
        if isTracking || isDragging || isDecelerating {
            resetReadingPosition()
            return
        }
        guard let readingPosition else { return }

        isRestoringReadingPosition = true
        defer { isRestoringReadingPosition = false }

        let targetY: CGFloat
        switch readingPosition {
        case .top:
            targetY = -adjustedContentInset.top
        case .item(let id, let distanceFromTop, let itemOrder):
            let index = itemOrder.firstIndex(of: id) ?? 0
            let candidates = [id] + Array(itemOrder.dropFirst(index + 1)) + Array(itemOrder.prefix(index).reversed())
            guard let indexPath = candidates.lazy.compactMap({ self.readingIndexPath?($0) }).first,
                  let frame = collectionViewLayout.layoutAttributesForItem(at: indexPath)?.frame else {
                resetReadingPosition()
                return
            }
            // Resizing can make a card shorter; keep the reading item visible.
            let distance = max(distanceFromTop, 1 - frame.height)
            targetY = frame.minY - distance - (readingTopInset?() ?? adjustedContentInset.top)
        }
        let minimumY = -adjustedContentInset.top
        let maximumY = max(minimumY, contentSize.height - bounds.height + adjustedContentInset.bottom)
        let offsetY = min(max(targetY, minimumY), maximumY)
        if abs(contentOffset.y - offsetY) > 0.5 {
            setContentOffset(CGPoint(x: contentOffset.x, y: offsetY), animated: false)
        }
    }
}
