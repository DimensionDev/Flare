import SwiftUI
import KotlinSharedUI

struct AdaptiveTimelineCard<Content: View>: View {
    @Environment(\.appearanceSettings.timelineDisplayMode) private var timelineDisplayMode
    @Environment(\.isMultipleColumn) private var isMultipleColumn
    let index: Int
    let totalCount: Int
    @ViewBuilder
    let content: () -> Content

    var body: some View {
        if isMultipleColumn || !(timelineDisplayMode == .plain) {
            ListCardView(index: index, totalCount: totalCount) {
                content()
            }
            .padding(.horizontal)
        } else {
            VStack(spacing: 0) {
                content()
                if totalCount <= 0 || index < totalCount - 1 {
                    Divider()
                }
            }
        }
    }
}
