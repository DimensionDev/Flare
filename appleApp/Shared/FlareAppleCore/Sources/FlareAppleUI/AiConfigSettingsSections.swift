import Observation
import FlareAppleCore
import Foundation
import KotlinSharedUI
import SwiftUI

public struct AiConfigSettingsView: View {
    @State private var state = AiConfigSettingsState()
    @State private var agentState = AiConfigSettingsState(agentConfig: true)

    public init() {}

    public var body: some View {
        List {
            AiConfigSettingsSectionsContent(state: state)
            AiConfigSettingsSectionsContent(state: agentState)
        }
        .aiConfigSettingsSheets(state: state)
        .aiConfigSettingsSheets(state: agentState)
    }
}

public struct AiConfigSettingsSections: View {
    @State private var state = AiConfigSettingsState()
    @State private var agentState = AiConfigSettingsState(agentConfig: true)

    public init() {}

    public var body: some View {
        Group {
            AiConfigSettingsSectionsContent(state: state)
            AiConfigSettingsSectionsContent(state: agentState)
        }
        .aiConfigSettingsSheets(state: state)
        .aiConfigSettingsSheets(state: agentState)
    }
}

@Observable
private final class AiConfigSettingsState {
    let presenter: KotlinPresenter<AiConfigPresenterState>

    init(agentConfig: Bool = false) {
        presenter = KotlinPresenter(presenter: AiConfigPresenter(agentConfig: agentConfig))
    }

    var editingField: AiConfigEditableField?
    var editingText = ""

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
        case .contextWindow:
            presenter.state.setOpenAIContextWindow(value: value)
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
    let state: AiConfigSettingsState

    var body: some View {
        Group {
            if state.presenter.state.isAgentConfig {
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
                    Toggle(
                        isOn: Binding(
                            get: { state.presenter.state.useSharedConfig },
                            set: { state.presenter.state.setUseSharedConfig(value: $0) }
                        )
                    ) {
                        Text("settings_ai_config_use_shared", bundle: FlareAppleUILocalization.bundle)
                        Text("settings_ai_config_use_shared_description", bundle: FlareAppleUILocalization.bundle)
                    }
                } header: {
                    Text("settings_ai_config_agent_title", bundle: FlareAppleUILocalization.bundle)
                }
            }

            if !state.presenter.state.isAgentConfig || !state.presenter.state.useSharedConfig {
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

                        editButton(field: .contextWindow, value: state.presenter.state.openAIContextWindow)

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
                                Text("settings_ai_config_model_description", bundle: FlareAppleUILocalization.bundle)
                            }
                            .transition(.opacity.combined(with: .move(edge: .top)))
                        }

                        if shouldShowManualModelInput {
                            editButton(field: .model, value: state.presenter.state.openAIModel)
                        }
                    }
                } header: {
                    Text(
                        state.presenter.state.isAgentConfig ? "settings_ai_config_agent_title" : "settings_ai_config_basic_title",
                        bundle: FlareAppleUILocalization.bundle
                    )
                }
            }

            if !state.presenter.state.isAgentConfig {
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
        }
        .animation(.easeInOut(duration: 0.2), value: state.presenter.state.aiType == .openAi)
        .animation(.easeInOut(duration: 0.2), value: state.presenter.state.useSharedConfig)
        .animation(.easeInOut(duration: 0.2), value: state.presenter.state.aiTldr)
    }

    private func editButton(field: AiConfigEditableField, value: String) -> some View {
        AiConfigEditButton(title: field.titleKey, value: value) {
            state.beginEditing(field: field, value: value)
        }
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
        case .none:
            "None"
        case .minimal:
            "Minimal"
        case .low:
            "Low"
        case .medium:
            "Medium"
        case .high:
            "High"
        case .xhigh:
            "Extra High"
        case .max:
            "Maximum"
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
    @Bindable var state: AiConfigSettingsState
    let field: AiConfigEditableField

    var body: some View {
        SettingsTextEditSheet(
            title: field.titleKey,
            text: $state.editingText,
            placeholder: field.placeholder,
            multiline: field.isMultiline,
            suggestions: field == .serverUrl
                ? (state.presenter.state.serverSuggestions as NSArray).cast(NSString.self).map(String.init)
                : [],
            footer: footer,
            isValid: { field != .contextWindow || state.presenter.state.isValidOpenAIContextWindow(value: $0) }
        ) { value in
            state.applyEdit(field: field, value: value)
        }
    }

    private var footer: Text? {
        if let key = field.footerKey {
            return Text(key, bundle: FlareAppleUILocalization.bundle)
        }
        return field.footerText.map { Text($0) }
    }
}

