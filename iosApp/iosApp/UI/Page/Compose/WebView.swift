#if os(iOS)
    typealias WebViewRepresentable = UIViewRepresentable
#elseif os(macOS)
    typealias WebViewRepresentable = NSViewRepresentable
#endif

#if os(iOS) || os(macOS)
    import SwiftUI
    import WebKit

    public struct WebView: WebViewRepresentable {
        // - Initializers

        public init(
            url: URL? = nil,
            configuration: WKWebViewConfiguration? = nil,
            webView: @escaping (WKWebView) -> Void = { _ in }
        ) {
            self.url = url
            self.configuration = configuration
            self.webView = webView
        }

        // - Properties

        private let url: URL?
        private let configuration: WKWebViewConfiguration?
        private let webView: (WKWebView) -> Void

        // - Functions

        #if os(iOS)
            public func makeUIView(context _: Context) -> WKWebView {
                makeView()
            }

            public func updateUIView(_: WKWebView, context _: Context) {}
        #endif

        #if os(macOS)
            public func makeNSView(context _: Context) -> WKWebView {
                makeView()
            }

            public func updateNSView(_: WKWebView, context _: Context) {}
        #endif
    }

    private extension WebView {
        func makeWebView() -> WKWebView {
            guard let configuration else { return WKWebView() }
            return WKWebView(frame: .zero, configuration: configuration)
        }

        func makeView() -> WKWebView {
            let view = makeWebView()
            webView(view)
            tryLoad(url, into: view)
            return view
        }

        func tryLoad(_ url: URL?, into view: WKWebView) {
            guard let url else { return }
            view.load(URLRequest(url: url))
        }
    }
#endif
