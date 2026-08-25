import UIKit

enum MediaViewerDismissLayer {
    case media
    case bottomOverlay
}

enum MediaViewerDismissGesturePolicy {
    // UIPan reports points/s; 1,300 matches LazyPager's 1.3 points/ms sensitivity.
    static let velocityThreshold: CGFloat = 1_300
    // Android's Swiper dismisses a slow drag after half the page height.
    static let distanceThreshold: CGFloat = 0.5
    static let scrollEdgeTolerance: CGFloat = 1

    static func verticalOffset(
        for layer: MediaViewerDismissLayer,
        translationY: CGFloat
    ) -> CGFloat {
        switch layer {
        case .media:
            translationY
        case .bottomOverlay:
            0
        }
    }

    static func shouldRestoreTouchedScrollPosition(didDismiss: Bool) -> Bool {
        !didDismiss
    }

    static func shouldBegin(
        velocityX: CGFloat,
        velocityY: CGFloat,
        zoomScale: CGFloat?,
        minimumZoomScale: CGFloat?
    ) -> Bool {
        guard abs(velocityY) > abs(velocityX) else { return false }
        guard let zoomScale, let minimumZoomScale else { return true }
        return zoomScale <= minimumZoomScale + 0.001
    }

    @MainActor
    static func shouldTakePriority(
        over gestureRecognizer: UIGestureRecognizer,
        velocityY: CGFloat = 0
    ) -> Bool {
        guard let scrollView = gestureRecognizer.view as? UIScrollView else {
            return false
        }
        guard gestureRecognizer === scrollView.panGestureRecognizer else {
            return false
        }
        return shouldBeginDismissPan(in: scrollView, velocityY: velocityY)
    }

    @MainActor
    static func hasScrollableVerticalContent(_ scrollView: UIScrollView) -> Bool {
        guard scrollView.isScrollEnabled else { return false }
        let range = verticalScrollRange(for: scrollView)
        return range.maximum - range.minimum > scrollEdgeTolerance
    }

    @MainActor
    static func shouldBeginDismissPan(
        in scrollView: UIScrollView?,
        velocityY: CGFloat
    ) -> Bool {
        guard let scrollView,
              hasScrollableVerticalContent(scrollView) else {
            return true
        }

        let range = verticalScrollRange(for: scrollView)
        if velocityY > 0 {
            return scrollView.contentOffset.y <= range.minimum + scrollEdgeTolerance
        }
        if velocityY < 0 {
            return scrollView.contentOffset.y >= range.maximum - scrollEdgeTolerance
        }
        return false
    }

    static func shouldDismiss(
        translationY: CGFloat,
        velocityY: CGFloat,
        containerHeight: CGFloat
    ) -> Bool {
        guard containerHeight > 0 else { return false }
        return abs(velocityY) >= velocityThreshold ||
            abs(translationY) >= containerHeight * distanceThreshold
    }

    static func progress(translationY: CGFloat, containerHeight: CGFloat) -> CGFloat {
        guard containerHeight > 0 else { return 0 }
        return min(abs(translationY) / containerHeight, 1)
    }

    @MainActor
    private static func verticalScrollRange(
        for scrollView: UIScrollView
    ) -> (minimum: CGFloat, maximum: CGFloat) {
        let minimum = -scrollView.adjustedContentInset.top
        let maximum = max(
            minimum,
            scrollView.contentSize.height
                - scrollView.bounds.height
                + scrollView.adjustedContentInset.bottom
        )
        return (minimum, maximum)
    }
}
