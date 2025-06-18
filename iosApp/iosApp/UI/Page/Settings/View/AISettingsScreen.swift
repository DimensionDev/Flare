import SensitiveContentAnalysis
import shared
import SwiftUI

struct AISettingsScreen: View {
    @Environment(\.appSettings) private var appSettings
    @Environment(FlareTheme.self) private var theme
    private let analyzer = SCSensitivityAnalyzer()

    private var isSystemAnalysisEnabled: Bool {
        analyzer.analysisPolicy.rawValue != 0
    }

    var body: some View {
        List {
            Section {
                VStack(alignment: .leading, spacing: 12) {
                    Text("AI Features")
                        .font(.headline)
                    Text("AI-powered features will be added here in future updates.")
                        .font(.body)
                        .foregroundColor(.secondary)
                    Text("Note: Sensitive Content Analysis has been moved to Media & Content settings for better organization.")
                        .font(.caption)
                        .foregroundColor(.orange)
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
