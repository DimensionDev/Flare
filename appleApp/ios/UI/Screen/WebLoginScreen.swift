import SwiftUI
import WebKit
import Observation
import FlareAppleUI
import FlareAppleCore

@available(iOS 26.0, *)
struct WebLoginScreen: View {
    @Environment(\.dismiss) var dismiss
    @State private var viewModel: WebLoginViewModel?
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
                        WebView(viewModel.page)
                            .onAppear {
                                if route.isValid, let requestURL = URL(string: url) {
                                    viewModel.page.load(requestURL)
                                }
                            }
                            .task { await viewModel.pollCookies() }
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
                ToolbarItem(placement: .principal) {
                    WebLoginOriginLabel(current: webLoginHTTPSOrigin(viewModel?.page.url) ?? webLoginHTTPSOrigin(URL(string: url)) ?? "", initial: webLoginHTTPSOrigin(URL(string: url)))
                }
                ToolbarItem(placement: .cancellationAction) {
                    Button { dismiss() } label: { Label("Cancel", systemImage: "xmark") }
                }
            }
        }
        .onDisappear { viewModel?.clearCookie() }
    }
}

@available(iOS 26.0, *)
struct NavigationDecider: WebPage.NavigationDeciding {
    let onCookie: ([HTTPCookie]) -> Void
    let route: WebViewProxyRoute
    let url: URL?
    func decidePolicy(for action: WebPage.NavigationAction, preferences: inout WebPage.NavigationPreferences) async -> WKNavigationActionPolicy {
        guard route.isValid else { return .cancel }
        guard action.target?.isMainFrame != false else { return .allow }
        return webLoginHTTPSOrigin(action.request.url) == nil ? .cancel : .allow
    }
    func decidePolicy(for response: WebPage.NavigationResponse) async -> WKNavigationResponsePolicy {
        guard route.isValid else { return .cancel }
        getCookies()
        return webLoginHTTPSOrigin(response.response.url) == nil ? .cancel : .allow
    }
    func getCookies() {
        route.dataStore.httpCookieStore.getAllCookies { (cookies) in
            guard route.isValid else { return }
            self.onCookie(cookies)
        }
    }
}

@available(iOS 26.0, *)
@Observable
final class WebLoginViewModel {
    let route: WebViewProxyRoute
    var page: WebPage
    let decider: NavigationDecider
    let onCookie: ([HTTPCookie]) -> Void
    init(
        onCookie: @escaping ([HTTPCookie]) -> Void,
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
    func pollCookies() async {
        while !Task.isCancelled && route.isValid {
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled && route.isValid else { return }
            decider.getCookies()
        }
    }

    func clearCookie() {
        clearWebLoginDataStore(dataStore: route.dataStore)
    }
}

struct WebLoginOriginLabel: View {
    let current: String
    let initial: String?

    var body: some View {
        Text(current)
            .lineLimit(1)
            .foregroundStyle(current.isEmpty || current == initial ? Color.secondary : Color.red)
    }
}

func webLoginHTTPSOrigin(_ url: URL?) -> String? {
    guard let url, url.scheme?.lowercased() == "https", let host = url.host, !host.isEmpty, url.user == nil, url.password == nil else {
        return nil
    }
    if let port = url.port, port != 443 {
        return "https://\(host):\(port)"
    }
    return "https://\(host)"
}

func clearWebLoginDataStore(dataStore: WKWebsiteDataStore, completion: @escaping @Sendable () -> Void = {}) {
    dataStore.fetchDataRecords(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes()) { records in
        dataStore.removeData(
            ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(),
            for: records,
            completionHandler: completion
        )
    }
}
