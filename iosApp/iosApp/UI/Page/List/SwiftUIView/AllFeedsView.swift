import Generated
import Kingfisher
import os
import shared
import SwiftUI

// 导入FeedDetailView所在模块，如果需要的话

private let logger = Logger(subsystem: "com.flare.app", category: "AllFeedsView")

struct AllFeedsView: View {
    @State private var presenter: PinnableTimelineTabPresenter
//    @EnvironmentObject private var router: Router
    @Environment(\.appSettings) private var appSettings
    @State private var lastKnownItemCount: Int = 0
    @State private var currentUser: UiUserV2?
    @State private var isMissingFeedData: Bool = false
    private let accountType: AccountType

    @StateObject private var tabSettingStore: AppBarTabSettingStore

    init(accountType: AccountType) {
        presenter = .init(accountType: accountType)
        self.accountType = accountType

        let user = UserManager.shared.getCurrentUser()
        _currentUser = State(initialValue: user)

        // 检查当前平台是否是Bluesky (只有Bluesky有Feeds)
        var missingFeedData = true
        if let user {
            let platformTypeString = String(describing: user.platformType).lowercased()
            if platformTypeString == "bluesky" {
                missingFeedData = false
                logger.debug("当前用户平台类型: \(platformTypeString), 支持Feeds")
            }
        }
        _isMissingFeedData = State(initialValue: missingFeedData)
        _tabSettingStore = StateObject(wrappedValue: AppBarTabSettingStore.shared)
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            if isMissingFeedData {
                notSupportedView
            } else {
                feedsListView(state)
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
            // 查找Feed类型的Tab
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
                                        // 获取数据并触发加载
                                        print("🟢 Feed加载: itemCount=\(feedsData.itemCount), index=\(index), lastKnownItemCount=\(lastKnownItemCount)")
                                        feedsData.get(index: Int32(index))

                                        lastKnownItemCount = Int(feedsData.itemCount)
                                        print("🟡 Feed更新后: itemCount=\(feedsData.itemCount), index=\(index), lastKnownItemCount=\(lastKnownItemCount)")
                                    }
                                }
                            }
                        }
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

    // 从Tabs中找到Feed类型的Tab
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

    // 加载状态视图
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
//    @EnvironmentObject private var router: Router
    @State private var navigateToDetail = false
    let accountType: AccountType

    init(list: UiList, accountType: AccountType, isPinned: Bool) {
        self.list = list
        self.accountType = accountType
        _isPinned = State(initialValue: isPinned)
    }

    var body: some View {
        ZStack {
            ListItemRowView(
                list: list,
                isPinned: isPinned,
                showCreator: true,
                showMemberCount: false,
                defaultUser: nil,
                onTap: {
                    navigateToDetail = true
                },
                onPinTap: {
                    isPinned.toggle()
                    sendPinStatusToAppBarNotification()
                }
            )

            // 隐藏的导航链接 - 使用FeedDetailView而不是ListDetailView
            NavigationLink(
                destination: FeedDetailView(
                    list: list,
                    accountType: accountType,
                    defaultUser: nil
                ),
                isActive: $navigateToDetail
            ) {
                EmptyView()
            }
            .opacity(0)
            .frame(width: 0, height: 0)
        }
    }

    private func sendPinStatusToAppBarNotification() {
        var listInfo: [String: Any] = [
            "listId": list.id,
            "listTitle": list.title,
            "isPinned": isPinned,
            "itemType": "feed",
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
