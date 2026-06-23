import AppleFontAwesome
import Combine
import FlareAppleCore
import KotlinSharedUI
import SwiftUI

public struct AiConfigSettingsView: View {
    @StateObject private var state = AiConfigSettingsState()

    public init() {}

    public var body: some View {
        List {
            AiConfigSettingsSectionsContent(state: state)
        }
        .aiConfigSettingsSheets(state: state)
    }
}

public struct AiConfigSettingsSections: View {
    @StateObject private var state = AiConfigSettingsState()

    public init() {}

    public var body: some View {
        AiConfigSettingsSectionsContent(state: state)
            .aiConfigSettingsSheets(state: state)
    }
}

private final class AiConfigSettingsState: ObservableObject {
    let presenter = KotlinPresenter(presenter: AiConfigPresenter())
    @Published var editingField: AiConfigEditableField?
    @Published var editingText = ""

    private var cancellables = Set<AnyCancellable>()

    init() {
        presenter.$state
            .dropFirst()
            .sink { [weak self] _ in
                self?.objectWillChange.send()
            }
            .store(in: &cancellables)
    }

    func beginEditing(field: AiConfigEditableField, value: String) {
        editingText = value
        editingField = field
    }

    func applyEdit(field: AiConfigEditableField, value: String) {
        switch field {
        case .serverUrl:
            presenter.state.setOpenAIServerUrl(value: value)
        case .apiKey:
            presenter.state.setOpenAIApiKey(value: value)
        case .extraBody:
            presenter.state.setOpenAIExtraBody(value: value)
        case .model:
            presenter.state.setOpenAIModel(value: value)
        case .tldrPrompt:
            presenter.state.setTldrPrompt(value: value)
        }
    }
}

private struct AiConfigSettingsSectionsContent: View {
    @ObservedObject var state: AiConfigSettingsState

    var body: some View {
        Group {
            Section {
                Picker(
                    selection: Binding(
                        get: { state.presenter.state.aiType },
                        set: { type in
                            state.presenter.state.selectType(type: type)
                        }
                    )
                ) {
                    ForEach(state.presenter.state.supportedTypes, id: \.name) { type in
                        Text(aiTypeOptionTitleKey(option: type), bundle: FlareAppleUILocalization.bundle).tag(type)
                    }
                } label: {
                    Text("AI Type", bundle: FlareAppleUILocalization.bundle)
                    Text("Select AI provider", bundle: FlareAppleUILocalization.bundle)
                }

                if state.presenter.state.aiType == .openAi {
                    editButton(field: .serverUrl, value: state.presenter.state.openAIServerUrl)
                    editButton(field: .apiKey, value: state.presenter.state.openAIApiKey)

                    Picker(
                        selection: Binding(
                            get: { state.presenter.state.openAIReasoningEffort },
                            set: { effort in
                                state.presenter.state.setOpenAIReasoningEffort(value: effort)
                            }
                        )
                    ) {
                        ForEach(state.presenter.state.supportedOpenAIReasoningEfforts, id: \.name) { effort in
                            Text(reasoningEffortTitleKey(option: effort), bundle: FlareAppleUILocalization.bundle)
                                .tag(effort)
                        }
                    } label: {
                        Text("Reasoning Effort", bundle: FlareAppleUILocalization.bundle)
                        Text(
                            "Choose how much effort the model spends on reasoning. Default uses the provider's default behavior.",
                            bundle: FlareAppleUILocalization.bundle
                        )
                    }
                    .transition(.opacity.combined(with: .move(edge: .top)))

                    editButton(field: .extraBody, value: state.presenter.state.openAIExtraBody)

                    if !shouldShowManualModelInput {
                        let selectedModel = state.presenter.state.openAIModel
                        Picker(
                            selection: Binding(
                                get: { selectedModel },
                                set: { model in
                                    if model.hasPrefix("__meta__") {
                                        return
                                    }
                                    state.presenter.state.setOpenAIModel(value: model)
                                }
                            )
                        ) {
                            switch onEnum(of: state.presenter.state.openAIModels) {
                            case .loading:
                                if !selectedModel.isEmpty {
                                    Text(selectedModel).tag(selectedModel)
                                }
                                Text("Loading models...", bundle: FlareAppleUILocalization.bundle)
                                    .tag("__meta__loading")
                            case .success(let data):
                                let models = (data.data as NSArray).cast(NSString.self).map(String.init)
                                ForEach(models, id: \.self) { model in
                                    Text(model).tag(model)
                                }
                            case .error:
                                EmptyView()
                            }
                        } label: {
                            Text("Model", bundle: FlareAppleUILocalization.bundle)
                            Text("AI model used for translation and summary", bundle: FlareAppleUILocalization.bundle)
                        }
                        .transition(.opacity.combined(with: .move(edge: .top)))
                    }

                    if shouldShowManualModelInput {
                        editButton(field: .model, value: state.presenter.state.openAIModel)
                    }
                }
            }

            Section {
                Toggle(
                    isOn: Binding(
                        get: { state.presenter.state.aiAgent },
                        set: { newValue in
                            state.presenter.state.setAIAgent(value: newValue)
                        }
                    )
                ) {
                    Text("ai_config_post_insight", bundle: FlareAppleUILocalization.bundle)
                    Text("ai_config_post_insight_description", bundle: FlareAppleUILocalization.bundle)
                }
            }

            Section {
                Toggle(
                    isOn: Binding(
                        get: { state.presenter.state.aiTldr },
                        set: { newValue in
                            state.presenter.state.setAITldr(value: newValue)
                        }
                    )
                ) {
                    Text("ai_config_summarize", bundle: FlareAppleUILocalization.bundle)
                    Text("Summarize long text with AI", bundle: FlareAppleUILocalization.bundle)
                }

                if state.presenter.state.aiTldr {
                    editButton(field: .tldrPrompt, value: state.presenter.state.tldrPrompt)
                }
            }
        }
        .animation(.easeInOut(duration: 0.2), value: state.presenter.state.aiType == .openAi)
        .animation(.easeInOut(duration: 0.2), value: state.presenter.state.aiTldr)
    }

