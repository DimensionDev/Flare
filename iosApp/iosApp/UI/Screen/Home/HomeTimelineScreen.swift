import SwiftUI
import shared

struct HomeTimelineScreen: View {
    @State var viewModel = TimelineViewModel()
    @Environment(StatusEvent.self) var statusEvent: StatusEvent
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
        .navigationTitle("Home")
        .activateViewModel(viewModel: viewModel)
    }
}

@Observable
class TimelineViewModel: MoleculeViewModelBase<HomeTimelineState, HomeTimelinePresenter> {
}

#Preview {
    HomeTimelineScreen()
}
