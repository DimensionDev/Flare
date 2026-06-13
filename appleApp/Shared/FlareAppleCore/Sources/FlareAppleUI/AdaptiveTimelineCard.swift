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
        #if os(macOS)
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
        #else
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
        #endif
    }
}
