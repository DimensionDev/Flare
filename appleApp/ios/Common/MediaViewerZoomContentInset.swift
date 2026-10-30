import UIKit

/// LazyPager animates `contentInset` for 0.3s on the first zoom-view layout.
/// That move is not driven by the finger, so apply the same inset immediately.
enum MediaViewerZoomContentInset {
    private static var installed = false
    private static var applying = false

    static func applyWithoutAnimation(_ inset: UIEdgeInsets, to scrollView: UIScrollView) {
        if applying {
            scrollView.flare_applyContentInset(inset)
            return
        }
        applying = true
        defer { applying = false }
        UIView.performWithoutAnimation {
            scrollView.flare_applyContentInset(inset)
        }
    }

    static func install() {
        guard !installed else { return }
        installed = true
        guard let original = class_getInstanceMethod(
            UIScrollView.self,
            #selector(setter: UIScrollView.contentInset)
        ),
        let replacement = class_getInstanceMethod(
            UIScrollView.self,
            #selector(UIScrollView.flare_applyContentInset(_:))
        ) else {
            installed = false
            return
        }
        method_exchangeImplementations(original, replacement)
    }
}

extension UIScrollView {
    @objc func flare_applyContentInset(_ inset: UIEdgeInsets) {
        let discardAnimation = MediaViewerDismissGesturePolicy.shouldDiscardAnimatedContentInset(
            viewClassName: String(describing: type(of: self)),
            inheritedAnimationDuration: UIView.inheritedAnimationDuration,
            runtimeClassName: NSStringFromClass(type(of: self))
        )
        if discardAnimation {
            MediaViewerZoomContentInset.applyWithoutAnimation(inset, to: self)
        } else {
            flare_applyContentInset(inset)
        }
    }
}
