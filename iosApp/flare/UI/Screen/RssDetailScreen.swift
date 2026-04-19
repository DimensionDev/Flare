import SwiftUI
import KotlinSharedUI
import SafariServices
import SwiftUIBackports
import WebKit

struct RssDetailScreen: View {
    @State private var webViewHeight: CGFloat = .zero
    let placeholderText = """
Lorem ipsum dolor sit amet, consectetur adipiscing elit. Etiam consectetur sapien a tortor sollicitudin fermentum. Vestibulum at tincidunt ipsum. In maximus justo dui, ut auctor ante faucibus vitae. Donec fermentum nec sem id viverra. Donec efficitur feugiat consectetur. Cras tempor suscipit tempus. Sed velit elit, vestibulum ut rhoncus in, pretium eget justo. Integer nec vulputate nisi.

Nullam eu augue ut est gravida pharetra. Cras ullamcorper sodales enim, eget feugiat metus aliquam in. Donec elementum venenatis nisi, non hendrerit sem commodo bibendum. Sed pharetra luctus ipsum, vitae lacinia sem. Proin sodales, enim sed vestibulum semper, nisi ipsum sagittis ante, at semper elit purus in ipsum. Fusce accumsan varius scelerisque. Pellentesque blandit risus purus, id convallis sem fermentum nec. Donec accumsan ullamcorper porta.

Vestibulum tempus turpis nibh. Praesent tempus varius mattis. Nullam et cursus diam, sit amet tincidunt quam. Nunc id nunc nibh. Etiam vel gravida metus. Quisque eget lacus hendrerit, aliquam diam non, faucibus dui. Praesent dignissim tortor in rutrum euismod. Pellentesque elit lorem, imperdiet sed lectus sed, venenatis molestie urna.

Mauris porttitor sapien ex, sed pharetra nibh mattis id. Interdum et malesuada fames ac ante ipsum primis in faucibus. Proin euismod congue risus, ut fermentum mi. Pellentesque sagittis commodo malesuada. Cras molestie vulputate nisl quis placerat. Integer egestas imperdiet sem, ac bibendum tortor imperdiet sed. Pellentesque maximus velit quis felis interdum tincidunt.

Maecenas fringilla vitae leo sit amet lacinia. Donec in dui a ex hendrerit volutpat. Etiam sit amet aliquet arcu. Phasellus placerat at eros eu ornare. Morbi venenatis mi sed tortor aliquet, nec volutpat ex commodo. In interdum elit ac leo efficitur, ultricies sodales enim feugiat. Fusce rutrum erat felis, malesuada varius risus imperdiet vitae. Ut bibendum sagittis metus, a placerat ex varius vel.

"""
    
    @StateObject private var presenter: KotlinPresenter<RssDetailPresenterState>
    @Environment(\.translateConfig) private var translateConfig
    let url: String
    @State private var showTLDR = false
    @State private var showTranslate = false
    
    private var shouldTranslate: Bool {
        translateConfig.preTranslate || showTranslate
    }
    
