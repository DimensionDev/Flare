import FlareAppleCore
import KotlinSharedUI
import SwiftUI

public struct ProxySettingsView: View {
    @State private var presenter = KotlinPresenter(presenter: ProxySettingsPresenter())
    @State private var editing: ProxyProfile?

    public init() {}

    public var body: some View {
        Form {
            if presenter.state.busy || presenter.state.settings == nil {
                Section { ProgressView() }
            }
            if let error = presenter.state.error {
                Section { Text(verbatim: error).foregroundStyle(.red) }
            }
            if let settings = presenter.state.settings {
                let accounts = presenter.state.accounts.filter { $0.accountKey.host != "nostr" }
                Section {
                    policyPicker(settings, accountKey: nil)
                } header: {
                    Text("proxy_global", bundle: FlareAppleUILocalization.bundle)
                }

                Section {
                    ForEach(settings.profiles, id: \.id) { profile in
                        profileRow(profile, settings: settings)
                            .contextMenu {
                                Button { editing = profile } label: {
                                    Label { Text("proxy_edit", bundle: FlareAppleUILocalization.bundle) } icon: { Image(systemName: "pencil") }
                                }
                                Button { presenter.state.test(profile: profile, fromEditor: false) } label: {
                                    Label { Text("proxy_test", bundle: FlareAppleUILocalization.bundle) } icon: { Image(systemName: "network") }
                                }
                                Button(role: .destructive) { presenter.state.remove(id: profile.id) } label: {
                                    Label { Text("proxy_delete", bundle: FlareAppleUILocalization.bundle) } icon: { Image(systemName: "trash") }
                                }
                                .disabled(inUse(profile, settings))
                            }
                            #if os(iOS)
                            .swipeActions {
                                Button(role: .destructive) { presenter.state.remove(id: profile.id) } label: {
                                    Label { Text("proxy_delete", bundle: FlareAppleUILocalization.bundle) } icon: { Image(systemName: "trash") }
                                }
                                .disabled(inUse(profile, settings))
                            }
                            #endif
                    }
                    Button {
                        editing = ProxyProfile(id: UUID().uuidString, name: "", protocol: .https, host: "", port: 443)
                    } label: {
                        Label { Text("proxy_add", bundle: FlareAppleUILocalization.bundle) } icon: { Image(systemName: "plus") }
                    }
                } header: {
                    Text("proxy_profiles", bundle: FlareAppleUILocalization.bundle)
                }

                if !accounts.isEmpty {
                    Section {
                        ForEach(accounts, id: \.accountKey) { account in
                            if let profile = presenter.state.accountProfiles[account.accountKey] {
                                UserCompatView(data: profile) {
                                    accountPolicyPicker(settings, account: account)
                                }
                            } else {
                                HStack(spacing: 8) {
                                    Image(fontAwesome: account.platformIcon.fontAwesomeIcon)
                                        .frame(width: 44, height: 44)
                                    VStack(alignment: .leading) {
                                        Text(verbatim: account.platformDisplayName)
                                        Text(verbatim: account.accountKey.description())
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                    .lineLimit(1)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    accountPolicyPicker(settings, account: account)
                                }
                            }
                        }
                    } header: {
                        Text("proxy_accounts", bundle: FlareAppleUILocalization.bundle)
                    }
                }
            }
        }
        .formStyle(.grouped)
        .disabled(presenter.state.busy)
        .navigationTitle(Text("proxy_title", bundle: FlareAppleUILocalization.bundle))
        #if os(macOS)
        .navigationSubtitle(Text("proxy_settings_subtitle", bundle: FlareAppleUILocalization.bundle))
        #endif
        .sheet(isPresented: Binding(get: { editing != nil }, set: { if !$0 { closeEditor() } })) {
            if let profile = editing {
                ProxyEditor(
                    profile: profile,
                    isNew: presenter.state.settings?.profiles.contains { $0.id == profile.id } != true,
                    state: presenter.state,
                    onClose: closeEditor
                )
                .id(profile.id)
                .interactiveDismissDisabled(presenter.state.busy)
            }
        }
    }

    private func closeEditor() {
        presenter.state.clearEditorError()
        editing = nil
    }

    private func accountPolicyPicker(_ settings: ProxySettings, account: UiAccount) -> some View {
        policyPicker(settings, accountKey: account.accountKey, title: account.accountKey.description())
            .labelsHidden()
            .pickerStyle(.menu)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: 180, alignment: .trailing)
    }

