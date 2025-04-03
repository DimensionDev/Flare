import shared
import SwiftUI

struct AppearanceUIScreen: View {
    @Environment(\.appSettings) private var appSettings
    @State private var presenter = AppearancePresenter()

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            List {
                if case let .success(success) = onEnum(of: state.sampleStatus) {
                    StatusItemView(
                        data: success.data,
                        detailKey: nil
                    )
                }
                Section("settings_appearance_generic") {
                    Picker(selection: Binding(get: {
                        appSettings.appearanceSettings.theme
                    }, set: { value in
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.theme, to: value))
                    }), content: {
                        Text("settings_appearance_theme_auto")
                            .tag(Theme.auto)
                        Text("settings_appearance_theme_dark")
                            .tag(Theme.dark)
                        Text("settings_appearance_theme_light")
                            .tag(Theme.light)
                    }, label: {
                        Text("settings_appearance_theme_color")
                        Text("settings_appearance_theme_color_description")
                    })

                    Picker(selection: Binding(get: {
                        appSettings.appearanceSettings.avatarShape
                    }, set: { value in
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.avatarShape, to: value))
                    }), content: {
                        Text("settings_appearance_avatar_shape_round")
                            .tag(AvatarShape.circle)
                        Text("settings_appearance_avatar_shape_square")
                            .tag(AvatarShape.square)
                    }, label: {
                        Text("settings_appearance_avatar_shape")
                        Text("settings_appearance_avatar_shape_description")
                    })
                    Toggle(isOn: Binding(get: {
                        appSettings.appearanceSettings.showActions
                    }, set: { value in
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.showActions, to: value))
                    })) {
                        Text("settings_appearance_show_actions")
                        Text("settings_appearance_show_actions_description")
                    }
                    if appSettings.appearanceSettings.showActions {
                        Toggle(isOn: Binding(get: {
                            appSettings.appearanceSettings.showNumbers
                        }, set: { value in
                            appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.showNumbers, to: value))
                        })) {
                            Text("settings_appearance_show_numbers")
                            Text("settings_appearance_show_numbers_description")
                        }
                    }
                    Toggle(isOn: Binding(get: {
                        appSettings.appearanceSettings.showLinkPreview
                    }, set: { value in
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.showLinkPreview, to: value))
                    })) {
                        Text("settings_appearance_show_link_previews")
                        Text("settings_appearance_show_link_previews_description")
                    }
                    Toggle(isOn: Binding(get: {
                        appSettings.appearanceSettings.showMedia
                    }, set: { value in
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.showMedia, to: value))
                    })) {
                        Text("settings_appearance_show_media")
                        Text("settings_appearance_show_media_description")
                    }
                    if appSettings.appearanceSettings.showMedia {
                        Toggle(isOn: Binding(get: {
                            appSettings.appearanceSettings.showSensitiveContent
                        }, set: { value in
                            appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.showSensitiveContent, to: value))
                        })) {
                            Text("settings_appearance_show_cw_img")
                            Text("settings_appearance_show_cw_img_description")
                        }
                    }
                }
                .buttonStyle(.plain)
                .navigationTitle("settings_appearance_title")
            }
            #if os(macOS)
            .toggleStyle(.switch)
            .pickerStyle(.segmented)
            #endif
        }
    }
}
