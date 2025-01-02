import SwiftUI
import shared

struct ProfileContentView: View {
    let tabs: [ProfileStateTab]
    @Binding var selectedTab: Int
    let refresh: () async throws -> Void
    let presenter: ProfilePresenter
    let accountType: AccountType
    let userKey: MicroBlogKey?
    let tabStore: ProfileTabStore
    
    private func handleTabSelection(_ tab: ProfileStateTab) {
        tabStore.updateCurrentPresenter(for: tab)
    }
    
    @ViewBuilder
    func tabContent(for tab: ProfileStateTab) -> some View {
        switch onEnum(of: tab) {
        case .timeline(let timeline):
            TimelineTabContent(
                timeline: timeline,
                refresh: refresh,
                tabStore: tabStore,
                tab: tab
            )
        case .media(let media):
            ProfileMediaListScreen(
                accountType: accountType,
                userKey: userKey
            )
        }
    }
    
    var body: some View {
        GeometryReader { geometry in
            TabView(selection: $selectedTab) {
                ForEach(Array(tabs.enumerated()), id: \.offset) { index, tab in
                    tabContent(for: tab)
                        .tag(index)
                        .onChange(of: selectedTab) { newValue in
                            if newValue == index {
                                handleTabSelection(tab)
                            }
                        }
                }
            }
            .frame(height: geometry.size.height)
            .tabViewStyle(.page(indexDisplayMode: .never))
        }
    }
}

private struct TimelineTabContent: View, Equatable {
    let timeline: ProfileStateTabTimeline
    let refresh: () async throws -> Void
    let tabStore: ProfileTabStore
    let tab: ProfileStateTab
    
    static func == (lhs: TimelineTabContent, rhs: TimelineTabContent) -> Bool {
        return lhs.timeline.data == rhs.timeline.data
    }
    
    var body: some View {
        List {
            StatusTimelineComponent(
                data: timeline.data,
                detailKey: nil
            )
            .listRowBackground(Colors.Background.swiftUIPrimary)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .refreshable {
            try? await refresh()
        }
        .onAppear {
            tabStore.updateCurrentPresenter(for: tab)
        }
    }
}
