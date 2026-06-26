import SwiftUI
@preconcurrency import KotlinSharedUI
import FlareAppleCore

public struct AllFeedScreen<Destination: Hashable>: View {
    @StateObject private var presenter: KotlinPresenter<BlueskyFeedsWithTabsPresenterState>
    private let accountType: AccountType
    private let timelineDestination: (UiTimelineTabItem) -> Destination
    
    public init(
        accountType: AccountType,
        timelineDestination: @escaping (UiTimelineTabItem) -> Destination
    ) {
        self.accountType = accountType
        self.timelineDestination = timelineDestination
        self._presenter = .init(wrappedValue: .init(presenter: BlueskyFeedsWithTabsPresenter(accountType: accountType)))
    }
    
    public var body: some View {
        List {
            Section {
                PagingView(data: presenter.state.myFeeds) { item in
                    NavigationLink(
                        value: timelineDestination(presenter.state.timelineTabItem(item: item))
                    ) {
                        UiListView(data: item)
                    }
                } loadingContent: {
                    UiListPlaceholder()
                }
            } header: {
                Text("all_feeds_section_my_feeds", bundle: FlareAppleUILocalization.bundle)
            }
            
            Section {
                PagingView(data: presenter.state.popularFeeds) { pair in
                    if let item = pair.first {
                        NavigationLink(
                            value: timelineDestination(presenter.state.timelineTabItem(item: item))
                        ) {
                            UiListView(data: item)
                        }
                    }
                } loadingContent: {
                    UiListPlaceholder()
                }
            } header: {
                Text("all_feeds_section_explore_feeds", bundle: FlareAppleUILocalization.bundle)
            }

        }
        .navigationTitle(Text("all_feeds_title", bundle: FlareAppleUILocalization.bundle))
        .refreshable {
            try? await presenter.state.refreshSuspend()
        }
    }
}
