import SwiftUI
import AuthenticationServices
import Combine
import Foundation
import FlareAppleCore
import FlareAppleUI
@preconcurrency import KotlinSharedUI
import WebKit

private enum MacOSServiceSelectionAnimation {
    static let standard: Animation = .easeInOut(duration: 0.2)
    static let inline: AnyTransition = .opacity.combined(with: .scale(scale: 0.98))
}

struct ServiceSelectionScreen: View {
    @Environment(\.dismiss) private var dismiss

    let toHome: () -> Void

    @StateObject private var presenter: KotlinPresenter<ServiceSelectState>
    @State private var instanceInput = ""
    @State private var selectedMethods: [String: LoginMethodType] = [:]

    init(toHome: @escaping () -> Void) {
        self.toHome = toHome
        self._presenter = .init(wrappedValue: .init(presenter: ServiceSelectPresenter(toHome: toHome)))
    }

    var body: some View {
        Form {
            Section {
                header
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
                        .transition(MacOSServiceSelectionAnimation.inline)
                }
            } else {
                Section {
                    recommendedInstances(state: presenter.state)
                        .id("recommendations")
                        .transition(MacOSServiceSelectionAnimation.inline)
                } header: {
                    Text(ServiceSelectCopy.welcomeListHint)
                }
            }
        }
        .formStyle(.grouped)
        .animation(MacOSServiceSelectionAnimation.standard, value: serviceContentKey(state: presenter.state))
        .onChange(of: instanceInput) { _, newValue in
            presenter.state.setFilter(value: newValue)
        }
        .navigationTitle("Login")
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
        VStack(alignment: .leading, spacing: 8) {
            Text(ServiceSelectCopy.welcomeTitle)
                .font(.title2.weight(.semibold))
            Text(ServiceSelectCopy.welcomeMessage)
                .font(.body)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func instanceInputView(state: ServiceSelectState) -> some View {
        HStack(spacing: 10) {
            platformIndicator(state: state)
                .frame(width: 20, height: 20)

            TextField(ServiceSelectCopy.instancePlaceholder, text: $instanceInput)
                .textFieldStyle(.roundedBorder)
                .disabled(state.loading)
                .onSubmit {
                    state.setFilter(value: instanceInput)
                }

            Button {
                instanceInput = ""
                state.setFilter(value: "")
                selectedMethods.removeAll()
            } label: {
                Image(fontAwesome: instanceInput.isEmpty ? .magnifyingGlass : .xmark)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 14, height: 14)
            }
            .buttonStyle(.borderless)
            .foregroundStyle(.secondary)
            .disabled(instanceInput.isEmpty)
            .help(instanceInput.isEmpty ? ServiceSelectCopy.search : ServiceSelectCopy.clear)
        }
    }

