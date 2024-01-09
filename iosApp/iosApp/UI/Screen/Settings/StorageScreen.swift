import SwiftUI
import shared

struct StorageScreen: View {
    @State var viewModel = StorageViewModel()
    var body: some View {
        List {
            Button(role: .destructive) {
                viewModel.model.clearCache()
            } label: {
                HStack(alignment: .center) {
                    Image(systemName: "trash")
                        .font(.title)
                    Spacer()
                        .frame(width: 16)
                    VStack(alignment: .leading) {
                        Text("Clear cache database")
                        Text(
                            viewModel.model.userCount.description + " users," +
                            viewModel.model.statusCount.description + " statues will be deleted"
                        )
                        .font(.caption)
                    }
                }
            }
            .buttonStyle(.borderless)
        }
        .navigationTitle("Storage")
        .activateViewModel(viewModel: viewModel)
    }
}

@Observable
class StorageViewModel: MoleculeViewModelBase<StorageState, StoragePresenter> {
}

#Preview {
    StorageScreen()
}