    @ViewBuilder
    private func profileRow(_ profile: ProxyProfile, settings: ProxySettings) -> some View {
        #if os(macOS)
        HStack {
            profileSummary(profile, settings: settings)
            Spacer()
            Button { editing = profile } label: { Text("proxy_edit", bundle: FlareAppleUILocalization.bundle) }
            Button(role: .destructive) { presenter.state.remove(id: profile.id) } label: {
                Image(systemName: "trash")
            }
            .accessibilityLabel(Text("proxy_delete", bundle: FlareAppleUILocalization.bundle))
            .disabled(inUse(profile, settings))
        }
        #else
        Button { editing = profile } label: {
            HStack {
                profileSummary(profile, settings: settings)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.tertiary)
                    .accessibilityHidden(true)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        #endif
    }

    private func profileSummary(_ profile: ProxyProfile, settings: ProxySettings) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(verbatim: profile.name)
            Text(verbatim: "\(profile.protocol.name) · \(profile.host):\(profile.port)")
                .font(.caption)
                .foregroundStyle(.secondary)
            if inUse(profile, settings) {
                Text("proxy_in_use", bundle: FlareAppleUILocalization.bundle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            if presenter.state.isTested(profile: profile) {
                Label { Text("proxy_test_success", bundle: FlareAppleUILocalization.bundle) } icon: { Image(systemName: "checkmark.circle") }
                    .font(.caption)
            }
        }
    }

    private func inUse(_ profile: ProxyProfile, _ settings: ProxySettings) -> Bool {
        settings.global.proxyId == profile.id || settings.accounts.values.contains { $0.proxyId == profile.id }
    }

    private func policyPicker(_ settings: ProxySettings, accountKey: MicroBlogKey?, title: String? = nil) -> some View {
        let policy = settings.policy(accountKey: accountKey)
        let options = [ProxyPolicy(mode: accountKey == nil ? .system : .inherit, proxyId: nil), ProxyPolicy(mode: .direct, proxyId: nil)] + settings.profiles.map { ProxyPolicy(mode: .proxy, proxyId: $0.id) }
        return Picker(selection: Binding(get: { policy }, set: { presenter.state.select(accountKey: accountKey, policy: $0) })) {
            ForEach(options, id: \.self) { option in Text(policyLabel(option, settings)).tag(option) }
        } label: {
            if let title {
                Text(verbatim: title)
            } else {
                Text("proxy_title", bundle: FlareAppleUILocalization.bundle)
            }
        }
        .accessibilityLabel(Text(title ?? String(localized: "proxy_title", bundle: FlareAppleUILocalization.bundle)))
        .accessibilityValue(Text(policyLabel(policy, settings)))
    }

    private func policyLabel(_ policy: ProxyPolicy, _ settings: ProxySettings) -> String {
        switch policy.mode {
        case .system: String(localized: "proxy_system", bundle: FlareAppleUILocalization.bundle)
        case .direct: String(localized: "proxy_direct", bundle: FlareAppleUILocalization.bundle)
        case .inherit: String(localized: "proxy_inherit", bundle: FlareAppleUILocalization.bundle)
        default: settings.profiles.first { $0.id == policy.proxyId }?.name ?? policy.proxyId ?? ""
        }
    }
}

private struct ProxyEditor: View {
    let profile: ProxyProfile
    let isNew: Bool
    let state: ProxySettingsState
    let onClose: () -> Void
    @State private var name: String
    @State private var host: String
    @State private var port: String
    @State private var proxyProtocol: ProxyProtocol

    init(profile: ProxyProfile, isNew: Bool, state: ProxySettingsState, onClose: @escaping () -> Void) {
        self.profile = profile
        self.isNew = isNew
        self.state = state
        self.onClose = onClose
        _name = State(initialValue: profile.name)
        _host = State(initialValue: profile.host)
        _port = State(initialValue: String(profile.port))
        _proxyProtocol = State(initialValue: profile.protocol)
    }

