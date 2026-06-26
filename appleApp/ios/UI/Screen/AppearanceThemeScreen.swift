import SwiftUI
import FlareAppleUI

struct AppearanceThemeScreen: View {
    var body: some View {
        List {
            AppearanceThemeSettingsSection()
        }
        .navigationTitle("appearance_theme")
    }
}
