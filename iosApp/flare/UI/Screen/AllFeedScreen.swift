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
                            .timeline(
                                presenter.state.timelineTabItem(item: item)
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
            
            Section {
                PagingView(data: presenter.state.popularFeeds) { pair in
                    if let item = pair.first {
                        NavigationLink(
                            value: Route
                                    .timeline(
                                    presenter.state.timelineTabItem(item: item)
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
        .navigationTitle("all_feeds_title")
        .refreshable {
            try? await presenter.state.refreshSuspend()
        }
    }
}
