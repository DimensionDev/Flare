import SwiftUI
@preconcurrency import KotlinSharedUI

struct AllListScreen: View {
    @Environment(\.tabKey) private var tabKeyEnv
    @Environment(\.isActive) private var isActive
    @StateObject private var presenter: KotlinPresenter<AllListWithTabsPresenterState>
    private let accountType: AccountType
    @State private var editListId: String? = nil
    @State private var deleteListId: String? = nil
    @State private var showCreateListSheet: Bool = false
    
    init(accountType: AccountType) {
        self.accountType = accountType
        self._presenter = .init(wrappedValue: .init(presenter: AllListWithTabsPresenter(accountType: accountType)))
    }
    
    var body: some View {
        ScrollViewReader { proxy in
            List {
                PagingView(data: presenter.state.items) { item in
                    NavigationLink(
                        value: Route
                            .tabItem(
                                ListTimelineTabItem(
                                    account: accountType,
                                    listId: item.id,
                                    metaData: TabMetaData(
                                        title: TitleType.Text(content: item.title),
                                        icon: IconType.Material(icon: .list)
                                    )
                                )
                            )
                    ) {
                        UiListView(data: item)
                            .if(!item.readonly) { view in
                                view.swipeActions(edge: .leading) {
                                    Button {
                                        editListId = item.id
                                    } label: {
                                        Label {
                                            Text("list_edit_title")
                                        } icon: {
                                            Image(.faPen)
                                        }
                                    }
                                }
                                .swipeActions(edge: .trailing) {
                                    Button(role: .destructive) {
                                        deleteListId = item.id
                                    } label: {
                                        Label {
                                            Text("delete")
                                        } icon: {
                                            Image(.faTrash)
                                        }
                                    }
                                }
                                .contextMenu {
                                    Button {
                                        editListId = item.id
                                    } label: {
                                        Label {
                                            Text("edit")
                                        } icon: {
                                            Image(.faPen)
                                        }
                                    }
                                    Button(role: .destructive) {
                                        deleteListId = item.id
                                    } label: {
                                        Label {
                                            Text("delete")
                                        } icon: {
                                            Image(.faTrash)
                                        }
                                    }
                                }
                            }
                    }
                } loadingContent: {
                    UiListPlaceholder()
                }
                .id("top")
            }
            .onReceive(NotificationCenter.default.publisher(for: .scrollToTop)) { notification in
                let targetTab = notification.userInfo?["tab"] as? String
                if isActive && (targetTab == nil || targetTab == tabKeyEnv) {
                    withAnimation {
                        proxy.scrollTo("top", anchor: .top)
                    }
                }
            }
            .navigationTitle("all_lists_title")
            .refreshable {
                try? await presenter.state.refreshSuspend()
            }
            .sheet(item: $editListId) { id in
                NavigationStack {
                    EditListScreen(accountType: accountType, listId: id)
                }
            }
            .alert("delete_list_title", isPresented: Binding(get: {
                deleteListId != nil
            }, set: { value in
                if !value {
                    deleteListId = nil
                }
            }), presenting: deleteListId, actions: { id in
                Button("Cancel", role: .cancel) {}
                Button("Delete", role: .destructive) {
                    Task {
                        try? await DeleteListPresenter(accountType: accountType, listId: id).models.value.deleteList()
                    }
                }
            }, message: { data in
                Text("delete_list_description")
            })
            .sheet(isPresented: $showCreateListSheet) {
                NavigationStack {
                    CreateListScreen(accountType: accountType)
                }
            }
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        showCreateListSheet = true
                    } label: {
                        Image(.faPlus)
                    }
                }
            }
        }
    }
}
