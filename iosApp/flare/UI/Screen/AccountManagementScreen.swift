import SwiftUI
import KotlinSharedUI

struct AccountManagementScreen: View {
    @StateObject private var presenter = KotlinPresenter(presenter: AccountManagementPresenter())
    var body: some View {
        List {
            StateView(state: presenter.state.accounts) { accounts in
                StateView(state: presenter.state.activeAccount) { currentAcount in
                    ForEach(0..<accounts.size, id: \.self) { index in
                        let account = accounts.get(index: index)
                        if let userState = account.second {
                            StateView(state: userState) { user in
                                UserCompatView(data: user) {
                                    Image(systemName: currentAcount.accountKey == user.key ? "checkmark.circle.fill" : "circle")
                                        .foregroundColor(.blue)
                                }
                                .onTapGesture {
                                    presenter.state.setActiveAccount(accountKey: user.key)
                                }
                                .swipeActions {
                                    Button(role: .destructive) {
                                        presenter.state.logout(accountKey: user.key)
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
                        } else {
                            UserLoadingView()
                        }
                    }
                }
            }
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
}
