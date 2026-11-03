import SwiftUI
import FlareAppleUI

struct AppearanceMediaScreen: View {
    var body: some View {
        List {
            AppearanceMediaSettingsSection()
        }
        .timelineMediaViewport()
        .navigationTitle("appearance_media_group_title")
    }
}
