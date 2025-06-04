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
                Toggle(isOn: Binding(
                    get: {
                        isSystemAnalysisEnabled && appSettings.otherSettings.sensitiveContentAnalysisEnabled
                    },
                    set: { newValue in
                        if isSystemAnalysisEnabled {
                            appSettings.updateOther(newValue: appSettings.otherSettings.also { settings in
                                settings.sensitiveContentAnalysisEnabled = newValue
                            })
                        }
                    }
                )) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Sensitive Content Analysis")
                            .font(.body)
                        Text(isSystemAnalysisEnabled ?
                            "Automatically detect and blur sensitive images using Apple's Sensitive Content Analysis framework runs locally on your device" :
                            "No feature enabled that is requiring Sensitive Analysis on device, analysis will be disabled. Please enable it in System Settings > Privacy & Security > Sensitive Content Warning.")
                            .font(.caption)
                            .foregroundColor(isSystemAnalysisEnabled ? .secondary : .orange)
                    }
                }
                .disabled(!isSystemAnalysisEnabled)
            }
        }
        .scrollContentBackground(.hidden)
        .background(theme.secondaryBackgroundColor)
        .navigationTitle("AI Settings")
        .navigationBarTitleDisplayMode(.inline)
    }
}