    @ViewBuilder
    private func platformIndicator(state: ServiceSelectState) -> some View {
        ZStack {
            switch onEnum(of: state.detectedPlatformType) {
            case .success(let success):
                Image(fontAwesome: state.platformIcon(platformType: success.data.platformType).fontAwesomeIcon)
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
        .animation(MacOSServiceSelectionAnimation.standard, value: platformIndicatorKey(state: state))
    }

    @ViewBuilder
    private func loginContent(state: ServiceSelectState, node: NodeData) -> some View {
        let methods = state.loginMethods(platformType: node.platformType)
        if let firstMethod = methods.first {
            let key = "\(node.platformType)-\(node.host)"
            let selectedMethod = Binding<LoginMethodType>(
                get: { selectedMethods[key] ?? firstMethod.type },
                set: { method in
                    withAnimation(MacOSServiceSelectionAnimation.standard) {
                        selectedMethods[key] = method
                    }
                }
            )

            VStack(alignment: .leading, spacing: 12) {
                platformHeader(state: state, node: node) {
                    if methods.count > 1 {
                        Picker("", selection: selectedMethod) {
                            ForEach(methods.indices, id: \.self) { index in
                                Text(methods[index].title.text).tag(methods[index].type)
                            }
                        }
                        .pickerStyle(.menu)
                        .labelsHidden()
                    }
                }

                if node.compatibleMode {
                    Text(ServiceSelectCopy.compatibilityWarning(node.software))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                LoginFlowView {
                    state.createLoginHandler(
                        platformType: node.platformType,
                        host: node.host,
                        methodType: selectedMethod.wrappedValue,
                        redirectUri: nil,
                    )
                }
                    .id("\(key)-\(selectedMethod.wrappedValue)")

                LoginAgreementView(
                    urlString: state.agreementUrl(platformType: node.platformType, host: node.host)
                )
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .animation(MacOSServiceSelectionAnimation.standard, value: "\(selectedMethod.wrappedValue)")
        }
    }

    private func platformHeader<Trailing: View>(
        state: ServiceSelectState,
        node: NodeData,
        @ViewBuilder trailing: () -> Trailing
    ) -> some View {
        HStack(spacing: 8) {
            Image(fontAwesome: state.platformIcon(platformType: node.platformType).fontAwesomeIcon)
                .resizable()
                .scaledToFit()
                .frame(width: 22, height: 22)

            VStack(alignment: .leading, spacing: 2) {
                Text(platformTitle(node.platformType))
                    .font(.headline)
                Text(node.host)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            trailing()
        }
    }

    private func recommendedInstances(state: ServiceSelectState) -> some View {
        PagingView(
            data: state.instances,
            maxCount: 20,
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
                    .padding(.vertical, 16)
            }
        )
    }

    private func select(instance: UiInstance, state: ServiceSelectState) {
        withAnimation(MacOSServiceSelectionAnimation.standard) {
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
            return "Fanbox"
        }
    }

}

struct ReloginScreen: View {
    @Environment(\.dismiss) private var dismiss

    let target: ReloginTarget
    let toHome: () -> Void

    @StateObject private var presenter: KotlinPresenter<ReloginState>
    @State private var selectedMethod: LoginMethodType?

    init(target: ReloginTarget, toHome: @escaping () -> Void) {
        self.target = target
        self.toHome = toHome
        self._presenter = .init(wrappedValue: .init(presenter: ReloginPresenter(target: target, onSuccess: toHome)))
    }

    var body: some View {
        Form {
            Section {
                header
            }
            Section {
                loginContent(state: presenter.state)
                    .id("\(target.accountKey)-\(selectedMethod.map { String(describing: $0) } ?? "default")")
            }
        }
        .formStyle(.grouped)
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
        HStack(spacing: 10) {
            Image(fontAwesome: presenter.state.platformIcon().fontAwesomeIcon)
                .resizable()
                .scaledToFit()
                .frame(width: 28, height: 28)
            VStack(alignment: .leading, spacing: 2) {
                Text(ServiceSelectCopy.loginExpired)
                    .font(.title3.weight(.semibold))
                Text("\(target.accountKey.id)@\(target.accountKey.host)")
                    .font(.headline)
                Text(ServiceSelectCopy.loginExpiredMessage)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
        }
    }

    @ViewBuilder
    private func loginContent(state: ReloginState) -> some View {
        let methods = state.methods
        if let firstMethod = methods.first {
            let selectedMethod = Binding<LoginMethodType>(
                get: { self.selectedMethod ?? firstMethod.type },
                set: { method in
                    withAnimation(MacOSServiceSelectionAnimation.standard) {
                        self.selectedMethod = method
                    }
                }
            )
            VStack(alignment: .leading, spacing: 12) {
                if methods.count > 1 {
                    Picker("", selection: selectedMethod) {
                        ForEach(methods.indices, id: \.self) { index in
                            Text(methods[index].title.text).tag(methods[index].type)
                        }
                    }
                    .pickerStyle(.menu)
                    .labelsHidden()
                }

                LoginFlowView {
                    state.createLoginHandler(methodType: selectedMethod.wrappedValue)
                }
                .id("\(target.accountKey)-\(selectedMethod.wrappedValue)")

                LoginAgreementView(urlString: state.agreementUrl())
            }
            .frame(maxWidth: .infinity, alignment: .leading)
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
        VStack(alignment: .leading, spacing: 10) {
            ForEach(presenter.state.flowState.fields, id: \.id) { field in
                LoginFieldView(field: field) { id, value in
                    presenter.state.updateField(id: id, value: value)
                } onSubmit: {
                    if let action = presenter.state.flowState.actions.first(where: { $0.enabled }) {
                        presenter.state.perform(actionId: action.id)
                    }
                }
            }

            if let qrContent {
                QRLoginView(content: qrContent)
                    .frame(maxWidth: .infinity)
                    .transition(MacOSServiceSelectionAnimation.inline)
            }

            ForEach(presenter.state.flowState.actions, id: \.id) { action in
                Button {
                    presenter.state.perform(actionId: action.id)
                    if action.label == .cancel {
                        withAnimation(MacOSServiceSelectionAnimation.standard) {
                            qrContent = nil
                        }
                    }
                } label: {
                    progressButtonLabel(title: action.label.text, isLoading: presenter.state.flowState.loading)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .frame(maxWidth: .infinity)
                .disabled(!action.enabled || presenter.state.flowState.loading)
            }

            LoginErrorText(message: presenter.state.flowState.error)
        }
        .animation(MacOSServiceSelectionAnimation.standard, value: flowAnimationKey)
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
                NavigationStack {
                    MacOSWebLoginScreen(onCookie: { cookie in
                        guard presenter.state.canResume(value: cookie) else {
                            return
                        }
                        presenter.state.resume(value: cookie)
                        self.webCookieUrl = nil
                    }, url: webCookieUrl)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button {
                                self.webCookieUrl = nil
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
                .frame(width: 600, height: 600)
            }
        }
    }

    private func collectEffects() async {
        for await effect in presenter.state.effects {
            switch onEnum(of: effect) {
            case .openUrl(let openUrl):
                await authenticate(url: openUrl.url)
            case .showQr(let showQr):
                withAnimation(MacOSServiceSelectionAnimation.standard) {
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
        VStack(alignment: .leading, spacing: 6) {
            switch field.type {
            case .passwordInput:
                SecureField(field.label.text, text: $value)
                    .textFieldStyle(.roundedBorder)
            case .displayText:
                Text(field.value)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            default:
                TextField(field.label.text, text: $value)
                    .textFieldStyle(.roundedBorder)
                    .textContentType(field.id == "username" ? .username : nil)
            }

            LoginErrorText(message: field.error)
        }
        .disabled(field.readOnly)
        .onChange(of: value) { _, newValue in
            onUpdate(field.id, newValue)
        }
        .onSubmit {
            onSubmit()
        }
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
                .frame(width: 200, height: 200)
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
            HStack(spacing: 10) {
                instanceIcon
                    .frame(width: 28, height: 28)

                VStack(alignment: .leading, spacing: 2) {
                    Text(instance.name)
                        .lineLimit(1)
                    Text(instance.domain)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                    if let description = instance.description_, !description.isEmpty {
                        Text(description)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(2)
                    }
                }

                Spacer()
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private var instanceIcon: some View {
        if let iconUrl = instance.iconUrl, !iconUrl.isEmpty {
            NetworkImage(data: iconUrl)
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
        HStack(spacing: 10) {
            Image(systemName: "questionmark.circle")
                .frame(width: 28, height: 28)
            VStack(alignment: .leading, spacing: 4) {
                Text("Placeholder")
                Text("placeholder")
                    .font(.caption)
                Text("Description Placeholder")
                    .font(.caption)
            }
            Spacer()
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
                .frame(maxWidth: .infinity, alignment: .leading)
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

private struct MacOSWebLoginScreen: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel: MacOSWebLoginViewModel
    private let url: String

    init(
        onCookie: @escaping (String) -> Void,
        url: String
    ) {
        self._viewModel = .init(wrappedValue: .init(onCookie: onCookie, url: url))
        self.url = url
    }

    var body: some View {
        VStack(spacing: 0) {
            if viewModel.canShowWebView {
                MacOSWebView(url: URL(string: url), configuration: viewModel.configuration) { webView in
                    webView.navigationDelegate = viewModel.delegate
                }
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
    }
}

private struct MacOSWebView: NSViewRepresentable {
    let url: URL?
    let configuration: WKWebViewConfiguration
    let configure: (WKWebView) -> Void

    func makeNSView(context: Context) -> WKWebView {
        let webView = WKWebView(frame: .zero, configuration: configuration)
        configure(webView)
        if let url {
            webView.load(URLRequest(url: url))
        }
        return webView
    }

    func updateNSView(_ nsView: WKWebView, context: Context) {}
}

private final class MacOSCookieNavigationDelegate: NSObject, WKNavigationDelegate {
    private let onNavigationResponse: () -> Void

    init(onNavigationResponse: @escaping () -> Void) {
        self.onNavigationResponse = onNavigationResponse
    }

    func webView(_ webView: WKWebView, decidePolicyFor navigationResponse: WKNavigationResponse) async -> WKNavigationResponsePolicy {
        onNavigationResponse()
        return .allow
    }
}

private final class MacOSWebLoginViewModel: ObservableObject {
    @Published var canShowWebView = false

    let delegate: MacOSCookieNavigationDelegate

    init(
        onCookie: @escaping (String) -> Void,
        url: String
    ) {
        self.delegate = MacOSCookieNavigationDelegate {
            WKWebsiteDataStore.default().httpCookieStore.getAllCookies { cookies in
                let cookieString = MacOSWebLoginViewModel.cookieHeaderString(from: cookies, for: URL(string: url))
                onCookie(cookieString)
            }
        }
        clearCookie()
    }

    var configuration: WKWebViewConfiguration {
        let configuration = WKWebViewConfiguration()
        configuration.defaultWebpagePreferences.allowsContentJavaScript = true
        return configuration
    }

    private func clearCookie() {
        let dataStore = WKWebsiteDataStore.default()
        dataStore.fetchDataRecords(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes()) { records in
            dataStore.removeData(
                ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(),
                for: records,
                completionHandler: {
                    self.canShowWebView = true
                }
            )
        }
    }

    private static func cookieHeaderString(from cookies: [HTTPCookie], for url: URL?) -> String {
        let host = url?.host?.lowercased()
        let filtered = cookies.filter { cookie in
            guard let host else {
                return true
            }
            let domain = cookie.domain.lowercased()
            return domain == host || (domain.hasPrefix(".") && (domain.hasSuffix(host) || host.hasSuffix(domain)))
        }
        return filtered.map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
    }
}

private extension URL {
    var isPixivOAuthUrl: Bool {
        scheme == "https" &&
            host == "app-api.pixiv.net" &&
            path == "/web/v1/login"
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
