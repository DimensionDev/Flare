import SwiftUI
import KotlinSharedUI
import WebKit
import Combine

@available(iOS 26.0, *)
struct WebLoginScreen: View {
    @Environment(\.dismiss) var dismiss
    @StateObject private var viewModel: WebLoginViewModel
    let url: String
    init(
        onCookie: @escaping (String) -> Void,
        url: String,
        initialCookies: [WebCookieSeed] = []
    ) {
        self._viewModel = .init(wrappedValue: .init(onCookie: onCookie, url: url, initialCookies: initialCookies))
        self.url = url
    }
    var body: some View {
        NavigationStack {
            if viewModel.canShowWebView {
                WebView(viewModel.page)
                    .onAppear {
                        if let requestURL = URL(string: url) {
                            viewModel.page.load(requestURL)
                        }
                    }
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button {
                                dismiss()
                            } label: {
                                Label {
                                    Text("Cancel")
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

@available(iOS 26.0, *)
struct NavigationDecider: WebPage.NavigationDeciding {
    let onCookie: (String) -> Void
    let config: WebPage.Configuration
    let url: URL?
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

@available(iOS 26.0, *)
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
        initialCookies: [WebCookieSeed],
    ) {
        var conf = WebPage.Configuration()
        conf.defaultNavigationPreferences.allowsContentJavaScript = true
        self.config = conf
        self.decider = .init(onCookie: onCookie, config: conf, url: .init(string: url))
        self.page = WebPage(configuration: conf, navigationDecider: decider)
        self.onCookie = onCookie
        self.page.customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.0 Mobile/15E148 Safari/604.1"
        clearCookie(initialCookies: initialCookies)
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
    
    func clearCookie(initialCookies: [WebCookieSeed]) {
        let dataStore = WKWebsiteDataStore.default()
        dataStore.fetchDataRecords(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes()) { records in
            dataStore.removeData(
                ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(),
                for: records,
                completionHandler: {
                    dataStore.httpCookieStore.setCookies(initialCookies) {
                        self.canShowWebView = true
                    }
                }
            )
        }
    }
}

private extension WKHTTPCookieStore {
    func setCookies(_ seeds: [WebCookieSeed], completion: @escaping () -> Void) {
        guard !seeds.isEmpty else {
            completion()
            return
        }
        let group = DispatchGroup()
        for seed in seeds {
            guard let cookie = HTTPCookie(seed: seed) else {
                continue
            }
            group.enter()
            setCookie(cookie) {
                group.leave()
            }
        }
        group.notify(queue: .main, execute: completion)
    }
}

private extension HTTPCookie {
    convenience init?(seed: WebCookieSeed) {
        var properties: [HTTPCookiePropertyKey: Any] = [
            .name: seed.name,
            .value: seed.value,
            .domain: seed.domain,
            .path: seed.path,
        ]
        if seed.secure {
            properties[.secure] = "TRUE"
        }
        self.init(properties: properties)
    }
}
