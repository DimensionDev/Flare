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
                            // Timeline & Display
                            Label {
                                Text(SettingsDestination.timelineDisplay.title)
                                    .foregroundColor(theme.labelColor)
                            } icon: {
                                Image(systemName: SettingsDestination.timelineDisplay.icon)
                                    .foregroundColor(theme.tintColor)
                            }
                            .tag(SettingsDestination.timelineDisplay)

                            // Media & Content
                            Label {
                                Text(SettingsDestination.mediaContent.title)
                                    .foregroundColor(theme.labelColor)
                            } icon: {
                                Image(systemName: SettingsDestination.mediaContent.icon)
                                    .foregroundColor(theme.tintColor)
                            }
                            .tag(SettingsDestination.mediaContent)

                            // Translation & Language
                            Label {
                                Text(SettingsDestination.translationLanguage.title)
                                    .foregroundColor(theme.labelColor)
                            } icon: {
                                Image(systemName: SettingsDestination.translationLanguage.icon)
                                    .foregroundColor(theme.tintColor)
                            }
                            .tag(SettingsDestination.translationLanguage)

                            // Browser Settings
                            Label {
                                Text(SettingsDestination.browserSettings.title)
                                    .foregroundColor(theme.labelColor)
                            } icon: {
                                Image(systemName: SettingsDestination.browserSettings.icon)
                                    .foregroundColor(theme.tintColor)
                            }
                            .tag(SettingsDestination.browserSettings)

                            // AI Settings
                            Label {
                                Text(SettingsDestination.aiSettings.title)
                                    .foregroundColor(theme.labelColor)
                            } icon: {
                                Image(systemName: SettingsDestination.aiSettings.icon)
                                    .foregroundColor(theme.tintColor)
                            }
                            .tag(SettingsDestination.aiSettings)

                            // Storage & Privacy
                            Label {
                                Text(SettingsDestination.storagePrivacy.title)
                                    .foregroundColor(theme.labelColor)
                            } icon: {
                                Image(systemName: SettingsDestination.storagePrivacy.icon)
                                    .foregroundColor(theme.tintColor)
                            }
                            .tag(SettingsDestination.storagePrivacy)

                            // About
                            Label {
                                Text(SettingsDestination.about.title)
                                    .foregroundColor(theme.labelColor)
                            } icon: {
                                Image(systemName: SettingsDestination.about.icon)
                                    .foregroundColor(theme.tintColor)
                            }
                            .tag(SettingsDestination.about)

                            // Feature Requests
//                            Label {
//                                Text(SettingsDestination.support.title)
//                                    .foregroundColor(theme.labelColor)
//                            } icon: {
//                                Image(systemName: SettingsDestination.support.icon)
//                                    .foregroundColor(theme.tintColor)
//                            }
//                            .tag(SettingsDestination.support)
                        }.listRowBackground(theme.primaryBackgroundColor)
                    }
                    .background(theme.secondaryBackgroundColor)
                    .navigationTitle("settings_title")
                    .environment(\.defaultMinListRowHeight, 60)
                } detail: {
                    if let detail = selectedDetail {
                        switch detail {
                        case .timelineDisplay:
                            TimelineDisplayScreen()
                        case .mediaContent:
                            MediaContentScreen()
                        case .translationLanguage:
                            TranslationLanguageScreen()
                        case .browserSettings:
                            BrowserSettingsScreen()
                        case .aiSettings:
                            AISettingsScreen()
                        case .storagePrivacy:
                            StoragePrivacyScreen()
                        case .about:
                            AboutScreen()
                        case .support:
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
    case timelineDisplay = "timeline_display"
    case mediaContent = "media_content"
    case translationLanguage = "translation_language"
    case browserSettings = "browser_settings"
    case aiSettings = "ai_settings"
    case storagePrivacy = "storage_privacy"
    case about
    case support

    public var id: String { rawValue }

    var title: String {
        switch self {
        case .timelineDisplay: "Timeline & Display"
        case .mediaContent: "Media & Content"
        case .translationLanguage: "Translation & Language"
        case .browserSettings: "Browser Settings"
        case .aiSettings: "AI Settings"
        case .storagePrivacy: "Storage & Privacy"
        case .about: "About"
        case .support: "Feature Requests"
        }
    }

    var icon: String {
        switch self {
        case .timelineDisplay: "list.bullet.rectangle"
        case .mediaContent: "photo.on.rectangle"
        case .translationLanguage: "character.bubble"
        case .browserSettings: "network"
        case .aiSettings: "brain"
        case .storagePrivacy: "lock.shield"
        case .about: "info.circle"
        case .support: "list.bullet.rectangle.portrait"
        }
    }

    var priority: Int {
        switch self {
        case .timelineDisplay, .mediaContent: 3
        case .translationLanguage, .browserSettings, .aiSettings: 2
        case .storagePrivacy, .about, .support: 1
        }
    }
}
