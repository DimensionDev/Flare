import SwiftUI
import shared

struct StatusDetailScreen: View {
    @State private var viewModel: StatusDetailViewModel
    @Environment(StatusEvent.self) var statusEvent: StatusEvent
    init(accountType: AccountType, statusKey: MicroBlogKey) {
        viewModel = .init(accountType: accountType, statusKey: statusKey)
    }
    var body: some View {
        List {
            StatusTimelineComponent(
                data: viewModel.model.listState,
                mastodonEvent: statusEvent,
                misskeyEvent: statusEvent,
                blueskyEvent: statusEvent,
                xqtEvent: statusEvent
            )
        }
        .listStyle(.plain)
        .refreshable {
            try? await viewModel.model.refresh()
        }
        .activateViewModel(viewModel: viewModel)
    }
}

@Observable
class StatusDetailViewModel: MoleculeViewModelProto {
    let presenter: StatusContextPresenter
    var model: StatusContextState
    typealias Model = StatusContextState
    typealias Presenter = StatusContextPresenter
    init(accountType: AccountType, statusKey: MicroBlogKey) {
        presenter = .init(accountType: accountType, statusKey: statusKey)
        model = presenter.models.value
    }
}
