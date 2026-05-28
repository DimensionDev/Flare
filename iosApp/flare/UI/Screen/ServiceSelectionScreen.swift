import SwiftUI
@preconcurrency import KotlinSharedUI
import AuthenticationServices
import WebKit
import CoreImage.CIFilterBuiltins

struct ServiceSelectionScreen: View {
    @Environment(\.webAuthenticationSession) private var webAuthenticationSession

    let toHome: () -> Void

    @StateObject private var presenter: KotlinPresenter<ServiceSelectState>
    @StateObject private var vvoLoginPresenter: KotlinPresenter<VVOLoginState>
    @StateObject private var xqtLoginPresenter: KotlinPresenter<XQTLoginState>

    @State private var instanceInput = ""
    @State private var showVVOLoginSheet = false
    @State private var showXQTLoginSheet = false
    @State private var blueskyMode: BlueskyLoginMode = .password
    @State private var blueskyUsername = ""
    @State private var blueskyPassword = ""
    @State private var blueskyAuthFactorToken = ""
    @State private var nostrMode: NostrLoginMode = .key
    @State private var nostrCredential = ""

    init(toHome: @escaping () -> Void) {
        self.toHome = toHome
        self._presenter = .init(wrappedValue: .init(presenter: ServiceSelectPresenter(toHome: toHome)))
        self._vvoLoginPresenter = .init(wrappedValue: .init(presenter: VVOLoginPresenter(toHome: toHome)))
        self._xqtLoginPresenter = .init(wrappedValue: .init(presenter: XQTLoginPresenter(toHome: toHome)))
    }