struct AiConfigEditButton: View {
    let title: LocalizedStringKey
    let value: String
    var secret = false
    let onEdit: () -> Void

    var body: some View {
        #if os(macOS)
        HStack(spacing: 16) {
            fieldLabel.frame(maxWidth: .infinity, alignment: .leading)
            Button(action: onEdit) {
                Label {
                    Text("edit", bundle: FlareAppleUILocalization.bundle)
                } icon: {
                    Image(fontAwesome: .pen)
                }
            }
            .buttonStyle(.bordered)
            .controlSize(.small)
            .help(Text("edit", bundle: FlareAppleUILocalization.bundle))
            .accessibilityLabel(Text(title, bundle: FlareAppleUILocalization.bundle))
        }
        #else
        Button(action: onEdit) {
            fieldLabel
        }
        .buttonStyle(.plain)
        #endif
    }

    private var fieldLabel: some View {
        Label {
            Text(title, bundle: FlareAppleUILocalization.bundle)
            Text(value.isEmpty ? FlareAppleUILocalization.string("Not set") : secret ? "••••••••" : value)
                .lineLimit(2)
        } icon: {
            EmptyView()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
    }
}

struct SettingsTextEditSheet: View {
    let title: LocalizedStringKey
    @Binding var text: String
    var placeholder = ""
    var secret = false
    var multiline = false
    var suggestions: [String] = []
    var footer: Text?
    var isValid: (String) -> Bool = { _ in true }
    let onSave: (String) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    if secret {
                        SecureField(placeholder, text: $text)
                            .accessibilityLabel(Text(title, bundle: FlareAppleUILocalization.bundle))
                    } else if multiline {
                        TextEditor(text: $text).frame(minHeight: 180)
                            .accessibilityLabel(Text(title, bundle: FlareAppleUILocalization.bundle))
                    } else {
                        TextField(placeholder, text: $text)
                            .accessibilityLabel(Text(title, bundle: FlareAppleUILocalization.bundle))
                    }
                } footer: {
                    footer?.font(.footnote)
                }

                let matches = suggestions.filter { text.isEmpty || $0.localizedCaseInsensitiveContains(text) }
                if !matches.isEmpty {
                    Section {
                        ForEach(matches, id: \.self) { suggestion in
                            Button {
                                text = suggestion
                            } label: {
                                Text(suggestion).font(.callout.monospaced()).lineLimit(1)
                            }
                            .buttonStyle(.plain)
                        }
                    } header: {
                        Text("Suggestions", bundle: FlareAppleUILocalization.bundle)
                    }
                }
            }
            .formStyle(.grouped)
            #if os(iOS)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            #endif
            .navigationTitle(Text(title, bundle: FlareAppleUILocalization.bundle))
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button { dismiss() } label: { Image(fontAwesome: .xmark) }
                        .accessibilityLabel(Text("Cancel", bundle: FlareAppleUILocalization.bundle))
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        onSave(text)
                        dismiss()
                    } label: { Image(fontAwesome: .check) }
                        .accessibilityLabel(Text("done", bundle: FlareAppleUILocalization.bundle))
                        .disabled(!isValid(text))
                }
            }
        }
    }
}

private extension View {
    func aiConfigSettingsSheets(state: AiConfigSettingsState) -> some View {
        @Bindable var state = state
        return sheet(item: $state.editingField) { field in
            AiConfigEditSheet(state: state, field: field)
        }
    }
}

private enum AiConfigEditableField: String, Identifiable {
    case serverUrl
    case apiKey
    case contextWindow
    case extraBody
    case model
    case tldrPrompt

    var id: String { rawValue }

    var titleKey: LocalizedStringKey {
        switch self {
        case .serverUrl:
            "ai_config_server_provider_section_header"
        case .apiKey:
            "API Key"
        case .contextWindow:
            "settings_ai_config_context_window"
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
        case .contextWindow:
            "128000"
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
        case .contextWindow:
            "settings_ai_config_context_window_hint"
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
