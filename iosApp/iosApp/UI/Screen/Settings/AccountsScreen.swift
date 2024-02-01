import SwiftUI
import shared

struct AccountsScreen: View {
    @State var viewModel = AccountsViewModel()
    @State var showServiceSelectSheet = false
    var body: some View {
        List {
            switch onEnum(of: viewModel.model.accounts) {
            case .success(let data):
                if data.data.size > 0 {
                    ForEach(1...data.data.size, id: \.self) { index in
                        let item = data.data.get(index: index - 1)
                        switch onEnum(of: item) {
                        case .success(let user):
                            Button {
                                viewModel.model.setActiveAccount(accountKey: user.data.userKey)
                            } label: {
                                HStack {
                                    UserComponent(user: user.data)
                                    Spacer()
                                    switch onEnum(of: viewModel.model.activeAccount) {
                                    case .success(let activeAccount):
                                        Image(
                                            systemName: activeAccount.data.accountKey == user.data.userKey ?
                                            "checkmark.circle.fill" :
                                                "circle"
                                        )
                                        .foregroundStyle(.blue)
                                    default:
                                        Image(systemName: "circle")
                                            .foregroundStyle(.blue)
                                    }
                                }
                                .swipeActions(edge: .trailing) {
                                    Button(role: .destructive) {
                                        viewModel.model.removeAccount(accountKey: user.data.userKey)
                                    } label: {
                                        Label("delete", systemImage: "trash")
                                    }
                                }
                            }
                            .buttonStyle(.plain)
                            #if os(macOS)
                            .contextMenu {
                                Button(role: .destructive) {
                                    viewModel.model.removeAccount(accountKey: user.data.userKey)
                                } label: {
                                    Label("delete", systemImage: "trash")
                                }
                            }
                            #endif
                        case .error:
                            Text("error")
                        case .loading:
                            Text("loading")
                        }
                    }
                } else {
                    Text("no_accounts")
                }
            case .error:
                Text("error")
            case .loading:
                Text("loading")
            }
        }
        .navigationTitle("accounts_management_title")
        .toolbar {
            Button(action: {
                showServiceSelectSheet = true
            }, label: {
                Image(systemName: "plus")
            })
        }
        .sheet(isPresented: $showServiceSelectSheet, content: {
            ServiceSelectScreen {
                showServiceSelectSheet = false
            }
#if os(macOS)
            .frame(minWidth: 600, minHeight: 400)
#endif
        })
        .activateViewModel(viewModel: viewModel)
    }
}

@Observable
class AccountsViewModel: MoleculeViewModelBase<AccountsState, AccountsPresenter> {
}
