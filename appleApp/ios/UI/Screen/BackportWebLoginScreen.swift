import SwiftUI
import WebKit
import Combine

struct BackportWebLoginScreen: View {
    @Environment(\.dismiss) var dismiss
    @StateObject private var viewModel: BackportWebLoginViewModel
    let url: String
    init(
        onCookie: @escaping ([HTTPCookie]) -> Void,
        url: String
    ) {
        self._viewModel = .init(wrappedValue: .init(onCookie: onCookie, url: url))
        self.url = url
    }
    
    var body: some View {
        NavigationStack {
            if viewModel.canShowWebView {
                BackportWebView(url: URL(string: url), configuration: viewModel.configuration) { webView in
                    webView.customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.0 Mobile/15E148 Safari/604.1"
                    webView.navigationDelegate = viewModel.delegate
                }
                .toolbar {
                    ToolbarItem(placement: .principal) {
                        WebLoginOriginLabel(
                            current: viewModel.currentOrigin,
                            initial: webLoginHTTPSOrigin(URL(string: url))
                        )
                    }
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
                .task {
                    await viewModel.pollCookies()
                }
            }
        }
        .onDisappear {
            viewModel.clearCookie()
        }
    }
}

class WKDelegate: NSObject, WKNavigationDelegate {
    let decidePolicy: () -> Void
    var onOriginChange: (String) -> Void = { _ in }

    init(decidePolicy: @escaping () -> Void
    ) {
        self.decidePolicy = decidePolicy
    }

    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction) async -> WKNavigationActionPolicy {
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
        decidePolicy()
        return webLoginHTTPSOrigin(navigationResponse.response.url) == nil ? .cancel : .allow
    }
}

class BackportWebLoginViewModel: ObservableObject {
    @Published
    var canShowWebView = false
    let url: String
    let onCookie: ([HTTPCookie]) -> Void
    @Published var currentOrigin: String
    lazy var delegate: WKDelegate = {
        let delegate = WKDelegate { [weak self] in
            guard let self else { return }
            WKWebsiteDataStore.default().httpCookieStore.getAllCookies { cookies in
                self.onCookie(cookies)
            }
        }
        delegate.onOriginChange = { [weak self] origin in
            self?.currentOrigin = origin
        }
        return delegate
    }()
    private var observers = [NSKeyValueObservation]()
    init(
        onCookie: @escaping ([HTTPCookie]) -> Void,
        url: String
    ) {
        self.onCookie = onCookie
        self.url = url
        self.currentOrigin = webLoginHTTPSOrigin(URL(string: url)) ?? ""
        clearCookie()
    }
    var configuration: WKWebViewConfiguration {
        let configuration = WKWebViewConfiguration()
        configuration.defaultWebpagePreferences.allowsContentJavaScript = true
        return configuration
    }
    func clearCookie() {
        clearWebLoginDataStore { [weak self] in
            Task { @MainActor [weak self] in
                self?.canShowWebView = true
            }
        }
    }

    func getCookies() {
        WKWebsiteDataStore.default().httpCookieStore.getAllCookies { cookies in
            self.onCookie(cookies)
        }
    }

    func pollCookies() async {
        while !Task.isCancelled {
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            getCookies()
        }
    }
    deinit {
        observers.removeAll()
    }
}