    var body: some View {
        StateView(state: presenter.state.data) { document in
            if shouldTranslate {
                RssTranslateProvider(document: document) { translatedTitle, translatedHtml, isTranslating in
                    RssArticleContentView(
                        document: document,
                        url: url,
                        showTLDR: $showTLDR,
                        showTranslate: $showTranslate,
                        webViewHeight: $webViewHeight,
                        translatedTitle: translatedTitle,
                        translatedHtml: translatedHtml,
                        isTranslating: isTranslating,
                        showTranslateButton: false
                    )
                }
            } else {
                RssArticleContentView(
                    document: document,
                    url: url,
                    showTLDR: $showTLDR,
                    showTranslate: $showTranslate,
                    webViewHeight: $webViewHeight,
                    translatedTitle: nil,
                    translatedHtml: nil,
                    isTranslating: false,
                    showTranslateButton: true
                )
            }
        } errorContent: { error in
            Text(error.message ?? "Unknown error")
        } loadingContent: {
            ScrollView {
                VStack {
                    Text("Loading...")
                        .font(.title)
                        .bold()
                        .redacted(reason: .placeholder)
                    Divider()
                    Text(placeholderText)
                        .redacted(reason: .placeholder)
                        .padding()
                }
                .padding()
            }
        }
        .toolbar {
            ToolbarItem {
                if let url = URL(string: url) {
                    Button {
                        UIApplication.shared.open(url)
                    } label: {
                        Label {
                            Text("Open in Browser")
                        } icon: {
                            Image(systemName: "safari")
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Thin wrapper that owns the translate presenter and passes results to content
private struct RssTranslateProvider<Content: View>: View {
    @StateObject private var translatePresenter: KotlinPresenter<RssDetailTranslatePresenterState>
    let document: DocumentData
    let content: (_ translatedTitle: String?, _ translatedHtml: String?, _ isTranslating: Bool) -> Content
    
    init(
        document: DocumentData,
        @ViewBuilder content: @escaping (_ translatedTitle: String?, _ translatedHtml: String?, _ isTranslating: Bool) -> Content
    ) {
        self.document = document
        self.content = content
        self._translatePresenter = .init(wrappedValue: .init(
            presenter: RssDetailTranslatePresenter(
                htmlContent: document.content,
                title: document.title,
                targetLanguage: Locale.current.language.languageCode?.identifier ?? "en"
            )
        ))
    }
    
    private var translatedTitle: String? {
        switch onEnum(of: translatePresenter.state.translatedTitle) {
        case .success(let data): return String(data.data)
        case .loading, .error: return nil
        }
    }
    
    private var translatedHtml: String? {
        switch onEnum(of: translatePresenter.state.translatedHtml) {
        case .success(let data): return String(data.data)
        case .loading, .error: return nil
        }
    }
    
    private var isTranslating: Bool {
        switch onEnum(of: translatePresenter.state.translatedHtml) {
        case .loading: return true
        case .success, .error: return false
        }
    }
    
    var body: some View {
        content(translatedTitle, translatedHtml, isTranslating)
    }
}

// MARK: - Single article content view used in both translated and untranslated modes
private struct RssArticleContentView: View {
    let document: DocumentData
    let url: String
    @Binding var showTLDR: Bool
    @Binding var showTranslate: Bool
    @Binding var webViewHeight: CGFloat
    let translatedTitle: String?
    let translatedHtml: String?
    let isTranslating: Bool
    let showTranslateButton: Bool

    @Environment(\.openURL) private var openURL
    
    var body: some View {
        ScrollView {
            VStack(
                spacing: 16
            ) {
                Text(translatedTitle ?? document.title)
                    .font(.title2)
                    .bold()
                
                if document.siteName != nil || document.byline != nil || document.publishDateTime != nil {
                    VStack(alignment: .leading, spacing: 4) {
                        if let siteName = document.siteName, let host = URL(string: url)?.host() {
                            HStack(spacing: 4) {
                                FavTabIcon(host: host)
                                    .frame(width: 16, height: 16)
                                Text(siteName)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }
                        HStack {
                            if let byline = document.byline {
                                Text(byline)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                            if let publishDateTime = document.publishDateTime {
                                Spacer()
                                DateTimeText(data: publishDateTime, fullTime: true)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                }
                
                Divider()
                
                if showTLDR {
                    TLDRTextView(text: document.textContent)
                        .padding()
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Divider()
                }
                
                if isTranslating {
                    ProgressView()
                        .padding(.vertical, 4)
                        .frame(maxWidth: .infinity, alignment: .center)
                }
                
                HtmlWebView(
                    dynamicHeight: $webViewHeight,
                    htmlString: translatedHtml ?? document.content,
                    baseURL: .init(string: url),
                    onOpenURL: { url in self.openURL(url) }
                )
                .frame(height: webViewHeight)
            }
            .padding()
        }
        .toolbar {
            ToolbarItem {
                if showTranslateButton {
                    Button {
                        showTranslate = true
                    } label: {
                        Image(.faLanguage)
                    }
                }
            }
            ToolbarItem {
                Button {
                    showTLDR = true
                } label: {
                    Text("Summarize")
                }
            }
        }
    }
}

extension RssDetailScreen {
    init(url: String, descriptionHtml: String? = nil, descriptionTitle: String? = nil) {
        self.url = url
        self._presenter = .init(wrappedValue: .init(presenter: RssDetailPresenter(url: url, descriptionHtml: descriptionHtml, descriptionTitle: descriptionTitle)))
    }
}

struct HtmlWebView: UIViewRepresentable {
    @Environment(\.colorScheme) private var colorScheme
    @Binding var dynamicHeight: CGFloat
    let htmlString: String?
    let baseURL: URL?
    let onOpenURL: ((URL) -> Void)?
    
    var forceColorScheme: ColorScheme? = nil
    
    class Coordinator: NSObject, WKNavigationDelegate {
        var parent: HtmlWebView
        init(_ parent: HtmlWebView) { self.parent = parent }

        @MainActor
        func webView(_ webView: WKWebView,
                     decidePolicyFor navigationAction: WKNavigationAction,
                     decisionHandler: @escaping @MainActor (WKNavigationActionPolicy) -> Void) {
            guard let targetURL = navigationAction.request.url else {
                decisionHandler(.allow)
                return
            }

            if targetURL.scheme == "flare-media-image",
               let components = URLComponents(url: targetURL, resolvingAgainstBaseURL: false),
               let imageUrl =
                components.queryItems?.first(where: { $0.name == "url" })?.value,
               let deeplink = URL(string: DeeplinkRoute.Media.MediaImage(uri: imageUrl, previewUrl: nil).toUri()) {
                parent.onOpenURL?(deeplink)
                decisionHandler(.cancel)
                return
            }
            
            if let baseURL = parent.baseURL,
               targetURL.host() == baseURL.host(),
               targetURL.scheme == baseURL.scheme {
                decisionHandler(.allow)
                return
            }
            
            parent.onOpenURL?(targetURL)
            decisionHandler(.cancel)
        }
        
        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            webView.evaluateJavaScript("document.documentElement.scrollHeight") { height, _ in
                DispatchQueue.main.async {
                    if let h = height as? CGFloat {
                        self.parent.dynamicHeight = h
                    } else if let d = height as? Double {
                        self.parent.dynamicHeight = CGFloat(d)
                    }
                }
            }
        }
    }
    
    func makeCoordinator() -> Coordinator { Coordinator(self) }
    
    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        let webview = WKWebView(frame: .zero, configuration: config)
        webview.navigationDelegate = context.coordinator
        webview.scrollView.bounces = false
        webview.scrollView.isScrollEnabled = false
        
        webview.isOpaque = false
        webview.backgroundColor = .clear
        webview.scrollView.backgroundColor = .clear
        
        applyStyleOverride(to: webview)
        
        if let html = htmlString {
            webview.loadHTMLString(getHtmlData(html: html, scheme: (forceColorScheme ?? colorScheme)),
                                   baseURL: baseURL)
        }
        return webview
    }
    
    func updateUIView(_ uiView: WKWebView, context: Context) {
        applyStyleOverride(to: uiView)
        if let html = htmlString {
            uiView.loadHTMLString(getHtmlData(html: html, scheme: (forceColorScheme ?? colorScheme)),
                                  baseURL: baseURL)
        }
    }
    
    private func applyStyleOverride(to webview: WKWebView) {
        if let forced = forceColorScheme {
            webview.overrideUserInterfaceStyle = (forced == .dark) ? .dark : .light
        } else {
            webview.overrideUserInterfaceStyle = .unspecified
        }
    }
    
    func getHtmlData(html: String, scheme: ColorScheme) -> String {
        """
        <!doctype html>
        <html>
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <meta name="color-scheme" content="light dark">
          <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/github-markdown-css/5.8.1/github-markdown.min.css">
          <style>
            .markdown-body img {
              display: block;
              width: 100%;
              height: auto;
            }
            .markdown-body video {
              display: block;
              width: 100%;
              height: auto;
            }
            .markdown-body img[data-flare-clickable="true"] {
              cursor: pointer;
            }
        
          </style>
          <script>
            function bindFlareImageClicks() {
              const images = document.querySelectorAll('.markdown-body img');
              images.forEach((img) => {
                if (img.dataset.flareClickable === 'true') {
                  return;
                }
                img.dataset.flareClickable = 'true';
                img.addEventListener('click', function(event) {
                  event.preventDefault();
                  event.stopPropagation();
                  const src = img.currentSrc || img.src;
                  if (!src) {
                    return;
                  }
                  window.location.href = 'flare-media-image://open?url=' + encodeURIComponent(src);
                });
              });
            }

            document.addEventListener('DOMContentLoaded', bindFlareImageClicks);
          </script>
        </head>
        <body>
        <article class="markdown-body">
          \(html)
        </article>
        <script>
          bindFlareImageClicks();
        </script>
        </body>
        </html>
        """
    }
}
