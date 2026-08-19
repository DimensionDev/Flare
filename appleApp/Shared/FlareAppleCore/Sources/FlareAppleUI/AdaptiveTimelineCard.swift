import SwiftUI
import KotlinSharedUI

#if os(macOS)
import AppKit
#endif

public struct AdaptiveTimelineCard<Content: View>: View {
    @Environment(\.timelineAppearance.timelineDisplayMode) private var timelineDisplayMode
    @Environment(\.isMultipleColumn) private var isMultipleColumn
    private let index: Int
    private let totalCount: Int
    private let content: () -> Content

    public init(
        index: Int,
        totalCount: Int,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.index = index
        self.totalCount = totalCount
        self.content = content
    }

    public var body: some View {
        if isMultipleColumn {
            ListCardView(index: index, totalCount: totalCount) {
                content()
            }
            .padding(.horizontal, 2)
            .padding(.vertical, 6)
        } else if !(timelineDisplayMode == .plain) {
            ListCardView(index: index, totalCount: totalCount) {
                content()
            }
            .padding(.horizontal, 16)
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
