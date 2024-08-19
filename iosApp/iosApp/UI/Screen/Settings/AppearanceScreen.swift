import SwiftUI
import shared

struct AppearanceScreen: View {
    @Environment(\.appSettings) private var appSettings
    @State private var presenter = AppearancePresenter()

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            List {
                if case .success(let success) = onEnum(of: state.sampleStatus) {
                    StatusItemView(
                        data: success.data,
                        detailKey: nil
                    )
                }
                Section("appearance_settings_generic") {
                    Picker(selection: Binding(get: {
                        appSettings.appearanceSettings.theme
                    }, set: { value in
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.theme, to: value))
                    }), content: {
                        Text("app_theme_auto")
                            .tag(Theme.auto)
                        Text("app_theme_dark")
                            .tag(Theme.dark)
                        Text("app_theme_light")
                            .tag(Theme.light)
                    }, label: {
                        Text("appearance_theme_title")
                        Text("appearance_theme_description")
                    })
                    Picker(selection: Binding(get: {
                        appSettings.appearanceSettings.avatarShape
                    }, set: { value in
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.avatarShape, to: value))
                    }), content: {
                        Text("avatar_shape_circle")
                            .tag(AvatarShape.circle)
                        Text("avatar_shape_square")
                            .tag(AvatarShape.square)
                    }, label: {
                        Text("appearance_avatar_shape_title")
                        Text("appearance_avatar_shape_description")
                    })
                    Toggle(isOn: Binding(get: {
                        appSettings.appearanceSettings.showActions
                    }, set: { value in
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.showActions, to: value))
                    })) {
                        Text("appearance_show_actions_title")
                        Text("appearance_show_actions_description")
                    }
                    if appSettings.appearanceSettings.showActions {
                        Toggle(isOn: Binding(get: {
                            appSettings.appearanceSettings.showNumbers
                        }, set: { value in
                            appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.showNumbers, to: value))
                        })) {
                            Text("appearance_show_numbers_title")
                            Text("appearance_show_numbers_description")
                        }
                    }
                    Toggle(isOn: Binding(get: {
                        appSettings.appearanceSettings.showLinkPreview
                    }, set: { value in
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.showLinkPreview, to: value))
                    })) {
                        Text("appearance_show_link_preview_title")
                        Text("appearance_show_link_preview_description")
                    }
                    Toggle(isOn: Binding(get: {
                        appSettings.appearanceSettings.showMedia
                    }, set: { value in
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.showMedia, to: value))
                    })) {
                        Text("appearance_show_media_title")
                        Text("appearance_show_media_description")
                    }
                    if appSettings.appearanceSettings.showMedia {
                        Toggle(isOn: Binding(get: {
                            appSettings.appearanceSettings.showSensitiveContent
                        }, set: { value in
                            appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.showSensitiveContent, to: value))
                        })) {
                            Text("appearance_show_sensitive_content_title")
                            Text("appearance_show_sensitive_content_description")
                        }
                    }
                }
                .buttonStyle(.plain)
                .navigationTitle("appearance_title")
            }
            #if os(macOS)
            .toggleStyle(.switch)
            .pickerStyle(.segmented)
            #endif
        }
    }
}
