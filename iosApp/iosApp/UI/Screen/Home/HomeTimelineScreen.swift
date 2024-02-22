import SwiftUI
import shared

struct HomeTimelineScreen: View {
    @State var viewModel: TimelineViewModel
    @Environment(StatusEvent.self) var statusEvent: StatusEvent

    init(accountKey: MicroBlogKey) {
        viewModel = .init(accountKey: accountKey)
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
        .navigationTitle("home_timeline_title")
#if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
#else
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button(action: {
                    Task {
                        try? await viewModel.model.refresh()
                    }
                }, label: {
                    Image(systemName: "arrow.clockwise.circle")
                })
            }
        }
#endif
        .activateViewModel(viewModel: viewModel)
    }
}

@Observable
class TimelineViewModel: MoleculeViewModelProto {
    typealias Model = HomeTimelineState
    typealias Presenter = HomeTimelinePresenter
    let presenter: Presenter
    var model: Model
    init(accountKey: MicroBlogKey) {
        presenter = .init(accountKey: accountKey)
        model = presenter.models.value
    }
}