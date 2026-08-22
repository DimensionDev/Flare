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
    @Namespace private var selectedTabIndicatorNamespace

    public init(tabs: [ProfileState.Tab], selectedTab: Binding<Int>) {
        self.tabs = tabs
        self._selectedTab = selectedTab
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
                            withAnimation(.spring(response: 0.25, dampingFraction: 0.85)) {
                                selectedTab = index
                            }
                        } label: {
                            Text(profileTabTitle(for: tabs[index]))
                                .foregroundStyle(
                                    selectedTab == index ? Color.primary : Color.secondary
                                )
                                .padding(.vertical, 12)
                                .overlay(alignment: .bottom) {
                                    if selectedTab == index {
                                        Capsule()
                                            .fill(Color.accentColor)
                                            .frame(height: 3)
                                            .matchedGeometryEffect(
                                                id: "selectedTabIndicator",
                                                in: selectedTabIndicatorNamespace
                                            )
                                    }
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
