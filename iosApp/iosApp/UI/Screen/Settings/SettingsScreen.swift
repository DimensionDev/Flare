import SwiftUI
import shared

struct SettingsScreen: View {
    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    @State private var selectedDetail: SettingsDestination?
    @State private var viewModel = SettingsViewModel()
    var body: some View {
        NavigationSplitView {
            List(selection: $selectedDetail) {
                Section {
                    AccountItem(
                        userState: viewModel.model.user,
                        supportingContent: { _ in
                            AnyView(
                                Text("Account management")
                                    .lineLimit(1)
                                    .font(.subheadline)
                                    .opacity(0.5)
                            )
                        }
                    )
                    .tag(SettingsDestination.account)
                }
                Section {
                    ListItem(systemIconName: "paintpalette", title: "Appearance")
                        .tag(SettingsDestination.appearance)
                    ListItem(systemIconName: "externaldrive", title: "Storage")
                        .tag(SettingsDestination.storage)
                    ListItem(systemIconName: "exclamationmark.circle", title: "About")
                        .tag(SettingsDestination.about)
                }
            }
            .if(horizontalSizeClass == .compact, transform: { view in
                view
                    .listStyle(.insetGrouped)
            })
            .if(horizontalSizeClass != .compact, transform: { view in
                view
                    .listStyle(.grouped)
            })
            .navigationTitle("Settings")
        } detail: {
            if let detail = selectedDetail {
                switch detail {
                case .account:
                    AccountsScreen()
                case .appearance:
                    AppearanceScreen()
                case .storage:
                    StorageScreen()
                case .about:
                    AboutScreen()
                }
            } else {
                Text("Settings")
            }
        }
        .activateViewModel(viewModel: viewModel)
    }
}

struct ListItem: View {
    let systemIconName: String
    let title: LocalizedStringKey
    var body: some View {
        HStack {
            Image(systemName: systemIconName)
                .frame(width: 24)
            Text(title)
        }
    }
}

public enum SettingsDestination: String, CaseIterable, Identifiable {
    case account
    case appearance
    case storage
    case about
    public var id: String {
        self.rawValue
    }
}

class SettingsViewModel: MoleculeViewModelBase<ActiveAccountState, ActiveAccountPresenter> {
}
