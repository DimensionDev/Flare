import SwiftUI
import shared

struct HomeTimelineScreen: View {
    @State var viewModel = TimelineViewModel()
    var body: some View {
        List {
            StatusTimelineComponent(data: viewModel.model.listState)
        }
        .listStyle(.plain)
        .refreshable {
            do {
                try await viewModel.model.refresh()
            } catch {
                
            }
            
        }
        .activateViewModel(viewModel: viewModel)
    }
}

@Observable
class TimelineViewModel: MoleculeViewModelBase<HomeTimelineState, HomeTimelinePresenter> {
}

#Preview {
    HomeTimelineScreen()
}

