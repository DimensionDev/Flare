import FlareAppleCore
import KotlinSharedUI
import Observation
import SwiftUI

@Observable
final class ImageTranslationSettingsState {
    let presenter = KotlinPresenter(presenter: ImageTranslationSettingsPresenter())
    var editingField: ImageTranslationEditingField?

    func beginEditing(_ field: ImageTranslationField) {
        let state = presenter.state
        editingField = ImageTranslationEditingField(
            field: field,
            value: state.config.value(id: field.id),
            onSave: { state.setField(id: field.id, value: $0) }
        )
    }
}

struct ImageTranslationEditingField: Identifiable {
    let field: ImageTranslationField
    let value: String
    let onSave: (String) -> Void
    var id: String { field.id }
}

struct ImageTranslationSettingsSection: View {
    let state: ImageTranslationSettingsState

    var body: some View {
        Section {
            Picker(selection: Binding(
                get: { state.presenter.state.config.provider },
                set: { state.presenter.state.selectProvider(provider: $0) }
            )) {
                ForEach(state.presenter.state.providers, id: \.name) { provider in
                    Text(LocalizedStringKey(provider.title), bundle: FlareAppleUILocalization.bundle).tag(provider)
                }
            } label: {
                Text("Image translation provider", bundle: FlareAppleUILocalization.bundle)
                Text("Choose which service handles translation", bundle: FlareAppleUILocalization.bundle)
            }

            if state.presenter.state.config.provider == .openAiimages {
                Toggle(isOn: Binding(
                    get: { state.presenter.state.useSharedConfig },
                    set: { state.presenter.state.setUseSharedConfig(value: $0) }
                )) {
                    Text("settings_ai_config_use_shared", bundle: FlareAppleUILocalization.bundle)
                    Text("image_translation_use_shared_description", bundle: FlareAppleUILocalization.bundle)
                }
            }

            ForEach(state.presenter.state.fields, id: \.id) { field in
                if !state.presenter.state.useSharedConfig || !field.isSharedAIField {
                    Group {
                        if !field.choices.isEmpty || !field.suggestions.isEmpty || field.loading {
                            let value = state.presenter.state.config.value(id: field.id)
                            let options = field.choices.isEmpty ? field.suggestions : field.choices
                            Picker(selection: Binding(
                                get: { Optional(value) },
                                set: { selection in
                                    if let selection {
                                        state.presenter.state.setField(id: field.id, value: selection)
                                    } else {
                                        state.beginEditing(field)
                                    }
                                }
                            )) {
                                if !options.contains(value) {
                                    Text(value.isEmpty ? FlareAppleUILocalization.string("Not set") : value).tag(Optional(value))
                                }
                                ForEach(options, id: \.self) { value in
                                    Text(FlareAppleUILocalization.string(field.choiceTitle(value: value))).tag(Optional(value))
                                }
                                if field.choices.isEmpty {
                                    Text("image_translation_manual_input", bundle: FlareAppleUILocalization.bundle).tag(nil as String?)
                                }
                            } label: {
                                Text(LocalizedStringKey(field.title), bundle: FlareAppleUILocalization.bundle)
                                if field.loading {
                                    Text("Loading models...", bundle: FlareAppleUILocalization.bundle)
                                }
                            }
                        } else {
                            editButton(field)
                        }
                    }
                    .transition(.opacity.combined(with: .move(edge: .top)))
                }
            }
        } header: {
            Text("Image translation", bundle: FlareAppleUILocalization.bundle)
        } footer: {
            VStack(alignment: .leading, spacing: 4) {
                Text("Original images, including private images, are sent to the selected service. Choose a model that returns images.", bundle: FlareAppleUILocalization.bundle)
                if state.presenter.state.config.provider == .qwen {
                    Text("Qwen requires a publicly accessible original URL. Private images cannot be translated with this provider.", bundle: FlareAppleUILocalization.bundle)
                }
            }
        }
        .animation(.easeInOut(duration: 0.2), value: state.presenter.state.config.provider.name)
        .animation(.easeInOut(duration: 0.2), value: state.presenter.state.useSharedConfig)
    }

    private func editButton(_ field: ImageTranslationField) -> some View {
        AiConfigEditButton(
            title: LocalizedStringKey(field.title),
            value: state.presenter.state.config.value(id: field.id),
            secret: field.secret
        ) {
            state.beginEditing(field)
        }
    }
}

private struct ImageTranslationFieldEditor: View {
    let editing: ImageTranslationEditingField
    @State private var text: String

    init(editing: ImageTranslationEditingField) {
        self.editing = editing
        _text = State(initialValue: editing.value)
    }

    var body: some View {
        SettingsTextEditSheet(
            title: LocalizedStringKey(editing.field.title),
            text: $text,
            placeholder: editing.field.placeholder,
            secret: editing.field.secret,
            multiline: editing.field.multiline,
            suggestions: editing.field.editSuggestions
        ) { value in
            editing.onSave(value.trimmingCharacters(in: .whitespacesAndNewlines))
        }
    }
}

extension View {
    func imageTranslationSettingsSheets(state: ImageTranslationSettingsState) -> some View {
        @Bindable var state = state
        return sheet(item: $state.editingField) { editing in
            ImageTranslationFieldEditor(editing: editing)
        }
    }
}

public extension View {
    func imageTranslationErrorAlert(
        state: ImageTranslationPresenterState,
        media: (any UiMedia)?,
        enabled: Bool = true
    ) -> some View {
        alert(
            Text("Image translation", bundle: FlareAppleUILocalization.bundle),
            isPresented: Binding(
                get: { enabled && (media.map { state.item(media: $0).error != nil } ?? false) },
                set: { if !$0, let media { state.clearError(media: media) } }
            )
        ) {
            Button("OK") { if let media { state.clearError(media: media) } }
        } message: {
            if let media, let error = state.item(media: media).error {
                Text(LocalizedStringKey(error.message), bundle: FlareAppleUILocalization.bundle)
            }
        }
    }
}
