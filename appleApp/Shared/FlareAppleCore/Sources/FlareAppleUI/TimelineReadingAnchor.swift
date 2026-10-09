import SwiftUI

private struct TracksTimelineReadingPosition: EnvironmentKey {
    static let defaultValue = false
}

public extension EnvironmentValues {
    var tracksTimelineReadingPosition: Bool {
        get { self[TracksTimelineReadingPosition.self] }
        set { self[TracksTimelineReadingPosition.self] = newValue }
    }
}

public struct TimelineReadingFrames: PreferenceKey {
    public static let defaultValue: [String: CGRect] = [:]
    public static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) {
        value.merge(nextValue(), uniquingKeysWith: { _, new in new })
    }
}

private struct TimelineReadingAnchor: ViewModifier {
    let key: String?
    @Environment(\.tracksTimelineReadingPosition) private var enabled

    @ViewBuilder
    func body(content: Content) -> some View {
        if enabled, let key {
            content.id(key).background {
                GeometryReader { proxy in
                    Color.clear.preference(
                        key: TimelineReadingFrames.self,
                        value: [key: proxy.frame(in: .named("timeline-reading-viewport"))]
                    )
                }
            }
        } else {
            content
        }
    }
}

public extension View {
    func timelineReadingAnchor(_ key: String?) -> some View {
        modifier(TimelineReadingAnchor(key: key))
    }
}
