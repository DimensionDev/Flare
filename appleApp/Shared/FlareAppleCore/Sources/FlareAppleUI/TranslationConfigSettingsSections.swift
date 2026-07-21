import Combine
import FlareAppleCore
import KotlinSharedUI
import SwiftUI

public struct TranslationConfigSettingsView: View {
    @StateObject private var state = TranslationConfigSettingsState()

    public init() {}

    public var body: some View {
        List {
            TranslationConfigSettingsSectionsContent(state: state)
        }
        .translationConfigSettingsSheets(state: state)
    }
}

public struct TranslationConfigSettingsSections: View {
    @StateObject private var state = TranslationConfigSettingsState()

    public init() {}

    public var body: some View {
        TranslationConfigSettingsSectionsContent(state: state)
            .translationConfigSettingsSheets(state: state)
    }
}

private final class TranslationConfigSettingsState: ObservableObject {
    let presenter = KotlinPresenter(presenter: AiConfigPresenter())
    let aiTranslationTestPresenter = KotlinPresenter(presenter: AiTranslationTestPresenter())
    @Published var editingField: TranslationConfigEditableField?
    @Published var editingText = ""
    @Published var showExcludedLanguagesPicker = false
    @Published var pendingExcludedLanguages: Set<String> = []
    @Published var excludedLanguagesQuery = ""

    private var cancellables = Set<AnyCancellable>()

    init() {
        presenter.$state
            .dropFirst()
            .sink { [weak self] _ in
                self?.objectWillChange.send()
            }
            .store(in: &cancellables)
        aiTranslationTestPresenter.$state
            .dropFirst()
            .sink { [weak self] _ in
                self?.objectWillChange.send()
            }
            .store(in: &cancellables)
    }

    func beginEditing(field: TranslationConfigEditableField, value: String) {
        editingText = value
        editingField = field
    }

    func beginEditingExcludedLanguages() {
        pendingExcludedLanguages = Set(excludedLanguages)
        excludedLanguagesQuery = ""
        showExcludedLanguagesPicker = true
    }

    func applyTranslationEdit(field: TranslationConfigEditableField, value: String) {
        switch field {
        case .translatePrompt:
            presenter.state.setTranslatePrompt(value: value)
        case .deepLApiKey:
            presenter.state.setDeepLApiKey(value: value)
        case .googleCloudApiKey:
            presenter.state.setGoogleCloudApiKey(value: value)
        case .libreTranslateBaseUrl:
            presenter.state.setLibreTranslateBaseUrl(value: value)
        case .libreTranslateApiKey:
            presenter.state.setLibreTranslateApiKey(value: value)
        }
    }

    func applyExcludedLanguages() {
        presenter.state.setAutoTranslateExcludedLanguages(
            value: languageOptions
                .map(\.tag)
                .filter { pendingExcludedLanguages.contains($0) }
        )
        showExcludedLanguagesPicker = false
    }

    var excludedLanguages: [String] {
        (presenter.state.autoTranslateExcludedLanguages as NSArray).cast(NSString.self).map(String.init)
    }

    var languageOptions: [TranslationLanguageOption] {
        let current = Locale.current
        let baseOptions = Locale.LanguageCode.isoLanguageCodes.map { code in
            let tag = code.identifier
            return TranslationLanguageOption(
                tag: tag,
                title: current.localizedString(forLanguageCode: tag) ?? tag
            )
        }
        let specialOptions = [
            TranslationLanguageOption(
                tag: "zh-CN",
                title: current.localizedString(forIdentifier: "zh-Hans") ?? "Chinese (Simplified)"
            ),
            TranslationLanguageOption(
                tag: "zh-TW",
                title: current.localizedString(forIdentifier: "zh-Hant") ?? "Chinese (Traditional)"
            )
        ]
        let knownTags = Set((specialOptions + baseOptions).map(\.tag))
        let customOptions = excludedLanguages
            .filter { !knownTags.contains($0) }
            .map { TranslationLanguageOption(tag: $0, title: $0) }
        return (specialOptions + baseOptions + customOptions)
            .reduce(into: [String: TranslationLanguageOption]()) { result, option in
                result[option.tag] = result[option.tag] ?? option
            }
            .values
            .sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
    }

    var displayExcludedLanguages: String {
        if excludedLanguages.isEmpty {
            return FlareAppleUILocalization.string("Not set")
        }
        let titles = Dictionary(uniqueKeysWithValues: languageOptions.map { ($0.tag, $0.title) })
        return excludedLanguages.map { titles[$0] ?? $0 }.joined(separator: ", ")
    }

    var filteredLanguageOptions: [TranslationLanguageOption] {
        if excludedLanguagesQuery.isEmpty {
            return languageOptions
        }
        return languageOptions.filter { option in
            option.title.localizedCaseInsensitiveContains(excludedLanguagesQuery) ||
                option.tag.localizedCaseInsensitiveContains(excludedLanguagesQuery)
        }
    }
}

