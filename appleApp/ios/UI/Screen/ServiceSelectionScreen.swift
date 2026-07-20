import SwiftUI
import FlareAppleUI
@preconcurrency import KotlinSharedUI
import AuthenticationServices
import Foundation
import WebKit
import FlareAppleCore

private enum ServiceSelectionAnimation {
    static let standard: Animation = .easeInOut(duration: 0.22)
    static let panel: AnyTransition = .asymmetric(
        insertion: .opacity.combined(with: .move(edge: .bottom)),
        removal: .opacity
    )
    static let inline: AnyTransition = .asymmetric(
        insertion: .opacity.combined(with: .scale(scale: 0.96)),
        removal: .opacity
    )
}

struct ServiceSelectionScreen: View {
    let toHome: () -> Void
    @StateObject private var presenter: KotlinPresenter<ServiceSelectState>
    @State private var instanceInput = ""
    @State private var selectedMethods: [String: LoginMethodType] = [:]

    init(toHome: @escaping () -> Void) {
        self.toHome = toHome
        self._presenter = .init(wrappedValue: .init(presenter: ServiceSelectPresenter(toHome: toHome)))
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
                        .id("login-\(node.platformType)-\(node.host)")
                        .transition(ServiceSelectionAnimation.panel)
                }
            } else {
                Section {
                    recommendedInstances(state: presenter.state)
                        .id("recommendations")
                        .transition(ServiceSelectionAnimation.panel)
                } header: {
                    Text(ServiceSelectCopy.welcomeListHint)
                }
            }
        }
        .listStyle(.insetGrouped)
        .animation(ServiceSelectionAnimation.standard, value: serviceContentKey(state: presenter.state))
        .onChange(of: instanceInput) { _, newValue in
            presenter.state.setFilter(value: newValue)
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
                    selectedMethods.removeAll()
                } label: {
                    Image(fontAwesome: instanceInput.isEmpty ? .magnifyingGlass : .xmark)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 16, height: 16)
                }
                .buttonStyle(.plain)
                .foregroundStyle(.secondary)
                .disabled(instanceInput.isEmpty)
                .accessibilityLabel(Text(instanceInput.isEmpty ? ServiceSelectCopy.search : ServiceSelectCopy.clear))
            }
            .frame(maxWidth: 420)
    }

    @ViewBuilder
    private func platformIndicator(state: ServiceSelectState) -> some View {
        ZStack {
            switch onEnum(of: state.detectedPlatformType) {
            case .success(let success):
                Image(fontAwesome: state.platformIcon(platformType: success.data.platformType).fontAwesomeIcon)
                    .resizable()
                    .scaledToFit()
                    .transition(ServiceSelectionAnimation.inline)
            case .loading:
                ProgressView()
                    .controlSize(.small)
                    .transition(ServiceSelectionAnimation.inline)
            case .error:
                Image(systemName: "questionmark.circle")
                    .foregroundStyle(.secondary)
                    .transition(ServiceSelectionAnimation.inline)
            }
        }
        .animation(ServiceSelectionAnimation.standard, value: platformIndicatorKey(state: state))
    }

    @ViewBuilder
    private func loginContent(state: ServiceSelectState, node: NodeData) -> some View {
        let methods = state.loginMethods(platformType: node.platformType)
        if let firstMethod = methods.first {
            let key = "\(node.platformType)-\(node.host)"
            let selectedMethod = selectedMethods[key] ?? firstMethod.type
            VStack(spacing: 14) {
                platformHeader(state: state, node: node)

                if node.compatibleMode {
                    Text(ServiceSelectCopy.compatibilityWarning(node.software))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .transition(ServiceSelectionAnimation.inline)
                }

                if methods.count > 1 {
                    Picker("", selection: Binding(
                        get: { selectedMethod },
                        set: { method in
                            withAnimation(ServiceSelectionAnimation.standard) {
                                selectedMethods[key] = method
                            }
                        }
                    )) {
                        ForEach(methods.indices, id: \.self) { index in
                            Text(methods[index].title.text).tag(methods[index].type)
                        }
                    }
                    .pickerStyle(.segmented)
                    .transition(ServiceSelectionAnimation.inline)
                }

                LoginFlowView {
                    state.createLoginHandler(
                        platformType: node.platformType,
                        host: node.host,
                        methodType: selectedMethod,
                        redirectUri: nil,
                    )
                }
                    .id("\(key)-\(selectedMethod)")
                    .transition(ServiceSelectionAnimation.panel)

                LoginAgreementView(
                    urlString: state.agreementUrl(platformType: node.platformType, host: node.host)
                )
                .transition(ServiceSelectionAnimation.inline)
            }
            .animation(ServiceSelectionAnimation.standard, value: "\(selectedMethod)")
        }
    }

    private func platformHeader(state: ServiceSelectState, node: NodeData) -> some View {
        HStack(spacing: 8) {
            Image(fontAwesome: state.platformIcon(platformType: node.platformType).fontAwesomeIcon)
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

    private func recommendedInstances(state: ServiceSelectState) -> some View {
        PagingView(
            data: state.instances,
            successContent: { instance in
                ServiceInstanceRow(
                    instance: instance,
                    icon: state.platformIcon(platformType: instance.type).fontAwesomeIcon
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

    private func select(instance: UiInstance, state: ServiceSelectState) {
        withAnimation(ServiceSelectionAnimation.standard) {
            instanceInput = instance.domain
            state.setFilter(value: instance.domain)
            selectedMethods.removeAll()
        }
    }

    private func detectedNode(from state: UiState<NodeData>) -> NodeData? {
        guard case .success(let success) = onEnum(of: state) else {
            return nil
        }
        return success.data
    }

    private func serviceContentKey(state: ServiceSelectState) -> String {
        if let node = detectedNode(from: state.detectedPlatformType), state.canNext {
            return "login-\(node.platformType)-\(node.host)-\(node.compatibleMode)"
        }
        return "recommendations"
    }

    private func platformIndicatorKey(state: ServiceSelectState) -> String {
        switch onEnum(of: state.detectedPlatformType) {
        case .success(let success):
            return "success-\(success.data.platformType)-\(success.data.host)"
        case .loading:
            return "loading"
        case .error:
            return "error"
        }
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
        case .pixiv:
            return "Pixiv"
        case .fanbox:
            return "FANBOX"
        }
    }
}

struct ReloginScreen: View {
    let target: ReloginTarget
    let toHome: () -> Void
    @Environment(\.dismiss) private var dismiss

    @StateObject private var presenter: KotlinPresenter<ReloginState>
    @State private var selectedMethod: LoginMethodType?

    init(target: ReloginTarget, toHome: @escaping () -> Void) {
        self.target = target
        self.toHome = toHome
        self._presenter = .init(wrappedValue: .init(presenter: ReloginPresenter(target: target, onSuccess: toHome)))
    }

    var body: some View {
        List {
            Section {
                header
                    .listRowBackground(Color.clear)
            }

            Section {
                loginContent(state: presenter.state)
                    .id("\(target.accountKey)-\(selectedMethod.map { String(describing: $0) } ?? "default")")
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(ServiceSelectCopy.loginExpired)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button {
                    dismiss()
                } label: {
                    Label {
                        Text("Cancel")
                    } icon: {
                        Image(fontAwesome: .xmark)
                    }
                }
            }
        }
    }

    private var header: some View {
        VStack(spacing: 8) {
            Image(fontAwesome: presenter.state.platformIcon().fontAwesomeIcon)
                .resizable()
                .scaledToFit()
                .frame(width: 36, height: 36)
            Text(ServiceSelectCopy.loginExpired)
                .font(.title2.weight(.semibold))
                .multilineTextAlignment(.center)
            Text("\(target.accountKey.id)@\(target.accountKey.host)")
                .font(.headline)
                .multilineTextAlignment(.center)
            Text(ServiceSelectCopy.loginExpiredMessage)
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
    }

    @ViewBuilder
    private func loginContent(state: ReloginState) -> some View {
        let methods = state.methods
        if let firstMethod = methods.first {
            let selectedMethod = selectedMethod ?? firstMethod.type
            VStack(spacing: 14) {
                if methods.count > 1 {
                    Picker("", selection: Binding(
                        get: { selectedMethod },
                        set: { method in
                            withAnimation(ServiceSelectionAnimation.standard) {
                                self.selectedMethod = method
                            }
                        }
                    )) {
                        ForEach(methods.indices, id: \.self) { index in
                            Text(methods[index].title.text).tag(methods[index].type)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                LoginFlowView {
                    state.createLoginHandler(methodType: selectedMethod)
                }
                    .id("\(target.accountKey)-\(selectedMethod)")

                LoginAgreementView(urlString: state.agreementUrl())
            }
        }
    }
}

private struct LoginFlowView: View {
    @Environment(\.webAuthenticationSession) private var webAuthenticationSession

    @StateObject private var presenter: KotlinPresenter<LoginFlowPresenterState>
    @State private var qrContent: String?
    @State private var webCookieUrl: String?

    init(handler: @escaping () -> LoginMethodHandler) {
        self._presenter = .init(wrappedValue: .init(presenter: LoginFlowPresenter(handler: handler())))
    }

    var body: some View {
        VStack(spacing: 12) {
            ForEach(presenter.state.flowState.fields, id: \.id) { field in
                LoginFieldView(field: field) { id, value in
                    presenter.state.updateField(id: id, value: value)
                } onSubmit: {
                    if let action = presenter.state.flowState.actions.first(where: { $0.enabled }) {
                        presenter.state.perform(actionId: action.id)
                    }
                }
                .transition(ServiceSelectionAnimation.inline)
            }

            if let qrContent {
                QRLoginView(content: qrContent)
                    .transition(ServiceSelectionAnimation.panel)
            }

            ForEach(presenter.state.flowState.actions, id: \.id) { action in
                Button {
                    presenter.state.perform(actionId: action.id)
                    if action.label == .cancel {
                        withAnimation(ServiceSelectionAnimation.standard) {
                            qrContent = nil
                        }
                    }
                } label: {
                    progressButtonLabel(title: action.label.text, isLoading: presenter.state.flowState.loading)
                }
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity)
                .disabled(!action.enabled || presenter.state.flowState.loading)
                .transition(ServiceSelectionAnimation.inline)
            }

            LoginErrorText(message: presenter.state.flowState.error)
                .transition(ServiceSelectionAnimation.inline)
        }
        .animation(ServiceSelectionAnimation.standard, value: flowAnimationKey)
        .task {
            await collectEffects()
        }
        .sheet(isPresented: Binding(
            get: { webCookieUrl != nil },
            set: { value in
                if !value {
                    webCookieUrl = nil
                }
            }
        )) {
            if let webCookieUrl {
                if #available(iOS 26.0, *) {
                    WebLoginScreen(onCookie: { cookie in
                        guard presenter.state.canResume(value: cookie) else { return }
                        presenter.state.resume(value: cookie)
                        self.webCookieUrl = nil
                    }, url: webCookieUrl)
                } else {
                    BackportWebLoginScreen(onCookie: { cookie in
                        guard presenter.state.canResume(value: cookie) else { return }
                        presenter.state.resume(value: cookie)
                        self.webCookieUrl = nil
                    }, url: webCookieUrl)
                }
            }
        }
    }

    private func collectEffects() async {
        for await effect in presenter.state.effects {
            switch onEnum(of: effect) {
            case .openUrl(let openUrl):
                await authenticate(url: openUrl.url)
            case .showQr(let showQr):
                withAnimation(ServiceSelectionAnimation.standard) {
                    qrContent = showQr.content
                }
            case .openWebCookieLogin(let webCookie):
                webCookieUrl = webCookie.url
            }
        }
    }

    private func progressButtonLabel(title: String, isLoading: Bool) -> some View {
        HStack(spacing: 8) {
            if isLoading {
                ProgressView()
                    .controlSize(.small)
                    .transition(ServiceSelectionAnimation.inline)
            }
            Text(title)
        }
        .frame(maxWidth: .infinity)
    }

    private var flowAnimationKey: String {
        let flowState = presenter.state.flowState
        let fields =
            flowState.fields
                .map { "\($0.id):\($0.type):\($0.readOnly)" }
                .joined(separator: ",")
        let actions =
            flowState.actions
                .map { "\($0.id):\($0.label):\($0.enabled)" }
                .joined(separator: ",")
        return [
            fields,
            actions,
            "\(flowState.loading)",
            flowState.error ?? "",
            qrContent ?? "",
        ].joined(separator: "|")
    }

    private func authenticate(url: String) async {
        guard let authURL = URL(string: url) else {
            presenter.state.onExternalAuthenticationDismissed(
                error: URLError(.badURL).localizedDescription
            )
            return
        }
        do {
            let response = try await webAuthenticationSession.authenticate(
                using: authURL,
                callbackURLScheme: authURL.isPixivOAuthUrl ? "pixiv" : APPSCHEMA
            )
            presenter.state.resume(value: response.absoluteString)
        } catch is CancellationError {
            presenter.state.onExternalAuthenticationDismissed(error: nil)
        } catch {
            presenter.state.onExternalAuthenticationDismissed(
                error: error.isCanceledWebAuthentication ? nil : error.localizedDescription
            )
        }
    }
}

private extension URL {
    var isPixivOAuthUrl: Bool {
        scheme == "https" &&
            host == "app-api.pixiv.net" &&
            path == "/web/v1/login"
    }
}

private extension Error {
    var isCanceledWebAuthentication: Bool {
        let error = self as NSError
        return error.domain == ASWebAuthenticationSessionErrorDomain &&
            error.code == ASWebAuthenticationSessionError.Code.canceledLogin.rawValue
    }
}

private struct LoginFieldView: View {
    let field: LoginField
    let onUpdate: (String, String) -> Void
    let onSubmit: () -> Void

    @State private var value: String

    init(
        field: LoginField,
        onUpdate: @escaping (String, String) -> Void,
        onSubmit: @escaping () -> Void
    ) {
        self.field = field
        self.onUpdate = onUpdate
        self.onSubmit = onSubmit
        self._value = .init(initialValue: field.value)
    }

    var body: some View {
        Group {
            switch field.type {
            case .passwordInput:
                SecureField(field.label.text, text: $value)
                    .textContentType(.password)
            case .displayText:
                Text(field.value)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            default:
                TextField(field.label.text, text: $value)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .textContentType(field.id == "username" ? .username : nil)
            }
        }
        .textFieldStyle(.roundedBorder)
        .disabled(field.readOnly)
        .onChange(of: value) { _, newValue in
            onUpdate(field.id, newValue)
        }
        .onSubmit {
            onSubmit()
        }

        LoginErrorText(message: field.error)
    }
}

private struct QRLoginView: View {
    let content: String

    var body: some View {
        VStack(spacing: 10) {
            Text(ServiceSelectCopy.nostrQRHint)
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            QRCodeView(text: content)
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
            Text(content)
                .font(.caption2)
                .textSelection(.enabled)
                .multilineTextAlignment(.center)
                .lineLimit(4)
        }
    }
}

private struct ServiceInstanceRow: View {
    let instance: UiInstance
    let icon: FontAwesomeIcon
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
            Image(fontAwesome: icon)
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
            Text("placeholder")
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
                .transition(ServiceSelectionAnimation.inline)
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

private enum ServiceSelectCopy {
    static let welcomeTitle = String(localized: "service_select_welcome_title", defaultValue: "Welcome to Flare")
    static let welcomeMessage = String(localized: "service_select_welcome_message", defaultValue: "Enter a server to get started.")
    static let welcomeHint = String(localized: "service_select_welcome_hint", defaultValue: "Flare supports Mastodon, Misskey, Bluesky, Nostr, and X.")
    static let welcomeListHint = String(localized: "service_select_welcome_list_hint", defaultValue: "Or choose from these servers")
    static let instancePlaceholder = String(localized: "service_select_instance_input_placeholder", defaultValue: "Instance URL")
    static let emptyMessage = String(localized: "service_select_empty_message", defaultValue: "No instances found")
    static let nostrQRHint = String(localized: "nostr_login_qr_hint", defaultValue: "Scan with Amber, Alby, or another Nostr Connect signer.")
    static let nostrQRWaiting = String(localized: "nostr_login_qr_waiting", defaultValue: "Waiting for signer approval...")
    static let nostrQRLinkLabel = String(localized: "nostr_login_qr_link_label", defaultValue: "Nostr Connect link")
    static let eulaPrivacyPolicy = String(localized: "eula_privacy_policy", defaultValue: "EULA and Privacy Policy")
    static let loginAgreementPrefix = String(localized: "login_agreement_prefix", defaultValue: "By logging in, you agree to the ")
    static let loginExpired = String(localized: "login_expired", defaultValue: "Login session expired")
    static let loginExpiredMessage = String(localized: "login_expired_message", defaultValue: "Log in again to continue using this account.")
    static let search = String(localized: "search")
    static let clear = String(localized: "Clear")

    static func compatibilityWarning(_ software: String) -> String {
        String(
            format: String(
                localized: "service_select_compatibility_warning",
                defaultValue: "This server uses %@, Flare will run in compatibility mode. Some features may not work properly."
            ),
            software
        )
    }
}
