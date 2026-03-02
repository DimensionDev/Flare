import SwiftUI
import KotlinSharedUI

struct AiConfigScreen: View {
    @StateObject private var presenter = KotlinPresenter(presenter: AiConfigPresenter())
    @State private var editingField: EditableField?
    @State private var editingText: String = ""

    private enum EditableField: String, Identifiable {
        case serverUrl
        case apiKey
        case translatePrompt
        case tldrPrompt

        var id: String { rawValue }
    }

    var body: some View {
        List {
            Section {
                Picker(
                    selection: Binding(
                        get: { aiTypeOption(type: presenter.state.aiConfig.type) },
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

                if isOpenAIType(presenter.state.aiConfig.type) {
                    Button {
                        beginEditing(
                            field: .serverUrl,
                            value: openAIValue(presenter.state.aiConfig.type).serverUrl
                        )
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Server URL")
                            Text(displayText(openAIValue(presenter.state.aiConfig.type).serverUrl))
                                .foregroundStyle(.secondary)
                                .font(.subheadline)
                        }
                    }
                    .buttonStyle(.plain)
                    .transition(.opacity.combined(with: .move(edge: .top)))
                }

                if isOpenAIType(presenter.state.aiConfig.type) {
                    Button {
                        beginEditing(
                            field: .apiKey,
                            value: openAIValue(presenter.state.aiConfig.type).apiKey
                        )
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("API Key")
                            Text(displayText(openAIValue(presenter.state.aiConfig.type).apiKey))
                                .foregroundStyle(.secondary)
                                .font(.subheadline)
                        }
                    }
                    .buttonStyle(.plain)
                    .transition(.opacity.combined(with: .move(edge: .top)))
                }

                if isOpenAIType(presenter.state.aiConfig.type) {
                    Picker(
                        selection: Binding(
                            get: { String(openAIValue(presenter.state.aiConfig.type).model) },
                            set: { model in
                                if model.hasPrefix("__meta__") {
                                    return
                                }
                                presenter.state.update { current in
                                    switch onEnum(of: current.type) {
                                    case .onDevice:
                                        return current
                                    case .openAI(let openAI):
                                        return current.doCopy(
                                            translation: current.translation,
                                            tldr: current.tldr,
                                            type: openAI.doCopy(
                                                serverUrl: openAI.serverUrl,
                                                apiKey: openAI.apiKey,
                                                model: model
                                            ),
                                            translatePrompt: current.translatePrompt,
                                            tldrPrompt: current.tldrPrompt
                                        )
                                    }
                                }
                            }
                        )
                    ) {
                        switch onEnum(of: presenter.state.openAIModels) {
                        case .loading:
                            Text("Loading models...").tag("__meta__loading")
                        case .error:
                            Text("Failed to load models").tag("__meta__error")
                        case .success(let data):
                            let models = (data.data as NSArray).cast(NSString.self).map(String.init)
                            if models.isEmpty {
                                Text("No models available").tag("__meta__empty")
                            } else {
                                ForEach(models, id: \.self) { model in
                                    Text(model).tag(model)
                                }
                            }
                        }
                    } label: {
                        Text("Model")
                        Text("OpenAI model used for translation and summary")
                    }
                    .transition(.opacity.combined(with: .move(edge: .top)))
                }
            }

            Section {
                Toggle(
                    isOn: Binding(
                        get: { presenter.state.aiConfig.translation },
                        set: { newValue in
                            presenter.state.update { current in
                                current.doCopy(
                                    translation: newValue,
                                    tldr: current.tldr,
                                    type: current.type,
                                    translatePrompt: current.translatePrompt,
                                    tldrPrompt: current.tldrPrompt
                                )
                            }
                        }
                    )
                ) {
                    Text("ai_config_translate")
                    Text("Translate text with AI")
                }

                if presenter.state.aiConfig.translation {
                    Button {
                        beginEditing(
                            field: .translatePrompt,
                            value: String(presenter.state.aiConfig.translatePrompt)
                        )
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Translate Prompt")
                            Text(displayText(String(presenter.state.aiConfig.translatePrompt)))
                                .foregroundStyle(.secondary)
                                .font(.subheadline)
                        }
                    }
                    .buttonStyle(.plain)
                    .transition(.opacity.combined(with: .move(edge: .top)))
                }

                Toggle(
                    isOn: Binding(
                        get: { presenter.state.aiConfig.tldr },
                        set: { newValue in
                            presenter.state.update { current in
                                current.doCopy(
                                    translation: current.translation,
                                    tldr: newValue,
                                    type: current.type,
                                    translatePrompt: current.translatePrompt,
                                    tldrPrompt: current.tldrPrompt
                                )
                            }
                        }
                    )
                ) {
                    Text("ai_config_summarize")
                    Text("Summarize long text with AI")
                }

                if presenter.state.aiConfig.tldr {
                    Button {
                        beginEditing(
                            field: .tldrPrompt,
                            value: String(presenter.state.aiConfig.tldrPrompt)
                        )
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Summary Prompt")
                            Text(displayText(String(presenter.state.aiConfig.tldrPrompt)))
                                .foregroundStyle(.secondary)
                                .font(.subheadline)
                        }
                    }
                    .buttonStyle(.plain)
                    .transition(.opacity.combined(with: .move(edge: .top)))
                }
            }
        }
        .animation(.easeInOut(duration: 0.2), value: isOpenAIType(presenter.state.aiConfig.type))
        .animation(.easeInOut(duration: 0.2), value: presenter.state.aiConfig.translation)
        .animation(.easeInOut(duration: 0.2), value: presenter.state.aiConfig.tldr)
        .sheet(item: $editingField) { field in
            NavigationStack {
                Form {
                    Section {
                        if field == .translatePrompt || field == .tldrPrompt {
                            TextEditor(text: $editingText)
                                .frame(minHeight: 180)
                        } else {
                            TextField(fieldPlaceholder(field: field), text: $editingText)
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

    private func isOpenAIType(_ type: any AppSettingsAiConfigType) -> Bool {
        switch onEnum(of: type) {
        case .onDevice:
            return false
        case .openAI:
            return true
        }
    }

    private func openAIValue(_ type: any AppSettingsAiConfigType) -> (serverUrl: String, apiKey: String, model: String) {
        switch onEnum(of: type) {
        case .onDevice:
            return ("", "", "")
        case .openAI(let openAI):
            return (String(openAI.serverUrl), String(openAI.apiKey), String(openAI.model))
        }
    }

    private func aiTypeTitle(type: any AppSettingsAiConfigType) -> String {
        aiTypeOptionTitle(option: aiTypeOption(type: type))
    }

    private func aiTypeOption(type: any AppSettingsAiConfigType) -> AiTypeOption {
        switch onEnum(of: type) {
        case .onDevice:
            return .onDevice
        case .openAI:
            return .openAi
        }
    }

    private func aiTypeOptionTitle(option: AiTypeOption) -> String {
        switch option {
        case .onDevice:
            return "On Device"
        case .openAi:
            return "OpenAI Compatible"
        }
    }

    private func displayText(_ value: String) -> String {
        value.isEmpty ? "Not set" : value
    }

    private func displayModelText(_ value: String) -> String {
        value.isEmpty ? "Select model" : value
    }

    private func beginEditing(field: EditableField, value: String) {
        editingText = value
        editingField = field
    }

    private func fieldTitle(field: EditableField) -> String {
        switch field {
        case .serverUrl:
            return "Server URL"
        case .apiKey:
            return "API Key"
        case .translatePrompt:
            return "Translate Prompt"
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
        case .translatePrompt:
            return ""
        case .tldrPrompt:
            return ""
        }
    }

    private func applyEdit(field: EditableField, value: String) {
        presenter.state.update { current in
            switch field {
            case .serverUrl:
                switch onEnum(of: current.type) {
                case .onDevice:
                    return current
                case .openAI(let openAI):
                    return current.doCopy(
                        translation: current.translation,
                        tldr: current.tldr,
                        type: openAI.doCopy(serverUrl: value, apiKey: openAI.apiKey, model: openAI.model),
                        translatePrompt: current.translatePrompt,
                        tldrPrompt: current.tldrPrompt
                    )
                }
            case .apiKey:
                switch onEnum(of: current.type) {
                case .onDevice:
                    return current
                case .openAI(let openAI):
                    return current.doCopy(
                        translation: current.translation,
                        tldr: current.tldr,
                        type: openAI.doCopy(serverUrl: openAI.serverUrl, apiKey: value, model: openAI.model),
                        translatePrompt: current.translatePrompt,
                        tldrPrompt: current.tldrPrompt
                    )
                }
            case .translatePrompt:
                return current.doCopy(
                    translation: current.translation,
                    tldr: current.tldr,
                    type: current.type,
                    translatePrompt: value,
                    tldrPrompt: current.tldrPrompt
                )
            case .tldrPrompt:
                return current.doCopy(
                    translation: current.translation,
                    tldr: current.tldr,
                    type: current.type,
                    translatePrompt: current.translatePrompt,
                    tldrPrompt: value
                )
            }
        }
    }
}
