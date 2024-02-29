import SwiftUI
import shared

struct HomeTimelineScreen: View {
    @Environment(\.openURL) private var openURL
    @State var viewModel: TimelineViewModel
    @Environment(StatusEvent.self) var statusEvent: StatusEvent

    init(accountType: AccountType) {
        _viewModel = .init(initialValue: .init(accountType: accountType))
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
#endif
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button(action: {
                    openURL(URL(string: AppDeepLink.Compose.shared.invoke())!)
                }, label: {
                    Image(systemName: "square.and.pencil")
                })
                Button(action: {
                    Task {
                        try? await viewModel.model.refresh()
                    }
                }, label: {
                    Image(systemName: "arrow.clockwise.circle")
                })
            }
        }
        .activateViewModel(viewModel: viewModel)
    }
}

@Observable
class TimelineViewModel: MoleculeViewModelProto {
    typealias Model = HomeTimelineState
    typealias Presenter = HomeTimelinePresenter
    let presenter: Presenter
    var model: Model
    init(accountType: AccountType) {
        presenter = .init(accountType: accountType)
        model = presenter.models.value
    }
}
