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
    let url: String
    @State private var showTLDR = false
    
    var body: some View {
        StateView(state: presenter.state.data) { document in
            ScrollView {
                VStack(
                    alignment: .trailing,
                    spacing: 16,
                ) {
                    Text(document.title)
                        .font(.title)
                        .bold()
                        .frame(maxWidth: .infinity, alignment: .center)
                    
                    Button {
                        showTLDR = true
                    } label: {
                        Text("Summarize this article")
                    }
                    .backport
                    .glassProminentButtonStyle()
                    
                    if showTLDR {
                        ListCardView {
                            TLDRTextView(text: document.textContent)
                                .padding()
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }
                    
                    ListCardView {
                        HtmlWebView(
                            dynamicHeight: $webViewHeight,
                            htmlString: document.content,
                            baseURL: .init(string: url)
                        )
                        .frame(height: webViewHeight)
                        .padding()
//                        RichText(text: document.richText, expandImageSize: true)
//                            .padding()
                    }
                }
                .padding()
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
                    ListCardView {
                        Text(placeholderText)
                            .redacted(reason: .placeholder)
                            .padding()
                    }
                }
                .padding()
            }
        }
        .background(Color(.systemGroupedBackground))
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

extension RssDetailScreen {
    init(url: String) {
        self.url = url
        self._presenter = .init(wrappedValue: .init(presenter: RssDetailPresenter(url: url)))
    }
}



struct HtmlWebView: UIViewRepresentable {
    @Binding var dynamicHeight: CGFloat
    let htmlString: String?
    let baseURL: URL?
    
    class Coordinator: NSObject, WKNavigationDelegate {
        var parent: HtmlWebView

        init(_ parent: HtmlWebView) {
            self.parent = parent
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            webView.evaluateJavaScript("document.documentElement.scrollHeight", completionHandler: { (height, error) in
                DispatchQueue.main.async {
                    self.parent.dynamicHeight = height as! CGFloat
                }
            })
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    func makeUIView(context: Context) -> WKWebView {
        let webview = WKWebView()
        webview.scrollView.bounces = false
        webview.navigationDelegate = context.coordinator
        webview.scrollView.isScrollEnabled = false
        if let htmlString {
            webview.loadHTMLString(getHtmlData(html: htmlString), baseURL: baseURL)
        }
        return webview
    }
    func updateUIView(_ uiView: WKWebView, context: Context) {
        if let htmlString {
            uiView.loadHTMLString(getHtmlData(html: htmlString), baseURL: baseURL)
        }
    }
    
    func getHtmlData(html: String) -> String {
        return """
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
        body { font-family: -apple-system; }
        img { max-width: 100%; height: auto; }
        </style>
        </head>
        <body>
        \(html)
        </body>
        </html>
        """
    }
}


struct SafariView: UIViewControllerRepresentable {

    let url: URL

    func makeUIViewController(context: UIViewControllerRepresentableContext<SafariView>) -> SFSafariViewController {
        let config = SFSafariViewController.Configuration()
        config.entersReaderIfAvailable = true
        return SFSafariViewController(url: url, configuration: config)
    }

    func updateUIViewController(_ uiViewController: SFSafariViewController, context: UIViewControllerRepresentableContext<SafariView>) {

    }

}
