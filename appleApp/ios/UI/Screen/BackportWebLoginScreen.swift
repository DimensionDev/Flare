import SwiftUI
import WebKit
import Observation
import FlareAppleUI
import FlareAppleCore

struct BackportWebLoginScreen: View {
    @Environment(\.dismiss) var dismiss
    @State private var viewModel: BackportWebLoginViewModel?
    private let onCookie: ([HTTPCookie]) -> Void
    let url: String
    init(
        onCookie: @escaping ([HTTPCookie]) -> Void,
        url: String
    ) {
        self.url = url
        self.onCookie = onCookie
    }
    
    var body: some View {
        NavigationStack {
            ProxyWebContent { route in
                Group {
                    if let viewModel, viewModel.route === route {
                        BackportWebView(url: URL(string: url), configuration: viewModel.configuration) { webView in
                            webView.customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.0 Mobile/15E148 Safari/604.1"
                            webView.navigationDelegate = viewModel.delegate
                            route.bind(webView)
                        }
                    } else {
                        ProgressView()
                    }
                }
                .task(id: route.key) {
                    guard viewModel?.route !== route else { return }
                    viewModel = BackportWebLoginViewModel(onCookie: onCookie, url: url, route: route)
                }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button { dismiss() } label: { Label("Cancel", systemImage: "xmark") }
                }
            }
        }
    }
}

class WKDelegate: NSObject, WKNavigationDelegate {
    let decidePolicy: () -> Void
    let route: WebViewProxyRoute
    init(route: WebViewProxyRoute, decidePolicy: @escaping () -> Void
    ) {
        self.route = route
        self.decidePolicy = decidePolicy
    }
    
    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction) async -> WKNavigationActionPolicy {
        route.isValid ? .allow : .cancel
    }

    func webView(_ webView: WKWebView, decidePolicyFor navigationResponse: WKNavigationResponse) async -> WKNavigationResponsePolicy {
        guard route.isValid else { return .cancel }
        decidePolicy()
        return .allow
    }
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        route.report(error)
    }
    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        route.report(error)
    }

}

@Observable
final class BackportWebLoginViewModel {
    let route: WebViewProxyRoute
    let url: String
    let onCookie: ([HTTPCookie]) -> Void
    let delegate: WKDelegate
    init(
        onCookie: @escaping ([HTTPCookie]) -> Void,
        url: String,
        route: WebViewProxyRoute
    ) {
        self.route = route
        self.onCookie = onCookie
        self.url = url
        self.delegate = WKDelegate(route: route) {
            route.dataStore.httpCookieStore.getAllCookies { (cookies) in
                guard route.isValid else { return }
                onCookie(cookies)
            }
        }
    }
    var configuration: WKWebViewConfiguration {
        let configuration = WKWebViewConfiguration()
        configuration.defaultWebpagePreferences.allowsContentJavaScript = true
        configuration.websiteDataStore = route.dataStore
        return configuration
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
}
