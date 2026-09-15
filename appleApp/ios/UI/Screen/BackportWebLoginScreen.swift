import SwiftUI
import WebKit
import Observation
import FlareAppleUI

struct BackportWebLoginScreen: View {
    @Environment(\.dismiss) var dismiss
    @State private var viewModel: BackportWebLoginViewModel?
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
                BackportWebView(url: URL(string: url), configuration: viewModel.configuration) { webView in
                    webView.customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.0 Mobile/15E148 Safari/604.1"
                    webView.navigationDelegate = viewModel.delegate
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
            viewModel = BackportWebLoginViewModel(onCookie: onCookie, url: url)
        }
    }
}

class WKDelegate: NSObject, WKNavigationDelegate {
    let decidePolicy: () -> Void
    init(decidePolicy: @escaping () -> Void
    ) {
        self.decidePolicy = decidePolicy
    }
    
    func webView(_ webView: WKWebView, decidePolicyFor navigationResponse: WKNavigationResponse) async -> WKNavigationResponsePolicy {
        decidePolicy()
        return .allow
    }
}

@Observable
final class BackportWebLoginViewModel {
    var canShowWebView = false
    let url: String
    let onCookie: (String) -> Void
    let delegate: WKDelegate
    @ObservationIgnored private var observers = [NSKeyValueObservation]()
    init(
        onCookie: @escaping (String) -> Void,
        url: String
    ) {
        self.onCookie = onCookie
        self.url = url
        self.delegate = WKDelegate {
            WKWebsiteDataStore.default().httpCookieStore.getAllCookies { (cookies) in
                let cookieString = BackportWebLoginViewModel.cookieHeaderString(from: cookies, for: .init(string: url))
                onCookie(cookieString)
            }
        }
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
    private static func cookieHeaderString(from cookies: [HTTPCookie], for url: URL?) -> String {
        let host = url?.host?.lowercased()
        let filtered = cookies.filter { cookie in
            guard let host = host else { return true }
            let domain = cookie.domain.lowercased()
            return domain == host || (domain.hasPrefix(".") && (domain.hasSuffix(host) || host.hasSuffix(domain)))
        }
        return filtered.map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
    }
    deinit {
        observers.removeAll()
    }
}
