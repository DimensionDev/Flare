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
        }.task {
            await viewModel.activate()
        }
    }
}

#Preview {
    ContentView()
}

class CounterViewModel: MoleculeViewModelBase<CounterState, CounterPresenter> {
}

@Observable
class MoleculeViewModelBase<Model, Presenter: PresenterBase<Model>> {
    private let presenter = Presenter()
    private(set) var model: Model
    
    init() {
        model = presenter.models.value!
    }
    
    @MainActor
    func activate() async {
        for await model in presenter.models {
            self.model = model!
        }
    }
}
