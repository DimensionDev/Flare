import SwiftUI
import shared

struct ContentView: View {
    @State var viewModel: CounterViewModel = CounterViewModel()
    var body: some View {
        VStack {
            Text(viewModel.model.count)
            Button(action: {
                viewModel.model.increment()
            }, label: {
                Text("click me!")
            })
        }.activateViewModel(viewModel: viewModel)
    }
}

#Preview {
    ContentView()
}

class CounterViewModel: MoleculeViewModelBase<CounterState, CounterPresenter> {
}
