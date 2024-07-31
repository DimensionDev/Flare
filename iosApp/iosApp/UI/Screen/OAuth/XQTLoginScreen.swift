import SwiftUI
import WebKit
import shared

struct XQTLoginScreen: View {
    @State var viewModel: XQTViewModel
    let url = "https://" + UiApplicationXQT.shared.host
    init(toHome: @escaping () -> Void) {
        viewModel = .init(toHome: toHome)
        viewModel.clearCookie()
    }
    var body: some View {
        Observing(viewModel.presenter.models) { _ in
            ZStack {
                if viewModel.canShowWebView {
                    WebView(url: URL(string: url), configuration: viewModel.configuration) { webView in
                        webView.customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.0 Mobile/15E148 Safari/604.1"
                        viewModel.observe(webView: webView)
                    }
                }
            }
        }
    }
}

@Observable
class XQTViewModel {
    typealias Presenter = XQTLoginPresenter
    let presenter: XQTLoginPresenter
    var canShowWebView = false
    private var observers = [NSKeyValueObservation]()
    init(toHome: @escaping () -> Void) {
        presenter = .init(toHome: toHome)
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
                for: records.filter { $0.displayName.contains(xqtHost) },
                completionHandler: {
                    self.canShowWebView = true
                }
            )
        }
    }
    func observe(webView: WKWebView) {
        observers.append(webView.observe(\.url, options: .new) { _, _ in
            self.getCookies(webView: webView)
        })
    }
    func getCookies(webView: WKWebView) {
        webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { (cookies) in
            var cookieString = ""
            for cookie in cookies {
                if cookie.domain.contains(xqtHost) {
                    cookieString += "\(cookie.name)=\(cookie.value); "
                }
            }
            if self.presenter.models.value.checkChocolate(cookie: cookieString), !self.presenter.models.value.loading {
                self.presenter.models.value.login(chocolate: cookieString)
            }
        }
    }
    deinit {
        observers.removeAll()
    }
}
