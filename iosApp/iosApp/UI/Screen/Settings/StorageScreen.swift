import SwiftUI
import shared

struct StorageScreen: View {
    @State private var presenter = StoragePresenter()

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            List {
                Button(role: .destructive) {
                    state.clearCache()
                } label: {
                    HStack(alignment: .center) {
                        Image(systemName: "trash")
                            .font(.title)
                        Spacer()
                            .frame(width: 16)
                        VStack(alignment: .leading) {
                            Text("storage_clear_cache")
                            Text(
                                "\(state.userCount) users, \(state.statusCount) statuses will be deleted"
                            )
                                .font(.caption)
                        }
                    }
                }
                .buttonStyle(.borderless)
            }
            .navigationTitle("storage_title")
        }
    }
}

#Preview {
    StorageScreen()
}