private struct TranslationConfigSettingsSectionsContent: View {
    @ObservedObject var state: TranslationConfigSettingsState

    var body: some View {
        Section {
            Picker(
                selection: Binding(
                    get: { state.presenter.state.translateProvider },
                    set: { provider in
                        state.presenter.state.selectTranslateProvider(type: provider)
                    }
                )
            ) {
                ForEach(state.presenter.state.supportedTranslateProviders, id: \.name) { provider in
                    Text(translateProviderOptionTitleKey(option: provider), bundle: FlareAppleUILocalization.bundle)
                        .tag(provider)
                }
            } label: {
                Text("Translation Provider", bundle: FlareAppleUILocalization.bundle)
                Text("Choose which service handles translation", bundle: FlareAppleUILocalization.bundle)
            }

            Toggle(
                isOn: Binding(
                    get: { state.presenter.state.preTranslate },
                    set: { newValue in
                        state.presenter.state.setPreTranslate(value: newValue)
                    }
                )
            ) {
                Text("ai_config_pre_translate", bundle: FlareAppleUILocalization.bundle)
                Text("ai_config_pre_translate_description", bundle: FlareAppleUILocalization.bundle)
            }
            .transition(.opacity.combined(with: .move(edge: .top)))

            Toggle(
                isOn: Binding(
                    get: { state.presenter.state.showOriginalWithTranslation },
                    set: { state.presenter.state.setShowOriginalWithTranslation(value: $0) }
                )
            ) {
                Text("Show original with translation", bundle: FlareAppleUILocalization.bundle)
                Text("Display the original text before its translation.", bundle: FlareAppleUILocalization.bundle)
            }

            if state.presenter.state.preTranslate {
                Toggle(
                    isOn: Binding(
                        get: { state.presenter.state.preferPlatformTranslation },
                        set: { state.presenter.state.setPreferPlatformTranslation(value: $0) }
                    )
                ) {
                    Text("Prefer platform translations", bundle: FlareAppleUILocalization.bundle)
                    Text(
                        "Use a platform-provided translation when one is available during auto-translate.",
                        bundle: FlareAppleUILocalization.bundle
                    )
                }

                Button {
                    state.beginEditingExcludedLanguages()
                } label: {
                    Label {
                        Text("Auto-translate excluded languages", bundle: FlareAppleUILocalization.bundle)
                        Text(state.displayExcludedLanguages)
                    } icon: {
                        EmptyView()
                    }
                }
                .buttonStyle(.plain)
            }

            providerSpecificControls
        }
        .animation(.easeInOut(duration: 0.2), value: state.presenter.state.translateProvider.name)
        .animation(.easeInOut(duration: 0.2), value: state.presenter.state.preTranslate)
    }

    @ViewBuilder
    private var providerSpecificControls: some View {
        switch state.presenter.state.translateProvider {
        case .ai:
            editButton(field: .translatePrompt, value: state.presenter.state.translatePrompt)

            VStack(alignment: .leading, spacing: 8) {
                Text("AI translation test", bundle: FlareAppleUILocalization.bundle)
                Text(
                    "Run a short rich-text sample through the current AI translation setup.",
                    bundle: FlareAppleUILocalization.bundle
                )
                .font(.subheadline)
                .foregroundStyle(.secondary)
                Text("Sample text", bundle: FlareAppleUILocalization.bundle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                RichText(text: state.aiTranslationTestPresenter.state.sampleText)
                Button {
                    state.aiTranslationTestPresenter.state.runTest()
                } label: {
                    Text("Test translation", bundle: FlareAppleUILocalization.bundle)
                }
                if state.aiTranslationTestPresenter.state.isLoading {
                    ProgressView()
                }
                if let errorMessage = state.aiTranslationTestPresenter.state.errorMessage {
                    Text(errorMessage)
                        .foregroundStyle(.red)
                        .font(.footnote)
                }
                if let translatedText = state.aiTranslationTestPresenter.state.translatedText {
                    Text("Translated text", bundle: FlareAppleUILocalization.bundle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    RichText(text: translatedText)
                }
            }
            .padding(.vertical, 4)

        case .deepL:
            editButton(field: .deepLApiKey, value: state.presenter.state.deepLApiKey)

            Toggle(
                isOn: Binding(
                    get: { state.presenter.state.deepLUsePro },
                    set: { newValue in
                        state.presenter.state.setDeepLUsePro(value: newValue)
                    }
                )
            ) {
                Text("DeepL Pro Endpoint", bundle: FlareAppleUILocalization.bundle)
                Text("Use api.deepl.com instead of the free endpoint", bundle: FlareAppleUILocalization.bundle)
            }
            .transition(.opacity.combined(with: .move(edge: .top)))

        case .googleCloud:
            editButton(field: .googleCloudApiKey, value: state.presenter.state.googleCloudApiKey)

        case .libreTranslate:
            editButton(field: .libreTranslateBaseUrl, value: state.presenter.state.libreTranslateBaseUrl)
            editButton(field: .libreTranslateApiKey, value: state.presenter.state.libreTranslateApiKey)

        case .googleWeb:
            EmptyView()
        }
    }

    private func editButton(field: TranslationConfigEditableField, value: String) -> some View {
        Button {
            state.beginEditing(field: field, value: value)
        } label: {
            Label {
                Text(field.titleKey, bundle: FlareAppleUILocalization.bundle)
                Text(displayText(value))
            } icon: {
                EmptyView()
            }

        }
        .buttonStyle(.plain)
        .transition(.opacity.combined(with: .move(edge: .top)))
    }

    private func translateProviderOptionTitleKey(option: TranslateProviderOption) -> LocalizedStringKey {
        switch option {
        case .ai:
            "AI"
        case .googleWeb:
            "Google Translate (Web)"
        case .deepL:
            "DeepL"
        case .googleCloud:
            "Google Cloud Translate"
        case .libreTranslate:
            "LibreTranslate"
        }
    }

    private func displayText(_ value: String, fallback: String = "Not set") -> String {
        value.isEmpty ? FlareAppleUILocalization.string(fallback) : value
    }
}

private struct TranslationConfigEditSheet: View {
    @ObservedObject var state: TranslationConfigSettingsState
    let field: TranslationConfigEditableField

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextEditor(text: $state.editingText)
                        .frame(minHeight: 180)
                }
            }
            .formStyle(.grouped)
            .navigationTitle(Text(field.titleKey, bundle: FlareAppleUILocalization.bundle))
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button {
                        state.editingField = nil
                    } label: {
                        Image(fontAwesome: .xmark)
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        state.applyTranslationEdit(field: field, value: state.editingText)
                        state.editingField = nil
                    } label: {
                        Image(fontAwesome: .check)
                    }
                }
            }
        }
    }
}

