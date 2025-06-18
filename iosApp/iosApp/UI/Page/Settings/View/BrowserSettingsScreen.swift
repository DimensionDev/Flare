import shared
import SwiftUI

struct BrowserSettingsScreen: View {
    @Environment(\.appSettings) private var appSettings
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        List {
            // 浏览器集成设置
            Section("Browser Integration") {
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

                // 阅读器视图设置 (如果需要的话，目前在数据模型中存在但UI中未使用)
                // Toggle(isOn: Binding(get: {
                //     appSettings.otherSettings.inAppBrowserReaderView
                // }, set: { value in
                //     appSettings.updateOther(newValue: appSettings.otherSettings.also { settings in
                //         settings.inAppBrowserReaderView = value
                //     })
                // })) {
                //     Text("Reader View")
                //     Text("Enable reader view in in-app browser")
                // }
            }.listRowBackground(theme.primaryBackgroundColor)
        }
        .scrollContentBackground(.hidden)
        .background(theme.secondaryBackgroundColor)
        .navigationTitle("Browser Settings")
        .navigationBarTitleDisplayMode(.inline)
        #if os(macOS)
            .toggleStyle(.switch)
        #endif
    }
}
