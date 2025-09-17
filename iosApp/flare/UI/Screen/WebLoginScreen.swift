import SwiftUI
import KotlinSharedUI
import WebKit

struct WebLoginScreen: View {
    @Environment(\.dismiss) var dismiss
    @State private var viewModel: WebLoginViewModel
    let url: String
    init(onCookie: @escaping (String) -> Void, url: String) {
        self._viewModel = .init(initialValue: .init(onCookie: onCookie))
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
    func decidePolicy(for response: WebPage.NavigationResponse) async -> WKNavigationResponsePolicy {
        getCookies()
        return .allow
    }
    func getCookies() {
        config.websiteDataStore.httpCookieStore.getAllCookies { (cookies) in
            var cookieString = ""
            for cookie in cookies {
                cookieString += "\(cookie.name)=\(cookie.value); "
            }
            self.onCookie(cookieString)
        }
    }
}

@Observable
class WebLoginViewModel {
    var config: WebPage.Configuration
    var page: WebPage
    let decider: NavigationDecider
    let onCookie: (String) -> Void
    init(
        onCookie: @escaping (String) -> Void
    ) {
        var conf = WebPage.Configuration()
        conf.defaultNavigationPreferences.allowsContentJavaScript = true
        self.config = conf
        self.decider = .init(onCookie: onCookie, config: self._config)
        self.page = WebPage(configuration: self._config, navigationDecider: decider)
        self.onCookie = onCookie
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
