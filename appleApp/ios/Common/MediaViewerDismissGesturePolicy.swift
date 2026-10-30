import UIKit

enum MediaViewerDismissLayer {
    case media
    case bottomOverlay
}

struct MediaViewerDismissTransform: Equatable {
    var offset: CGSize = .zero
    var scale: CGFloat = 1
    /// Unit point in the full-page container that stays under the finger while scaling.
    var anchor = CGPoint(x: 0.5, y: 0.5)
}

enum MediaViewerDismissGesturePolicy {
    /// Fraction of the page height the media must be projected to travel.
    static let distanceThreshold: CGFloat = 0.25
    // UIScrollView's normal deceleration (0.998 per ms) coasts velocity × 0.499 s.
    static let projectionDuration: CGFloat = 0.499
    static let minimumScale: CGFloat = 0.6
    static let scrollEdgeTolerance: CGFloat = 1
    /// Movement that is clearly paging, so the dismiss pan can release the scroll view.
    static let horizontalYieldDistance: CGFloat = 10
    /// The dismiss pan does not share a drag. A chrome tap may still land with the double-tap zoom.
    static func allowsSimultaneousRecognition(chromeTap: Bool, otherIsTap: Bool) -> Bool {
        chromeTap && otherIsTap
    }

    @MainActor
    static func belongsToViewer(_ touchedView: UIView, viewer: UIView) -> Bool {
        // LazyPager embeds hosting views without attaching their controllers as children.
        // View containment still distinguishes media content from a separately presented sheet.
        touchedView.isDescendant(of: viewer)
    }

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

    /// Keep the zoom view where the finger landed. Writing its offset after the gesture jumps the image.
    static func shouldHoldTouchedScrollPosition(didDismiss: Bool) -> Bool {
        !didDismiss
    }

    static func shouldBegin(
        velocityX: CGFloat,
        velocityY: CGFloat,
        zoomScale: CGFloat?,
        minimumZoomScale: CGFloat?,
        translationX: CGFloat = 0,
        translationY: CGFloat = 0
    ) -> Bool {
        let horizontal: CGFloat
        let vertical: CGFloat
        if hypot(velocityX, velocityY) >= 1 {
            horizontal = velocityX
            vertical = velocityY
        } else {
            horizontal = translationX
            vertical = translationY
        }
        guard abs(vertical) > abs(horizontal) else { return false }
        guard let zoomScale, let minimumZoomScale else { return true }
        return zoomScale <= minimumZoomScale + 0.001
    }

    /// A horizontal drag is paging. Release it before waiting on a velocity sample.
    static func shouldYieldToHorizontalPan(translationX: CGFloat, translationY: CGFloat) -> Bool {
        let distance = hypot(translationX, translationY)
        return distance >= horizontalYieldDistance && abs(translationX) > abs(translationY) * 1.25
    }

    @MainActor
    static func delaysPanOutsideViewer(
        _ gestureRecognizer: UIGestureRecognizer,
        viewer: UIView
    ) -> Bool {
        guard gestureRecognizer is UIPanGestureRecognizer,
              let view = gestureRecognizer.view else {
            return false
        }
        if let scrollView = view as? UIScrollView,
           gestureRecognizer === scrollView.panGestureRecognizer {
            return false
        }
        return !view.isDescendant(of: viewer)
    }

    @MainActor
    static func touchesPager(_ view: UIView) -> Bool {
        var candidate: UIView? = view
        while let current = candidate {
            if current is UIScrollView { return true }
            candidate = current.superview
        }
        return false
    }

    static func shouldDiscardAnimatedContentInset(
        viewClassName: String,
        inheritedAnimationDuration: TimeInterval,
        runtimeClassName: String = ""
    ) -> Bool {
        guard inheritedAnimationDuration > 0 else { return false }
        return viewClassName.contains("ZoomableView") || runtimeClassName.contains("ZoomableView")
    }

    @MainActor
    static func shouldTakePriority(
        over gestureRecognizer: UIGestureRecognizer,
        velocityY: CGFloat = 0,
        translationY: CGFloat = 0
    ) -> Bool {
        guard let scrollView = gestureRecognizer.view as? UIScrollView else {
            return false
        }
        guard gestureRecognizer === scrollView.panGestureRecognizer else {
            return false
        }
        return shouldBeginDismissPan(
            in: scrollView,
            velocityY: velocityY,
            translationY: translationY
        )
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
        velocityY: CGFloat,
        translationY: CGFloat = 0
    ) -> Bool {
        guard let scrollView,
              hasScrollableVerticalContent(scrollView) else {
            return true
        }

        let vertical = velocityY != 0 ? velocityY : translationY
        let range = verticalScrollRange(for: scrollView)
        if vertical > 0 {
            return scrollView.contentOffset.y <= range.minimum + scrollEdgeTolerance
        }
        if vertical < 0 {
            return scrollView.contentOffset.y >= range.maximum - scrollEdgeTolerance
        }
        return false
    }

    static func transform(
        translation: CGPoint,
        startLocation: CGPoint,
        containerSize: CGSize
    ) -> MediaViewerDismissTransform {
        guard containerSize.width > 0, containerSize.height > 0 else {
            return MediaViewerDismissTransform()
        }
        let dragProgress = progress(translationY: translation.y, containerHeight: containerSize.height)
        return MediaViewerDismissTransform(
            offset: CGSize(width: translation.x, height: translation.y),
            scale: 1 - (1 - minimumScale) * dragProgress,
            anchor: CGPoint(
                x: startLocation.x / containerSize.width,
                y: startLocation.y / containerSize.height
            )
        )
    }

    static func projectedTranslationY(translationY: CGFloat, velocityY: CGFloat) -> CGFloat {
        translationY + velocityY * projectionDuration
    }

    static func shouldDismiss(
        translationY: CGFloat,
        velocityY: CGFloat,
        containerHeight: CGFloat
    ) -> Bool {
        guard containerHeight > 0 else { return false }
        let projected = projectedTranslationY(translationY: translationY, velocityY: velocityY)
        return abs(projected) >= containerHeight * distanceThreshold
    }

    static func dismissDirection(
        translationY: CGFloat,
        velocityY: CGFloat
    ) -> CGFloat {
        projectedTranslationY(translationY: translationY, velocityY: velocityY) < 0 ? -1 : 1
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
