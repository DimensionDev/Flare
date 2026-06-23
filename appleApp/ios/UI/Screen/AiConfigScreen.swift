import FlareAppleUI
import SwiftUI

struct AiConfigScreen: View {
    var body: some View {
        AiConfigSettingsView()
            .navigationTitle("ai_config_title")
    }
}

struct TranslationConfigScreen: View {
    var body: some View {
        TranslationConfigSettingsView()
            .navigationTitle("settings_translation_title")
    }
}