    private var value: ProxyProfile {
        ProxyProfile(id: profile.id, name: name.trimmingCharacters(in: .whitespacesAndNewlines), protocol: proxyProtocol,
                     host: host.trimmingCharacters(in: .whitespacesAndNewlines), port: Int32(port) ?? 0)
    }

    private var title: Text {
        Text(isNew ? "proxy_add" : "proxy_edit", bundle: FlareAppleUILocalization.bundle)
    }

    var body: some View {
        #if os(macOS)
        VStack(spacing: 0) {
            title.font(.headline).frame(maxWidth: .infinity, alignment: .leading).padding()
            form
            Divider()
            HStack {
                Spacer()
                cancelButton.keyboardShortcut(.cancelAction)
                saveButton.keyboardShortcut(.defaultAction)
            }
            .labelStyle(.titleOnly)
            .padding()
        }
        .frame(width: 520, height: 560)
        #else
        NavigationStack {
            form
                .navigationTitle(title)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { cancelButton.labelStyle(.iconOnly) }
                    ToolbarItem(placement: .confirmationAction) { saveButton.labelStyle(.iconOnly) }
                }
        }
        #endif
    }

    private var form: some View {
        Form {
            Section {
                LabeledContent {
                    TextField(text: $name) { Text("proxy_name", bundle: FlareAppleUILocalization.bundle) }
                        .labelsHidden()
                        #if os(iOS)
                        .multilineTextAlignment(.trailing)
                        #endif
                } label: { Text("proxy_name", bundle: FlareAppleUILocalization.bundle) }
                Picker(selection: $proxyProtocol) {
                    Text(verbatim: "HTTPS").tag(ProxyProtocol.https)
                    Text(verbatim: "SOCKS5").tag(ProxyProtocol.socks5)
                } label: { Text("proxy_protocol", bundle: FlareAppleUILocalization.bundle) }
                LabeledContent {
                    TextField(text: $host) { Text("proxy_host", bundle: FlareAppleUILocalization.bundle) }
                        .labelsHidden()
                        #if os(iOS)
                        .multilineTextAlignment(.trailing)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.URL)
                        #endif
                } label: { Text("proxy_host", bundle: FlareAppleUILocalization.bundle) }
                LabeledContent {
                    TextField(text: $port) { Text("proxy_port", bundle: FlareAppleUILocalization.bundle) }
                        .labelsHidden()
                        #if os(iOS)
                        .multilineTextAlignment(.trailing)
                        .keyboardType(.numberPad)
                        #endif
                } label: { Text("proxy_port", bundle: FlareAppleUILocalization.bundle) }
            } footer: {
                Text("proxy_no_auth", bundle: FlareAppleUILocalization.bundle)
            }
            Section {
                Button { state.test(profile: value, fromEditor: true) } label: {
                    Text("proxy_test", bundle: FlareAppleUILocalization.bundle)
                }
                if state.busy { ProgressView() }
                if state.isTested(profile: value) {
                    Label { Text("proxy_test_success", bundle: FlareAppleUILocalization.bundle) } icon: { Image(systemName: "checkmark.circle") }
                }
                if let error = state.editorError { Text(verbatim: error).foregroundStyle(.red) }
            } footer: {
                Text("proxy_test_hint", bundle: FlareAppleUILocalization.bundle)
            }
        }
        .formStyle(.grouped)
        .autocorrectionDisabled()
        .disabled(state.busy)
        #if os(macOS)
        .textFieldStyle(.roundedBorder)
        #endif
    }

    private var cancelButton: some View {
        Button(action: onClose) {
            Label { Text("proxy_cancel", bundle: FlareAppleUILocalization.bundle) } icon: { Image(fontAwesome: .xmark) }
        }
        .disabled(state.busy)
    }

    private var saveButton: some View {
        Button { state.save(profile: value, onSaved: onClose) } label: {
            Label { Text("proxy_save", bundle: FlareAppleUILocalization.bundle) } icon: { Image(fontAwesome: .check) }
        }
        .disabled(state.busy)
    }
}
