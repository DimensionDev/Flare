import shared
import SwiftUI

struct SettingsUIScreen: View {
    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    @State private var selectedDetail: SettingsDestination?
    @State private var presenter = ActiveAccountPresenter()

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            FlareTheme {
                NavigationSplitView {
                    List(selection: $selectedDetail) {
                        Section {
                            AccountItem(
                                userState: state.user,
                                supportingContent: { _ in
                                    AnyView(
                                        Text("settings_accounts_title")
                                            .lineLimit(1)
                                            .font(.subheadline)
                                            .opacity(0.5)
                                    )
                                }
                            )
                            .tag(SettingsDestination.account)
                        }
                        Section {
                            Label("settings_appearance_generic", systemImage: "paintpalette")
                                .tag(SettingsDestination.appearance)

                            Label("base settings", systemImage: "gear")
                                .tag(SettingsDestination.other)

                            Label("settings_storage_title", systemImage: "externaldrive")
                                .tag(SettingsDestination.storage)

                            Label("settings_about_subtitle", systemImage: "exclamationmark.circle")
                                .tag(SettingsDestination.about)
                        }
                    }
                    .navigationTitle("settings_title")
                    .environment(\.defaultMinListRowHeight, 50)
                } detail: {
                    if let detail = selectedDetail {
                        switch detail {
                        case .account:
                            AccountsScreen()
                        case .appearance:
                            AppearanceUIScreen()
                        case .other:
                            BaseSettingScreen()
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
    case other
    case storage
    case about
    public var id: String {
        rawValue
    }
}
