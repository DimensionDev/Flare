import SwiftUI
@preconcurrency import KotlinSharedUI

struct AntennasListScreen: View {
    let accountType: AccountType
    @StateObject private var presenter: KotlinPresenter<MisskeyAntennasListWithTabsPresenterState>
    var body: some View {
        List {
            PagingView(data: presenter.state.data) { item in
                NavigationLink(
                    value: Route.timeline(
                        presenter.state.timelineTabItem(item: item)
                    )
                ) {
                    UiListView(data: item)
                }
            } loadingContent: {
                UiListPlaceholder()
            }
        }
        .navigationTitle("antennas_lists_title")
        .refreshable {
            try? await presenter.state.refreshSuspend()
        }
    }
}


extension AntennasListScreen {
    init(accountType: AccountType) {
        self.accountType = accountType
        self._presenter = .init(wrappedValue: .init(presenter: MisskeyAntennasListWithTabsPresenter(accountType: accountType)))
    }
}
