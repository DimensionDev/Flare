import SwiftUI
import KotlinSharedUI

struct EditListMemberScreen: View {
    @Environment(\.dismiss) var dismiss
    @StateObject private var presenter: KotlinPresenter<EditListMemberState>
    @State private var searchText: String = ""

    init(accountType: AccountType, listId: String) {
        self._presenter = .init(wrappedValue: .init(presenter: EditListMemberPresenter(accountType: accountType, listId: listId)))
    }
    
    var body: some View {
        List {
            PagingView(data: presenter.state.users) { user in
                if let added = user.second, let data = user.first {
                    UserCompatView(data: data) {
                        if added.boolValue {
                            Button(role: .destructive) {
                                presenter.state.removeMember(userKey: data.key)
                            } label: {
                                Image(.faTrash)
                            }
                        } else {
                            Button {
                                presenter.state.addMember(userKey: data.key)
                            } label: {
                                Image(.faPlus)
                            }
                        }
                    } onClicked: {
                        
                    }
                } else {
                    EmptyView()
                }
            } loadingContent: {
                UserLoadingView()
            } errorContent: { error, retry in
                ListErrorView(error: error, onRetry: retry)
            } emptyContent: {
                ListEmptyView()
            }
        }
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button {
                    dismiss()
                } label: {
                    Image("fa-xmark")
                }
            }
        }
        .navigationTitle("list_edit_member_title")
        .searchable(text: $searchText, prompt: "search")
        .onSubmit(of: .search) {
            presenter.state.setFilter(value: searchText)
        }
    }
}
