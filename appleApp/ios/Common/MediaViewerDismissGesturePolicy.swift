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

    static let disablesSystemAnimationOnDismiss = true

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
    static func shouldTakePriority(over gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard let scrollView = gestureRecognizer.view as? UIScrollView else {
            return false
        }
        return gestureRecognizer === scrollView.panGestureRecognizer
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
}
