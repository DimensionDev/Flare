import SwiftUI
import shared

struct StatusDetailScreen: View {
    @State
    var presenter: StatusContextPresenter
    let statusKey: MicroBlogKey
    init(accountType: AccountType, statusKey: MicroBlogKey) {
        presenter = .init(accountType: accountType, statusKey: statusKey)
        self.statusKey = statusKey
    }
    var body: some View {
        Observing(presenter.models) { state in
            List {
                StatusTimelineComponent(
                    data: state.listState,
                    detailKey: statusKey
                )
            }
            .listStyle(.plain)
            .refreshable {
                try? await state.refresh()
            }
        }
    }
}
