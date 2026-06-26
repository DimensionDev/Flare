import SwiftUI
@preconcurrency import KotlinSharedUI
import FlareAppleCore

public struct AllListScreen<Destination: Hashable>: View {
    @StateObject private var presenter: KotlinPresenter<AllListWithTabsPresenterState>
    private let accountType: AccountType
    private let timelineDestination: (UiTimelineTabItem) -> Destination
    private let createListContent: (() -> AnyView)?
    private let editListContent: ((String) -> AnyView)?
    @State private var editListId: String? = nil
    @State private var deleteListId: String? = nil
    @State private var showCreateListSheet: Bool = false

    public init(
        accountType: AccountType,
        timelineDestination: @escaping (UiTimelineTabItem) -> Destination,
        createListContent: (() -> AnyView)? = nil,
        editListContent: ((String) -> AnyView)? = nil
    ) {
        self.accountType = accountType
        self.timelineDestination = timelineDestination
        self.createListContent = createListContent
        self.editListContent = editListContent
        self._presenter = .init(wrappedValue: .init(presenter: AllListWithTabsPresenter(accountType: accountType)))
    }

    public var body: some View {
        List {
            PagingView(data: presenter.state.items) { item in
                NavigationLink(
                    value: timelineDestination(presenter.state.timelineTabItem(item: item))
                ) {
                    UiListView(data: item)
                        .if(item.readonly) { view in
                            view.swipeActions(edge: .leading) {
                                if editListContent != nil {
                                    Button {
                                        editListId = item.id
                                    } label: {
                                        Label {
                                            Text("edit_list_title", bundle: FlareAppleUILocalization.bundle)
                                        } icon: {
                                            Image(fontAwesome: .pen)
                                        }
                                    }
                                }
                            }
                            .swipeActions(edge: .trailing) {
                                Button(role: .destructive) {
                                    deleteListId = item.id
                                } label: {
                                    Label {
                                        Text("delete", bundle: FlareAppleUILocalization.bundle)
                                    } icon: {
                                        Image(fontAwesome: .trash)
                                    }
                                }
                            }
                            .contextMenu {
                                if editListContent != nil {
                                    Button {
                                        editListId = item.id
                                    } label: {
                                        Label {
                                            Text("edit", bundle: FlareAppleUILocalization.bundle)
                                        } icon: {
                                            Image(fontAwesome: .pen)
                                        }
                                    }
                                }
                                Button(role: .destructive) {
                                    deleteListId = item.id
                                } label: {
                                    Label {
                                        Text("delete", bundle: FlareAppleUILocalization.bundle)
                                    } icon: {
                                        Image(fontAwesome: .trash)
                                    }
                                }
                            }
                        }
                }
            } loadingContent: {
                UiListPlaceholder()
            }
        }
        .navigationTitle(Text("all_lists_title", bundle: FlareAppleUILocalization.bundle))
        .refreshable {
            try? await presenter.state.refreshSuspend()
        }
        .sheet(isPresented: Binding(get: {
            editListId != nil
        }, set: { value in
            if !value {
                editListId = nil
            }
        })) {
            if let id = editListId, let editListContent {
                NavigationStack {
                    editListContent(id)
                }
            } else {
                EmptyView()
            }
        }
        .alert(Text("delete_list_title", bundle: FlareAppleUILocalization.bundle), isPresented: Binding(get: {
            deleteListId != nil
        }, set: { value in
            if !value {
                deleteListId = nil
            }
        }), presenting: deleteListId, actions: { id in
            Button(role: .cancel) {
            } label: {
                Text("Cancel", bundle: FlareAppleUILocalization.bundle)
            }
            Button(role: .destructive) {
                Task {
                    try? await DeleteListPresenter(accountType: accountType, listId: id).models.value.deleteList()
                    deleteListId = nil
                }
            } label: {
                Text("delete", bundle: FlareAppleUILocalization.bundle)
            }
        }, message: { data in
            Text("delete_list_description", bundle: FlareAppleUILocalization.bundle)
        })
        .sheet(isPresented: $showCreateListSheet) {
            if let createListContent {
                NavigationStack {
                    createListContent()
                }
            } else {
                EmptyView()
            }
        }
        .toolbar {
            if createListContent != nil {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        showCreateListSheet = true
                    } label: {
                        Image(fontAwesome: .plus)
                    }
                }
            }
        }
    }
}
