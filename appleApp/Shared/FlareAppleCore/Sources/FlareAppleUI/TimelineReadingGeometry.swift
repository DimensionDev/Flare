import SwiftUI
import KotlinSharedUI

public struct TimelineReadingFrames: PreferenceKey {
    public static var defaultValue: [String: CGRect] { [:] }
    public static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) {
        value.merge(nextValue(), uniquingKeysWith: { _, new in new })
    }
}

public extension View {
    func timelineReadingItem(_ item: UiTimelineV2?) -> some View {
        modifier(TimelineReadingItemModifier(item: item))
    }
}

private struct TimelineReadingItemModifier: ViewModifier {
    @Environment(\.timelineReading) private var reading
    let item: UiTimelineV2?

    @ViewBuilder
    func body(content: Content) -> some View {
        if reading != nil, let item, !item.isPaginationGap {
            content.id(item.stableItemKey).background {
                GeometryReader { geometry in
                    Color.clear.preference(key: TimelineReadingFrames.self,
                        value: [item.stableItemKey: geometry.frame(in: .named("timeline-reading"))])
                }
            }
        } else {
            content
        }
    }
}
