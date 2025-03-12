import Generated
import Kingfisher
import os
import shared
import SwiftUI

private let logger = Logger(subsystem: "com.flare.app", category: "AllListsView")

struct AllListsView: View {
    @State private var presenter: AllListPresenter
    @EnvironmentObject private var router: Router
    @Environment(\.appSettings) private var appSettings
    @State private var lastKnownItemCount: Int = 0
    @State private var currentUser: UiUserV2?
    @State private var isMastodonUser: Bool = false
    private let accountType: AccountType

    @StateObject private var tabSettingStore: AppBarTabSettingStore

    init(accountType: AccountType) {
        presenter = .init(accountType: accountType)
        self.accountType = accountType

        let user = UserManager.shared.getCurrentUser()
        _currentUser = State(initialValue: user)

        // 检查是否为Mastodon账户
        var isMastodon = false
        if let user {
            let platformTypeString = String(describing: user.platformType).lowercased()

            if platformTypeString == "mastodon" {
                isMastodon = true
                logger.debug("当前用户平台类型: \(platformTypeString), 是否Mastodon: \(isMastodon)")
            }
        }
        _isMastodonUser = State(initialValue: isMastodon)
        _tabSettingStore = StateObject(wrappedValue: AppBarTabSettingStore(accountType: accountType))
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            List {
                switch onEnum(of: state.items) {
                case .loading:
                    loadingListsView
                case let .success(successData):

                    ForEach(0 ..< successData.itemCount, id: \.self) { index in
                        if successData.itemCount > index {
                            if let list = successData.peek(index: Int32(index)) {
                                EnhancedListRowView(
                                    list: list,
                                    accountType: accountType,
                                    isPinned: tabSettingStore.pinnedListIds.contains(list.id), // 从 Store 获取 pin 状态
                                    defaultUser: isMastodonUser ? currentUser : nil
                                )
                                .onAppear {
                                    // 获取数据并触发加载
                                    print("🟢 列表加载: itemCount=\(successData.itemCount), index=\(index), lastKnownItemCount=\(lastKnownItemCount)")
                                    successData.get(index: Int32(index))

                                    lastKnownItemCount = Int(successData.itemCount)
                                    print("🟡 列表更新后: itemCount=\(successData.itemCount), index=\(index), lastKnownItemCount=\(lastKnownItemCount)")
                                }
                            }
                        }
                    }
                case let .empty(emptyState):
                    VStack(spacing: 16) {
                        Text("NO LIST")
                            .font(.headline)

                        Button("Refresh") {
                            emptyState.refresh()
                        }
                        .buttonStyle(.bordered)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding()
                case let .error(errorState):
                    let errorMessage = (errorState.error as? KotlinThrowable)?.message ?? "UNKNOWN ERROR"
                    let error = NSError(domain: "AllListsView", code: 0, userInfo: [NSLocalizedDescriptionKey: errorMessage])
                    ErrorView(error: error) {
                        state.refresh()
                    }
                }
            }
            .navigationTitle("List")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Text("Count: \(lastKnownItemCount)")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
        }
    }

    // 加载状态视图
    private var loadingListsView: some View {
        VStack {
            ForEach(0 ..< 5, id: \.self) { _ in
                ListRowSkeletonView()
                    .padding(.horizontal)
            }
        }
    }
}

private struct EnhancedListRowView: View {
    let list: UiList
    @State private var isPinned: Bool
    @EnvironmentObject private var router: Router
    @State private var navigateToDetail = false
    let accountType: AccountType
    let defaultUser: UiUserV2?

    init(list: UiList, accountType: AccountType, isPinned: Bool, defaultUser: UiUserV2? = nil) {
        self.list = list
        self.accountType = accountType
        _isPinned = State(initialValue: isPinned)
        self.defaultUser = defaultUser
    }

    var body: some View {
        ZStack {
            ListItemRowView(
                list: list,
                isPinned: isPinned,
                showCreator: true,
                showMemberCount: true,
                defaultUser: defaultUser,
                onTap: {
                    navigateToDetail = true
                },
                onPinTap: {
                    isPinned.toggle()
                    sendPinStatusToAppBarNotification()
                }
            )

            // 隐藏的导航链接
            NavigationLink(
                destination: ListDetailView(
                    list: list,
                    accountType: accountType,
                    defaultUser: defaultUser
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
