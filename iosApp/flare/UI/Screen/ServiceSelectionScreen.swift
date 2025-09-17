import SwiftUI
import KotlinSharedUI
import Kingfisher
import AuthenticationServices

struct ServiceSelectionScreen: View {
    @Environment(\.webAuthenticationSession) private var webAuthenticationSession
    @Environment(\.openURL) private var openURL
    let toHome: () -> Void
    let key = UUID().uuidString

    var body: some View {
        ServiceSelectionView(key: key, data: .init(onXQT: {

        }, onVVO: {

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
        .background(Color(.systemGroupedBackground))
        .ignoresSafeArea()
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
