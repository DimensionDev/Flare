import shared
import SwiftUI
#if canImport(_Translation_SwiftUI)
    import Translation
#endif

struct TranslationLanguageScreen: View {
    @Environment(\.appSettings) private var appSettings
    @State private var isTranslating: Bool = false
    @State private var translationConfig: TranslationSession.Configuration?
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        List {
            // 翻译引擎设置
            Section("Translation Engine") {
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
            }.listRowBackground(theme.primaryBackgroundColor)

            // 翻译行为设置
            Section("Translation Behavior") {
                // 自动翻译开关
                Toggle(isOn: Binding(get: {
                    appSettings.appearanceSettings.autoTranslate
                }, set: { value in
                    appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.autoTranslate, to: value))
                })) {
                    Text("Auto Translate")
                    Text("Auto translate non-current language content")
                }
            }.listRowBackground(theme.primaryBackgroundColor)
             .disabled(true)

            // 语言管理设置 (iOS 18+)
            if #available(iOS 18, *) {
                Section("Language Management") {
                    // 离线语言包初始化
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

                    // 翻译测试功能
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
                }.listRowBackground(theme.primaryBackgroundColor)
            }
        }
        .scrollContentBackground(.hidden)
        .background(theme.secondaryBackgroundColor)
        .navigationTitle("Translation & Language")
        .navigationBarTitleDisplayMode(.inline)
        #if os(macOS)
            .toggleStyle(.switch)
        #endif
            .translationTask(translationConfig) { _ in
                isTranslating = false
            }
    }
}
