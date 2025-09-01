import Kingfisher
import shared
import SwiftUI

struct AllFeedsView: View {
    @State private var presenter: PinnableTimelineTabPresenter
    @Environment(FlareRouter.self) private var router
    @Environment(\.appSettings) private var appSettings
    @Environment(FlareMenuState.self) private var menuState
    @State private var lastKnownItemCount: Int = 0
    @State private var currentUser: UiUserV2?
    @State private var isMissingFeedData: Bool = false
    private let accountType: AccountType
    @Environment(FlareTheme.self) private var theme

    @StateObject private var tabSettingStore: AppBarTabSettingStore

    init(accountType: AccountType) {
        presenter = .init(accountType: accountType)
        self.accountType = accountType

        let (user, accountTypeSpecific) = UserManager.shared.getCurrentUser()
        _currentUser = State(initialValue: user)

        // check if the current platform is Bluesky (only Bluesky has Feeds)
        var missingFeedData = true
        if let user {
            let platformTypeString = String(describing: user.platformType).lowercased()
            if platformTypeString == "bluesky" {
                missingFeedData = false
                FlareLog.debug("[AllFeedsView] current user platform: \(platformTypeString), supports Feeds")
            }
        }
        _isMissingFeedData = State(initialValue: missingFeedData)
        _tabSettingStore = StateObject(wrappedValue: AppBarTabSettingStore.shared)
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            if isMissingFeedData {
                notSupportedView.background(theme.secondaryBackgroundColor)
            } else {
                feedsListView(state).background(theme.secondaryBackgroundColor)
            }
        }
    }

    private var notSupportedView: some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle")
                .font(.largeTitle)
                .foregroundColor(.yellow)

            Text("Feeds are only supported in Bluesky")
                .font(.headline)

            Text("Your current account doesn't support feeds")
                .font(.subheadline)
                .foregroundColor(.gray)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding()
    }

    @ViewBuilder
    private func feedsListView(_ state: PinnableTimelineTabPresenterState) -> some View {
        switch onEnum(of: state.tabs) {
        case .loading:
            loadingFeedsView
        case let .success(tabsData):
            let feedTab = findFeedTab(in: tabsData.data)
            if let feedTab {
                switch onEnum(of: feedTab.data) {
                case .loading:
                    loadingFeedsView
                case let .success(feedsData):
                    List {
                        ForEach(0 ..< feedsData.itemCount, id: \.self) { index in
                            if feedsData.itemCount > index {
                                if let feed = feedsData.peek(index: Int32(index)) {
                                    EnhancedFeedRowView(
                                        list: feed,
                                        accountType: accountType,
                                        isPinned: tabSettingStore.pinnedListIds.contains(feed.id)
                                    )
                                    .onAppear {
                                        FlareLog.debug("Feed loading: itemCount=\(feedsData.itemCount), index=\(index), lastKnownItemCount=\(lastKnownItemCount)")
                                        feedsData.get(index: Int32(index))

                                        lastKnownItemCount = Int(feedsData.itemCount)
                                        FlareLog.debug("Feed updated: itemCount=\(feedsData.itemCount), index=\(index), lastKnownItemCount=\(lastKnownItemCount)")
                                    }
                                }
                            }
                        }
                        .scrollContentBackground(.hidden)
                        .listRowBackground(theme.primaryBackgroundColor)
                    }
                    .navigationTitle("Feeds")
                    .toolbar {
                        ToolbarItem(placement: .navigationBarTrailing) {
                            Text("Count: \(lastKnownItemCount)")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                case let .empty(emptyState):
                    VStack(spacing: 16) {
                        Text("NO FEEDS")
                            .font(.headline)

                        Button("Refresh") {
                            FlareHapticManager.shared.dataRefresh()
                            emptyState.refresh()
                        }
                        .buttonStyle(.bordered)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding()
                    .navigationTitle("Feeds")
                case let .error(errorState):
                    let errorMessage = (errorState.error as? KotlinThrowable)?.message ?? "UNKNOWN ERROR"
                    let error = NSError(domain: "AllFeedsView", code: 0, userInfo: [NSLocalizedDescriptionKey: errorMessage])
                    ErrorView(error: error) {
                        // Unable to refresh directly with PinnableTimelineTabPresenter
                    }
                    .navigationTitle("Feeds")
                }
            } else {
                VStack(spacing: 16) {
                    Text("NO FEEDS FOUND")
                        .font(.headline)

                    Text("Your account doesn't have any feeds")
                        .font(.subheadline)
                        .foregroundColor(.gray)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding()
                .navigationTitle("Feeds")
            }
        case .error:
            ErrorView(error: NSError(domain: "AllFeedsView", code: 1, userInfo: [NSLocalizedDescriptionKey: "Failed to load feeds"])) {
                // Unable to refresh directly
            }
            .navigationTitle("Feeds")
        }
    }

    // find the Feed type tab in Tabs
    private func findFeedTab(in tabs: ImmutableListWrapper<PinnableTimelineTabPresenterStateTab>) -> PinnableTimelineTabPresenterStateTab? {
        for i in 0 ..< tabs.size {
            if let tab = tabs.get(index: Int32(i)) as? PinnableTimelineTabPresenterStateTab {
                if tab is PinnableTimelineTabPresenterStateTabFeed {
                    return tab
                }
            }
        }
        return nil
    }

    // loading feeds view
    private var loadingFeedsView: some View {
        VStack {
            ForEach(0 ..< 5, id: \.self) { _ in
                ListRowSkeletonView()
                    .padding(.horizontal)
            }
        }
        .navigationTitle("Feeds")
    }
}

private struct EnhancedFeedRowView: View {
    let list: UiList
    @State private var isPinned: Bool
    @Environment(FlareRouter.self) private var router
    let accountType: AccountType

    init(list: UiList, accountType: AccountType, isPinned: Bool) {
        self.list = list
        self.accountType = accountType
        _isPinned = State(initialValue: isPinned)
    }

    var body: some View {
        ListItemRowView(
            list: list,
            isPinned: isPinned,
            showCreator: true,
            showMemberCount: false,
            defaultUser: nil,
            onTap: {
                router.navigate(to: .feedDetail(
                    accountType: accountType,
                    list: list,
                    defaultUser: nil
                ))
            },
            onPinTap: {
                isPinned.toggle()
                sendPinStatusToAppBarNotification()
            }
        )
    }

    private func sendPinStatusToAppBarNotification() {
        var listInfo: [String: Any] = [
            "listId": list.id,
            "listTitle": list.title,
            "isPinned": isPinned,
            "itemType": "feed"
        ]
        if let listIconUrl = list.avatar {
            listInfo["listIconUrl"] = listIconUrl
        }

        NotificationCenter.default.post(
            name: .listPinStatusChanged,
            object: nil,
            userInfo: listInfo
        )
    }
}
