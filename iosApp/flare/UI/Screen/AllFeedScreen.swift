import SwiftUI
@preconcurrency import KotlinSharedUI

struct AllFeedScreen: View {
    @StateObject private var presenter: KotlinPresenter<BlueskyFeedsWithTabsPresenterState>
    private let accountType: AccountType
    
    init(accountType: AccountType) {
        self.accountType = accountType
        self._presenter = .init(wrappedValue: .init(presenter: BlueskyFeedsWithTabsPresenter(accountType: accountType)))
    }
    
    var body: some View {
        List {
            Section {
                PagingView(data: presenter.state.myFeeds) { item in
                    NavigationLink(
                        value: Route
                            .tabItem(
                                Bluesky.FeedTabItem(account: accountType, uri: item.id, metaData: .init(title: TitleType.Text(content: item.title), icon: IconType.Material(icon: .feeds)))
                            )
                    ) {
                        Label {
                            Text(item.title)
                        } icon: {
                            if let image = item.avatar {
                                AvatarView(data: image)
                            } else {
                                Image("fa-list")
                            }
                        }
                    }
                }
            } header: {
                Text("all_feeds_section_my_feeds")
            }
            
            Section {
                PagingView(data: presenter.state.popularFeeds) { pair in
                    if let item = pair.first {
                        NavigationLink(
                            value: Route
                                .tabItem(
                                    Bluesky.FeedTabItem(account: accountType, uri: item.id, metaData: .init(title: TitleType.Text(content: item.title), icon: IconType.Material(icon: .feeds)))
                                )
                        ) {
                            Label {
                                Text(item.title)
                            } icon: {
                                if let image = item.avatar {
                                    AvatarView(data: image)
                                } else {
                                    Image("fa-list")
                                }
                            }
                        }
                    }
                }
            } header: {
                Text("all_feeds_section_explore_feeds")
            }

        }
        .navigationTitle("all_feeds_title")
        .refreshable {
            try? await presenter.state.refreshSuspend()
        }
    }
}
