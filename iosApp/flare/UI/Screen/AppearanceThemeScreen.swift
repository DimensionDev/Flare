import SwiftUI
import KotlinSharedUI

struct AppearanceThemeScreen: View {
    @StateObject private var presenter = KotlinPresenter(presenter: SettingsPresenter())
    var body: some View {
        List {
            StateView(state: presenter.state.appearance) { appearance in
                Picker(selection: Binding(get: {
                    appearance.theme
                }, set: { newValue in
                    presenter.state.updateAppearanceSettings { settings in
                        settings.copy(theme: newValue)
                    }
                })) {
                    Text("appearance_theme_system").tag(Theme.system)
                    Text("appearance_theme_light").tag(Theme.light)
                    Text("appearance_theme_dark").tag(Theme.dark)
                } label: {
                    Text("appearance_theme")
                    Text("appearance_theme_description")
                }
                Picker(selection: Binding(get: {
                    appearance.avatarShape
                }, set: { newValue in
                    presenter.state.updateAppearanceSettings { settings in
                        settings.copy(avatarShape: newValue)
                    }
                })) {
                    Text("appearance_avatar_shape_circle").tag(AvatarShape.circle)
                    Text("appearance_avatar_shape_square").tag(AvatarShape.square)
                } label: {
                    Text("appearance_avatar_shape")
                    Text("appearance_avatar_shape_description")
                }
                VStack(alignment: .leading) {
                    Text("appearance_font_size_diff")
                    Slider(value: Binding(get: {
                        appearance.fontSizeDiff
                    }, set: { newValue in
                        presenter.state.updateAppearanceSettings { settings in
                            settings.copy(fontSizeDiff: newValue)
                        }
                    }), in: -2...4, step: 1) {
                        Text("appearance_font_size_diff")
                    } minimumValueLabel: {
                        Image(systemName: "textformat.size.smaller")
                    } maximumValueLabel: {
                        Image(systemName: "textformat.size.larger")
                    }
                }
            }
        }
        .navigationTitle("appearance_theme_group_title")
    }
}
