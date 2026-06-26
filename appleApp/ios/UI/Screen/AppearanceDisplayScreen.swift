import SwiftUI
import FlareAppleUI

struct AppearanceDisplayScreen: View {
    var body: some View {
        List {
            AppearanceDisplaySettingsSection()
        }
        .navigationTitle("appearance_display_group_title")
    }
}
