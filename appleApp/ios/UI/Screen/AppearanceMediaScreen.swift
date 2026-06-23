import SwiftUI
import FlareAppleUI

struct AppearanceMediaScreen: View {
    var body: some View {
        List {
            AppearanceMediaSettingsSection()
        }
        .navigationTitle("appearance_media_group_title")
    }
}
