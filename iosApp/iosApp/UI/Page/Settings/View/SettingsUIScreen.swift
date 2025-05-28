import shared
import SwiftUI

struct SettingsUIScreen: View {
    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    @State private var selectedDetail: SettingsDestination?
    @State private var presenter = ActiveAccountPresenter()
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        ZStack {
            ObservePresenter(presenter: presenter) { _ in
                NavigationSplitView {
                    List(selection: $selectedDetail) {
                        Section {
                            Label {
                                Text("settings_appearance_generic")
                                    .foregroundColor(theme.labelColor)
                            } icon: {
                                Image(systemName: "paintpalette")
                                    .foregroundColor(theme.tintColor)
                            }
                            .tag(SettingsDestination.appearance)

                            Label {
                                Text("base settings")
                                    .foregroundColor(theme.labelColor)
                            } icon: {
                                Image(systemName: "gear")
                                    .foregroundColor(theme.tintColor)
                            }
                            .tag(SettingsDestination.other)

                            Label {
                                Text("settings_storage_title")
                                    .foregroundColor(theme.labelColor)
                            } icon: {
                                Image(systemName: "externaldrive")
                                    .foregroundColor(theme.tintColor)
                            }
                            .tag(SettingsDestination.storage)

                            Label {
                                Text("Feature Requests")
                                    .foregroundColor(theme.labelColor)
                            } icon: {
                                Image(systemName: "list.bullet.rectangle.portrait")
                                    .foregroundColor(theme.tintColor)
                            }
                            .tag(SettingsDestination.wishlist)

                            Label {
                                Text("settings_about_subtitle")
                                    .foregroundColor(theme.labelColor)
                            } icon: {
                                Image(systemName: "exclamationmark.circle")
                                    .foregroundColor(theme.tintColor)
                            }
                            .tag(SettingsDestination.about)
                        }.listRowBackground(theme.primaryBackgroundColor)
                    }
                    .background(theme.secondaryBackgroundColor)
                    .navigationTitle("settings_title")
                    .environment(\.defaultMinListRowHeight, 60)
                } detail: {
                    if let detail = selectedDetail {
                        switch detail {
                        // case .account:
                        //     AccountsScreen()
                        case .appearance:
                            AppearanceUIScreen()
                        case .other:
                            BaseSettingScreen()
                        case .storage:
                            StorageScreen()
                        case .about:
                            AboutScreen()
                        case .wishlist:
                            WishlistView()
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
        .scrollContentBackground(.hidden)
        .listRowBackground(theme.secondaryBackgroundColor)
    }
}

public enum SettingsDestination: String, CaseIterable, Identifiable {
    // case account
    case appearance
    case other
    case storage
    case about
    case wishlist
    // case serverInfo
    public var id: String {
        rawValue
    }
}
