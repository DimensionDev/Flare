import SwiftUI
@preconcurrency import KotlinSharedUI

struct AllFeedScreen: View {
    @Environment(\.tabKey) private var tabKeyEnv
    @Environment(\.isActive) private var isActive
    @StateObject private var presenter: KotlinPresenter<BlueskyFeedsWithTabsPresenterState>
    private let accountType: AccountType
    
    init(accountType: AccountType) {
        self.accountType = accountType
        self._presenter = .init(wrappedValue: .init(presenter: BlueskyFeedsWithTabsPresenter(accountType: accountType)))
    }
    
    var body: some View {
        ScrollViewReader { proxy in
            List {
                Section {
                    PagingView(data: presenter.state.myFeeds) { item in
                        NavigationLink(
                            value: Route
                                .tabItem(
                                    Bluesky.FeedTabItem(account: accountType, uri: item.id, metaData: .init(title: TitleType.Text(content: item.title), icon: IconType.Material(icon: .feeds)))
                                )
                        ) {
                            UiListView(data: item)
                        }
                    } loadingContent: {
                        UiListPlaceholder()
                    }
                } header: {
                    Text("all_feeds_section_my_feeds")
                }
                .id("top")
                
                Section {
                    PagingView(data: presenter.state.popularFeeds) { pair in
                        if let item = pair.first {
                            NavigationLink(
                                value: Route
                                    .tabItem(
                                        Bluesky.FeedTabItem(account: accountType, uri: item.id, metaData: .init(title: TitleType.Text(content: item.title), icon: IconType.Material(icon: .feeds)))
                                    )
                            ) {
                                UiListView(data: item)
                            }
                        }
                    } loadingContent: {
                        UiListPlaceholder()
                    }
                } header: {
                    Text("all_feeds_section_explore_feeds")
                }

            }
            .onReceive(NotificationCenter.default.publisher(for: .scrollToTop)) { notification in
                let targetTab = notification.userInfo?["tab"] as? String
                if isActive && (targetTab == nil || targetTab == tabKeyEnv) {
                    withAnimation {
                        proxy.scrollTo("top", anchor: .top)
                    }
                }
            }
            .navigationTitle("all_feeds_title")
            .refreshable {
                try? await presenter.state.refreshSuspend()
            }
        }
    }
}
