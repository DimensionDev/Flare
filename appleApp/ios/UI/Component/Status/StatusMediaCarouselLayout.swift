import UIKit

/// Carousel item sizes depend on both dimensions of the collection's viewport.
final class StatusMediaCarouselLayout: UICollectionViewFlowLayout {
    override func shouldInvalidateLayout(forBoundsChange newBounds: CGRect) -> Bool {
        if collectionView?.bounds.size != newBounds.size { return true }
        return super.shouldInvalidateLayout(forBoundsChange: newBounds)
    }

    override func invalidationContext(forBoundsChange newBounds: CGRect) -> UICollectionViewLayoutInvalidationContext {
        let context = super.invalidationContext(forBoundsChange: newBounds)
        if collectionView?.bounds.size != newBounds.size,
           let flowContext = context as? UICollectionViewFlowLayoutInvalidationContext {
            // Generic invalidation can reuse delegate metrics from the previous
            // size. Offset-only changes must not remeasure every carousel item.
            flowContext.invalidateFlowLayoutDelegateMetrics = true
            flowContext.invalidateFlowLayoutAttributes = true
        }
        return context
    }
}
