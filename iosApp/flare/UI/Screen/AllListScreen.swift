import SwiftUI
@preconcurrency import KotlinSharedUI

struct AllListScreen: View {
    @StateObject private var presenter: KotlinPresenter<AllListWithTabsPresenterState>
    private let accountType: AccountType
    
    init(accountType: AccountType) {
        self.accountType = accountType
        self._presenter = .init(wrappedValue: .init(presenter: AllListWithTabsPresenter(accountType: accountType)))
    }
    
    var body: some View {
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
                }
            } loadingContent: {
                UiListPlaceholder()
            }
        }
        .navigationTitle("all_lists_title")
        .refreshable {
            try? await presenter.state.refreshSuspend()
        }
    }
}