    private func editButton(field: AiConfigEditableField, value: String) -> some View {
        Button {
            state.beginEditing(field: field, value: value)
        } label: {
            Label {
                Text(field.titleKey, bundle: FlareAppleUILocalization.bundle)
                if field == .extraBody {
                    if !value.isEmpty {
                        Text(value)
                    }
                } else {
                    Text(field.displayValue(value))
                }
            } icon: {
                EmptyView()
            }

        }
        .buttonStyle(.plain)
        .transition(.opacity.combined(with: .move(edge: .top)))
    }

    private func aiTypeOptionTitleKey(option: AiTypeOption) -> LocalizedStringKey {
        switch option {
        case .onDevice:
            "On Device"
        case .openAi:
            "AI-compatible API"
        }
    }

    private func reasoningEffortTitleKey(option: AiReasoningEffortOption) -> LocalizedStringKey {
        switch option {
        case .default:
            "Default"
        case .low:
            "Low"
        case .medium:
            "Medium"
        case .high:
            "High"
        }
    }

    private var shouldShowManualModelInput: Bool {
        switch onEnum(of: state.presenter.state.openAIModels) {
        case .loading:
            return false
        case .error:
            return true
        case .success(let data):
            let models = (data.data as NSArray).cast(NSString.self).map(String.init)
            return models.isEmpty
        }
    }
}

private struct AiConfigEditSheet: View {
    @ObservedObject var state: AiConfigSettingsState
    let field: AiConfigEditableField

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    if field.isMultiline {
                        TextEditor(text: $state.editingText)
                            .frame(minHeight: 180)
                    } else {
                        TextField(field.placeholder, text: $state.editingText)
                    }
                } footer: {
                    if let footerKey = field.footerKey {
                        Text(footerKey, bundle: FlareAppleUILocalization.bundle)
                            .font(.footnote)
                    } else if let footerText = field.footerText {
                        Text(footerText)
                            .font(.footnote)
                    }
                }

                if field == .serverUrl {
                    let suggestions = filteredServerSuggestions(query: state.editingText)
                    if !suggestions.isEmpty {
                        Section {
                            ForEach(suggestions, id: \.self) { suggestion in
                                Button {
                                    state.editingText = suggestion
                                } label: {
                                    Text(suggestion)
                                        .font(.callout.monospaced())
                                        .lineLimit(1)
                                }
                                .buttonStyle(.plain)
                            }
                        } header: {
                            Text("Suggestions", bundle: FlareAppleUILocalization.bundle)
                        }
                    }
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
                        state.applyEdit(field: field, value: state.editingText)
                        state.editingField = nil
                    } label: {
                        Image(fontAwesome: .check)
                    }
                }
            }
        }
    }

    private var serverSuggestions: [String] {
        (state.presenter.state.serverSuggestions as NSArray).cast(NSString.self).map(String.init)
    }

    private func filteredServerSuggestions(query: String) -> [String] {
        if query.isEmpty {
            return serverSuggestions
        }
        return serverSuggestions.filter {
            $0.localizedCaseInsensitiveContains(query)
        }
    }
}

private extension View {
    func aiConfigSettingsSheets(state: AiConfigSettingsState) -> some View {
        sheet(item: Binding(
            get: { state.editingField },
            set: { state.editingField = $0 }
        )) { field in
            AiConfigEditSheet(state: state, field: field)
        }
    }
}

private enum AiConfigEditableField: String, Identifiable {
    case serverUrl
    case apiKey
    case extraBody
    case model
    case tldrPrompt

    var id: String { rawValue }

    var titleKey: LocalizedStringKey {
        switch self {
        case .serverUrl:
            "Server URL"
        case .apiKey:
            "API Key"
        case .extraBody:
            "Extra Body"
        case .model:
            "Manual Model"
        case .tldrPrompt:
            "Summary Prompt"
        }
    }

    var placeholder: String {
        switch self {
        case .serverUrl:
            "https://api.example.com/v1/"
        case .apiKey:
            "sk-..."
        case .extraBody:
            "{\"thinking\": {\"type\": \"enabled\"}}"
        case .model:
            "model-name"
        case .tldrPrompt:
            ""
        }
    }

    var isMultiline: Bool {
        self == .extraBody || self == .tldrPrompt
    }

    var footerKey: LocalizedStringKey? {
        switch self {
        case .serverUrl:
            "Server URL must end with '/' and support the AI-compatible v1/chat/completions API."
        default:
            nil
        }
    }

    var footerText: String? {
        switch self {
        case .extraBody:
            placeholder
        default:
            nil
        }
    }

    func displayValue(_ value: String) -> String {
        if !value.isEmpty {
            return value
        }
        switch self {
        case .model:
            return FlareAppleUILocalization.string("Select model")
        default:
            return FlareAppleUILocalization.string("Not set")
        }
    }
}
