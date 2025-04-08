import shared
import SwiftUI
#if canImport(_Translation_SwiftUI)
    import Translation
#endif

struct BaseSettingScreen: View {
    @Environment(\.appSettings) private var appSettings
    @State private var isTranslating: Bool = false
    @State private var translationConfig: TranslationSession.Configuration?

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

            Section("Translation Settings") {
                HStack {
                    Label("Translation Engine", systemImage: "character.bubble")
                    Spacer()
                    Picker("", selection: Binding(get: {
                        appSettings.otherSettings.translationProvider
                    }, set: { value in
                        appSettings.updateOther(newValue: appSettings.otherSettings.also { settings in
                            settings.translationProvider = value
                        })
                    })) {
                        Text("Google Translate")
                            .tag(TranslationProvider.google)
                        Text("System Offline Translation")
                            .tag(TranslationProvider.systemOffline)
                    }
                    .labelsHidden()
                    .pickerStyle(.menu)
                }

                if #available(iOS 18, *) {
                    if appSettings.otherSettings.translationProvider == .systemOffline {
                        Button(action: {
                            if translationConfig == nil {
                                translationConfig = TranslationSession.Configuration()
                            } else {
                                translationConfig?.invalidate()
                            }
                            isTranslating = true
                        }) {
                            HStack {
                                Label("First Initialize Offline Languages", systemImage: "arrow.down.circle")
                                Spacer()
                                if isTranslating {
                                    ProgressView()
                                        .controlSize(.small)
                                } else {
                                    Image(systemName: "chevron.right")
                                        .foregroundColor(.gray)
                                }
                            }
                        }
                        .buttonStyle(.plain)
                    }

                    VStack(alignment: .leading, spacing: 12) {
                        Text("Test Translation")
                            .font(.subheadline)
                            .foregroundColor(.gray)

                        Text("Hello World!")
                            .font(.body)

                        // 使用TranslatableText进行翻译测试
                        TranslatableText(originalText: "Hello World!")
                            .id(appSettings.otherSettings.translationProvider) // 切换引擎时刷新
                    }
                    .padding(.vertical, 8)
                }

                Toggle(isOn: Binding(get: {
                    appSettings.appearanceSettings.autoTranslate
                }, set: { value in
                    appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.autoTranslate, to: value))
                })) {
                    Text("Auto Translate")
                    Text("Auto translate non-current language content")
                }
            }
            .buttonStyle(.plain)
            .navigationTitle("Other Settings")
        }
        #if os(macOS)
        .toggleStyle(.switch)
        #endif
        .translationTask(translationConfig) { _ in
            isTranslating = false
        }
    }
}

extension OtherSettings {
    func also(transform: (OtherSettings) -> Void) -> OtherSettings {
        transform(self)
        return self
    }
}
