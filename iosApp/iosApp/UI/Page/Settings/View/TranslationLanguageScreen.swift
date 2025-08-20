import shared
import SwiftUI
#if canImport(_Translation_SwiftUI)
    import Translation
#endif

struct TranslationLanguageScreen: View {
    @Environment(\.appSettings) private var appSettings
    @State private var showSystemTranslationTest: Bool = false
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        List {
            Section("Translation Engine") {
                HStack {
                    Label("Translation Engine", systemImage: "character.bubble")
                    Spacer()
                    Picker("", selection: Binding(get: {
                        appSettings.otherSettings.translationProvider
                    }, set: { value in
                        FlareHapticManager.shared.selection()
                        appSettings.updateOther(newValue: appSettings.otherSettings.also { settings in
                            settings.translationProvider = value
                        })
                    })) {
                        Text("Google Translate")
                            .tag(TranslationProvider.google)
                        Text("System Translation")
                            .tag(TranslationProvider.systemOffline)
                    }
                    .labelsHidden()
                    .pickerStyle(.menu)
                }
            }.listRowBackground(theme.primaryBackgroundColor)

            VStack(alignment: .leading, spacing: 12) {
                Text("Test Translation")
                    .font(.subheadline)
                    .foregroundColor(.gray)

                Text("Hello World!")
                    .font(.body)

                TranslatableText(originalText: "Hello World!")
                    .id(appSettings.otherSettings.translationProvider) // 切换引擎时刷新
            }
            .padding(.vertical, 8)

            if appSettings.otherSettings.translationProvider == .systemOffline {
                Button(action: {
                    FlareHapticManager.shared.buttonPress()
                    showSystemTranslationTest = true
                }) {
                    HStack {
                        Label("Test System Translation", systemImage: "character.bubble.fill")
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(theme.secondaryBackgroundColor)
        .navigationTitle("Translation & Language")
        .navigationBarTitleDisplayMode(.inline)
        #if os(macOS)
            .toggleStyle(.switch)
        #endif
        #if canImport(_Translation_SwiftUI)
        .addTranslateView(isPresented: $showSystemTranslationTest, text: "hello world")
        #endif
    }
}

extension OtherSettings {
    func also(transform: (OtherSettings) -> Void) -> OtherSettings {
        transform(self)
        return self
    }
}
