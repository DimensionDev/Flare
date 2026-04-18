import SwiftUI
import KotlinSharedUI
import FlareUI

struct AccountManagementScreen: View {
    @StateObject private var presenter = KotlinPresenter(presenter: AccountManagementPresenter())
    @State private var tabItems: [AccountsStateAccountItem] = []
    @State private var pendingLogoutAccountKey: MicroBlogKey? = nil
    @State private var pendingLogoutAccountName: String? = nil

    var body: some View {
        List {
            StateView(state: presenter.state.activeAccount) { currentAcount in
                ForEach(tabItems, id: \.account.accountKey) { account in
                    StateView(state: account.profile) { user in
                        UserCompatView(data: user) {
                            HStack {
                                Image(systemName: currentAcount.accountKey == user.key ? "checkmark.circle.fill" : "circle")
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
                        .contextMenu {
                            if account.account.platformType == .nostr {
                                NavigationLink(value: Route.nostrRelays(account.account.accountKey)) {
                                    Label {
                                        Text("Manage relays")
                                    } icon: {
                                        Image("fa-list")
                                    }
                                }
                            }
                            Button(role: .destructive) {
                                requestLogoutConfirmation(
                                    accountKey: user.key,
                                    accountName: user.handle.canonical
                                )
                            } label: {
                                Label {
                                    Text("logout_title")
                                } icon: {
                                    Image("fa-trash")
                                }
                            }
                        }
                        .swipeActions {
                            if account.account.platformType == .nostr {
                                NavigationLink(value: Route.nostrRelays(account.account.accountKey)) {
                                    Label {
                                        Text("Manage relays")
                                    } icon: {
                                        Image("fa-list")
                                    }
                                }
                                .tint(.accentColor)
                            }
                            Button(role: .destructive) {
                                requestLogoutConfirmation(
                                    accountKey: user.key,
                                    accountName: user.handle.canonical
                                )
                            } label: {
                                Label {
                                    Text("logout_title")
                                } icon: {
                                    Image("fa-trash")
                                }
                            }
                        }
                    } errorContent: { error in
                        UserErrorView(error: error)
                            .contextMenu {
                                if account.account.platformType == .nostr {
                                    NavigationLink(value: Route.nostrRelays(account.account.accountKey)) {
                                        Label {
                                            Text("Manage relays")
                                        } icon: {
                                            Image("fa-square-rss")
                                        }
                                    }
                                }
                                Button(role: .destructive) {
                                    requestLogoutConfirmation(
                                        accountKey: account.account.accountKey,
                                        accountName: account.account.accountKey.id
                                    )
                                } label: {
                                    Label {
                                        Text("logout_title")
                                    } icon: {
                                        Image("fa-trash")
                                    }
                                }
                            }
                            .swipeActions {
                                if account.account.platformType == .nostr {
                                    NavigationLink(value: Route.nostrRelays(account.account.accountKey)) {
                                        Label {
                                            Text("Manage relays")
                                        } icon: {
                                            Image("fa-square-rss")
                                        }
                                    }
                                    .tint(.accentColor)
                                }
                                Button(role: .destructive) {
                                    requestLogoutConfirmation(
                                        accountKey: account.account.accountKey,
                                        accountName: account.account.accountKey.id
                                    )
                                } label: {
                                    Label {
                                        Text("logout_title")
                                    } icon: {
                                        Image("fa-trash")
                                    }
                                }
                            }
                    } loadingContent: {
                        UserLoadingView()
                    }
                }
                .onMove(perform: move)
            }
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
            Button("Delete", role: .destructive) {
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
                        Image("fa-plus")
                    }
                }
            }
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