    var body: some View {
        List {
            Section {
                header
                    .listRowBackground(Color.clear)
            }

            Section {
                instanceInputView(state: presenter.state)
            } footer: {
                Text(ServiceSelectCopy.welcomeHint)
            }

            if let node = detectedNode(from: presenter.state.detectedPlatformType), presenter.state.canNext {
                Section {
                    loginContent(state: presenter.state, node: node)
                }
            } else {
                Section {
                    recommendedInstances(state: presenter.state)
                } header: {
                    Text(ServiceSelectCopy.welcomeListHint)
                }
            }
        }
        .listStyle(.insetGrouped)
        .onChange(of: instanceInput) { _, newValue in
            presenter.state.setFilter(value: newValue)
        }
        .sheet(isPresented: $showVVOLoginSheet, onDismiss: {
            showVVOLoginSheet = false
        }, content: {
            if #available(iOS 26.0, *) {
                WebLoginScreen(onCookie: { cookie in
                    if vvoLoginPresenter.state.checkChocolate(cookie: cookie) {
                        vvoLoginPresenter.state.login(chocolate: cookie)
                    }
                }, url: "https://\(PlatformTypeKt.vvoHost)/login?backURL=https://\(PlatformTypeKt.vvoHost)/")
            } else {
                BackportWebLoginScreen(onCookie: { cookie in
                    if vvoLoginPresenter.state.checkChocolate(cookie: cookie) {
                        vvoLoginPresenter.state.login(chocolate: cookie)
                    }
                }, url: "https://\(PlatformTypeKt.vvoHost)/login?backURL=https://\(PlatformTypeKt.vvoHost)/")
            }
        })
        .sheet(isPresented: $showXQTLoginSheet) {
            showXQTLoginSheet = false
        } content: {
            if #available(iOS 26.0, *) {
                WebLoginScreen(onCookie: { cookie in
                    if xqtLoginPresenter.state.checkChocolate(cookie: cookie) {
                        xqtLoginPresenter.state.login(chocolate: cookie)
                    }
                }, url: "https://\(PlatformTypeKt.xqtHost)")
            } else {
                BackportWebLoginScreen(onCookie: { cookie in
                    if xqtLoginPresenter.state.checkChocolate(cookie: cookie) {
                        xqtLoginPresenter.state.login(chocolate: cookie)
                    }
                }, url: "https://\(PlatformTypeKt.xqtHost)")
            }
        }
    }

    private var header: some View {
        VStack(spacing: 8) {
            Text(ServiceSelectCopy.welcomeTitle)
                .font(.title)
                .fontWeight(.semibold)
                .multilineTextAlignment(.center)
            Text(ServiceSelectCopy.welcomeMessage)
                .font(.body)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: 520)
    }

    private func instanceInputView(state: ServiceSelectState) -> some View {
        TextField(ServiceSelectCopy.instancePlaceholder, text: $instanceInput)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .keyboardType(.URL)
            .submitLabel(.done)
            .disabled(state.loading)
            .onSubmit {
                state.setFilter(value: instanceInput)
            }
            .safeAreaInset(edge: .leading) {
                platformIndicator(state: state)
                    .frame(width: 24, height: 24)
            }
            .safeAreaInset(edge: .trailing) {
                Button {
                    instanceInput = ""
                    state.setFilter(value: "")
                    resetLoginInputs()
                } label: {
                    Image(instanceInput.isEmpty ? "fa-magnifying-glass" : "fa-xmark")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 16, height: 16)
                }
                .buttonStyle(.plain)
                .foregroundStyle(.secondary)
                .disabled(instanceInput.isEmpty)
                .accessibilityLabel(Text(instanceInput.isEmpty ? "Search" : "Clear"))
            }
            .frame(maxWidth: 420)
    }

    @ViewBuilder
    private func platformIndicator(state: ServiceSelectState) -> some View {
        switch onEnum(of: state.detectedPlatformType) {
        case .success(let success):
            Image(state.platformIcon(platformType: success.data.platformType).imageName)
                .resizable()
                .scaledToFit()
        case .loading:
            ProgressView()
                .controlSize(.small)
        case .error:
            Image(systemName: "questionmark.circle")
                .foregroundStyle(.secondary)
        }
    }

    @ViewBuilder
    private func loginContent(state: ServiceSelectState, node: NodeData) -> some View {
        VStack(spacing: 14) {
            platformHeader(state: state, node: node)

            if node.compatibleMode {
                Text(ServiceSelectCopy.compatibilityWarning(node.software))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }

            switch node.platformType {
            case .mastodon:
                mastodonLogin(state: state, node: node)
            case .misskey:
                misskeyLogin(state: state, node: node)
            case .bluesky:
                blueskyLogin(state: state, node: node)
            case .nostr:
                nostrLogin(state: state)
            case .xQt:
                nextButton {
                    showXQTLoginSheet = true
                }
            case .vvo:
                nextButton {
                    showVVOLoginSheet = true
                }
            }

            LoginAgreementView(
                urlString: state.agreementUrl(platformType: node.platformType, host: node.host)
            )
        }
    }

    private func platformHeader(state: ServiceSelectState, node: NodeData) -> some View {
        HStack(spacing: 8) {
            Image(state.platformIcon(platformType: node.platformType).imageName)
                .resizable()
                .scaledToFit()
                .frame(width: 24, height: 24)
            VStack(alignment: .leading, spacing: 2) {
                Text(platformTitle(node.platformType))
                    .font(.headline)
                Text(node.host)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
        }
    }

    @ViewBuilder
    private func mastodonLogin(state: ServiceSelectState, node: NodeData) -> some View {
        if let resumedState = state.mastodonLoginState.resumedState {
            verificationState(resumedState)
        } else {
            nextButton(isLoading: state.mastodonLoginState.loading) {
                state.mastodonLoginState.login(host: node.host) { url in
                    authenticate(url: url) { callbackURL in
                        state.mastodonLoginState.resume(url: callbackURL)
                    }
                }
            }
            LoginErrorText(message: state.mastodonLoginState.error)
        }
    }

    @ViewBuilder
    private func misskeyLogin(state: ServiceSelectState, node: NodeData) -> some View {
        if let resumedState = state.misskeyLoginState.resumedState {
            verificationState(resumedState)
        } else {
            nextButton(isLoading: state.misskeyLoginState.loading) {
                state.misskeyLoginState.login(host: node.host) { url in
                    authenticate(url: url) { callbackURL in
                        state.misskeyLoginState.resume(url: callbackURL)
                    }
                }
            }
            LoginErrorText(message: state.misskeyLoginState.error)
        }
    }

    private func blueskyLogin(state: ServiceSelectState, node: NodeData) -> some View {
        VStack(spacing: 12) {
            Picker("", selection: $blueskyMode) {
                ForEach(BlueskyLoginMode.allCases) { mode in
                    Text(mode.title).tag(mode)
                }
            }
            .pickerStyle(.segmented)
            .onChange(of: blueskyMode) { _, _ in
                state.blueskyLoginState.clear()
                state.blueskyOauthLoginState.clear()
            }

            TextField(ServiceSelectCopy.blueskyUsername, text: $blueskyUsername)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(.emailAddress)
                .textContentType(.username)
                .submitLabel(blueskyMode == .password ? .next : .done)
                .disabled(state.blueskyLoginState.loading || state.blueskyOauthLoginState.loading)
                .textFieldStyle(.roundedBorder)

            if blueskyMode == .password {
                SecureField(ServiceSelectCopy.blueskyPassword, text: $blueskyPassword)
                    .textContentType(.password)
                    .disabled(state.blueskyLoginState.loading)
                    .textFieldStyle(.roundedBorder)

                if state.blueskyLoginState.require2FA {
                    TextField(ServiceSelectCopy.blueskyAuthFactorToken, text: $blueskyAuthFactorToken)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .textFieldStyle(.roundedBorder)
                }
            } else {
                Text(ServiceSelectCopy.blueskyOAuthHint)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }

            Button {
                if blueskyMode == .password {
                    state.blueskyLoginState.login(
                        baseUrl: node.host,
                        username: blueskyUsername,
                        password: blueskyPassword,
                        authFactorToken: blueskyAuthFactorToken
                    )
                } else {
                    state.blueskyOauthLoginState.login(baseUrl: node.host, userName: blueskyUsername) { url in
                        authenticate(url: url) { callbackURL in
                            state.blueskyOauthLoginState.resume(url: callbackURL)
                        }
                    }
                }
            } label: {
                progressButtonLabel(
                    title: ServiceSelectCopy.loginButton,
                    isLoading: state.blueskyLoginState.loading || state.blueskyOauthLoginState.loading
                )
            }
            .buttonStyle(.borderedProminent)
            .frame(maxWidth: .infinity)
            .disabled(!canLoginBluesky || state.blueskyLoginState.loading || state.blueskyOauthLoginState.loading)

            LoginErrorText(message: state.blueskyLoginState.error?.message ?? state.blueskyOauthLoginState.error)
        }
    }

    @ViewBuilder
    private func nostrLogin(state: ServiceSelectState) -> some View {
        VStack(spacing: 12) {
            Picker("", selection: $nostrMode) {
                ForEach(NostrLoginMode.available(amberAvailable: state.nostrLoginState.amberAvailable)) { mode in
                    Text(mode.title).tag(mode)
                }
            }
            .pickerStyle(.segmented)
            .onChange(of: state.nostrLoginState.amberAvailable) { _, amberAvailable in
                if !amberAvailable && nostrMode == .amber {
                    nostrMode = .key
                }
            }

            switch nostrMode {
            case .key:
                TextField(ServiceSelectCopy.nostrAccountHint, text: $nostrCredential)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .textFieldStyle(.roundedBorder)
                    .disabled(state.nostrLoginState.loading)

                Button {
                    state.nostrLoginState.login(input: nostrCredential)
                } label: {
                    progressButtonLabel(title: ServiceSelectCopy.loginButton, isLoading: state.nostrLoginState.loading)
                }
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity)
                .disabled(nostrCredential.isEmpty || state.nostrLoginState.loading)

            case .qr:
                Button {
                    state.nostrLoginState.startQrLogin()
                } label: {
                    progressButtonLabel(title: ServiceSelectCopy.nostrQRButton, isLoading: state.nostrLoginState.loading)
                }
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity)
                .disabled(state.nostrLoginState.loading || state.nostrLoginState.qrConnectUri != nil)

                if let connectUri = state.nostrLoginState.qrConnectUri {
                    VStack(spacing: 10) {
                        Text(ServiceSelectCopy.nostrQRHint)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                        QRCodeView(text: connectUri)
                            .frame(width: 220, height: 220)
                            .background(.white, in: RoundedRectangle(cornerRadius: 8))
                        HStack(spacing: 8) {
                            ProgressView()
                                .controlSize(.small)
                            Text(ServiceSelectCopy.nostrQRWaiting)
                                .font(.caption)
                        }
                        Text(ServiceSelectCopy.nostrQRLinkLabel)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Text(connectUri)
                            .font(.caption2)
                            .textSelection(.enabled)
                            .multilineTextAlignment(.center)
                            .lineLimit(4)
                        Button(ServiceSelectCopy.cancelButton) {
                            state.nostrLoginState.cancelQrLogin()
                        }
                        .buttonStyle(.bordered)
                    }
                }

            case .amber:
                Button {
                    state.nostrLoginState.connectAmber()
                } label: {
                    progressButtonLabel(title: ServiceSelectCopy.nostrAmberButton, isLoading: state.nostrLoginState.loading)
                }
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity)
                .disabled(state.nostrLoginState.loading || !state.nostrLoginState.amberAvailable)
            }

            LoginErrorText(message: state.nostrLoginState.error?.message)
        }
    }

    @ViewBuilder
    private func verificationState(_ state: UiState<KotlinNothing>) -> some View {
        switch onEnum(of: state) {
        case .loading:
            VStack(spacing: 8) {
                Text(ServiceSelectCopy.verifyingCredentials)
                    .font(.callout)
                    .multilineTextAlignment(.center)
                ProgressView()
            }
        case .error(let error):
            LoginErrorText(message: error.throwable.message ?? "Unknown error")
        case .success:
            EmptyView()
        }
    }

    private func recommendedInstances(state: ServiceSelectState) -> some View {
        PagingView(
            data: state.instances,
            successContent: { instance in
                ServiceInstanceRow(
                    instance: instance,
                    iconName: state.platformIcon(platformType: instance.type).imageName
                ) {
                    select(instance: instance, state: state)
                }
            },
            loadingContent: {
                ServiceInstancePlaceholderRow()
            },
            errorContent: { error, retry in
                ListErrorView(error: error, onRetry: retry)
            },
            emptyContent: {
                Text(ServiceSelectCopy.emptyMessage)
                    .font(.headline)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity)
                    .padding()
            }
        )
    }

    private func nextButton(isLoading: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            progressButtonLabel(title: ServiceSelectCopy.nextButton, isLoading: isLoading)
        }
        .buttonStyle(.borderedProminent)
        .frame(maxWidth: .infinity)
        .disabled(isLoading)
    }

    private func progressButtonLabel(title: String, isLoading: Bool) -> some View {
        HStack(spacing: 8) {
            if isLoading {
                ProgressView()
                    .controlSize(.small)
            }
            Text(title)
        }
        .frame(maxWidth: .infinity)
    }

    private var canLoginBluesky: Bool {
        !blueskyUsername.isEmpty && (blueskyMode == .oauth || !blueskyPassword.isEmpty)
    }

    private func select(instance: UiInstance, state: ServiceSelectState) {
        instanceInput = instance.domain
        state.setFilter(value: instance.domain)
        resetLoginInputs()
    }

    private func resetLoginInputs() {
        blueskyMode = .password
        blueskyUsername = ""
        blueskyPassword = ""
        blueskyAuthFactorToken = ""
        nostrMode = .key
        nostrCredential = ""
        presenter.state.blueskyLoginState.clear()
        presenter.state.blueskyOauthLoginState.clear()
    }

    private func authenticate(url: String, onCallback: @escaping (String) -> Void) {
        Task {
            guard let authURL = URL(string: url) else { return }
            let response = try? await webAuthenticationSession.authenticate(
                using: authURL,
                callbackURLScheme: APPSCHEMA
            )
            if let responseString = response?.absoluteString {
                onCallback(responseString)
            }
        }
    }

    private func detectedNode(from state: UiState<NodeData>) -> NodeData? {
        guard case .success(let success) = onEnum(of: state) else {
            return nil
        }
        return success.data
    }

    private func platformTitle(_ type: PlatformType) -> String {
        switch type {
        case .mastodon:
            return "Mastodon"
        case .misskey:
            return "Misskey"
        case .bluesky:
            return "Bluesky"
        case .nostr:
            return "Nostr"
        case .xQt:
            return "X"
        case .vvo:
            return "Weibo"
        }
    }
}

