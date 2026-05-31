import SwiftUI
@preconcurrency import KotlinSharedUI
import AuthenticationServices
import WebKit
import CoreImage.CIFilterBuiltins
import Combine

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
                }

                if methods.count > 1 {
                    Picker("", selection: Binding(
                        get: { selectedMethod },
                        set: { selectedMethods[key] = $0 }
                    )) {
                        ForEach(methods.indices, id: \.self) { index in
                            Text(methods[index].title.text).tag(methods[index].type)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                let handler = state.createLoginHandler(
                    platformType: node.platformType,
                    host: node.host,
                    methodType: selectedMethod
                )
                LoginFlowView(handler: handler)
                    .id("\(key)-\(selectedMethod)")

                LoginAgreementView(
                    urlString: state.agreementUrl(platformType: node.platformType, host: node.host)
                )
            }
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

    private func select(instance: UiInstance, state: ServiceSelectState) {
        instanceInput = instance.domain
        state.setFilter(value: instance.domain)
        selectedMethods.removeAll()
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

private struct LoginFlowView: View {
    @Environment(\.webAuthenticationSession) private var webAuthenticationSession

    @StateObject private var presenter: KotlinPresenter<LoginFlowPresenterState>
    @State private var qrContent: String?
    @State private var webCookieUrl: String?

    init(handler: LoginMethodHandler) {
        self._presenter = .init(wrappedValue: .init(presenter: LoginFlowPresenter(handler: handler)))
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
            }

            if let qrContent {
                QRLoginView(content: qrContent)
            }

            ForEach(presenter.state.flowState.actions, id: \.id) { action in
                Button {
                    presenter.state.perform(actionId: action.id)
                    if action.label == .cancel {
                        qrContent = nil
                    }
                } label: {
                    progressButtonLabel(title: action.label.text, isLoading: presenter.state.flowState.loading)
                }
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity)
                .disabled(!action.enabled || presenter.state.flowState.loading)
            }

            LoginErrorText(message: presenter.state.flowState.error)
        }
        .onReceive(
            presenter.state.effects
                .toPublisher()
                .catch { _ in Empty<LoginEffect, Never>() }
                .receive(on: DispatchQueue.main)
        ) { effect in
            switch onEnum(of: effect) {
            case .openUrl(let openUrl):
                authenticate(url: openUrl.url)
            case .showQr(let showQr):
                qrContent = showQr.content
            case .openWebCookieLogin(let webCookie):
                webCookieUrl = webCookie.url
            }
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
                        presenter.state.resume(value: cookie)
                        self.webCookieUrl = nil
                    }, url: webCookieUrl)
                } else {
                    BackportWebLoginScreen(onCookie: { cookie in
                        presenter.state.resume(value: cookie)
                        self.webCookieUrl = nil
                    }, url: webCookieUrl)
                }
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

    private func authenticate(url: String) {
        Task {
            guard let authURL = URL(string: url) else { return }
            let response = try? await webAuthenticationSession.authenticate(
                using: authURL,
                callbackURLScheme: APPSCHEMA
            )
            if let responseString = response?.absoluteString {
                presenter.state.resume(value: responseString)
            }
        }
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
            case .password:
                SecureField(field.label.text, text: $value)
                    .textContentType(.password)
            case .readOnlyText:
                Text(field.value)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            default:
                TextField(field.label.text, text: $value)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(field.type == .otp ? .numberPad : .default)
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
    static let emptyMessage = String(localized: "service_select_empty_message", defaultValue: "No instances found")
    static let nostrQRHint = String(localized: "nostr_login_qr_hint", defaultValue: "Scan with Amber, Alby, or another Nostr Connect signer.")
    static let nostrQRWaiting = String(localized: "nostr_login_qr_waiting", defaultValue: "Waiting for signer approval...")
    static let nostrQRLinkLabel = String(localized: "nostr_login_qr_link_label", defaultValue: "Nostr Connect link")
    static let eulaPrivacyPolicy = String(localized: "eula_privacy_policy", defaultValue: "EULA and Privacy Policy")
    static let loginAgreementPrefix = String(localized: "login_agreement_prefix", defaultValue: "By logging in, you agree to the ")

    static func compatibilityWarning(_ software: String) -> String {
        "This server uses \(software), Flare will run in compatibility mode. Some features may not work properly."
    }
}
