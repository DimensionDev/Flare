import shared
import SwiftUI

struct HapticFeedbackScreen: View {
    @Environment(\.appSettings) private var appSettings
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        List {
            Section {
                Toggle(isOn: Binding(get: {
                    appSettings.appearanceSettings.hapticFeedback.isEnabled
                }, set: { value in
                    FlareHapticManager.shared.selection()
                    appSettings.update(newValue: appSettings.appearanceSettings.changing(
                        path: \.hapticFeedback.isEnabled, to: value
                    ))
                })) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Enable Haptic Feedback")
                            .font(.body)
                        Text("Provide vibration feedback for button taps, tab switches, and other interactions")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }

                if appSettings.appearanceSettings.hapticFeedback.isEnabled {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Feedback Intensity")
                                .font(.body)
                            Text("Choose the vibration strength for haptic feedback")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                        Picker("", selection: Binding(get: {
                            appSettings.appearanceSettings.hapticFeedback.intensity
                        }, set: { value in
                            FlareHapticManager.shared.generate(.impact(mapIntensityToImpactStyle(value)))

                            appSettings.update(newValue: appSettings.appearanceSettings.changing(
                                path: \.hapticFeedback.intensity, to: value
                            ))
                        })) {
                            ForEach(HapticFeedbackSettings.HapticIntensity.allCases, id: \.self) { intensity in
                                Text(intensity.displayName).tag(intensity)
                            }
                        }
                        .labelsHidden()
                        .pickerStyle(.menu)
                    }
                }
            }
        }
        .listRowBackground(theme.primaryBackgroundColor)
        .scrollContentBackground(.hidden)
        .background(theme.secondaryBackgroundColor)
        .navigationTitle("Haptic Feedback")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func mapIntensityToImpactStyle(_ intensity: HapticFeedbackSettings.HapticIntensity) -> FlareHapticManager.ImpactStyle {
        switch intensity {
        case .light: .light
        case .medium: .medium
        case .heavy: .heavy
        }
    }
}
