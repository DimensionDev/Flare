import SwiftUI
import shared

struct StatusDetailScreen: View {
    @State private var viewModel: StatusDetailViewModel
    @Environment(StatusEvent.self) var statusEvent: StatusEvent
    init(accountKey: MicroBlogKey, statusKey: MicroBlogKey) {
        viewModel = .init(accountKey: accountKey, statusKey: statusKey)
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
    init(accountKey: MicroBlogKey, statusKey: MicroBlogKey) {
        presenter = .init(accountKey: accountKey, statusKey: statusKey)
        model = presenter.models.value
    }
}
