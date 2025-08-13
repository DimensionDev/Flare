import SensitiveContentAnalysis
import shared
import SwiftUI

struct AISettingsScreen: View {
    @Environment(\.appSettings) private var appSettings
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        List {
            Section {
                VStack(alignment: .leading, spacing: 12) {
                    Text("AI Features")
                        .font(.headline)
                    Text("AI-powered features will be added here in future updates.")
                        .font(.body)
                        .foregroundColor(.secondary)
                }
                .padding(.vertical, 8)
            }
        }
        .scrollContentBackground(.hidden)
        .background(theme.secondaryBackgroundColor)
        .navigationTitle("AI Settings")
        .navigationBarTitleDisplayMode(.inline)
    }
}
