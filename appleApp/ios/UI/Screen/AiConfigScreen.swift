import FlareAppleUI
import SwiftUI

struct AiConfigScreen: View {
    var body: some View {
        AiConfigSettingsView()
            .navigationTitle("AI")
    }
}

struct TranslationConfigScreen: View {
    var body: some View {
        TranslationConfigSettingsView()
            .navigationTitle("settings_translation_title")
    }
}
