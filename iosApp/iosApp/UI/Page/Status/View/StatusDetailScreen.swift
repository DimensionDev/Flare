import SwiftUI
import shared

struct StatusDetailScreen: View {
    @State private var presenter: StatusContextPresenter
    private let statusKey: MicroBlogKey

    init(accountType: AccountType, statusKey: MicroBlogKey) {
        presenter = .init(accountType: accountType, statusKey: statusKey)
        self.statusKey = statusKey
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
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
