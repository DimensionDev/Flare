import KotlinSharedUI
import SwiftUI

struct AdaptiveTimelineCard<Content: View>: View {
    @Environment(\.timelineAppearance.timelineDisplayMode) private var timelineDisplayMode
    @Environment(\.isMultipleColumn) private var isMultipleColumn

    let index: Int
    let totalCount: Int
    @ViewBuilder let content: () -> Content

    var body: some View {
        if isMultipleColumn || timelineDisplayMode == .card {
            content()
                .background(Color(nsColor: .controlBackgroundColor))
                .clipShape(RoundedRectangle(cornerRadius: 18))
                .padding(.horizontal, isMultipleColumn ? 6 : 12)
                .padding(.vertical, 6)
        } else {
            VStack(spacing: 0) {
                content()
                if index < totalCount - 1 {
                    Divider()
                }
            }
        }
    }
}
