import SwiftUI
import AuthenticationServices

struct AuthSessionView: UIViewControllerRepresentable {
    var callback: (URL) -> Void
    
    let authURL: String
    
    let callbackURLScheme: String
    
    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }
    
    func makeUIViewController(context: Context) -> UIViewController {
        let viewController = UIViewController()
        guard let url = URL(string: authURL) else {
            return viewController
        }
        
        let session = ASWebAuthenticationSession(url: url, callbackURLScheme: callbackURLScheme) { callbackURL, error in
            if let callbackURL {
                callback(callbackURL)
            } else if let error {
                fatalError(error.localizedDescription)
            }
        }
        
        session.prefersEphemeralWebBrowserSession = true
        
        session.presentationContextProvider = context.coordinator
        
        session.start()
        
        return viewController
    }
    
    func updateUIViewController(_: UIViewController, context _: Context) {}
}

class Coordinator: NSObject, ASWebAuthenticationPresentationContextProviding {
    var parent: AuthSessionView
    
    init(parent: AuthSessionView) {
        self.parent = parent
    }
    
    func presentationAnchor(for _: ASWebAuthenticationSession) -> ASPresentationAnchor {
        let scenes = UIApplication.shared.connectedScenes
        let windowScene = scenes.first as? UIWindowScene
        guard let window = windowScene?.windows.first else {
            fatalError("No windows in the application")
        }
        return window
    }
}
