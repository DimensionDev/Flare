import SwiftUI
import shared

struct TimelineScreen: View {
    @State
    var presenter: TimelinePresenter
    var body: some View {
        Observing(presenter.models) { state in
            List {
                StatusTimelineComponent(
                    data: state.listState,
                    detailKey: nil
                )
            }
            .listStyle(.plain)
            .refreshable {
                try? await presenter.models.value.refresh()
            }
        }
    }
}

struct HomeTimelineScreen: View {
    @Environment(\.openURL) private var openURL
    private let presenter: HomeTimelinePresenter
    init(accountType: AccountType) {
        presenter = .init(accountType: accountType)
    }
    var body: some View {
        TimelineScreen(presenter: presenter)
            .navigationTitle("home_timeline_title")
#if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
#endif
    }
}
