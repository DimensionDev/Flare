import FlareAppleCore
import KotlinSharedUI
import SwiftUI

public struct ProxySettingsView: View {
    @State private var presenter = KotlinPresenter(presenter: ProxySettingsPresenter())
    @State private var editing: ProxyProfile?
    @State private var showError = false

    public init() {}

    public var body: some View {
        Form {
            if case .success(let data) = onEnum(of: presenter.state.settings) {
                let settings = data.data
                let accounts = presenter.state.accounts.filter { $0.accountKey.host != "nostr" }
                Section {
                    policyPicker(settings)
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
                            policyPicker(settings, account: account)
                        }
                    } header: {
                        Text("proxy_accounts", bundle: FlareAppleUILocalization.bundle)
                    }
                }
            }
        }
        .formStyle(.grouped)
        .disabled(presenter.state.operation is UiStateLoading<KotlinUnit>)
        .navigationTitle(Text("proxy_title", bundle: FlareAppleUILocalization.bundle))
        #if os(macOS)
        .navigationSubtitle(Text("proxy_settings_subtitle", bundle: FlareAppleUILocalization.bundle))
        #endif
        .sheet(isPresented: Binding(get: { editing != nil }, set: { if !$0 { closeEditor() } })) {
            if let profile = editing {
                ProxyEditor(
                    profile: profile,
                    isNew: (presenter.state.settings as? UiStateSuccess<ProxySettings>)?.data.profiles.contains { $0.id == profile.id } != true,
                    onClose: closeEditor
                )
                .id(profile.id)
            }
        }
        .onChange(of: presenter.state.error, initial: true) { _, error in
            showError = error != nil
        }
        .alert(Text("proxy_title", bundle: FlareAppleUILocalization.bundle), isPresented: $showError, presenting: presenter.state.error) { _ in
            if presenter.state.settings is UiStateError<ProxySettings> {
                Button { presenter.state.retry() } label: {
                    Text("action_retry", bundle: FlareAppleUILocalization.bundle)
                }
            }
            Button(role: .cancel) {} label: {
                Text("proxy_cancel", bundle: FlareAppleUILocalization.bundle)
            }
        } message: { error in
            Text(error.localizedKey, bundle: FlareAppleUILocalization.bundle)
        }
    }

    private func closeEditor() {
        editing = nil
    }

    @ViewBuilder
    private func profileRow(_ profile: ProxyProfile, settings: ProxySettings) -> some View {
        #if os(macOS)
        HStack {
            profileSummary(profile)
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
                profileSummary(profile)
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

    private func profileSummary(_ profile: ProxyProfile) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(verbatim: profile.name)
            Text(verbatim: "\(profile.protocol.name) · \(profile.host):\(profile.port)")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private func inUse(_ profile: ProxyProfile, _ settings: ProxySettings) -> Bool {
        settings.global.proxyId == profile.id || settings.accounts.values.contains { $0.proxyId == profile.id }
    }

    private func policyPicker(_ settings: ProxySettings, account: UiAccount? = nil) -> some View {
        let accountKey = account?.accountKey
        let policy = settings.policy(accountKey: accountKey)
        let options = [ProxyPolicy(mode: accountKey == nil ? .system : .inherit, proxyId: nil), ProxyPolicy(mode: .direct, proxyId: nil)] + settings.profiles.map { ProxyPolicy(mode: .proxy, proxyId: $0.id) }
        return Picker(selection: Binding(get: { policy }, set: { presenter.state.select(accountKey: accountKey, policy: $0) })) {
            ForEach(options, id: \.self) { option in Text(policyLabel(option, settings)).tag(option) }
        } label: {
            if let account {
                if let profile = presenter.state.accountProfiles[account.accountKey] {
                    UserCompatView(data: profile)
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
                    }
                }
            } else {
                Text("proxy_title", bundle: FlareAppleUILocalization.bundle)
            }
        }
        .accessibilityLabel(Text(account.map { presenter.state.accountLabel(account: $0) } ?? String(localized: "proxy_title", bundle: FlareAppleUILocalization.bundle)))
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
    let onClose: () -> Void
    @State private var presenter = KotlinPresenter(presenter: ProxyEditorPresenter())
    @State private var name: String
    @State private var host: String
    @State private var port: String
    @State private var proxyProtocol: ProxyProtocol

    init(profile: ProxyProfile, isNew: Bool, onClose: @escaping () -> Void) {
        self.profile = profile
        self.isNew = isNew
        self.onClose = onClose
        _name = State(initialValue: profile.name)
        _host = State(initialValue: profile.host)
        _port = State(initialValue: String(profile.port))
        _proxyProtocol = State(initialValue: profile.protocol)
    }

    private var state: ProxyEditorState { presenter.state }
    private var isLoading: Bool {
        state.saveState is UiStateLoading<KotlinUnit> || state.testState is UiStateLoading<ProxyProfile>
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
        .interactiveDismissDisabled(isLoading)
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
        .interactiveDismissDisabled(isLoading)
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
                Button { state.test(profile: value) } label: {
                    Text("proxy_test", bundle: FlareAppleUILocalization.bundle)
                }
                if isLoading { ProgressView() }
                if state.isTested(profile: value) {
                    Label { Text("proxy_test_success", bundle: FlareAppleUILocalization.bundle) } icon: { Image(systemName: "checkmark.circle") }
                }
                if let error = state.error { Text(error.localizedKey, bundle: FlareAppleUILocalization.bundle).foregroundStyle(.red) }
            } footer: {
                Text("proxy_test_hint", bundle: FlareAppleUILocalization.bundle)
            }
        }
        .formStyle(.grouped)
        .onChange(of: state.saveState) { _, operation in
            if operation is UiStateSuccess<KotlinUnit> { onClose() }
        }
        .autocorrectionDisabled()
        .disabled(isLoading)
        #if os(macOS)
        .textFieldStyle(.roundedBorder)
        #endif
    }

    private var cancelButton: some View {
        Button(action: onClose) {
            Label { Text("proxy_cancel", bundle: FlareAppleUILocalization.bundle) } icon: { Image(fontAwesome: .xmark) }
        }
        .disabled(isLoading)
    }

    private var saveButton: some View {
        Button { state.save(profile: value) } label: {
            Label { Text("proxy_save", bundle: FlareAppleUILocalization.bundle) } icon: { Image(fontAwesome: .check) }
        }
        .disabled(isLoading)
    }
}

private extension ProxySettingsError {
    var localizedKey: LocalizedStringKey {
        switch self {
        case .nameRequired: "proxy_error_name"
        case .invalidHost: "proxy_error_host"
        case .invalidPort: "proxy_error_port"
        case .inUse: "proxy_error_in_use"
        case .invalidPolicy: "proxy_error_policy"
        case .requestFailed: "proxy_error_request"
        default: "proxy_error_request"
        }
    }
}