private struct TranslationExcludedLanguagesSheet: View {
    @ObservedObject var state: TranslationConfigSettingsState

    var body: some View {
        NavigationStack {
            languageList
            #if os(iOS)
            .environment(\.editMode, .constant(.active))
            #endif
            .searchable(
                text: $state.excludedLanguagesQuery,
                prompt: Text("Search language", bundle: FlareAppleUILocalization.bundle)
            )
            .navigationTitle(Text("Auto-translate excluded languages", bundle: FlareAppleUILocalization.bundle))
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button {
                        state.showExcludedLanguagesPicker = false
                    } label: {
                        Image(fontAwesome: .xmark)
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        state.applyExcludedLanguages()
                    } label: {
                        Image(fontAwesome: .check)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var languageList: some View {
        #if os(macOS)
        List(state.filteredLanguageOptions) { option in
            Toggle(isOn: Binding(
                get: {
                    state.pendingExcludedLanguages.contains(option.tag)
                },
                set: { isSelected in
                    if isSelected {
                        state.pendingExcludedLanguages.insert(option.tag)
                    } else {
                        state.pendingExcludedLanguages.remove(option.tag)
                    }
                }
            )) {
                languageLabel(option: option)
            }
            .toggleStyle(.checkbox)
        }
        .frame(height: 400)
        #else
        List(selection: $state.pendingExcludedLanguages) {
            ForEach(state.filteredLanguageOptions) { option in
                languageLabel(option: option)
                    .tag(option.tag)
            }
        }
        #endif
    }

    private func languageLabel(option: TranslationLanguageOption) -> some View {
        Label {
            Text(option.title)
            if option.title != option.tag {
                Text(option.tag)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        } icon: {
            EmptyView()
        }
    }
}

private extension View {
    func translationConfigSettingsSheets(state: TranslationConfigSettingsState) -> some View {
        sheet(item: Binding(
            get: { state.editingField },
            set: { state.editingField = $0 }
        )) { field in
            TranslationConfigEditSheet(state: state, field: field)
        }
        .sheet(isPresented: Binding(
            get: { state.showExcludedLanguagesPicker },
            set: { state.showExcludedLanguagesPicker = $0 }
        )) {
            TranslationExcludedLanguagesSheet(state: state)
        }
    }
}

private enum TranslationConfigEditableField: String, Identifiable {
    case translatePrompt
    case deepLApiKey
    case googleCloudApiKey
    case libreTranslateBaseUrl
    case libreTranslateApiKey

    var id: String { rawValue }

    var titleKey: LocalizedStringKey {
        switch self {
        case .translatePrompt:
            "Translate Prompt"
        case .deepLApiKey:
            "DeepL API Key"
        case .googleCloudApiKey:
            "Google Cloud API Key"
        case .libreTranslateBaseUrl:
            "LibreTranslate Base URL"
        case .libreTranslateApiKey:
            "LibreTranslate API Key"
        }
    }
}

private struct TranslationLanguageOption: Identifiable {
    let tag: String
    let title: String

    var id: String { tag }
}
