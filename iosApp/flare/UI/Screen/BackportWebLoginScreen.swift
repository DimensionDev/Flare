import SwiftUI
import WebKit
import Combine

struct BackportWebLoginScreen: View {
    @Environment(\.dismiss) var dismiss
    @StateObject private var viewModel: BackportWebLoginViewModel
    let url: String
    init(onCookie: @escaping (String) -> Void, url: String) {
        self._viewModel = .init(wrappedValue: .init(onCookie: onCookie, url: url))
        self.url = url
    }
    
    var body: some View {
        NavigationStack {
            if viewModel.canShowWebView {
                BackportWebView(url: URL(string: url), configuration: viewModel.configuration) { webView in
                    webView.customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.0 Mobile/15E148 Safari/604.1"
                    webView.navigationDelegate = viewModel.delegate
                }
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button {
                            dismiss()
                        } label: {
                            Label {
                                Text("compose_button_cancel")
                            } icon: {
                                Image(systemName: "xmark")
                            }
                        }
                    }
                }
            }
        }
    }
}

class WKDelegate: NSObject, WKNavigationDelegate {
    let decidePolicy: () -> Void
    init(decidePolicy: @escaping () -> Void
    ) {
        self.decidePolicy = decidePolicy
    }
    
    func webView(_ webView: WKWebView, decidePolicyFor navigationResponse: WKNavigationResponse) async -> WKNavigationResponsePolicy {
        decidePolicy()
        return .allow
    }
}

class BackportWebLoginViewModel: ObservableObject {
    @Published
    var canShowWebView = false
    let url: String
    let onCookie: (String) -> Void
    let delegate: WKDelegate
    private var observers = [NSKeyValueObservation]()
    init(onCookie: @escaping (String) -> Void, url: String) {
        self.onCookie = onCookie
        self.url = url
        self.delegate = WKDelegate {
            WKWebsiteDataStore.default().httpCookieStore.getAllCookies { (cookies) in
                let cookieString = BackportWebLoginViewModel.cookieHeaderString(from: cookies, for: .init(string: url))
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
    func clearCookie() {
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
            guard let host = host else { return true }
            let domain = cookie.domain.lowercased()
            return domain == host || (domain.hasPrefix(".") && (domain.hasSuffix(host) || host.hasSuffix(domain)))
        }
        return filtered.map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
    }
    deinit {
        observers.removeAll()
    }
}
