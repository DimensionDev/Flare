import SwiftUI
import KotlinSharedUI
import WebKit
import Combine

struct WebLoginScreen: View {
    @Environment(\.dismiss) var dismiss
    @StateObject private var viewModel: WebLoginViewModel
    let url: String
    init(onCookie: @escaping (String) -> Void, url: String) {
        self._viewModel = .init(wrappedValue: .init(onCookie: onCookie, url: url))
        self.url = url
    }
    var body: some View {
        if viewModel.canShowWebView {
            WebView(viewModel.page)
                .onAppear {
                    viewModel.page.load(.init(string: url)!)
                }
        }
    }
}

struct NavigationDecider: WebPage.NavigationDeciding {
    let onCookie: (String) -> Void
    let config: WebPage.Configuration
    let url: URL
    func decidePolicy(for response: WebPage.NavigationResponse) async -> WKNavigationResponsePolicy {
        getCookies()
        return .allow
    }
    func getCookies() {
        WKWebsiteDataStore.default().httpCookieStore.getAllCookies { (cookies) in
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

class WebLoginViewModel: ObservableObject {
    @Published
    var config: WebPage.Configuration
    @Published
    var page: WebPage
    let decider: NavigationDecider
    let onCookie: (String) -> Void
    init(
        onCookie: @escaping (String) -> Void,
        url: String,
    ) {
        var conf = WebPage.Configuration()
        conf.defaultNavigationPreferences.allowsContentJavaScript = true
        self.config = conf
        self.decider = .init(onCookie: onCookie, config: conf, url: .init(string: url)!)
        self.page = WebPage(configuration: conf, navigationDecider: decider)
        self.onCookie = onCookie
        self.page.customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.0 Mobile/15E148 Safari/604.1"
        clearCookie()
    }
    @Published
    var canShowWebView = false
    func getCookies() {
        config.websiteDataStore.httpCookieStore.getAllCookies { (cookies) in
            var cookieString = ""
            for cookie in cookies {
                cookieString += "\(cookie.name)=\(cookie.value); "
            }
            self.onCookie(cookieString)
        }
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
}
