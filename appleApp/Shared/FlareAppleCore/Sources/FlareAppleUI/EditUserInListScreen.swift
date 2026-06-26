import SwiftUI
import KotlinSharedUI
import FlareAppleCore

public struct EditUserInListScreen: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var presenter: KotlinPresenter<EditAccountListState>
    
    public init(accountType: AccountType, userKey: MicroBlogKey) {
        self._presenter = .init(wrappedValue: .init(presenter: EditAccountListPresenter(accountType: accountType, userKey: userKey)))
    }
    
    public var body: some View {
        List {
            PagingView(data: presenter.state.lists) { item in
                HStack {
                    UiListView(data: item)
                    Spacer()
                    StateView(state: presenter.state.userLists) { userLists in
                        let list = userLists.cast(UiList.self)
                        if list.contains(where: { $0.id == item.id }) {
                            Button {
                                presenter.state.removeList(list: item)
                            } label: {
                                Image(fontAwesome: .trash)
                            }
                        } else {
                            Button {
                                presenter.state.addList(list: item)
                            } label: {
                                Image(fontAwesome: .plus)
                            }
                        }
                    } loadingContent: {
                        ProgressView()
                    }

                }
            } loadingContent: {
                UserLoadingView()
            }

        }
        .navigationTitle(Text("edit_user_in_list", bundle: FlareAppleUILocalization.bundle))
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button {
                    dismiss()
                } label: {
                    Image(fontAwesome: .xmark)
                }
            }
        }
    }
}
