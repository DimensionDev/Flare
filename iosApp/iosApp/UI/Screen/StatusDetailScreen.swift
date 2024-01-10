import SwiftUI
import shared

struct StatusDetailScreen: View {
    @State private var viewModel: StatusDetailViewModel
    @Environment(StatusEvent.self) var statusEvent: StatusEvent
    init(statusKey: MicroBlogKey) {
        viewModel = .init(statusKey: statusKey)
    }
    var body: some View {
        List {
            StatusTimelineComponent(
                data: viewModel.model.listState,
                mastodonEvent: statusEvent,
                misskeyEvent: statusEvent,
                blueskyEvent: statusEvent
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
    let presenter: StatusPresenter
    var model: StatusState
    typealias Model = StatusState
    typealias Presenter = StatusPresenter
    init(statusKey: MicroBlogKey) {
        presenter = .init(statusKey: statusKey)
        model = presenter.models.value
    }
}
