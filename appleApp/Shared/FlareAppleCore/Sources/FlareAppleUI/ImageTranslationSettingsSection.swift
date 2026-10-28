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

            ForEach(state.presenter.state.fields, id: \.id) { field in
                if !field.choices.isEmpty {
                    Picker(selection: Binding(
                        get: { state.presenter.state.config.value(id: field.id) },
                        set: { state.presenter.state.setField(id: field.id, value: $0) }
                    )) {
                        ForEach(field.choices, id: \.self) { value in
                            Text(FlareAppleUILocalization.string(field.choiceTitle(value: value))).tag(value)
                        }
                    } label: {
                        Text(LocalizedStringKey(field.title), bundle: FlareAppleUILocalization.bundle)
                    }
                } else {
                    editButton(field)
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
    }

    private func editButton(_ field: ImageTranslationField) -> some View {
        #if os(macOS)
        HStack(spacing: 16) {
            fieldLabel(field)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button {
                state.beginEditing(field)
            } label: {
                Label {
                    Text("edit", bundle: FlareAppleUILocalization.bundle)
                } icon: {
                    Image(fontAwesome: .pen)
                }
            }
            .buttonStyle(.bordered)
            .controlSize(.small)
            .help(Text("edit", bundle: FlareAppleUILocalization.bundle))
            .accessibilityLabel(Text(LocalizedStringKey(field.title), bundle: FlareAppleUILocalization.bundle))
        }
        #else
        Button {
            state.beginEditing(field)
        } label: {
            fieldLabel(field)
        }
        .buttonStyle(.plain)
        #endif
    }

    private func fieldLabel(_ field: ImageTranslationField) -> some View {
        let value = state.presenter.state.config.value(id: field.id)
        return Label {
            Text(LocalizedStringKey(field.title), bundle: FlareAppleUILocalization.bundle)
            Text(value.isEmpty ? FlareAppleUILocalization.string("Not set") : field.secret ? "••••••••" : value)
                .lineLimit(2)
        } icon: {
            EmptyView()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
    }
}

private struct ImageTranslationFieldEditor: View {
    let editing: ImageTranslationEditingField
    @State private var text: String
    @Environment(\.dismiss) private var dismiss

    init(editing: ImageTranslationEditingField) {
        self.editing = editing
        _text = State(initialValue: editing.value)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    if editing.field.secret {
                        SecureField(LocalizedStringKey(editing.field.title), text: $text)
                    } else if editing.field.multiline {
                        TextEditor(text: $text).frame(minHeight: 180)
                    } else {
                        TextField(editing.field.placeholder, text: $text)
                    }
                }
            }
            .formStyle(.grouped)
            #if os(iOS)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            #endif
            .navigationTitle(Text(LocalizedStringKey(editing.field.title), bundle: FlareAppleUILocalization.bundle))
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button { dismiss() } label: { Image(fontAwesome: .xmark) }
                        .accessibilityLabel(Text("Cancel", bundle: FlareAppleUILocalization.bundle))
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        editing.onSave(text.trimmingCharacters(in: .whitespacesAndNewlines))
                        dismiss()
                    } label: { Image(fontAwesome: .check) }
                        .accessibilityLabel(Text("done", bundle: FlareAppleUILocalization.bundle))
                }
            }
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
