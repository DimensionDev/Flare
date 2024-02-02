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
                        Text("storage_clear_cache")
                        Text(
                            "\(viewModel.model.userCount) users, \(viewModel.model.statusCount) statuses will be deleted"
                        )
                        .font(.caption)
                    }
                }
            }
            .buttonStyle(.borderless)
        }
        .navigationTitle("storage_title")
        .activateViewModel(viewModel: viewModel)
    }
}

@Observable
class StorageViewModel: MoleculeViewModelBase<StorageState, StoragePresenter> {
}

#Preview {
    StorageScreen()
}
