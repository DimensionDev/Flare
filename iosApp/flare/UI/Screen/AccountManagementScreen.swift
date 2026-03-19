import SwiftUI
import KotlinSharedUI

struct AccountManagementScreen: View {
    @StateObject private var presenter = KotlinPresenter(presenter: AccountManagementPresenter())
    @State private var tabItems: [AccountsStateAccountItem] = []
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
                            Button(role: .destructive) {
                                tabItems.removeAll(where: { item in item.account.accountKey == user.key })
                                presenter.state.logout(accountKey: user.key)
                            } label: {
                                Label {
                                    Text("logout_title")
                                } icon: {
                                    Image("fa-trash")
                                }
                            }
                        }
                        .swipeActions {
                            Button(role: .destructive) {
                                tabItems.removeAll(where: { item in item.account.accountKey == user.key })
                                presenter.state.logout(accountKey: user.key)
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
                            .swipeActions {
                                Button(role: .destructive) {
                                    tabItems.removeAll(where: { item in item.account.accountKey == account.account.accountKey })
                                    presenter.state.logout(accountKey: account.account.accountKey)
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
}
