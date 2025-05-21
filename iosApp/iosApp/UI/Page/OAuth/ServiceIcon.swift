import Kingfisher
import os.log
import SVGView
import SwiftUI

struct ServiceIcon: View {
    let url: String
    let domain: String?
    var size: CGFloat = 48
    var contentMode: SwiftUI.ContentMode = .fill
    var clipShape: AnyShape? = nil
    var onSuccessURL: ((String) -> Void)? = nil
    @State private var currentURL: String
    @State private var urlIndex: Int = 0

    init(url: String, domain: String? = nil, size: CGFloat = 48, contentMode: SwiftUI.ContentMode = .fill, clipShape: AnyShape? = nil, onSuccessURL: ((String) -> Void)? = nil) {
        self.url = url
        self.domain = domain
        self.size = size
        self.contentMode = contentMode
        self.clipShape = clipShape
        self.onSuccessURL = onSuccessURL
        _currentURL = State(initialValue: url)
    }

    private var fallbackURLs: [String] {
        var urls: [String] = []
        // 如果初始 url 不为空，添加到列表
        if !url.isEmpty {
            urls.append(url)
        }
        // 无论初始 url 是否为空，都尝试 domain 相关的 URLs
        if let domain {
            urls.append(contentsOf: [
                "https://\(domain)/logo.svg",
                "https://\(domain)/apple-touch-icon.png",
                "https://\(domain)/favicon.ico",
            ])
        }
        return urls
    }

    private var isSVG: Bool {
        currentURL.lowercased().hasSuffix(".svg")
    }

    var body: some View {
        ZStack {
            if isSVG {
                SVGIconView(url: currentURL, size: size, contentMode: contentMode, onSuccess: handleSuccess, onFailure: handleFailure)
                    .frame(width: size, height: size)
                    .modifyIf(clipShape != nil) { view in
                        view.clipShape(clipShape!)
                    }
            } else {
                KFImage(URL(string: currentURL))
                    .placeholder {
                        Rectangle()
                            .foregroundColor(.gray.opacity(0.2))
                    }
                    .onSuccess { _ in
                        handleSuccess()
                    }
                    .onFailure { error in
                        os_log("[ServiceIcon] KFImage failed to load URL: %{public}@, error: %{public}@", log: .default, type: .error, currentURL, error.localizedDescription)
                        handleFailure()
                    }
                    .setProcessor(DownsamplingImageProcessor(size: CGSize(width: size * 2, height: size * 2)))
                    .fade(duration: 0.25)
                    .resizable()
                    .aspectRatio(contentMode: contentMode)
                    .frame(width: size, height: size)
                    .modifyIf(clipShape != nil) { view in
                        view.clipShape(clipShape!)
                    }
            }
        }
    }

    private func handleSuccess() {
        os_log("[ServiceIcon] Successfully loaded URL: %{public}@", log: .default, type: .debug, currentURL)
        onSuccessURL?(currentURL)
    }

    private func handleFailure() {
        os_log("[ServiceIcon] Failed to load URL: %{public}@", log: .default, type: .debug, currentURL)
        urlIndex += 1
        if urlIndex < fallbackURLs.count {
            let nextURL = fallbackURLs[urlIndex]
            os_log("[ServiceIcon] Trying next URL: %{public}@", log: .default, type: .debug, nextURL)
            currentURL = nextURL
        }
    }
}

// SVG专用视图
struct SVGIconView: View {
    let url: String
    let size: CGFloat
    let contentMode: SwiftUI.ContentMode
    let onSuccess: () -> Void
    let onFailure: () -> Void
    @State private var svgData: Data? = nil

    var body: some View {
        Group {
            if let data = svgData {
                SVGView(data: data)
                    .frame(width: size, height: size)
                    .aspectRatio(contentMode: contentMode)
            } else {
                Rectangle()
                    .foregroundColor(.gray.opacity(0.2))
                    .frame(width: size, height: size)
            }
        }
        .onAppear {
            loadSVG()
        }
        .onChange(of: url) { _ in
            loadSVG()
        }
    }

    private func loadSVG() {
        guard let url = URL(string: url) else {
            onFailure()
            return
        }

        URLSession.shared.dataTask(with: url) { data, _, error in
            DispatchQueue.main.async {
                if let data {
                    svgData = data
                    onSuccess()
                } else {
                    os_log("[SVGIconView] Failed to load SVG data: %{public}@", log: .default, type: .error, error?.localizedDescription ?? "Unknown error")
                    onFailure()
                }
            }
        }.resume()
    }
}

// 用于背景的扩展版本
struct ServiceIconBackground: View {
    let url: String
    let domain: String?
    var height: CGFloat = 80
    var blur: CGFloat = 3
    @State private var successURL: String?

    private var isSVG: Bool {
        (successURL ?? url).lowercased().hasSuffix(".svg")
    }

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                LazyHStack(spacing: 0) {
                    ForEach(0 ..< 3) { _ in
                        if isSVG {
                            SVGIconView(
                                url: successURL ?? url,
                                size: geometry.size.width / 3,
                                contentMode: .fill,
                                onSuccess: {},
                                onFailure: {}
                            )
                            .frame(width: geometry.size.width / 3)
                            .clipped()
                        } else {
                            KFImage(URL(string: successURL ?? url))
                                .placeholder {
                                    Rectangle()
                                        .foregroundColor(.gray.opacity(0.2))
                                }
                                .setProcessor(DownsamplingImageProcessor(size: CGSize(width: 150, height: 150)))
                                .fade(duration: 0.25)
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                                .frame(width: geometry.size.width / 3)
                                .clipped()
                        }
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(height: height)
                .blur(radius: blur)

                // 渐变遮罩层
                ZStack {
                    LinearGradient(
                        gradient: Gradient(colors: [
                            Color.black.opacity(0.3),
                            Color.black.opacity(0.5),
                            Color.black.opacity(0.3),
                        ]),
                        startPoint: .leading,
                        endPoint: .trailing
                    )

                    LinearGradient(
                        gradient: Gradient(colors: [
                            Color.black.opacity(0.2),
                            Color.black.opacity(0.6),
                        ]),
                        startPoint: .top,
                        endPoint: .bottom
                    )

                    RadialGradient(
                        gradient: Gradient(colors: [
                            Color.black.opacity(0.0),
                            Color.black.opacity(0.3),
                        ]),
                        center: .center,
                        startRadius: 0,
                        endRadius: height
                    )
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(
                ServiceIcon(url: url, domain: domain, size: 1) { successURL in
                    os_log("[ServiceIconBackground] Using successful URL: %{public}@", log: .default, type: .debug, successURL)
                    self.successURL = successURL
                }
                .frame(width: 0, height: 0)
            )
        }
        .frame(height: height)
    }
}
