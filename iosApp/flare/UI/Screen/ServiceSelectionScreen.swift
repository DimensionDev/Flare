import SwiftUI
import KotlinSharedUI
import Kingfisher
import AuthenticationServices
import WebKit

struct ServiceSelectionScreen : View {
    @Environment(\.webAuthenticationSession) private var webAuthenticationSession
    @Environment(\.openURL) private var openURL
    let toHome: () -> Void
    let key = UUID().uuidString
    @State private var showVVOLoginSheet = false
    @State private var showXQTLoginSheet = false
    @State private var vvoLoginPresenter: KotlinPresenter<VVOLoginState>
    @State private var xqtLoginPresenter: KotlinPresenter<XQTLoginState>
    
    init(toHome: @escaping () -> Void) {
        self.toHome = toHome
        self._vvoLoginPresenter = .init(initialValue: .init(presenter: VVOLoginPresenter(toHome: toHome)))
        self._xqtLoginPresenter = .init(initialValue: .init(presenter: XQTLoginPresenter(toHome: toHome)))
    }
    
    var body: some View {
        ServiceSelectionView(key: key, data: .init(onXQT: {
            showXQTLoginSheet = true
        }, onVVO: {
            showVVOLoginSheet = true
        }, onBack: {
            toHome()
        }, openUri: { url, callback in
            Task {
                let response = try? await webAuthenticationSession.authenticate(using: .init(string: url)!, callbackURLScheme: APPSCHEMA)
                if let responseString = response?.absoluteString {
                    _ = callback(responseString)
                }
            }
        })) { url in
            openURL.callAsFunction(.init(string: url)!)
        }
        .ignoresSafeArea()
        .background(Color(.systemGroupedBackground))
        .sheet(isPresented: $showVVOLoginSheet, onDismiss: {
            showVVOLoginSheet = false
        }, content: {
            WebLoginScreen(onCookie: { cookie in
                if vvoLoginPresenter.state.checkChocolate(cookie: cookie) {
                    vvoLoginPresenter.state.login(chocolate: cookie)
                }
            }, url: UiApplicationVVo.shared.loginUrl)
        })
        .sheet(isPresented: $showXQTLoginSheet) {
            showXQTLoginSheet = false
        } content: {
            WebLoginScreen(onCookie: { cookie in
                if xqtLoginPresenter.state.checkChocolate(cookie: cookie) {
                    xqtLoginPresenter.state.login(chocolate: cookie)
                }
            }, url: "https://" + UiApplicationXQT.shared.host)
        }

    }
}

struct ServiceSelectionView: UIViewControllerRepresentable {
    let key: String
    let state: ComposeUIStateProxy<ServiceSelectControllerState>
    let data: ServiceSelectControllerState
    init(key: String, data: ServiceSelectControllerState, onOpenLink: @escaping (String) -> Void) {
        self.key = key
        self.data = data
        if let state = ComposeUIStateProxyCache.shared.getOrCreate(key: key, factory: {
            ComposeUIStateProxy(initialState: data, onOpenLink: onOpenLink)
        }) as? ComposeUIStateProxy<ServiceSelectControllerState> {
            self.state = state
        } else {
            self.state = ComposeUIStateProxy(initialState: data, onOpenLink: onOpenLink)
        }
    }
    func makeUIViewController(context: Context) -> some UIViewController {
        return KotlinSharedUI.ServiceSelectController(state: state)
    }
    
    func updateUIViewController(_ uiViewController: UIViewControllerType, context: Context) {
        state.update(newState: data)
    }
    func dismantleUIViewController(
        _ uiViewController: Self.UIViewControllerType,
        coordinator: Self.Coordinator
    ) {
        ComposeUIStateProxyCache.shared.remove(key: key)
    }
}
