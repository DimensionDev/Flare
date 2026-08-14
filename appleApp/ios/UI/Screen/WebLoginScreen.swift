import SwiftUI
import WebKit
import Combine

@available(iOS 26.0, *)
struct WebLoginScreen: View {
    @Environment(\.dismiss) var dismiss
    @StateObject private var viewModel: WebLoginViewModel
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
                WebView(viewModel.page)
                    .onAppear {
                        if let requestURL = URL(string: url) {
                            viewModel.page.load(requestURL)
                        }
                    }
                    .toolbar {
                        ToolbarItem(placement: .principal) {
                            WebLoginOriginLabel(
                                current: webLoginHTTPSOrigin(viewModel.page.url) ?? webLoginHTTPSOrigin(URL(string: url)) ?? "",
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

@available(iOS 26.0, *)
struct NavigationDecider: WebPage.NavigationDeciding {
    let onCookie: ([HTTPCookie]) -> Void
    let config: WebPage.Configuration
    let url: URL?
    func decidePolicy(
        for action: WebPage.NavigationAction,
        preferences: inout WebPage.NavigationPreferences
    ) async -> WKNavigationActionPolicy {
        guard action.target?.isMainFrame != false else {
            return .allow
        }
        return webLoginHTTPSOrigin(action.request.url) == nil ? .cancel : .allow
    }

    func decidePolicy(for response: WebPage.NavigationResponse) async -> WKNavigationResponsePolicy {
        getCookies()
        return webLoginHTTPSOrigin(response.response.url) == nil ? .cancel : .allow
    }
    func getCookies() {
        WKWebsiteDataStore.default().httpCookieStore.getAllCookies { (cookies) in
            self.onCookie(cookies)
        }
    }

}

@available(iOS 26.0, *)
class WebLoginViewModel: ObservableObject {
    @Published
    var config: WebPage.Configuration
    @Published
    var page: WebPage
    let decider: NavigationDecider
    let onCookie: ([HTTPCookie]) -> Void
    init(
        onCookie: @escaping ([HTTPCookie]) -> Void,
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
    @Published
    var canShowWebView = false
    func getCookies() {
        config.websiteDataStore.httpCookieStore.getAllCookies { (cookies) in
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
    
    func clearCookie() {
        clearWebLoginDataStore { [weak self] in
            Task { @MainActor [weak self] in
                self?.canShowWebView = true
            }
        }
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

func clearWebLoginDataStore(completion: @escaping @Sendable () -> Void = {}) {
    let dataStore = WKWebsiteDataStore.default()
    dataStore.fetchDataRecords(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes()) { records in
        dataStore.removeData(
            ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(),
            for: records,
            completionHandler: completion
        )
    }
}
