import FlareAppleCore
import KotlinSharedUI
import SwiftUI

public struct ImageTranslationSettingsSection: View {
    @State private var presenter = KotlinPresenter(presenter: ImageTranslationSettingsPresenter())

    public init() {}

    public var body: some View {
        Section {
            Picker(selection: Binding(
                get: { presenter.state.config.provider },
                set: { presenter.state.selectProvider(provider: $0) }
            )) {
                ForEach(presenter.state.providers, id: \.name) { provider in
                    Text(LocalizedStringKey(provider.title), bundle: FlareAppleUILocalization.bundle).tag(provider)
                }
            } label: {
                Text("Image translation provider", bundle: FlareAppleUILocalization.bundle)
            }

            ForEach(presenter.state.fields, id: \.id) { field in
                if !field.choices.isEmpty {
                    Picker(selection: Binding(
                        get: { presenter.state.config.value(id: field.id) },
                        set: { presenter.state.setField(id: field.id, value: $0) }
                    )) {
                        ForEach(field.choices, id: \.self) { value in
                            Text(choiceTitle(field: field, value: value)).tag(value)
                        }
                    } label: {
                        Text(LocalizedStringKey(field.title), bundle: FlareAppleUILocalization.bundle)
                    }
                } else {
                    #if os(macOS)
                    ImageTranslationMacField(
                        field: field,
                        initialValue: presenter.state.config.value(id: field.id),
                        onChange: { presenter.state.setField(id: field.id, value: $0) }
                    )
                    .id(presenter.state.config.provider.name + field.id)
                    #else
                    NavigationLink {
                        ImageTranslationFieldEditor(
                            field: field,
                            initialValue: presenter.state.config.value(id: field.id),
                            onSave: { presenter.state.setField(id: field.id, value: $0) }
                        )
                    } label: {
                        LabeledContent {
                            let value = presenter.state.config.value(id: field.id)
                            Text(field.secret && !value.isEmpty ? "••••••••" : value)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        } label: {
                            Text(LocalizedStringKey(field.title), bundle: FlareAppleUILocalization.bundle)
                        }
                    }
                    #endif
                }
            }
        } header: {
            Text("Image translation", bundle: FlareAppleUILocalization.bundle)
        } footer: {
            VStack(alignment: .leading, spacing: 4) {
                Text("Original images, including private images, are sent to the selected service. Choose a model that returns images.", bundle: FlareAppleUILocalization.bundle)
                if presenter.state.config.provider == .qwen {
                    Text("Qwen requires a publicly accessible original URL. Private images cannot be translated with this provider.", bundle: FlareAppleUILocalization.bundle)
                }
            }
        }
    }

    private func choiceTitle(field: ImageTranslationField, value: String) -> String {
        FlareAppleUILocalization.string(field.choiceTitle(value: value))
    }
}

#if os(iOS)
private struct ImageTranslationFieldEditor: View {
    let field: ImageTranslationField
    let onSave: (String) -> Void
    @State private var text: String
    @Environment(\.dismiss) private var dismiss

    init(field: ImageTranslationField, initialValue: String, onSave: @escaping (String) -> Void) {
        self.field = field
        self.onSave = onSave
        _text = State(initialValue: initialValue)
    }

    var body: some View {
        Form {
            if field.secret {
                SecureField(LocalizedStringKey(field.title), text: $text)
            } else if field.multiline {
                TextEditor(text: $text).frame(minHeight: 180)
            } else {
                TextField(field.placeholder, text: $text)
            }
        }
        .textInputAutocapitalization(.never)
        .autocorrectionDisabled()
        .navigationTitle(Text(LocalizedStringKey(field.title), bundle: FlareAppleUILocalization.bundle))
        .navigationBarBackButtonHidden()
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button { dismiss() } label: { Text("Cancel", bundle: FlareAppleUILocalization.bundle) }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button {
                    onSave(text.trimmingCharacters(in: .whitespacesAndNewlines))
                    dismiss()
                } label: { Text("Save", bundle: FlareAppleUILocalization.bundle) }
            }
        }
    }
}
#endif

#if os(macOS)
private struct ImageTranslationMacField: View {
    let field: ImageTranslationField
    let onChange: (String) -> Void
    @State private var text: String

    init(field: ImageTranslationField, initialValue: String, onChange: @escaping (String) -> Void) {
        self.field = field
        self.onChange = onChange
        _text = State(initialValue: initialValue)
    }

    var body: some View {
        LabeledContent {
            if field.secret {
                SecureField(field.placeholder, text: $text)
            } else if field.multiline {
                TextEditor(text: $text)
                    .frame(minHeight: 100, maxHeight: 180)
            } else {
                TextField(field.placeholder, text: $text)
            }
        } label: {
            Text(LocalizedStringKey(field.title), bundle: FlareAppleUILocalization.bundle)
        }
        .textFieldStyle(.roundedBorder)
        .onChange(of: text) { _, value in onChange(value) }
    }
}
#endif


public struct ImageTranslationSettingsSheet: View {
    @Environment(\.dismiss) private var dismiss
    public init() {}
    public var body: some View {
        NavigationStack {
            #if os(macOS)
            Form { ImageTranslationSettingsSection() }
                .formStyle(.grouped)
                .frame(minWidth: 480, minHeight: 400)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button { dismiss() } label: { Text("Save", bundle: FlareAppleUILocalization.bundle) }
                    }
                }
            #else
            List { ImageTranslationSettingsSection() }
                .navigationTitle(Text("Image translation", bundle: FlareAppleUILocalization.bundle))
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button { dismiss() } label: { Text("Save", bundle: FlareAppleUILocalization.bundle) }
                    }
                }
            #endif
        }
    }
}
