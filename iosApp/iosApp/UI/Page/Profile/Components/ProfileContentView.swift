import SwiftUI
import shared

struct ProfileContentView: View {
    let tabs: [FLTabItem]
    @Binding var selectedTab: Int
    let refresh: () async -> Void
    let accountType: AccountType
    let userKey: MicroBlogKey?
    let tabStore: ProfileTabSettingStore
    
    var body: some View {
        TabView(selection: $selectedTab) {
            ForEach(Array(tabs.enumerated()), id: \.element.key) { index, tab in
//                if let mediaTab = tab as? FLProfileMediaTabItem {
//                    ProfileMediaListScreen(accountType: accountType, userKey: userKey)
//                        .tag(index)
//                } else
                if let timelineTab = tab as? FLTimelineTabItem,
                   let presenter = tabStore.getOrCreatePresenter(for: tab) {
                    TimelineView(
                        presenter: presenter,
                        refresh: refresh
                    )
                    .tag(index)
                }
            }
        }
        .onChange(of: selectedTab) { newIndex in
            if newIndex < tabs.count {
                let selectedTab = tabs[newIndex]
                tabStore.selectTab(selectedTab.key)
            }
        }
        .tabViewStyle(.page(indexDisplayMode: .never))
        .background(Colors.Background.swiftUIPrimary)
    }
}

private struct TimelineView: View {
    let presenter: TimelinePresenter?
    let refresh: () async -> Void
    
    var body: some View {
        if let presenter = presenter {
            ObservePresenter(presenter: presenter) { state in
                if let timelineState = state as? TimelineState {
                    List {
                        StatusTimelineComponent(
                            data: timelineState.listState,
                            detailKey: nil
                        )
                        .listRowBackground(Colors.Background.swiftUIPrimary)
                    }
                    .listStyle(.plain)
                    .scrollContentBackground(.hidden)
                    .refreshable {
                        await refresh()
                    }
                }
            }
        } else {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Colors.Background.swiftUIPrimary)
        }
    }
}
