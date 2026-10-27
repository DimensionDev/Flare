import FlareAppleCore
import SwiftUI

public struct ProxyWebContent<Content: View>: View {
    @State private var proxy = WebViewProxy()
    private let content: (WebViewProxyRoute) -> Content

    public init(@ViewBuilder content: @escaping (WebViewProxyRoute) -> Content) {
        self.content = content
    }

    public var body: some View {
        Group {
            if proxy.failed || proxy.route?.failed == true {
                VStack(spacing: 12) {
                    Text("proxy_webview_error", bundle: FlareAppleUILocalization.bundle)
                        .multilineTextAlignment(.center)
                    Button { proxy.reload() } label: {
                        Text("action_retry", bundle: FlareAppleUILocalization.bundle)
                    }
                }
                .padding()
            } else if let route = proxy.route, route.isValid {
                content(route).id(route.key)
            } else {
                ProgressView()
            }
        }
        .onAppear { proxy.start() }
        .onDisappear { proxy.stop() }
    }
}
