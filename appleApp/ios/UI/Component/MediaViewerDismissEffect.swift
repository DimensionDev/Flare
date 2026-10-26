import SwiftUI

struct MediaViewerDismissEffect: ViewModifier {
    // LazyPager hosts pages in separate SwiftUI trees. Reading the binding here
    // preserves the animation transaction when the dismiss gesture settles.
    @Binding var transform: MediaViewerDismissTransform

    func body(content: Content) -> some View {
        content
            // The gesture anchor is normalized to the full page, including media letterboxing.
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .scaleEffect(
                transform.scale,
                anchor: UnitPoint(x: transform.anchor.x, y: transform.anchor.y)
            )
            .offset(
                x: transform.offset.width,
                y: MediaViewerDismissGesturePolicy.verticalOffset(
                    for: .media,
                    translationY: transform.offset.height
                )
            )
    }
}
