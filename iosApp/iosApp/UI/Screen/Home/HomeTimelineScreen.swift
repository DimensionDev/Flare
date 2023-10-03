import SwiftUI
import shared
import RichText

struct HomeTimelineScreen: View {
    @State var viewModel = TimelineViewModel()
    var body: some View {
        List {
            StatusTimelineStateBuilder(data: viewModel.model.listState)
        }.listStyle(.plain).refreshable {
            viewModel.model.refresh()
        }.activateViewModel(viewModel: viewModel)
    }
}

@Observable
class TimelineViewModel: MoleculeViewModelBase<HomeTimelineState, HomeTimelinePresenter> {
}

#Preview {
    HomeTimelineScreen()
}

