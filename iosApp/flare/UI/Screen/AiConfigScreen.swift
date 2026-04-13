import SwiftUI
import KotlinSharedUI

struct AiConfigScreen: View {
    @StateObject private var presenter = KotlinPresenter(presenter: AiConfigPresenter())
    @State private var editingField: EditableField?
    @State private var editingText: String = ""

    private enum EditableField: String, Identifiable {
        case serverUrl
        case apiKey
        case model
        case tldrPrompt

        var id: String { rawValue }
    }

    var body: some View {
        List {
            Section {
                Picker(
                    selection: Binding(
                        get: { presenter.state.aiType },
                        set: { type in
                            presenter.state.selectType(type: type)
                        }
                    )
                ) {
                    ForEach(presenter.state.supportedTypes, id: \.name) { type in
                        Text(aiTypeOptionTitle(option: type)).tag(type)
                    }
                } label: {
                    Text("AI Type")
                    Text("Select AI provider")
                }

                if presenter.state.aiType == .openAi {
                    Button {
                        beginEditing(
                            field: .serverUrl,
                            value: presenter.state.openAIServerUrl
                        )
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Server URL")
                            Text(displayText(presenter.state.openAIServerUrl))
                                .foregroundStyle(.secondary)
                                .font(.subheadline)
                        }
                    }
                    .buttonStyle(.plain)
                    .transition(.opacity.combined(with: .move(edge: .top)))
                }

                if presenter.state.aiType == .openAi {
                    Button {
                        beginEditing(
                            field: .apiKey,
                            value: presenter.state.openAIApiKey
                        )
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("API Key")
                            Text(displayText(presenter.state.openAIApiKey))
                                .foregroundStyle(.secondary)
                                .font(.subheadline)
                        }
                    }
                    .buttonStyle(.plain)
                    .transition(.opacity.combined(with: .move(edge: .top)))
                }

                if presenter.state.aiType == .openAi && !shouldShowManualModelInput {
                    let selectedModel = presenter.state.openAIModel
                    Picker(
                        selection: Binding(
                            get: { selectedModel },
                            set: { model in
                                if model.hasPrefix("__meta__") {
                                    return
                                }
                                presenter.state.setOpenAIModel(value: model)
                            }
                        )
                    ) {
                        switch onEnum(of: presenter.state.openAIModels) {
                        case .loading:
                            if !selectedModel.isEmpty {
                                Text(selectedModel).tag(selectedModel)
                            }
                            Text("Loading models...").tag("__meta__loading")
                        case .success(let data):
                            let models = (data.data as NSArray).cast(NSString.self).map(String.init)
                            ForEach(models, id: \.self) { model in
                                Text(model).tag(model)
                            }
                        case .error:
                            EmptyView()
                        }
                    } label: {
                        Text("Model")
                        Text("OpenAI model used for translation and summary")
                    }
                    .transition(.opacity.combined(with: .move(edge: .top)))
                }

                if presenter.state.aiType == .openAi && shouldShowManualModelInput {
                    Button {
                        beginEditing(
                            field: .model,
                            value: presenter.state.openAIModel
                        )
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Manual Model")
                            Text(displayModelText(presenter.state.openAIModel))
                                .foregroundStyle(.secondary)
                                .font(.subheadline)
                        }
                    }
                    .buttonStyle(.plain)
                    .transition(.opacity.combined(with: .move(edge: .top)))
                }
            }

            Section {
                Toggle(
                    isOn: Binding(
                        get: { presenter.state.aiTldr },
                        set: { newValue in
                            presenter.state.setAITldr(value: newValue)
                        }
                    )
                ) {
                    Text("ai_config_summarize")
                    Text("Summarize long text with AI")
                }

                if presenter.state.aiTldr {
                    Button {
                        beginEditing(
                            field: .tldrPrompt,
                            value: presenter.state.tldrPrompt
                        )
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Summary Prompt")
                            Text(displayText(presenter.state.tldrPrompt))
                                .foregroundStyle(.secondary)
                                .font(.subheadline)
                        }
                    }
                    .buttonStyle(.plain)
                    .transition(.opacity.combined(with: .move(edge: .top)))
                }
            }
        }
        .animation(.easeInOut(duration: 0.2), value: presenter.state.aiType == .openAi)
        .animation(.easeInOut(duration: 0.2), value: presenter.state.aiTldr)
        .sheet(item: $editingField) { field in
            NavigationStack {
                Form {
                    Section {
                        if field == .tldrPrompt {
                            TextEditor(text: $editingText)
                                .frame(minHeight: 180)
                        } else {
                            TextField(fieldPlaceholder(field: field), text: $editingText)
                        }
                    } footer: {
                        if field == .serverUrl {
                            Text(serverUrlHint)
                                .font(.footnote)
                        }
                    }
                    if field == .serverUrl {
                        let suggestions = filteredServerSuggestions(query: editingText)
                        if !suggestions.isEmpty {
                            Section("Suggestions") {
                                ForEach(suggestions, id: \.self) { suggestion in
                                    Button {
                                        editingText = suggestion
                                    } label: {
                                        Text(suggestion)
                                            .font(.callout.monospaced())
                                            .lineLimit(1)
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                    }
                }
                .navigationTitle(fieldTitle(field: field))
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button {
                            editingField = nil
                        } label: {
                            Image("fa-xmark")
                        }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button {
                            applyEdit(field: field, value: editingText)
                            editingField = nil
                        } label: {
                            Image("fa-check")
                        }
                    }
                }
            }
        }
        .navigationTitle("ai_config_title")
    }

    private func aiTypeOptionTitle(option: AiTypeOption) -> LocalizedStringResource {
        switch option {
        case .onDevice:
            return "On Device"
        case .openAi:
            return "OpenAI Compatible"
        }
    }

    private func displayText(_ value: String) -> String {
        value.isEmpty ? String(localized: "Not set") : value
    }

    private func displayModelText(_ value: String) -> String {
        value.isEmpty ? String(localized: "Select model") : value
    }

    private var shouldShowManualModelInput: Bool {
        switch onEnum(of: presenter.state.openAIModels) {
        case .loading:
            return false
        case .error:
            return true
        case .success(let data):
            let models = (data.data as NSArray).cast(NSString.self).map(String.init)
            return models.isEmpty
        }
    }

    private var serverUrlHint: LocalizedStringResource {
        "Server URL must end with '/' and support the OpenAI-compatible v1/chat/completions API."
    }

    private var serverSuggestions: [String] {
        (presenter.state.serverSuggestions as NSArray).cast(NSString.self).map(String.init)
    }

    private func filteredServerSuggestions(query: String) -> [String] {
        if query.isEmpty {
            return serverSuggestions
        }
        return serverSuggestions.filter {
            $0.localizedCaseInsensitiveContains(query)
        }
    }

    private func beginEditing(field: EditableField, value: String) {
        editingText = value
        editingField = field
    }

    private func fieldTitle(field: EditableField) -> LocalizedStringResource {
        switch field {
        case .serverUrl:
            return "Server URL"
        case .apiKey:
            return "API Key"
        case .model:
            return "Manual Model"
        case .tldrPrompt:
            return "Summary Prompt"
        }
    }

    private func fieldPlaceholder(field: EditableField) -> String {
        switch field {
        case .serverUrl:
            return "https://api.openai.com/v1/"
        case .apiKey:
            return "sk-..."
        case .model:
            return "gpt-4.1-mini"
        case .tldrPrompt:
            return ""
        }
    }

    private func applyEdit(field: EditableField, value: String) {
        switch field {
        case .serverUrl:
            presenter.state.setOpenAIServerUrl(value: value)
        case .apiKey:
            presenter.state.setOpenAIApiKey(value: value)
        case .model:
            presenter.state.setOpenAIModel(value: value)
        case .tldrPrompt:
            presenter.state.setTldrPrompt(value: value)
        }
    }
}

