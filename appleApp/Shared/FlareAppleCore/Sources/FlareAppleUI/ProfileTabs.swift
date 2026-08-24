import KotlinSharedUI
import SwiftUI
import FlareAppleCore

public struct ProfileTabPicker: View {
    private let tabs: [ProfileState.Tab]
    @Binding private var selectedTab: Int

    public init(tabs: [ProfileState.Tab], selectedTab: Binding<Int>) {
        self.tabs = tabs
        self._selectedTab = selectedTab
    }

    public var body: some View {
        Picker(selection: $selectedTab) {
            ForEach(0..<tabs.count, id: \.self) { index in
                Text(profileTabTitle(for: tabs[index]))
                    .tag(index)
            }
        } label: {
            EmptyView()
        }
    }
}

public struct ProfileTabBar: View {
    @Environment(\.timelineAppearance.timelineDisplayMode) private var timelineDisplayMode
    @Environment(\.isMultipleColumn) private var isMultipleColumn
    private let tabs: [ProfileState.Tab]
    @Binding private var selectedTab: Int
    private let selectionProgress: CGFloat?

    public init(
        tabs: [ProfileState.Tab],
        selectedTab: Binding<Int>,
        selectionProgress: CGFloat? = nil
    ) {
        self.tabs = tabs
        self._selectedTab = selectedTab
        self.selectionProgress = selectionProgress
    }

    public var body: some View {
        ZStack(alignment: .bottom) {
            if timelineDisplayMode == .plain && !isMultipleColumn {
                Divider()
            }

            ScrollView(.horizontal) {
                HStack {
                    ForEach(0..<tabs.count, id: \.self) { index in
                        Button {
                            if selectionProgress == nil {
                                withAnimation(.spring(response: 0.25, dampingFraction: 0.85)) {
                                    selectedTab = index
                                }
                            } else {
                                selectedTab = index
                            }
                        } label: {
                            let title = profileTabTitle(for: tabs[index])
                            Text(title)
                                .foregroundStyle(Color.secondary)
                                .overlay {
                                    Text(title)
                                        .foregroundStyle(Color.primary)
                                        .opacity(tabEmphasis(at: index))
                                        .accessibilityHidden(true)
                                }
                                .padding(.vertical, 12)
                                .anchorPreference(
                                    key: ProfileTabFramePreferenceKey.self,
                                    value: .bounds
                                ) { anchor in
                                    [index: anchor]
                                }
                                .padding(.horizontal, 8)
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(selectedTab == index ? [.isSelected] : [])
                    }
                }
                .padding(.horizontal, 8)
            }
            .scrollIndicators(.hidden)
        }
        .frame(maxWidth: .infinity)
        .overlayPreferenceValue(ProfileTabFramePreferenceKey.self) { anchors in
            GeometryReader { proxy in
                if let frame = indicatorFrame(anchors: anchors, proxy: proxy) {
                    Capsule()
                        .fill(Color.accentColor)
                        .frame(width: frame.width, height: 3)
                        .position(x: frame.midX, y: proxy.size.height - 1.5)
                }
            }
            .allowsHitTesting(false)
        }
        .clipped()
    }

    private var resolvedSelectionProgress: CGFloat {
        guard !tabs.isEmpty else { return 0 }
        return min(
            max(selectionProgress ?? CGFloat(selectedTab), 0),
            CGFloat(tabs.count - 1)
        )
    }

    private func tabEmphasis(at index: Int) -> CGFloat {
        max(1 - abs(resolvedSelectionProgress - CGFloat(index)), 0)
    }

    private func indicatorFrame(
        anchors: [Int: Anchor<CGRect>],
        proxy: GeometryProxy
    ) -> CGRect? {
        let progress = resolvedSelectionProgress
        let fromIndex = Int(floor(progress))
        let toIndex = Int(ceil(progress))
        guard let fromAnchor = anchors[fromIndex] else { return nil }
        let fromFrame = proxy[fromAnchor]
        guard toIndex != fromIndex, let toAnchor = anchors[toIndex] else {
            return fromFrame
        }

        let toFrame = proxy[toAnchor]
        let fraction = progress - CGFloat(fromIndex)
        return CGRect(
            x: fromFrame.minX + (toFrame.minX - fromFrame.minX) * fraction,
            y: fromFrame.minY + (toFrame.minY - fromFrame.minY) * fraction,
            width: fromFrame.width + (toFrame.width - fromFrame.width) * fraction,
            height: fromFrame.height + (toFrame.height - fromFrame.height) * fraction
        )
    }
}

private struct ProfileTabFramePreferenceKey: PreferenceKey {
    static let defaultValue: [Int: Anchor<CGRect>] = [:]

    static func reduce(
        value: inout [Int: Anchor<CGRect>],
        nextValue: () -> [Int: Anchor<CGRect>]
    ) {
        value.merge(nextValue(), uniquingKeysWith: { _, next in next })
    }
}

public struct ProfileTabsLoadingPlaceholder: View {
    @Environment(\.timelineAppearance.timelineDisplayMode) private var timelineDisplayMode
    @Environment(\.isMultipleColumn) private var isMultipleColumn

    private let placeholderTitles = [
        "Posts",
        "Posts and replies",
        "Reposts",
        "Highlights",
        "Media",
    ]

    public init() {}

    public var body: some View {
        ZStack(alignment: .bottom) {
            if timelineDisplayMode == .plain && !isMultipleColumn {
                Divider()
            }

            ScrollView(.horizontal) {
                HStack {
                    ForEach(placeholderTitles.indices, id: \.self) { index in
                        Text(verbatim: placeholderTitles[index])
                            .foregroundStyle(.secondary)
                            .padding(.vertical, 12)
                            .overlay(alignment: .bottom) {
                                if index == 0 {
                                    Capsule()
                                        .fill(.placeholder)
                                        .frame(height: 3)
                                }
                            }
                            .padding(.horizontal, 8)
                    }
                }
                .padding(.horizontal, 8)
            }
            .scrollIndicators(.hidden)
        }
        .frame(maxWidth: .infinity)
        .redacted(reason: .placeholder)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

public struct ProfileTimelineLoadingPlaceholder: View {
    private let count = 5

    public init() {}

    public var body: some View {
        LazyVStack(spacing: 2) {
            ForEach(0..<count, id: \.self) { index in
                AdaptiveTimelineCard(index: index, totalCount: count) {
                    TimelinePlaceholderView()
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                }
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

public func profileTabTitle(for tab: ProfileState.Tab) -> String {
    tab.name.text
}

public func profileTimelineID(for tab: ProfileState.Tab) -> String {
    tab.id
}
