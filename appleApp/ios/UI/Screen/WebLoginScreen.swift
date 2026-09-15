import SwiftUI
import WebKit
import Observation
import FlareAppleUI

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
            if let viewModel, viewModel.canShowWebView {
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
        .task {
            // Initialize after state is installed so view rebuilds don't clear cookies again.
            guard viewModel == nil else { return }
            viewModel = WebLoginViewModel(onCookie: onCookie, url: url)
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
@Observable
final class WebLoginViewModel {
    var config: WebPage.Configuration
    var page: WebPage
    let decider: NavigationDecider
    let onCookie: (String) -> Void
    init(
        onCookie: @escaping (String) -> Void,
        url: String
    ) {
        var conf = WebPage.Configuration()
        conf.defaultNavigationPreferences.allowsContentJavaScript = true
        self.config = conf
        self.decider = .init(onCookie: onCookie, config: conf, url: .init(string: url))
        self.page = WebPage(configuration: conf, navigationDecider: decider)
        self.onCookie = onCookie
        self.page.customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.0 Mobile/15E148 Safari/604.1"
        clearCookie()
    }
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
