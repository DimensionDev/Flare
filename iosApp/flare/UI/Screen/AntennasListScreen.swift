import SwiftUI
@preconcurrency import KotlinSharedUI

struct AntennasListScreen: View {
    @Environment(\.tabKey) private var tabKeyEnv
    @Environment(\.isActive) private var isActive
    let accountType: AccountType
    @StateObject private var presenter: KotlinPresenter<AntennasListPresenterState>
    var body: some View {
        ScrollViewReader { proxy in
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
            .navigationTitle("antennas_lists_title")
            .refreshable {
                try? await presenter.state.refreshSuspend()
            }
        }
    }
}


extension AntennasListScreen {
    init(accountType: AccountType) {
        self.accountType = accountType
        self._presenter = .init(wrappedValue: .init(presenter: AntennasListPresenter(accountType: accountType)))
    }
}
