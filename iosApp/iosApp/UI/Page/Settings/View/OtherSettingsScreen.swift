import shared
import SwiftUI

struct OtherSettingsScreen: View {
    @Environment(\.appSettings) private var appSettings
    
    var body: some View {
        List {
            Section("Browser Settings") {
                HStack {
                    Label("Default Browser", systemImage: "network")
                    Spacer()
                    Picker("", selection: Binding(get: {
                        appSettings.otherSettings.preferredBrowser
                    }, set: { value in
                        appSettings.updateOther(newValue: appSettings.otherSettings.also { settings in
                            settings.preferredBrowser = value
                        })
                    })) {
                        Text("In-App Browser")
                            .tag(PreferredBrowser.inAppSafari)
                        Text("System Browser")
                            .tag(PreferredBrowser.safari)
                    }
                    .labelsHidden()
                    .pickerStyle(.menu)
                }
            }
            .buttonStyle(.plain)
            .navigationTitle("Other Settings")
        }
        #if os(macOS)
        .toggleStyle(.switch)
        #endif
    }
}

extension OtherSettings {
    func also(transform: (OtherSettings) -> Void) -> OtherSettings {
        transform(self)
        return self
    }
}

#Preview {
    OtherSettingsScreen()
} 