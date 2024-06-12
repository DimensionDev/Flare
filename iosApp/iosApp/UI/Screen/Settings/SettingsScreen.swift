import SwiftUI
import shared

struct SettingsScreen: View {
    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    @State private var selectedDetail: SettingsDestination?
    let presenter = ActiveAccountPresenter()
    var body: some View {
        FlareTheme {
            NavigationSplitView {
                List(selection: $selectedDetail) {
                    Observing(presenter.models) { state in
                            Section {
                                AccountItem(
                                    userState: state.user,
                                    supportingContent: { _ in
                                        AnyView(
                                            Text("settings_account_manage")
                                                .lineLimit(1)
                                                .font(.subheadline)
                                                .opacity(0.5)
                                        )
                                    }
                                )
                                .tag(SettingsDestination.account)
                            }
                    }
                    Section {
                        Label("settings_appearance", systemImage: "paintpalette")
                            .tag(SettingsDestination.appearance)
                        Label("settings_storage", systemImage: "externaldrive")
                            .tag(SettingsDestination.storage)
                        Label("settings_about", systemImage: "exclamationmark.circle")
                            .tag(SettingsDestination.about)
                    }
                }
                .navigationTitle("settings_title")
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
                    Text("settings_welcome")
                        .font(.title)
                        .multilineTextAlignment(.center)
                        .padding()
                }
            }
        }
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
