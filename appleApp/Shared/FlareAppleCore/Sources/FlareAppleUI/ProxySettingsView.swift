import FlareAppleCore
import KotlinSharedUI
import SwiftUI

public struct ProxySettingsView: View {
    @State private var presenter = KotlinPresenter(presenter: ProxySettingsPresenter())
    @State private var editing: ProxyProfile?

    public init() {}

    public var body: some View {
        Form {
            Section {
                Text("proxy_description", bundle: FlareAppleUILocalization.bundle)
                Text("proxy_scope", bundle: FlareAppleUILocalization.bundle)
            }
            if let error = presenter.state.error { Text(verbatim: error).foregroundStyle(.red) }
            if let settings = presenter.state.settings {
                Section {
                    policyPicker(settings, accountKey: nil)
                } header: { Text("proxy_global", bundle: FlareAppleUILocalization.bundle) }
                Section {
                    ForEach(settings.profiles, id: \.id) { profile in
                        VStack(alignment: .leading, spacing: 8) {
                            Text(verbatim: profile.name)
                            Text(verbatim: "\(profile.protocol.name) · \(profile.host):\(profile.port)").font(.caption)
                            HStack {
                                Button { editing = profile } label: { Text("proxy_edit", bundle: FlareAppleUILocalization.bundle) }
                                Button { presenter.state.test(profile: profile) } label: { Text("proxy_test", bundle: FlareAppleUILocalization.bundle) }
                                Button(role: .destructive) { presenter.state.remove(id: profile.id) } label: { Text("proxy_delete", bundle: FlareAppleUILocalization.bundle) }
                                    .disabled(inUse(profile, settings))
                            }
                            .buttonStyle(.borderless)
                            if inUse(profile, settings) { Text("proxy_in_use", bundle: FlareAppleUILocalization.bundle).font(.caption) }
                            if presenter.state.isTested(profile: profile) { Text("proxy_test_success", bundle: FlareAppleUILocalization.bundle) }
                        }
                    }
                    Button {
                        editing = ProxyProfile(id: UUID().uuidString, name: "", protocol: .https, host: "", port: 443)
                    } label: { Text("proxy_add", bundle: FlareAppleUILocalization.bundle) }
                } header: { Text("proxy_profiles", bundle: FlareAppleUILocalization.bundle) }
                Section {
                    ForEach(Array(presenter.state.accounts), id: \.accountKey) { account in
                        if account.accountKey.host != "nostr" {
                            VStack(alignment: .leading) {
                                Text(verbatim: "\(account.platformDisplayName) · \(account.accountKey)")
                                policyPicker(settings, accountKey: account.accountKey)
                            }
                        }
                    }
                } header: { Text("proxy_accounts", bundle: FlareAppleUILocalization.bundle) }
            }
            if presenter.state.busy { ProgressView() }
        }
        .formStyle(.grouped)
        .disabled(presenter.state.busy)
        .navigationTitle(Text("proxy_title", bundle: FlareAppleUILocalization.bundle))
        .sheet(isPresented: Binding(get: { editing != nil }, set: { if !$0 { editing = nil } })) {
            if let profile = editing {
                ProxyEditor(profile: profile, state: presenter.state, onClose: { editing = nil })
            }
        }
    }

    private func inUse(_ profile: ProxyProfile, _ settings: ProxySettings) -> Bool {
        settings.global.proxyId == profile.id || settings.accounts.values.contains { $0.proxyId == profile.id }
    }

    private func policyPicker(_ settings: ProxySettings, accountKey: MicroBlogKey?) -> some View {
        let policy = settings.policy(accountKey: accountKey)
        let options = [ProxyPolicy(mode: accountKey == nil ? .system : .inherit, proxyId: nil), ProxyPolicy(mode: .direct, proxyId: nil)] + settings.profiles.map { ProxyPolicy(mode: .proxy, proxyId: $0.id) }
        return VStack(alignment: .leading) {
            Picker(selection: Binding(get: { policy }, set: { presenter.state.select(accountKey: accountKey, policy: $0) })) {
                ForEach(options, id: \.self) { option in Text(policyLabel(option, settings)).tag(option) }
            } label: { Text("proxy_title", bundle: FlareAppleUILocalization.bundle) }
            if policy.mode == .inherit {
                Text("\(String(localized: "proxy_effective", bundle: FlareAppleUILocalization.bundle)): \(policyLabel(settings.global, settings))").font(.caption)
            }
        }
    }

    private func policyLabel(_ policy: ProxyPolicy, _ settings: ProxySettings) -> String {
        switch policy.mode {
        case .system: String(localized: "proxy_system", bundle: FlareAppleUILocalization.bundle)
        case .direct: String(localized: "proxy_direct", bundle: FlareAppleUILocalization.bundle)
        case .inherit: String(localized: "proxy_inherit", bundle: FlareAppleUILocalization.bundle)
        default: settings.profiles.first { $0.id == policy.proxyId }?.name ?? ""
        }
    }
}

private struct ProxyEditor: View {
    let profile: ProxyProfile
    let state: ProxySettingsState
    let onClose: () -> Void
    @State private var name: String
    @State private var host: String
    @State private var port: String
    @State private var proxyProtocol: ProxyProtocol

    init(profile: ProxyProfile, state: ProxySettingsState, onClose: @escaping () -> Void) {
        self.profile = profile
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

    var body: some View {
        NavigationStack {
            Form {
                TextField(text: $name) { Text("proxy_name", bundle: FlareAppleUILocalization.bundle) }
                Picker(selection: $proxyProtocol) {
                    Text("HTTPS").tag(ProxyProtocol.https)
                    Text("SOCKS5").tag(ProxyProtocol.socks5)
                } label: { Text("proxy_protocol", bundle: FlareAppleUILocalization.bundle) }
                TextField(text: $host) { Text("proxy_host", bundle: FlareAppleUILocalization.bundle) }
                    #if os(iOS)
                    .textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.URL)
                    #endif
                TextField(text: $port) { Text("proxy_port", bundle: FlareAppleUILocalization.bundle) }
                    #if os(iOS)
                    .keyboardType(.numberPad)
                    #endif
                Text("proxy_no_auth", bundle: FlareAppleUILocalization.bundle).font(.caption)
                Text("proxy_test_hint", bundle: FlareAppleUILocalization.bundle).font(.caption)
                Button { state.test(profile: value) } label: { Text("proxy_test", bundle: FlareAppleUILocalization.bundle) }
                if state.isTested(profile: value) { Text("proxy_test_success", bundle: FlareAppleUILocalization.bundle) }
                if let error = state.error { Text(verbatim: error).foregroundStyle(.red) }
                if state.busy { ProgressView() }
            }
            .formStyle(.grouped)
            .disabled(state.busy)
            .navigationTitle(Text("proxy_edit", bundle: FlareAppleUILocalization.bundle))
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(action: onClose) { Text("proxy_cancel", bundle: FlareAppleUILocalization.bundle) }.disabled(state.busy) }
                ToolbarItem(placement: .confirmationAction) { Button { state.save(profile: value, onSaved: onClose) } label: { Text("proxy_save", bundle: FlareAppleUILocalization.bundle) }.disabled(state.busy) }
            }
        }
        #if os(macOS)
        .frame(width: 520, height: 500)
        #endif
    }
}
