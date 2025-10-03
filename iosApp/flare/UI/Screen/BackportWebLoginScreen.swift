import SwiftUI
import WebKit
import Combine

struct BackportWebLoginScreen: View {
    @Environment(\.dismiss) var dismiss
    @StateObject private var viewModel: BackportWebLoginViewModel
    let url: String
    init(onCookie: @escaping (String) -> Void, url: String) {
        self._viewModel = .init(wrappedValue: .init(onCookie: onCookie))
        self.url = url
    }
    
    var body: some View {
        NavigationStack {
            if viewModel.canShowWebView {
                BackportWebView(url: URL(string: url), configuration: viewModel.configuration) { webView in
                    webView.customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.0 Mobile/15E148 Safari/604.1"
                    viewModel.observe(webView: webView)
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

class BackportWebLoginViewModel: ObservableObject {
    @Published
    var canShowWebView = false
    let onCookie: (String) -> Void
    private var observers = [NSKeyValueObservation]()
    init(onCookie: @escaping (String) -> Void) {
        self.onCookie = onCookie
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
    func observe(webView: WKWebView) {
        observers.append(webView.observe(\.url, options: .new) { _, _ in
            self.getCookies(webView: webView)
        })
    }
    func getCookies(webView: WKWebView) {
        webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { (cookies) in
            var cookieString = ""
            for cookie in cookies {
                cookieString += "\(cookie.name)=\(cookie.value); "
            }
            self.onCookie(cookieString)
        }
    }
    deinit {
        observers.removeAll()
    }
}
