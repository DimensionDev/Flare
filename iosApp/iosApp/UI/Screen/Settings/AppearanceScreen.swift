import SwiftUI
import shared

struct AppearanceScreen: View {
    @Environment(\.colorScheme) private var colorScheme: ColorScheme
    
    @State var viewModel = AppearanceViewModel()
    var body: some View {
        List {
            if case .success(let success) = onEnum(of: viewModel.model.sampleStatus) {
                StatusItemView(
                    status: success.data,
                    mastodonEvent: EmptyStatusEvent.shared,
                    misskeyEvent: EmptyStatusEvent.shared,
                    blueskyEvent: EmptyStatusEvent.shared
                )
            }
            Section("Generic") {
                Button(action: {}, label: {
                    AppearanceListItem(title: "Theme", desc: "Change the theme of the app")
                    Spacer()
                })
            }
        }
        .buttonStyle(.plain)
        .navigationTitle("Appearance")
        .activateViewModel(viewModel: viewModel)
    }
}
@Observable
class AppearanceViewModel: MoleculeViewModelBase<AppearanceState, AppearancePresenter> {
}

struct AppearanceListItem: View {
    let title: LocalizedStringKey
    let desc: LocalizedStringKey
    var body: some View {
        VStack(alignment: .leading) {
            Text(title)
            Text(desc)
                .font(.caption)
        }
    }
}

#Preview {
    AppearanceScreen()
}