struct TranslationConfigScreen: View {
    @StateObject private var presenter = KotlinPresenter(presenter: AiConfigPresenter())
    @StateObject private var aiTranslationTestPresenter = KotlinPresenter(presenter: AiTranslationTestPresenter())
    @State private var editingField: TranslationEditableField?
    @State private var editingText: String = ""
    @State private var showExcludedLanguagesPicker = false
    @State private var pendingExcludedLanguages: Set<String> = []
    @State private var excludedLanguagesQuery: String = ""

    private enum TranslationEditableField: String, Identifiable {
        case translatePrompt
        case deepLApiKey
        case googleCloudApiKey
        case libreTranslateBaseUrl
        case libreTranslateApiKey

        var id: String { rawValue }
    }

    private struct LanguageOption: Identifiable {
        let tag: String
        let title: String

        var id: String { tag }
    }

    var body: some View {
        List {
            Section {
                Picker(
                    selection: Binding(
                        get: { presenter.state.translateProvider },
                        set: { provider in
                            presenter.state.selectTranslateProvider(type: provider)
                        }
                    )
                ) {
                    ForEach(presenter.state.supportedTranslateProviders, id: \.name) { provider in
                        Text(translateProviderOptionTitle(option: provider)).tag(provider)
                    }
                } label: {
                    Text("Translation Provider")
                    Text("Choose which service handles translation")
                }

                Toggle(
                    isOn: Binding(
                        get: { presenter.state.preTranslate },
                        set: { newValue in
                            presenter.state.setPreTranslate(value: newValue)
                        }
                    )
                ) {
                    Text("ai_config_pre_translate")
                    Text("ai_config_pre_translate_description")
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
                
                if presenter.state.preTranslate {
                    Button {
                        pendingExcludedLanguages = Set(excludedLanguages)
                        excludedLanguagesQuery = ""
                        showExcludedLanguagesPicker = true
                    } label: {
                        Label {
                            Text("Auto-translate excluded languages")
                            Text(displayExcludedLanguages)
                        } icon: {
                            
                        }
                    }
                    .buttonStyle(.plain)
                }
                

                switch presenter.state.translateProvider {
                case .ai:
                    Button {
                        beginEditing(
                            field: .translatePrompt,
                            value: presenter.state.translatePrompt
                        )
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Translate Prompt")
                            Text(displayText(presenter.state.translatePrompt))
                                .foregroundStyle(.secondary)
                                .font(.subheadline)
                        }
                    }
                    .buttonStyle(.plain)
                    .transition(.opacity.combined(with: .move(edge: .top)))

                    VStack(alignment: .leading, spacing: 8) {
                        Text("AI translation test")
                        Text("Run a short rich-text sample through the current AI translation setup.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        Text("Sample text")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        RichText(text: aiTranslationTestPresenter.state.sampleText)
                        Button("Test translation") {
                            aiTranslationTestPresenter.state.runTest()
                        }
                        if aiTranslationTestPresenter.state.isLoading {
                            ProgressView()
                        }
                        if let errorMessage = aiTranslationTestPresenter.state.errorMessage {
                            Text(errorMessage)
                                .foregroundStyle(.red)
                                .font(.footnote)
                        }
                        if let translatedText = aiTranslationTestPresenter.state.translatedText {
                            Text("Translated text")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            RichText(text: translatedText)
                        }
                    }
                    .padding(.vertical, 4)

                case .deepL:
                    Button {
                        beginEditing(
                            field: .deepLApiKey,
                            value: presenter.state.deepLApiKey
                        )
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("DeepL API Key")
                            Text(displayText(presenter.state.deepLApiKey))
                                .foregroundStyle(.secondary)
                                .font(.subheadline)
                        }
                    }
                    .buttonStyle(.plain)

                    Toggle(
                        isOn: Binding(
                            get: { presenter.state.deepLUsePro },
                            set: { newValue in
                                presenter.state.setDeepLUsePro(value: newValue)
                            }
                        )
                    ) {
                        Text("DeepL Pro Endpoint")
                        Text("Use api.deepl.com instead of the free endpoint")
                    }
                    .transition(.opacity.combined(with: .move(edge: .top)))

                case .googleCloud:
                    Button {
                        beginEditing(
                            field: .googleCloudApiKey,
                            value: presenter.state.googleCloudApiKey
                        )
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Google Cloud API Key")
                            Text(displayText(presenter.state.googleCloudApiKey))
                                .foregroundStyle(.secondary)
                                .font(.subheadline)
                        }
                    }
                    .buttonStyle(.plain)

                case .libreTranslate:
                    Button {
                        beginEditing(
                            field: .libreTranslateBaseUrl,
                            value: presenter.state.libreTranslateBaseUrl
                        )
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("LibreTranslate Base URL")
                            Text(displayText(presenter.state.libreTranslateBaseUrl))
                                .foregroundStyle(.secondary)
                                .font(.subheadline)
                        }
                    }
                    .buttonStyle(.plain)

                    Button {
                        beginEditing(
                            field: .libreTranslateApiKey,
                            value: presenter.state.libreTranslateApiKey
                        )
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("LibreTranslate API Key")
                            Text(displayText(presenter.state.libreTranslateApiKey))
                                .foregroundStyle(.secondary)
                                .font(.subheadline)
                        }
                    }
                    .buttonStyle(.plain)

                case .googleWeb:
                    EmptyView()

                default:
                    EmptyView()
                }
            }
        }
        .animation(.easeInOut(duration: 0.2), value: presenter.state.translateProvider.name)
        .animation(.easeInOut(duration: 0.2), value: presenter.state.preTranslate)
        .sheet(item: $editingField) { field in
            NavigationStack {
                Form {
                    Section {
                        TextEditor(text: $editingText)
                            .frame(minHeight: 180)
                    }
                }
                .navigationTitle(translationEditTitle(field))
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button {
                            editingField = nil
                        } label: {
                            Image("fa-xmark")
                        }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button {
                            applyTranslationEdit(field: field, value: editingText)
                            editingField = nil
                        } label: {
                            Image("fa-check")
                        }
                    }
                }
            }
        }
        .sheet(isPresented: $showExcludedLanguagesPicker) {
            NavigationStack {
                List {
                    ForEach(filteredLanguageOptions) { option in
                        Button {
                            if pendingExcludedLanguages.contains(option.tag) {
                                pendingExcludedLanguages.remove(option.tag)
                            } else {
                                pendingExcludedLanguages.insert(option.tag)
                            }
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(option.title)
                                    if option.title != option.tag {
                                        Text(option.tag)
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                                Spacer()
                                if pendingExcludedLanguages.contains(option.tag) {
                                    Image(systemName: "checkmark")
                                }
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
                .searchable(text: $excludedLanguagesQuery, prompt: "Search language")
                .navigationTitle("Auto-translate excluded languages")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button {
                            showExcludedLanguagesPicker = false
                        } label: {
                            Image("fa-xmark")
                        }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button {
                            presenter.state.setAutoTranslateExcludedLanguages(
                                value: languageOptions
                                    .map(\.tag)
                                    .filter { pendingExcludedLanguages.contains($0) }
                            )
                            showExcludedLanguagesPicker = false
                        } label: {
                            Image("fa-check")
                        }
                    }
                }
            }
        }
        .navigationTitle("settings_translation_title")
    }

    private func translateProviderOptionTitle(option: TranslateProviderOption) -> LocalizedStringResource {
        switch option {
        case .ai:
            return "AI"
        case .googleWeb:
            return "Google Translate (Web)"
        case .deepL:
            return "DeepL"
        case .googleCloud:
            return "Google Cloud Translate"
        case .libreTranslate:
            return "LibreTranslate"
        default:
            return "AI"
        }
    }

    private func displayText(_ value: String, fallback: LocalizedStringResource = "Not set") -> String {
        value.isEmpty ? String(localized: fallback) : value
    }

    private var excludedLanguages: [String] {
        (presenter.state.autoTranslateExcludedLanguages as NSArray).cast(NSString.self).map(String.init)
    }

    private var languageOptions: [LanguageOption] {
        let current = Locale.current
        let baseOptions = Locale.isoLanguageCodes.map { code in
            LanguageOption(
                tag: code,
                title: current.localizedString(forLanguageCode: code) ?? code
            )
        }
        let specialOptions = [
            LanguageOption(tag: "zh-CN", title: current.localizedString(forIdentifier: "zh-Hans") ?? "Chinese (Simplified)"),
            LanguageOption(tag: "zh-TW", title: current.localizedString(forIdentifier: "zh-Hant") ?? "Chinese (Traditional)")
        ]
        let knownTags = Set((specialOptions + baseOptions).map(\.tag))
        let customOptions = excludedLanguages.filter { !knownTags.contains($0) }.map { LanguageOption(tag: $0, title: $0) }
        return (specialOptions + baseOptions + customOptions)
            .reduce(into: [String: LanguageOption]()) { result, option in
                result[option.tag] = result[option.tag] ?? option
            }
            .values
            .sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
    }

    private var displayExcludedLanguages: String {
        if excludedLanguages.isEmpty {
            return String(localized: "Not set")
        }
        let titles = Dictionary(uniqueKeysWithValues: languageOptions.map { ($0.tag, $0.title) })
        return excludedLanguages.map { titles[$0] ?? $0 }.joined(separator: ", ")
    }

    private var filteredLanguageOptions: [LanguageOption] {
        if excludedLanguagesQuery.isEmpty {
            return languageOptions
        }
        return languageOptions.filter { option in
            option.title.localizedCaseInsensitiveContains(excludedLanguagesQuery) ||
            option.tag.localizedCaseInsensitiveContains(excludedLanguagesQuery)
        }
    }

    private func beginEditing(field: TranslationEditableField, value: String) {
        editingText = value
        editingField = field
    }

    private func translationEditTitle(_ field: TranslationEditableField) -> LocalizedStringResource {
        switch field {
        case .translatePrompt:
            return "Translate Prompt"
        case .deepLApiKey:
            return "DeepL API Key"
        case .googleCloudApiKey:
            return "Google Cloud API Key"
        case .libreTranslateBaseUrl:
            return "LibreTranslate Base URL"
        case .libreTranslateApiKey:
            return "LibreTranslate API Key"
        }
    }

    private func applyTranslationEdit(field: TranslationEditableField, value: String) {
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
}