private enum BlueskyLoginMode: String, CaseIterable, Identifiable {
    case password
    case oauth

    var id: Self { self }

    var title: String {
        switch self {
        case .password:
            ServiceSelectCopy.blueskyPasswordMode
        case .oauth:
            ServiceSelectCopy.blueskyOAuthMode
        }
    }
}

private enum NostrLoginMode: String, CaseIterable, Identifiable {
    case key
    case qr
    case amber

    var id: Self { self }

    var title: String {
        switch self {
        case .key:
            return "Key"
        case .qr:
            return "QR"
        case .amber:
            return "Amber"
        }
    }

    static func available(amberAvailable: Bool) -> [NostrLoginMode] {
        amberAvailable ? [.key, .qr, .amber] : [.key, .qr]
    }
}

private struct ServiceInstanceRow: View {
    let instance: UiInstance
    let iconName: String
    let onSelect: () -> Void

    var body: some View {
        Button(action: onSelect) {
            Label {
                Text(instance.name)
                    .lineLimit(1)
                Text(instance.domain)
                    .lineLimit(1)
                if let description = instance.description_, !description.isEmpty {
                    Text(description)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                }
            } icon: {
                instanceIcon
                    .frame(width: 32, height: 32)
            }
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private var instanceIcon: some View {
        if let iconUrl = instance.iconUrl, !iconUrl.isEmpty {
            NetworkImage(data: iconUrl)
                .frame(maxWidth: 32)
                .clipShape(RoundedRectangle(cornerRadius: 4))
        } else {
            Image(iconName)
                .resizable()
                .scaledToFit()
        }
    }
}

private struct ServiceInstancePlaceholderRow: View {
    var body: some View {
        Label {
            Text("Placeholder")
                .lineLimit(1)
            Text("palceholder")
                .lineLimit(1)
            Text("Description Placeholder")
                .lineLimit(2)
                .multilineTextAlignment(.leading)
        } icon: {
            Image(systemName: "questionmark.circle")
        }
        .redacted(reason: .placeholder)
    }
}

private struct LoginErrorText: View {
    let message: String?

    var body: some View {
        if let message, !message.isEmpty {
            Text(message)
                .font(.caption)
                .foregroundStyle(.red)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
        }
    }
}

private struct LoginAgreementView: View {
    let urlString: String?

    var body: some View {
        if let urlString, let url = URL(string: urlString) {
            Text(agreement(url: url))
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
    }

    private func agreement(url: URL) -> AttributedString {
        var text = AttributedString(ServiceSelectCopy.loginAgreementPrefix + ServiceSelectCopy.eulaPrivacyPolicy + ".")
        if let range = text.range(of: ServiceSelectCopy.eulaPrivacyPolicy) {
            text[range].link = url
            text[range].foregroundColor = .accentColor
        }
        return text
    }
}

private struct QRCodeView: View {
    let text: String

    var body: some View {
        if let image = makeQRCode(from: text) {
            Image(uiImage: image)
                .interpolation(.none)
                .resizable()
                .scaledToFit()
                .padding(12)
        } else {
            Image(systemName: "qrcode")
                .resizable()
                .scaledToFit()
                .padding(40)
                .foregroundStyle(.secondary)
        }
    }

    private func makeQRCode(from text: String) -> UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(text.utf8)

        guard let outputImage = filter.outputImage else {
            return nil
        }

        let scaledImage = outputImage.transformed(by: CGAffineTransform(scaleX: 10, y: 10))
        guard let cgImage = CIContext().createCGImage(scaledImage, from: scaledImage.extent) else {
            return nil
        }
        return UIImage(cgImage: cgImage)
    }
}

private enum ServiceSelectCopy {
    static let welcomeTitle = String(localized: "service_select_welcome_title", defaultValue: "Welcome to Flare")
    static let welcomeMessage = String(localized: "service_select_welcome_message", defaultValue: "Enter a server to get started.")
    static let welcomeHint = String(localized: "service_select_welcome_hint", defaultValue: "Flare supports Mastodon, Misskey, Bluesky, Nostr, and X.")
    static let welcomeListHint = String(localized: "service_select_welcome_list_hint", defaultValue: "Or choose from these servers")
    static let instancePlaceholder = String(localized: "service_select_instance_input_placeholder", defaultValue: "Instance URL")
    static let nextButton = String(localized: "service_select_next_button", defaultValue: "Next")
    static let emptyMessage = String(localized: "service_select_empty_message", defaultValue: "No instances found")
    static let blueskyUsername = String(localized: "bluesky_login_username_hint", defaultValue: "Username")
    static let blueskyPassword = String(localized: "bluesky_login_password_hint", defaultValue: "Password")
    static let blueskyAuthFactorToken = String(localized: "bluesky_login_auth_factor_token_hint", defaultValue: "2FA token")
    static let blueskyOAuthMode = String(localized: "bluesky_login_oauth_button", defaultValue: "Log in with OAuth")
    static let blueskyPasswordMode = String(localized: "bluesky_login_use_password_button", defaultValue: "Use password")
    static let blueskyOAuthHint = String(localized: "bluesky_login_oauth_hint", defaultValue: "Bluesky OAuth may still be unstable. If you encounter issues, use password login instead.")
    static let loginButton = String(localized: "login_button", defaultValue: "Log in")
    static let verifyingCredentials = String(localized: "mastodon_login_verify_message", defaultValue: "Verifying your credentials...")
    static let nostrAccountHint = String(localized: "nostr_login_account_hint", defaultValue: "npub, nsec, hex key, bunker://, or nostrconnect://")
    static let nostrAmberButton = String(localized: "nostr_login_amber_button", defaultValue: "Connect Amber")
    static let nostrQRButton = String(localized: "nostr_login_qr_button", defaultValue: "Connect with QR")
    static let nostrQRHint = String(localized: "nostr_login_qr_hint", defaultValue: "Scan with Amber, Alby, or another Nostr Connect signer.")
    static let nostrQRWaiting = String(localized: "nostr_login_qr_waiting", defaultValue: "Waiting for signer approval...")
    static let nostrQRLinkLabel = String(localized: "nostr_login_qr_link_label", defaultValue: "Nostr Connect link")
    static let cancelButton = String(localized: "cancel_button", defaultValue: "Cancel")
    static let eulaPrivacyPolicy = String(localized: "eula_privacy_policy", defaultValue: "EULA and Privacy Policy")
    static let loginAgreementPrefix = String(localized: "login_agreement_prefix", defaultValue: "By logging in, you agree to the ")

    static func compatibilityWarning(_ software: String) -> String {
        "This server uses \(software), Flare will run in compatibility mode. Some features may not work properly."
    }
}
