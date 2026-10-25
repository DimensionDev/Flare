import FlareAppleCore
import SwiftUI

extension View {
    /// Caps single-column content to a readable width and centers it, so wide windows
    /// (e.g. full screen) don't stretch posts edge to edge. Grows with the text size.
    func readableWidth() -> some View {
        modifier(ReadableWidthModifier())
    }
}

private struct ReadableWidthModifier: ViewModifier {
    @Environment(\.fontScale) private var fontScale

    func body(content: Content) -> some View {
        content
            .frame(maxWidth: 680 * fontScale)
            .frame(maxWidth: .infinity)
    }
}
