import SwiftUI
@preconcurrency import KotlinSharedUI

struct AntennasListScreen: View {
    let accountType: AccountType
    @StateObject private var presenter: KotlinPresenter<AntennasListPresenterState>
    var body: some View {
        List {
            PagingView(data: presenter.state.data) { item in
                NavigationLink(
                    value: Route.timeline(
                        Misskey.AntennasTimelineTabItem(
                            antennasId: item.id,
                            account: accountType,
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
        .navigationTitle("antennas_lists_title")
        .refreshable {
            try? await presenter.state.refreshSuspend()
        }
    }
}


extension AntennasListScreen {
    init(accountType: AccountType) {
        self.accountType = accountType
        self._presenter = .init(wrappedValue: .init(presenter: AntennasListPresenter(accountType: accountType)))
    }
}
