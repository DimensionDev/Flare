import Generated
import Kingfisher
import os
import shared
import SwiftUI

private let logger = Logger(subsystem: "com.flare.app", category: "AllListsView")

struct AllListsView: View {
    @State private var presenter: AllListPresenter
    @EnvironmentObject private var router: FlareRouter
    @Environment(\.appSettings) private var appSettings
    @EnvironmentObject private var appState: FlareAppState
    @State private var lastKnownItemCount: Int = 0
    @State private var currentUser: UiUserV2?
    @State private var isMastodonUser: Bool = false
    private let accountType: AccountType
    @Environment(FlareTheme.self) private var theme

    @StateObject private var tabSettingStore: AppBarTabSettingStore

    init(accountType: AccountType) {
        presenter = .init(accountType: accountType)
        self.accountType = accountType

        //  let user ,  accountTypeSpecific = UserManager.shared.getCurrentUser()
        let result = UserManager.shared.getCurrentUser()
        let user: UiUserV2? = result.0

//        _currentUser = State(initialValue: user)

        // check if the user is a Mastodon account
        var isMastodon = false
        if let user {
            let platformTypeString = String(describing: user.platformType).lowercased()

            if platformTypeString == "mastodon" {
                isMastodon = true
                logger.debug("current user platform: \(platformTypeString), is Mastodon: \(isMastodon)")
            }
        }
        _isMastodonUser = State(initialValue: isMastodon)
        _tabSettingStore = StateObject(wrappedValue: AppBarTabSettingStore.shared)
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
                                    isPinned: tabSettingStore.pinnedListIds.contains(list.id), // get pin status from Store
                                    defaultUser: isMastodonUser ? currentUser : nil
                                )
                                .onAppear {
                                    FlareLog.debug("loading: itemCount=\(successData.itemCount), index=\(index), lastKnownItemCount=\(lastKnownItemCount)")
                                    successData.get(index: Int32(index))

                                    lastKnownItemCount = Int(successData.itemCount)
                                    FlareLog.debug("list updated: itemCount=\(successData.itemCount), index=\(index), lastKnownItemCount=\(lastKnownItemCount)")
                                }
                            }
                        }
                    }.scrollContentBackground(.hidden).listRowBackground(theme.primaryBackgroundColor)
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
            .scrollContentBackground(.hidden)
            .navigationBarTitleDisplayMode(.inline)
            .background(theme.secondaryBackgroundColor)
            .listRowBackground(theme.primaryBackgroundColor)
        }
    }

    // loading lists view
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
    @EnvironmentObject private var router: FlareRouter
    let accountType: AccountType
    let defaultUser: UiUserV2?
    @Environment(FlareTheme.self) private var theme

    init(list: UiList, accountType: AccountType, isPinned: Bool, defaultUser: UiUserV2? = nil) {
        self.list = list
        self.accountType = accountType
        _isPinned = State(initialValue: isPinned)
        self.defaultUser = defaultUser
    }

    var body: some View {
        ListItemRowView(
            list: list,
            isPinned: isPinned,
            showCreator: true,
            showMemberCount: true,
            defaultUser: defaultUser,
            onTap: {
                router.navigate(to: .listDetail(
                    accountType: accountType,
                    list: list,
                    defaultUser: defaultUser
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
            "itemType": "list",
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
