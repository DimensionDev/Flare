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
                        .task { await viewModel.pollCookies() }
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
                ToolbarItem(placement: .principal) {
                    WebLoginOriginLabel(current: viewModel?.currentOrigin ?? webLoginHTTPSOrigin(URL(string: url)) ?? "", initial: webLoginHTTPSOrigin(URL(string: url)))
                }
                ToolbarItem(placement: .cancellationAction) {
                    Button { dismiss() } label: { Label("Cancel", systemImage: "xmark") }
                }
            }
        }
        .onDisappear { viewModel?.clearCookie() }
    }
}

class WKDelegate: NSObject, WKNavigationDelegate {
    let route: WebViewProxyRoute
    let decidePolicy: () -> Void
    var onOriginChange: (String) -> Void = { _ in }

    init(route: WebViewProxyRoute, decidePolicy: @escaping () -> Void
    ) {
        self.route = route
        self.decidePolicy = decidePolicy
    }

    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction) async -> WKNavigationActionPolicy {
        guard route.isValid else { return .cancel }
        guard navigationAction.targetFrame?.isMainFrame != false else {
            return .allow
        }
        guard let origin = webLoginHTTPSOrigin(navigationAction.request.url) else {
            return .cancel
        }
        onOriginChange(origin)
        return .allow
    }
    
    func webView(_ webView: WKWebView, decidePolicyFor navigationResponse: WKNavigationResponse) async -> WKNavigationResponsePolicy {
        guard route.isValid else { return .cancel }
        decidePolicy()
        return webLoginHTTPSOrigin(navigationResponse.response.url) == nil ? .cancel : .allow
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
    var currentOrigin: String
    @ObservationIgnored lazy var delegate: WKDelegate = {
        let delegate = WKDelegate(route: route) { [weak self] in
            guard let self else { return }
            route.dataStore.httpCookieStore.getAllCookies { cookies in
                guard self.route.isValid else { return }
                guard self.route.isValid else { return }
            self.onCookie(cookies)
            }
        }
        delegate.onOriginChange = { [weak self] origin in
            self?.currentOrigin = origin
        }
        return delegate
    }()
    init(
        onCookie: @escaping ([HTTPCookie]) -> Void,
        url: String,
        route: WebViewProxyRoute
    ) {
        self.route = route
        self.onCookie = onCookie
        self.url = url
        self.currentOrigin = webLoginHTTPSOrigin(URL(string: url)) ?? ""
    }
    var configuration: WKWebViewConfiguration {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = route.dataStore
        configuration.defaultWebpagePreferences.allowsContentJavaScript = true
        return configuration
    }
    func clearCookie() {
        clearWebLoginDataStore(dataStore: route.dataStore)
    }

    func getCookies() {
        route.dataStore.httpCookieStore.getAllCookies { cookies in
            guard self.route.isValid else { return }
            self.onCookie(cookies)
        }
    }

    func pollCookies() async {
        while !Task.isCancelled && route.isValid {
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled && route.isValid else { return }
            getCookies()
        }
    }
}
