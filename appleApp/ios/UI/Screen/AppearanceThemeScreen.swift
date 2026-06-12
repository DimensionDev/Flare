import SwiftUI
import FlareAppleUI
import KotlinSharedUI
import FlareAppleCore

struct AppearanceThemeScreen: View {
    @StateObject private var presenter = KotlinPresenter(presenter: SettingsPresenter())
    @Environment(\.globalAppearance) private var globalAppearance
    @Environment(\.timelineAppearance) private var timelineAppearance
    var body: some View {
        List {
            Picker(selection: Binding(get: {
                globalAppearance.theme
            }, set: { newValue in
                presenter.state.updateTheme(value: newValue)
            })) {
                Text("appearance_theme_system").tag(Theme.system)
                Text("appearance_theme_light").tag(Theme.light)
                Text("appearance_theme_dark").tag(Theme.dark)
            } label: {
                Text("appearance_theme")
                Text("appearance_theme_description")
            }
            Picker(selection: Binding(get: {
                timelineAppearance.avatarShape
            }, set: { newValue in
                presenter.state.updateAvatarShape(value: newValue)
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
                    globalAppearance.fontSizeDiff
                }, set: { newValue in
                    presenter.state.updateFontScale(fontSizeDiff: newValue)
                }), in: -2...4, step: 1) {
                    Text("appearance_font_size_diff")
                } minimumValueLabel: {
                    Image(systemName: "textformat.size.smaller")
                } maximumValueLabel: {
                    Image(systemName: "textformat.size.larger")
                }
            }
        }
        .navigationTitle("appearance_theme_group_title")
    }
}
