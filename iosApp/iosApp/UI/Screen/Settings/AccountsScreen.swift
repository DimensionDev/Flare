import SwiftUI
import shared

struct AccountsScreen: View {
    @State var viewModel = AccountsViewModel()
    var body: some View {
        List {
            switch onEnum(of: viewModel.model.accounts) {
            case .success(let data):
                ForEach(1...data.data.size, id: \.self) { index in
                    let item = data.data.get(index: index - 1)
                    switch onEnum(of: item) {
                    case .success(let user):
                        HStack {
                            UserComponent(user: user.data)
                            Spacer()
                            switch onEnum(of: viewModel.model.activeAccount) {
                            case .success(let activeAccount):
                                Image(systemName: activeAccount.data.accountKey == user.data.userKey ? "checkmark.circle.fill" : "circle")
                                    .foregroundStyle(.blue)
                            default:
                                Image(systemName: "circle")
                                    .foregroundStyle(.blue)
                            }
                        }
                    case .error:
                        Text("error")
                    case .loading:
                        Text("loading")
                    }
                }
            case .error:
                Text("error")
            case .loading:
                Text("loading")
            }
        }
        .navigationTitle("Accounts")
        .toolbar {
            Button(action: {}) {
                Image(systemName: "plus")
            }
        }
        .activateViewModel(viewModel: viewModel)
    }
}

@Observable
class AccountsViewModel : MoleculeViewModelBase<AccountsState, AccountsPresenter> {
    
}

#Preview {
    NavigationStack {
        AccountsScreen()
    }
}
