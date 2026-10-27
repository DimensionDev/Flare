import SwiftUI
import WebKit
import Observation
import FlareAppleUI
import FlareAppleCore

@available(iOS 26.0, *)
struct WebLoginScreen: View {
    @Environment(\.dismiss) var dismiss
    @State private var viewModel: WebLoginViewModel?
    private let onCookie: (String) -> Void
    let url: String
    init(
        onCookie: @escaping (String) -> Void,
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
                        WebView(viewModel.page)
                            .onAppear {
                                if route.isValid, let requestURL = URL(string: url) {
                                    viewModel.page.load(requestURL)
                                }
                            }
                            .task {
                                do { for try await _ in viewModel.page.navigations {} }
                                catch { route.report(error) }
                            }
                    } else {
                        ProgressView()
                    }
                }
                .task(id: route.key) {
                    guard viewModel?.route !== route else { return }
                    viewModel = WebLoginViewModel(onCookie: onCookie, url: url, route: route)
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

@available(iOS 26.0, *)
struct NavigationDecider: WebPage.NavigationDeciding {
    let onCookie: (String) -> Void
    let route: WebViewProxyRoute
    let url: URL?
    func decidePolicy(for action: WebPage.NavigationAction, preferences: inout WebPage.NavigationPreferences) async -> WKNavigationActionPolicy {
        route.isValid ? .allow : .cancel
    }
    func decidePolicy(for response: WebPage.NavigationResponse) async -> WKNavigationResponsePolicy {
        guard route.isValid else { return .cancel }
        getCookies()
        return .allow
    }
    func getCookies() {
        route.dataStore.httpCookieStore.getAllCookies { (cookies) in
            guard route.isValid else { return }
            let cookieString = cookieHeaderString(from: cookies, for: url)
            self.onCookie(cookieString)
        }
    }
    private func cookieHeaderString(from cookies: [HTTPCookie], for url: URL?) -> String {
        let host = url?.host?.lowercased()
        let filtered = cookies.filter { cookie in
            guard let host = host else { return true }
            let domain = cookie.domain.lowercased()
            return domain == host || (domain.hasPrefix(".") && (domain.hasSuffix(host) || host.hasSuffix(domain)))
        }
        return filtered.map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
    }
}

@available(iOS 26.0, *)
@Observable
final class WebLoginViewModel {
    let route: WebViewProxyRoute
    var page: WebPage
    let decider: NavigationDecider
    let onCookie: (String) -> Void
    init(
        onCookie: @escaping (String) -> Void,
        url: String,
        route: WebViewProxyRoute
    ) {
        self.route = route
        var conf = WebPage.Configuration()
        conf.defaultNavigationPreferences.allowsContentJavaScript = true
        conf.websiteDataStore = route.dataStore
        self.decider = .init(onCookie: onCookie, route: route, url: .init(string: url))
        self.page = WebPage(configuration: conf, navigationDecider: decider)
        self.onCookie = onCookie
        self.page.customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.0 Mobile/15E148 Safari/604.1"
        route.onStop { [weak page] in
            page?.stopLoading()
            page?.load(html: "")
        }
    }
}
