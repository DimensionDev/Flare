import SwiftUI
import shared

struct StatusDetailScreen: View {
    let presenter: StatusContextPresenter
    @Environment(StatusEvent.self) var statusEvent: StatusEvent
    init(accountType: AccountType, statusKey: MicroBlogKey) {
        presenter = .init(accountType: accountType, statusKey: statusKey)
    }
    var body: some View {
        Observing(presenter.models) { state in
            List {
                StatusTimelineComponent(
                    data: state.listState,
                    mastodonEvent: statusEvent,
                    misskeyEvent: statusEvent,
                    blueskyEvent: statusEvent,
                    xqtEvent: statusEvent
                )
            }
            .listStyle(.plain)
            .refreshable {
                try? await state.refresh()
            }
        }
    }
}
