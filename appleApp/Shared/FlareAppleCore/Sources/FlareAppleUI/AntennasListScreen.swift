import SwiftUI
@preconcurrency import KotlinSharedUI
import FlareAppleCore

public struct AntennasListScreen<Destination: Hashable>: View {
    private let accountType: AccountType
    private let timelineDestination: (UiTimelineTabItem) -> Destination
    @StateObject private var presenter: KotlinPresenter<MisskeyAntennasListWithTabsPresenterState>

    public init(
        accountType: AccountType,
        timelineDestination: @escaping (UiTimelineTabItem) -> Destination
    ) {
        self.accountType = accountType
        self.timelineDestination = timelineDestination
        self._presenter = .init(wrappedValue: .init(presenter: MisskeyAntennasListWithTabsPresenter(accountType: accountType)))
    }

    public var body: some View {
        List {
            PagingView(data: presenter.state.data) { item in
                NavigationLink(
                    value: timelineDestination(presenter.state.timelineTabItem(item: item))
                ) {
                    UiListView(data: item)
                }
            } loadingContent: {
                UiListPlaceholder()
            }
        }
        .navigationTitle(Text("antenna_title", bundle: FlareAppleUILocalization.bundle))
        .refreshable {
            try? await presenter.state.refreshSuspend()
        }
    }
}
