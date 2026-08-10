import SwiftUI
import KotlinSharedUI
import FlareAppleCore
import FlareAppleUI

struct AccountManagementScreen: View {
    @StateObject private var presenter = KotlinPresenter(presenter: AccountManagementPresenter())
    @State private var tabItems: [AccountsStateAccountItem] = []
    @State private var pendingLogoutAccountKey: MicroBlogKey? = nil
    @State private var pendingLogoutAccountName: String? = nil

    var body: some View {
        List {
            ForEach(tabItems, id: \.account.accountKey) { account in
                StateView(state: account.profile) { user in
                    accountActions(for: account.account, accountName: user.handle.canonical) {
                        UserCompatView(data: user) {
                            HStack {
                                Image(systemName: activeAccountKey == user.key ? "checkmark.circle.fill" : "circle")
                                    .foregroundColor(.blue)
                                Image(systemName: "line.3.horizontal")
                                    .foregroundColor(.secondary)
                            }
                        } onClicked: {
                            presenter.state.setActiveAccount(accountKey: user.key)
                        }
                        .onTapGesture {
                            presenter.state.setActiveAccount(accountKey: user.key)
                        }
                    }
                } errorContent: { error in
                    accountActions(for: account.account, accountName: account.account.accountKey.id) {
                        Group {
                            if account.account.platformAvailable {
                                UserErrorView(error: error)
                            } else {
                                unavailableAccountRow(account.account)
                            }
                        }
                    }
                } loadingContent: {
                    UserLoadingView()
                }
            }
            .onMove(perform: move)
        }
        .onSuccessOf(of: presenter.state.accounts) { data in
            tabItems = data.cast(AccountsStateAccountItem.self)
        }
        .onChange(of: tabItems, { oldValue, newValue in
            presenter.state.setOrder(value: newValue.map { item in item.account.accountKey })
        })
        .alert("logout_title", isPresented: Binding(get: {
            pendingLogoutAccountKey != nil
        }, set: { value in
            if !value {
                clearPendingLogout()
            }
        })) {
            Button("Cancel", role: .cancel) {
                clearPendingLogout()
            }
            Button("delete", role: .destructive) {
                confirmLogout()
            }
        } message: {
            Text(
                pendingLogoutAccountName.map { "Are you sure you want to remove \($0) from this device?" } ??
                    "Are you sure you want to remove this account from this device?"
            )
        }
        .navigationTitle("account_management_title")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink(value: Route.serviceSelect) {
                    Label {
                        Text("login_title")
                    } icon: {
                        Image(fontAwesome: .plus)
                    }
                }
            }
        }
    }

    private func accountActions<Content: View>(
        for account: UiAccount,
        accountName: String?,
        @ViewBuilder content: () -> Content
    ) -> some View {
        content()
            .contextMenu {
                accountActionButtons(for: account, accountName: accountName)
            }
            .swipeActions {
                accountActionButtons(for: account, accountName: accountName)
            }
    }

    @ViewBuilder
    private func accountActionButtons(for account: UiAccount, accountName: String?) -> some View {
        if account.supportsRelayManagement {
            NavigationLink(value: Route.nostrRelays(account.accountKey)) {
                Label {
                    Text("Manage relays")
                } icon: {
                    Image(fontAwesome: .list)
                }
            }
            .tint(.accentColor)
        }
        Button(role: .destructive) {
            requestLogoutConfirmation(
                accountKey: account.accountKey,
                accountName: accountName
            )
        } label: {
            Label {
                Text("logout_title")
            } icon: {
                Image(fontAwesome: .trash)
            }
        }
    }

    private func unavailableAccountRow(_ account: UiAccount) -> some View {
        HStack(spacing: 12) {
            Image(fontAwesome: account.platformIcon.fontAwesomeIcon)
                .frame(width: 32, height: 32)
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: account.platformDisplayName)
                Text(verbatim: account.accountKey.description())
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var activeAccountKey: MicroBlogKey? {
        if case .success(let active) = onEnum(of: presenter.state.activeAccount) {
            active.data.accountKey
        } else {
            nil
        }
    }

    func move(from source: IndexSet, to destination: Int) {
        tabItems.move(fromOffsets: source, toOffset: destination)
    }

    private func requestLogoutConfirmation(accountKey: MicroBlogKey, accountName: String?) {
        pendingLogoutAccountKey = accountKey
        pendingLogoutAccountName = accountName
    }

    private func confirmLogout() {
        guard let accountKey = pendingLogoutAccountKey else { return }
        tabItems.removeAll(where: { item in item.account.accountKey == accountKey })
        presenter.state.logout(accountKey: accountKey)
        clearPendingLogout()
    }

    private func clearPendingLogout() {
        pendingLogoutAccountKey = nil
        pendingLogoutAccountName = nil
    }
}
