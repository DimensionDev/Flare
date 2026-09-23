import SwiftUI
import FlareAppleUI
import KotlinSharedUI

struct TimelineListBackground: ViewModifier {
    let columnPolicy: TimelineColumnPolicy
    @Environment(\.timelineAppearance.timelineDisplayMode) private var displayMode

    func body(content: Content) -> some View {
        content.background {
            // Measure the content width before extending its background under the sidebar.
            GeometryReader { geometry in
                Color(uiColor: TimelineUIKitAppearance.backgroundColor(
                    displayMode: displayMode,
                    isMultipleColumn: columnPolicy.columnCount(for: geometry.size.width) > 1
                ))
                .ignoresSafeArea()
            }
        }
    }
}
